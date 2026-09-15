import json

import httpx
import pytest
from fastapi.testclient import TestClient

from app.agora_client import AgoraClient, AgoraUpstreamError
from app.config import Settings
from app.main import create_app


class FakeAgoraClient:
    def __init__(self) -> None:
        self.join_calls = 0
        self.text_calls = []

    def create_user_tokens(self, channel_name: str, rtc_uid: int):
        return f"rtc-{channel_name}-{rtc_uid}", f"rtm-{rtc_uid}", 2_000_000_000

    async def join_agent(self, **kwargs):
        self.join_calls += 1
        return {"agent_id": "agent-1", "create_ts": 1_700_000_000, "status": "started"}

    async def interrupt_agent(self, agent_id: str, channel_name: str):
        return None

    async def leave_agent(self, agent_id: str, channel_name: str):
        return None

    async def speak(self, **kwargs):
        self.text_calls.append(("speak", kwargs))

    async def think(self, **kwargs):
        self.text_calls.append(("think", kwargs))

    async def close(self):
        return None


def settings() -> Settings:
    return Settings(
        agora_app_id="0" * 32,
        agora_app_certificate="1" * 32,
    )


@pytest.mark.parametrize("field", ["agora_app_id", "agora_app_certificate"])
def test_startup_rejects_placeholder_credentials_before_serving_requests(field):
    values = {"agora_app_id": "0" * 32, "agora_app_certificate": "1" * 32}
    values[field] = "your_placeholder"
    with pytest.raises(RuntimeError, match=f"{field.upper()} must each be exactly 32 characters"):
        with TestClient(create_app(Settings(**values), FakeAgoraClient())):
            pass


class FailingStopSession:
    async def stop(self):
        raise RuntimeError("temporary stop failure")


@pytest.mark.asyncio
async def test_sdk_agent_configuration_creates_an_async_session():
    sdk_settings = Settings(
        agora_app_id="0" * 32,
        agora_app_certificate="1" * 32,
    )
    async with httpx.AsyncClient() as http_client:
        agora = AgoraClient(sdk_settings, http_client=http_client)
        session = agora._build_agent().create_async_session(
            channel="room-a",
            agent_uid="123456",
            remote_uids=["42"],
        )
        assert session.status == "idle"


@pytest.mark.asyncio
async def test_join_serializes_current_turn_detection_and_required_asr_params():
    requests = []

    def handle(request):
        requests.append(request)
        return httpx.Response(200, json={"agent_id": "agent-1", "create_ts": 1_700_000_000})

    sdk_settings = Settings(agora_app_id="0" * 32, agora_app_certificate="1" * 32)
    async with httpx.AsyncClient(transport=httpx.MockTransport(handle)) as http_client:
        agora = AgoraClient(sdk_settings, http_client=http_client)
        await agora.join_agent(channel_name="room-a", requester_rtc_uid=42)

    payload = json.loads(requests[0].content)
    properties = payload["properties"]
    assert requests[0].url.path.endswith("/join")
    # The SDK adds its own language field; deprecated detection controls must
    # stay out of the top-level object.
    assert not {"type", "threshold", "silence_duration_ms", "interrupt_mode"} & set(
        properties["turn_detection"]
    )
    config = properties["turn_detection"]["config"]
    assert config["start_of_speech"]["mode"] == "vad"
    assert config["end_of_speech"]["mode"] == "vad"
    assert properties["interruption"] == {"enable": True, "mode": "start_of_speech"}
    assert properties["asr"]["vendor"] == "deepgram"
    # Managed credentials resolve the model through an SDK-generated preset.
    assert "deepgram_nova_3" in payload["preset"]
    assert properties["asr"]["params"]["language"] == "en"
    filler = properties["filler_words"]
    assert filler["enable"] is True
    assert filler["content"]["mode"] == "generated"
    assert filler["content"]["generated_config"]["fallback_strategy"] == "static"
    assert len(filler["content"]["static_config"]["phrases"]) >= 1


@pytest.mark.asyncio
async def test_sdk_custom_tool_and_text_actions_reach_agora():
    requests = []

    def handle(request):
        requests.append(request)
        return httpx.Response(200, json={"agent_id": "agent-1"})

    sdk_settings = Settings(
        agora_app_id="0" * 32, agora_app_certificate="1" * 32,
        public_base_url="https://example.com",
    )
    async with httpx.AsyncClient(transport=httpx.MockTransport(handle)) as http_client:
        agora = AgoraClient(sdk_settings, http_client=http_client)
        await agora.join_agent(channel_name="room-a", requester_rtc_uid=42)
        await agora.speak("agent-1", "room-a", "Hello", "APPEND", True)
        await agora.think("agent-1", "room-a", "Explain setup", "append", "append", "append", True)

    properties = json.loads(requests[0].content)["properties"]
    assert properties["advanced_features"]["enable_tools"] is True
    tool = properties["llm"]["tools"][0]
    assert tool["function"]["name"] == "getProjectGuidance"
    assert tool["server"]["url"] == "https://example.com/v1/tools/guidance?topic={{args.topic}}"
    assert requests[1].url.path.endswith("/agents/agent-1/speak")
    assert json.loads(requests[1].content)["priority"] == "APPEND"
    assert requests[2].url.path.endswith("/agents/agent-1/think")
    instruction = json.loads(requests[2].content)
    assert instruction["text"] == "Explain setup"
    for state in ("listening", "thinking", "speaking"):
        assert instruction[f"on_{state}_action"] == "append"


