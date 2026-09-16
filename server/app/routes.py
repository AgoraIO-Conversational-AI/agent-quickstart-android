from __future__ import annotations

import random
import secrets
import time
from pathlib import Path
from typing import Any, Literal

from fastapi import APIRouter, Depends, HTTPException, Request, status

from .agora_client import AgoraClient, AgoraTimeoutError, AgoraUpstreamError
from .config import Settings
from .schemas import (
    ActionResponse,
    AgentActionRequest,
    BootstrapRequest,
    BootstrapResponse,
    HealthResponse,
    JoinRequest,
    JoinResponse,
    RefreshRequest,
    RefreshResponse,
    SpeakRequest,
    ThinkRequest,
    VisualContextRequest,
    VisualContextResponse,
)
from .security import build_rate_limiter
from .session_store import SessionRecord, SessionStore


def create_router(settings: Settings, store: SessionStore, agora: AgoraClient) -> APIRouter:
    router = APIRouter()
    rate_limit = build_rate_limiter(settings)
    throttled = [Depends(rate_limit)]

    @router.get("/v1/tools/guidance", dependencies=throttled)
    def guidance(topic: Literal["setup", "troubleshooting"]):
        # Only these public project documents are readable; no arbitrary paths.
        path = Path(__file__).resolve().parents[2] / "docs" / f"{topic}.md"
        if not path.is_file():
            raise HTTPException(status_code=503, detail="Project guidance is unavailable.")
        return {"topic": topic, "source": f"docs/{topic}.md", "content": path.read_text(encoding="utf-8")}

    @router.get("/health", response_model=HealthResponse)
    async def health() -> HealthResponse:
        return HealthResponse(
            status="ok",
            version=settings.build_version,
            agora_configured=bool(settings.agora_app_id and settings.agora_app_certificate),
            active_sessions=await store.count(),
        )

    @router.post(
        "/v1/conversation/bootstrap",
        response_model=BootstrapResponse,
        dependencies=throttled,
    )
    async def bootstrap(body: BootstrapRequest) -> BootstrapResponse:
        rtc_uid = body.requester_rtc_uid or random.randint(100_000, 899_999)
        rtm_user_id = body.requester_rtm_user_id or str(rtc_uid)
        if rtm_user_id != str(rtc_uid):
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="requester_rtm_user_id must match requester_rtc_uid for the combined RTC/RTM token.",
            )
        channel = f"android-convoai-{int(time.time())}-{secrets.randbelow(900000) + 100000}"
        rtc_token, rtm_token, expires_at = agora.create_user_tokens(channel, rtc_uid)
        await store.put(
            SessionRecord(
                channel_name=channel,
                requester_rtc_uid=rtc_uid,
                requester_rtm_user_id=rtm_user_id,
                token_expires_at_unix=expires_at,
                created_at_unix=int(time.time()),
            )
        )
        return BootstrapResponse(
            app_id=settings.agora_app_id,
            agent_rtc_uid=settings.agent_uid,
            channel_name=channel,
            rtc_token=rtc_token,
            rtm_token=rtm_token,
            requester_rtc_uid=rtc_uid,
            requester_rtm_user_id=rtm_user_id,
            expires_at_unix=expires_at,
        )

    @router.post(
        "/v1/conversation/join",
        response_model=JoinResponse,
        dependencies=throttled,
    )
    async def join(body: JoinRequest) -> JoinResponse:
        async with store.join_guard(body.channel_name):
            record = await require_session(store, body.channel_name)
            if record.requester_rtc_uid != body.requester_rtc_uid:
                raise HTTPException(status_code=400, detail="Requester RTC UID does not match bootstrap.")
            if record.agent_id:
                return JoinResponse(
                    agent_id=record.agent_id,
                    created_at_unix=record.created_at_unix,
                    status=record.agent_state,
                )
            result = await call_agora(
                agora.join_agent(
                    channel_name=body.channel_name,
                    requester_rtc_uid=body.requester_rtc_uid,
                    agent_profile=body.agent_profile,
                    system_prompt=body.system_prompt,
                )
            )
            agent_id = str(result.get("agent_id", "")).strip()
            if not agent_id:
                raise HTTPException(status_code=502, detail="Agora response did not include agent_id.")
            created_at = int(result.get("create_ts") or time.time())
            agent_state = str(result.get("status") or "started")
            await store.set_agent(body.channel_name, agent_id, agent_state)
            return JoinResponse(
                agent_id=agent_id,
                created_at_unix=created_at,
                status=agent_state,
            )

    @router.post(
        "/v1/conversation/interrupt",
        response_model=ActionResponse,
        dependencies=throttled,
    )
    async def interrupt(body: AgentActionRequest) -> ActionResponse:
        await require_agent(store, body)
        await call_agora(agora.interrupt_agent(body.agent_id, body.channel_name))
        return ActionResponse(success=True, message="Agent interrupted.")

    @router.post(
        "/v1/conversation/leave",
        response_model=ActionResponse,
        dependencies=throttled,
    )
    async def leave(body: AgentActionRequest) -> ActionResponse:
        await require_agent(store, body)
        await call_agora(agora.leave_agent(body.agent_id, body.channel_name))
        await store.remove(body.channel_name)
        return ActionResponse(success=True, message="Agent left the channel.")

    @router.post("/v1/conversation/speak", response_model=ActionResponse, dependencies=throttled)
    async def speak(body: SpeakRequest) -> ActionResponse:
        await require_agent(store, body)
        await call_agora(agora.speak(**body.model_dump()))
        return ActionResponse(success=True, message="Speech request accepted.")

    @router.post("/v1/conversation/think", response_model=ActionResponse, dependencies=throttled)
    async def think(body: ThinkRequest) -> ActionResponse:
        await require_agent(store, body)
        await call_agora(agora.think(**body.model_dump()))
        return ActionResponse(success=True, message="Instruction accepted.")


    @router.post("/v1/conversation/visual-context", response_model=VisualContextResponse, dependencies=throttled)
    async def visual_context(body: VisualContextRequest) -> VisualContextResponse:
        await require_agent(store, body)
        summary = await call_agora(
            agora.analyze_camera_frame(
                image_base64=body.image_base64,
                mime_type=body.mime_type,
                question=body.question,
            )
        )
        instruction = (
            "Camera frame context for Live Lens. Use this visual summary as context for the next spoken turn. "
            "Do not claim you directly received a live video stream; refer to it as the latest camera frame if needed. "
            f"Summary: {summary}"
        )
        await call_agora(
            agora.think(
                agent_id=body.agent_id,
                channel_name=body.channel_name,
                text=instruction,
                on_listening_action="inject",
                on_thinking_action="append",
                on_speaking_action="append",
                interruptable=False,
            )
        )
        return VisualContextResponse(success=True, summary=summary)

    @router.post(
        "/v1/conversation/refresh",
        response_model=RefreshResponse,
        dependencies=throttled,
    )
    async def refresh(body: RefreshRequest) -> RefreshResponse:
        record = await require_session(store, body.channel_name)
        if (
            record.requester_rtc_uid != body.requester_rtc_uid
            or record.requester_rtm_user_id != body.requester_rtm_user_id
        ):
            raise HTTPException(status_code=400, detail="Refresh identity does not match bootstrap.")
        rtc_token, rtm_token, expires_at = agora.create_user_tokens(
            body.channel_name,
            body.requester_rtc_uid,
        )
        record.token_expires_at_unix = expires_at
        await store.put(record)
        return RefreshResponse(
            rtc_token=rtc_token,
            rtm_token=rtm_token,
            expires_at_unix=expires_at,
        )

    return router


async def require_session(store: SessionStore, channel_name: str) -> SessionRecord:
    record = await store.get(channel_name)
    if record is None:
        raise HTTPException(status_code=404, detail="Conversation session was not found or expired.")
    return record


async def require_agent(store: SessionStore, body: AgentActionRequest) -> SessionRecord:
    record = await require_session(store, body.channel_name)
    if record.agent_id != body.agent_id:
        raise HTTPException(status_code=400, detail="Agent ID does not match the conversation session.")
    return record


async def call_agora(awaitable: Any) -> Any:
    try:
        return await awaitable
    except AgoraTimeoutError as exc:
        raise HTTPException(status_code=504, detail=str(exc)) from exc
    except AgoraUpstreamError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc
