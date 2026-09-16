from __future__ import annotations

import secrets
import time
from typing import Any

import httpx
from agora_agent import Agent, Area, AsyncAgora, GeminiLive
from agora_agent.agentkit import generate_convo_ai_token
from agora_agent.core.api_error import ApiError

from .config import Settings


DEFAULT_SYSTEM_PROMPT = """You are Live Lens, a realtime voice assistant. Help the user reason about what they describe from their surroundings or camera view. Be concise, ask for clarification when visual details are missing, and do not claim that you can see the camera feed unless visual context is explicitly provided to you."""


class AgoraUpstreamError(RuntimeError):
    pass


class AgoraTimeoutError(TimeoutError):
    pass


AREA_BY_NAME = {
    "NORTH_AMERICA": Area.US,
    "US": Area.US,
    "EUROPE": Area.EU,
    "EU": Area.EU,
    "ASIA_PACIFIC": Area.AP,
    "AP": Area.AP,
    "CHINA": Area.CN,
    "CN": Area.CN,
}




def _humanize_agora_start_error(exc: BaseException, model: str) -> str:
    raw = str(exc).strip()
    generic_preview_rejection = (
        "status_code: 400" in raw
        and "reason" in raw
        and "success" in raw.lower()
        and "detail" in raw
    )
    if generic_preview_rejection and model.startswith("models/gemini-3.8-live"):
        return (
            "Agora accepted the request format but rejected the Gemini Live preview start. "
            f"The configured Google key/project likely does not have access to {model}, "
            "or the Agora project is not enabled for the Gemini Live preview. "
            "Use a Gemini API key that lists this model, then restart the local server."
        )
    return f"Agora Conversational AI start failed: {raw}"

class AgoraClient:
    def __init__(self, settings: Settings, http_client: httpx.AsyncClient | None = None) -> None:
        self.settings = settings
        self._http = http_client or httpx.AsyncClient(timeout=httpx.Timeout(60.0))
        self._owns_http = http_client is None
        area_name = settings.agora_area.strip().upper()
        if area_name not in AREA_BY_NAME:
            supported = ", ".join(sorted(AREA_BY_NAME))
            raise ValueError(f"Unsupported AGORA_AREA '{settings.agora_area}'. Use one of: {supported}.")
        self._client = AsyncAgora(
            area=AREA_BY_NAME[area_name],
            app_id=settings.agora_app_id,
            app_certificate=settings.agora_app_certificate,
            httpx_client=self._http,
        )
        self._sessions: dict[str, tuple[str, Any]] = {}

    def create_user_tokens(self, channel_name: str, rtc_uid: int) -> tuple[str, str, int]:
        token = generate_convo_ai_token(
            app_id=self.settings.agora_app_id,
            app_certificate=self.settings.agora_app_certificate,
            channel_name=channel_name,
            uid=rtc_uid,
            token_expire=self.settings.token_expiry_seconds,
        )
        expires_at = int(time.time()) + self.settings.token_expiry_seconds
        return token, token, expires_at

    def _build_agent(self, system_prompt: str | None = None) -> Agent:
        tools = []
        if self.settings.public_base_url and not self.settings.llm_model.lower().startswith("gemini"):
            tools.append({
                "type": "function",
                "function": {
                    "name": "getProjectGuidance",
                    "description": "Look up this Android quickstart's setup or troubleshooting instructions. Use for questions about configuring or debugging this project.",
                    "parameters": {
                        "type": "object",
                        "properties": {"topic": {"type": "string", "enum": ["setup", "troubleshooting"]}},
                        "required": ["topic"],
                        "additionalProperties": False,
                    },
                },
                "execution": {"mode": "sync"},
                "server": {
                    "method": "GET",
                    "url": self.settings.public_base_url.rstrip("/") + "/v1/tools/guidance?topic={{args.topic}}",
                    "timeout_ms": 5000,
                },
            })
        agent = Agent(
            client=self._client,
            advanced_features={"enable_rtm": True},
            parameters={
                "audio_scenario": "chorus",
                "data_channel": "rtm",
                "enable_error_message": True,
                "enable_metrics": True,
            },
        )

        return agent.with_mllm(
            GeminiLive(
                api_key=self.settings.gemini_api_key,
                model=self.settings.llm_model,
                instructions=system_prompt or DEFAULT_SYSTEM_PROMPT,
                voice=self.settings.gemini_voice,
                input_modalities=["audio"],
                output_modalities=["audio"],
                transcribe_agent=True,
                transcribe_user=True,
                http_options={"api_version": self.settings.gemini_api_version},
                thinking_level=self.settings.gemini_thinking_level or None,
                turn_detection={
                    "mode": "agora_vad",
                    "agora_vad_config": {
                        "interrupt_duration_ms": 160,
                        "prefix_padding_ms": 800,
                        "silence_duration_ms": 640,
                        "threshold": 0.5,
                    },
                },
            )
        )

    async def join_agent(
        self,
        channel_name: str,
        requester_rtc_uid: int,
        agent_profile: str | None = None,
        system_prompt: str | None = None,
    ) -> dict[str, Any]:
        session = self._build_agent(system_prompt).create_async_session(
            channel=channel_name,
            agent_uid=str(self.settings.agent_uid),
            remote_uids=[str(requester_rtc_uid)],
            name=f"android-server-agent-{int(time.time())}-{secrets.randbelow(9000) + 1000}",
            idle_timeout=30,
            preset=agent_profile,
            expires_in=self.settings.token_expiry_seconds,
            debug=False,
        )
        try:
            agent_id = await session.start()
        except httpx.TimeoutException as exc:
            raise AgoraTimeoutError("Agora request timed out.") from exc
        except (ApiError, httpx.HTTPError, RuntimeError, ValueError) as exc:
            message = _humanize_agora_start_error(exc, self.settings.llm_model)
            raise AgoraUpstreamError(message) from exc
        if not agent_id:
            raise AgoraUpstreamError("Agora response did not include agent_id.")
        self._sessions[agent_id] = (channel_name, session)
        return {
            "agent_id": agent_id,
            "create_ts": int(time.time()),
            "status": "started",
        }


    async def analyze_camera_frame(
        self,
        image_base64: str,
        mime_type: str,
        question: str | None = None,
    ) -> str:
        prompt = (
            "You are the vision module for Live Lens. Describe the visible scene in 2-4 concise sentences. "
            "Focus on objects, text, device state, hazards, and details useful for answering the user's next spoken question. "
            "If the image is blurry or unclear, say what is uncertain."
        )
        if question:
            prompt += f" User focus: {question.strip()}"
        url = (
            "https://generativelanguage.googleapis.com/"
            f"{self.settings.gemini_api_version}/{self.settings.gemini_vision_model}:generateContent"
        )
        payload = {
            "contents": [
                {
                    "role": "user",
                    "parts": [
                        {"text": prompt},
                        {
                            "inline_data": {
                                "mime_type": mime_type,
                                "data": image_base64,
                            }
                        },
                    ],
                }
            ],
            "generationConfig": {
                "temperature": 0.2,
                "maxOutputTokens": 220,
            },
        }
        try:
            response = await self._http.post(
                url,
                params={"key": self.settings.gemini_api_key},
                json=payload,
            )
            response.raise_for_status()
        except httpx.TimeoutException as exc:
            raise AgoraTimeoutError("Gemini vision request timed out.") from exc
        except httpx.HTTPStatusError as exc:
            detail = exc.response.text[:500]
            raise AgoraUpstreamError(f"Gemini vision request failed with status {exc.response.status_code}: {detail}") from exc
        except httpx.HTTPError as exc:
            raise AgoraUpstreamError(f"Gemini vision request failed: {exc}") from exc

        body = response.json()
        parts = (
            body.get("candidates", [{}])[0]
            .get("content", {})
            .get("parts", [])
        )
        summary = " ".join(
            str(part.get("text", "")).strip()
            for part in parts
            if part.get("text")
        ).strip()
        if not summary:
            raise AgoraUpstreamError("Gemini vision returned no summary for the camera frame.")
        return summary[:1200]

    async def interrupt_agent(self, agent_id: str, channel_name: str) -> None:
        session = self._require_session(agent_id, channel_name)
        try:
            await session.interrupt()
        except httpx.TimeoutException as exc:
            raise AgoraTimeoutError("Agora interrupt request timed out.") from exc
        except (ApiError, httpx.HTTPError, RuntimeError) as exc:
            raise AgoraUpstreamError(f"Agora agent interrupt failed: {exc}") from exc

    async def speak(self, agent_id: str, channel_name: str, text: str, priority: str, interruptable: bool) -> None:
        session = self._require_session(agent_id, channel_name)
        try:
            await session.say(text, priority=priority, interruptable=interruptable)
        except httpx.TimeoutException as exc:
            raise AgoraTimeoutError("Agora speech request timed out.") from exc
        except (ApiError, httpx.HTTPError, RuntimeError, ValueError) as exc:
            raise AgoraUpstreamError(f"Agora speech request failed: {exc}") from exc

    async def think(self, agent_id: str, channel_name: str, text: str, on_listening_action: str, on_thinking_action: str, on_speaking_action: str, interruptable: bool) -> None:
        session = self._require_session(agent_id, channel_name)
        try:
            await session.think(
                text,
                on_listening_action=on_listening_action,
                on_thinking_action=on_thinking_action,
                on_speaking_action=on_speaking_action,
                interruptable=interruptable,
            )
        except httpx.TimeoutException as exc:
            raise AgoraTimeoutError("Agora instruction request timed out.") from exc
        except (ApiError, httpx.HTTPError, RuntimeError, ValueError) as exc:
            raise AgoraUpstreamError(f"Agora instruction request failed: {exc}") from exc

    async def leave_agent(self, agent_id: str, channel_name: str) -> None:
        session = self._require_session(agent_id, channel_name)
        try:
            await session.stop()
        except httpx.TimeoutException as exc:
            raise AgoraTimeoutError("Agora stop request timed out.") from exc
        except (ApiError, httpx.HTTPError, RuntimeError) as exc:
            raise AgoraUpstreamError(f"Agora agent stop failed: {exc}") from exc
        self._sessions.pop(agent_id, None)

    def _require_session(self, agent_id: str, channel_name: str) -> Any:
        active = self._sessions.get(agent_id)
        if active is None or active[0] != channel_name:
            raise AgoraUpstreamError("The Agora agent session is not active in this server process.")
        return active[1]

    async def close(self) -> None:
        if self._owns_http:
            await self._http.aclose()