def test_guidance_restricts_topics_and_text_actions_validate_sessions_and_input():
    fake = FakeAgoraClient()
    with TestClient(create_app(settings(), fake)) as client:
        assert "QUICKSTART_SERVER_URL" in client.get("/v1/tools/guidance?topic=setup").json()["content"]
        assert client.get("/v1/tools/guidance?topic=../../server/.env.local").status_code == 422
        channel = client.post("/v1/conversation/bootstrap", json={"requester_rtc_uid": 42}).json()["channel_name"]
        client.post("/v1/conversation/join", json={"channel_name": channel, "requester_rtc_uid": 42})
        body = {"channel_name": channel, "agent_id": "agent-1", "text": "Hello"}
        for action in ("speak", "think"):
            endpoint = f"/v1/conversation/{action}"
            assert client.post(endpoint, json={**body, "agent_id": "wrong"}).status_code == 400
            assert client.post(endpoint, json={**body, "text": "   "}).status_code == 422
            assert client.post(endpoint, json={**body, "text": "x" * 2001}).status_code == 422
            assert client.post(endpoint, json=body).status_code == 200
        assert fake.text_calls[0][1]["priority"] == "APPEND"
        assert fake.text_calls[1][1]["on_speaking_action"] == "append"
        assert client.post("/v1/conversation/think", json={**body, "on_speaking_action": "inject"}).status_code == 422
        client.post("/v1/conversation/leave", json=body)
        assert client.post("/v1/conversation/think", json=body).status_code == 404


@pytest.mark.asyncio
async def test_sdk_client_rejects_an_unknown_area():
    sdk_settings = Settings(
        agora_app_id="0" * 32,
        agora_app_certificate="1" * 32,
        agora_area="somewhere",
    )
    async with httpx.AsyncClient() as http_client:
        with pytest.raises(ValueError, match="Unsupported AGORA_AREA"):
            AgoraClient(sdk_settings, http_client=http_client)


@pytest.mark.asyncio
async def test_failed_stop_keeps_session_available_for_retry():
    agora = object.__new__(AgoraClient)
    agora._sessions = {"agent-1": ("room-a", FailingStopSession())}

    with pytest.raises(AgoraUpstreamError, match="temporary stop failure"):
        await agora.leave_agent("agent-1", "room-a")

    assert "agent-1" in agora._sessions


def test_health_and_bootstrap_are_available_without_client_auth():
    with TestClient(create_app(settings(), FakeAgoraClient())) as client:
        assert client.get("/health").status_code == 200
        assert client.post("/v1/conversation/bootstrap", json={}).status_code == 200


def test_full_conversation_contract_and_idempotent_join():
    fake = FakeAgoraClient()
    with TestClient(create_app(settings(), fake)) as client:
        bootstrap = client.post(
            "/v1/conversation/bootstrap",
            json={"requester_rtc_uid": 42, "requester_rtm_user_id": "42"},
        )
        assert bootstrap.status_code == 200
        session = bootstrap.json()
        assert session["app_id"] == "0" * 32
        assert session["rtc_token"].startswith("rtc-")

        join_body = {"channel_name": session["channel_name"], "requester_rtc_uid": 42}
        first_join = client.post("/v1/conversation/join", json=join_body)
        second_join = client.post("/v1/conversation/join", json=join_body)
        assert first_join.json()["agent_id"] == "agent-1"
        assert second_join.json()["agent_id"] == "agent-1"
        assert fake.join_calls == 1

        refresh = client.post(
            "/v1/conversation/refresh",
            json={
                "channel_name": session["channel_name"],
                "requester_rtc_uid": 42,
                "requester_rtm_user_id": "42",
            },
        )
        assert refresh.status_code == 200
        assert refresh.json()["rtm_token"] == "rtm-42"

        action = {"channel_name": session["channel_name"], "agent_id": "agent-1"}
        assert client.post("/v1/conversation/interrupt", json=action).status_code == 200
        assert client.post("/v1/conversation/leave", json=action).status_code == 200
        assert client.post("/v1/conversation/refresh", json={
            "channel_name": session["channel_name"],
            "requester_rtc_uid": 42,
            "requester_rtm_user_id": "42",
        }).status_code == 404
