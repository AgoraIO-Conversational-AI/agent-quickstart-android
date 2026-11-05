from __future__ import annotations

import asyncio
import json
import random
import secrets
import time
from pathlib import Path
from typing import Any, Literal

from fastapi import APIRouter, Depends, HTTPException, Request, status
from fastapi.responses import StreamingResponse

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
)
from .document_bridge import DocumentBridge
from .schemas import DocumentPollRequest, DocumentResultRequest, DocumentStatusRequest, LocalChatRequest
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
    async def join(body: JoinRequest, request: Request) -> JoinResponse:
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
            document_llm_url = None
            if body.document_token:
                base = settings.public_base_url.rstrip("/")
                if not base.startswith("https://"):
                    raise HTTPException(400, "Set PUBLIC_BASE_URL to the backend HTTPS tunnel URL for document conversations.")
                record.document_bridge = DocumentBridge(body.document_token)
                document_llm_url = base + "/v1/document/chat/completions?channel=" + body.channel_name
            result = await call_agora(
                agora.join_agent(
                    channel_name=body.channel_name,
                    requester_rtc_uid=body.requester_rtc_uid,
                    agent_profile=body.agent_profile,
                    system_prompt=body.system_prompt,
                    **({"document_llm_url": document_llm_url, "document_token": body.document_token} if document_llm_url else {}),
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

    async def require_document(channel: str, token: str):
        record = await require_session(store, channel)
        bridge = record.document_bridge
        if not bridge or not secrets.compare_digest(bridge.token, token):
            raise HTTPException(403, "Invalid document session.")
        return bridge

    @router.post("/v1/document/request-status")
    async def document_request_status(body: DocumentStatusRequest):
        bridge = await require_document(body.channel_name, body.token)
        pending = bridge.pending.get(body.request_id)
        return {"active": pending is not None and not pending.result.done()}

    @router.post("/v1/document/chat/completions")
    async def local_chat(body: LocalChatRequest, channel: str, request: Request):
        authorization = request.headers.get("authorization", "")
        if not authorization.startswith("Bearer "):
            raise HTTPException(403, "Document model authorization required.")
        bridge = await require_document(channel, authorization[7:])
        turns = []
        for message in body.messages:
            if message.get("role") not in ("user", "assistant"):
                continue
            content = message.get("content", "")
            if isinstance(content, list):
                content = " ".join(part.get("text", "") for part in content if isinstance(part, dict) and part.get("type") == "text")
            if isinstance(content, str) and content.strip():
                turns.append((message["role"], content[:2000]))
        users = [text for role, text in turns if role == "user"]
        if not users:
            raise HTTPException(400, "A user question is required.")
        question = users[-1][:1000]
        history = "\n".join(role + ": " + text for role, text in turns[:-1])[-1500:]
        previous = users[-2][:500] if len(users) > 1 else ""
        # A new speech turn supersedes outstanding local generation for this session.
        for pending in list(bridge.pending.values()):
            if not pending.result.done():
                pending.result.cancel()
        updates = asyncio.Queue(maxsize=1)
        task = asyncio.create_task(bridge.search(question, history, previous, updates=updates))
        completion_id = "chatcmpl-" + secrets.token_hex(12)

        def chunk(delta, finish=None):
            return {"id": completion_id, "object": "chat.completion.chunk", "created": int(time.time()),
                    "model": "on-device-gemma", "choices": [{"index": 0, "delta": delta, "finish_reason": finish}]}

        async def events():
            try:
                yield "data: " + json.dumps(chunk({"role": "assistant"})) + "\n\n"
                emitted = ""
                while not task.done():
                    if await request.is_disconnected():
                        return
                    update = asyncio.create_task(updates.get())
                    try:
                        completed, _ = await asyncio.wait({task, update}, timeout=1, return_when=asyncio.FIRST_COMPLETED)
                        if update in completed:
                            snapshot = update.result()
                            if snapshot.startswith(emitted):
                                delta = snapshot[len(emitted):]
                                emitted = snapshot
                                if delta:
                                    yield "data: " + json.dumps(chunk({"content": delta})) + "\n\n"
                        elif not task.done():
                            yield ": waiting for on-device Gemma\n\n"
                    finally:
                        update.cancel()
                        await asyncio.gather(update, return_exceptions=True)
                result = await task
                answer = result.get("answer") or ("" if emitted else "The on-device model is unavailable. Please keep the Android app open and try again.")
                if answer.startswith(emitted):
                    remainder = answer[len(emitted):]
                    if remainder:
                        yield "data: " + json.dumps(chunk({"content": remainder})) + "\n\n"
                yield "data: " + json.dumps(chunk({}, "stop")) + "\n\n"
                yield "data: [DONE]\n\n"
            finally:
                task.cancel()
                await asyncio.gather(task, return_exceptions=True)

        return StreamingResponse(events(), media_type="text/event-stream", headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"})

    @router.post("/v1/document/poll")
    async def poll_document(body: DocumentPollRequest):
        bridge = await require_document(body.channel_name, body.token)
        return await bridge.poll()

    @router.post("/v1/document/result")
    async def document_result(body: DocumentResultRequest):
        bridge = await require_document(body.channel_name, body.token)
        pending = bridge.pending.get(body.request_id)
        if pending is None or pending.result.done():
            raise HTTPException(409, "Document request expired.")
        if not body.answer.startswith(pending.latest_answer):
            raise HTTPException(409, "Stale or inconsistent answer snapshot.")
        pending.latest_answer = body.answer
        if pending.updates is not None:
            if pending.updates.full():
                pending.updates.get_nowait()
            pending.updates.put_nowait(body.answer)
        if body.final:
            pending.result.set_result({"answer": body.answer})
        return {"success": True}

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
