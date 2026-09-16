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
        self.vision_calls = []

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

    async def analyze_camera_frame(self, **kwargs):
        self.vision_calls.append(kwargs)
        return "A development board is connected by USB on a wooden desk."

    async def close(self):
        return None


def settings() -> Settings:
    return Settings(
        agora_app_id="0" * 32,
        agora_app_certificate="1" * 32,
        gemini_api_key="test-gemini-key",
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
        gemini_api_key="test-gemini-key",
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
async def test_join_serializes_current_turn_detection_and_required_mllm_params():
    requests = []

    def handle(request):
        requests.append(request)
        return httpx.Response(200, json={"agent_id": "agent-1", "create_ts": 1_700_000_000})

    sdk_settings = Settings(
        agora_app_id="0" * 32,
        agora_app_certificate="1" * 32,
        gemini_api_key="test-gemini-key",
    )
    async with httpx.AsyncClient(transport=httpx.MockTransport(handle)) as http_client:
        agora = AgoraClient(sdk_settings, http_client=http_client)
        await agora.join_agent(channel_name="room-a", requester_rtc_uid=42)

    payload = json.loads(requests[0].content)
    properties = payload["properties"]
    assert requests[0].url.path.endswith("/join")
    assert "turn_detection" not in properties
    assert properties["mllm"]["vendor"] == "gemini"
    assert properties["mllm"]["api_key"] == "test-gemini-key"
    assert properties["mllm"]["input_modalities"] == ["audio"]
    assert properties["mllm"]["output_modalities"] == ["audio"]
    assert properties["mllm"]["params"]["model"] == "models/gemini-3.8-live"
    assert properties["mllm"]["params"]["voice"] == "Charon"
    assert properties["mllm"]["params"]["http_options"] == {"api_version": "v1beta"}
    assert properties["mllm"]["params"]["transcribe_agent"] is True
    assert properties["mllm"]["params"]["transcribe_user"] is True
    assert properties["mllm"]["params"]["instructions"].startswith("You are Live Lens")
    assert properties["mllm"]["turn_detection"]["mode"] == "agora_vad"
    assert properties["mllm"]["turn_detection"]["agora_vad_config"]["threshold"] == 0.5
    assert "filler_words" not in properties
    assert "interruption" not in properties


@pytest.mark.asyncio
async def test_sdk_text_actions_reach_agora_with_gemini_mllm():
    requests = []

    def handle(request):
        requests.append(request)
        return httpx.Response(200, json={"agent_id": "agent-1"})

    sdk_settings = Settings(
        agora_app_id="0" * 32, agora_app_certificate="1" * 32,
        gemini_api_key="test-gemini-key",
        public_base_url="https://example.com",
    )
    async with httpx.AsyncClient(transport=httpx.MockTransport(handle)) as http_client:
        agora = AgoraClient(sdk_settings, http_client=http_client)
        await agora.join_agent(channel_name="room-a", requester_rtc_uid=42)
        await agora.speak("agent-1", "room-a", "Hello", "APPEND", True)
        await agora.think("agent-1", "room-a", "Explain setup", "append", "append", "append", True)

    properties = json.loads(requests[0].content)["properties"]
    assert properties["mllm"]["vendor"] == "gemini"
    assert properties["advanced_features"]["enable_rtm"] is True
    assert properties["llm"] is None
    assert properties["asr"] is None
    assert properties["tts"] is None
    assert requests[1].url.path.endswith("/agents/agent-1/speak")
    assert json.loads(requests[1].content)["priority"] == "APPEND"
    assert requests[2].url.path.endswith("/agents/agent-1/think")
    instruction = json.loads(requests[2].content)
    assert instruction["text"] == "Explain setup"
    for state in ("listening", "thinking", "speaking"):
        assert instruction[f"on_{state}_action"] == "append"



def test_visual_context_analyzes_frame_and_injects_agent_context():
    fake = FakeAgoraClient()
    with TestClient(create_app(settings(), fake)) as client:
        session = client.post("/v1/conversation/bootstrap", json={"requester_rtc_uid": 42}).json()
        join = client.post(
            "/v1/conversation/join",
            json={"channel_name": session["channel_name"], "requester_rtc_uid": 42},
        ).json()
        response = client.post(
            "/v1/conversation/visual-context",
            json={
                "channel_name": session["channel_name"],
                "agent_id": join["agent_id"],
                "image_base64": "ZmFrZS1qcGVn",
                "mime_type": "image/jpeg",
                "question": "What board is this?",
            },
        )

    assert response.status_code == 200
    assert "development board" in response.json()["summary"]
    assert fake.vision_calls[0]["image_base64"] == "ZmFrZS1qcGVn"
    assert fake.vision_calls[0]["question"] == "What board is this?"
    assert fake.text_calls[-1][0] == "think"
    assert "Camera frame context" in fake.text_calls[-1][1]["text"]
    assert fake.text_calls[-1][1]["on_listening_action"] == "inject"


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
        gemini_api_key="test-gemini-key",
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
