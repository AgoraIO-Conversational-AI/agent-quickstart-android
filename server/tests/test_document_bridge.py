import asyncio
import json

import httpx
import pytest
from app.document_bridge import DocumentBridge
from app.main import create_app
from app.config import Settings
from app.agora_client import AgoraClient
from test_api import FakeAgoraClient


@pytest.mark.asyncio
async def test_search_roundtrip_and_end_cleanup():
    bridge = DocumentBridge("a" * 32)
    search = asyncio.create_task(bridge.search("amber indicator"))
    question = await bridge.poll()
    assert question["question"] == "amber indicator"
    bridge.pending[question["request_id"]].result.set_result({"sources": [{"paragraph": 1, "text": "Clean filter"}]})
    assert (await search)["sources"][0]["text"] == "Clean filter"
    assert bridge.pending == {}
    search = asyncio.create_task(bridge.search("battery"))
    await bridge.poll()
    bridge.close()
    assert (await search)["sources"] == []
    assert bridge.pending == {}


@pytest.mark.asyncio
async def test_bounded_queue_fails_closed():
    bridge = DocumentBridge("a" * 32, queue=asyncio.Queue(maxsize=1))
    first = asyncio.create_task(bridge.search("first"))
    await asyncio.sleep(0)
    assert (await bridge.search("second"))["sources"] == []
    bridge.close()
    await first
    assert not bridge.pending


@pytest.mark.asyncio
async def test_local_chat_roundtrip_isolates_sessions_and_rejects_late_results():
    settings = Settings(agora_app_id="0" * 32, agora_app_certificate="1" * 32, public_base_url="https://example.com")
    app = create_app(settings, FakeAgoraClient())
    async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="https://example.com") as client:
        boot = (await client.post("/v1/conversation/bootstrap", json={})).json()
        channel = boot["channel_name"]
        token = "a" * 32
        joined = await client.post("/v1/conversation/join", json={"channel_name": channel, "requester_rtc_uid": boot["requester_rtc_uid"], "document_token": token})
        assert joined.status_code == 200
        assert (await client.post("/v1/document/poll", json={"channel_name": channel, "token": "b" * 32})).status_code == 403
        search = asyncio.create_task(client.post("/v1/document/chat/completions", params={"channel": channel},
            headers={"Authorization": "Bearer " + token}, json={"messages": [{"role": "user", "content": "battery"}]}))
        request = (await client.post("/v1/document/poll", json={"channel_name": channel, "token": token})).json()
        result = {"channel_name": channel, "token": token, "request_id": request["request_id"], "sources": [{"paragraph": 2, "text": "Six hours"}], "answer": "The battery lasts six hours."}
        assert (await client.post("/v1/document/result", json=result)).status_code == 200
        assert "The battery lasts six hours." in (await search).text
        assert (await client.post("/v1/document/result", json=result)).status_code == 409
        await client.post("/v1/conversation/leave", json={"channel_name": channel, "agent_id": "agent-1"})
        assert (await client.post("/v1/document/poll", json={"channel_name": channel, "token": token})).status_code == 404


@pytest.mark.asyncio
async def test_document_join_uses_custom_gemma_without_cloud_llm_or_filler():
    requests = []
    def handle(request):
        requests.append(request)
        return httpx.Response(200, json={"agent_id": "agent-1"})
    settings = Settings(agora_app_id="0" * 32, agora_app_certificate="1" * 32)
    async with httpx.AsyncClient(transport=httpx.MockTransport(handle)) as client:
        agora = AgoraClient(settings, http_client=client)
        await agora.join_agent("room-a", 42, document_llm_url="https://example.com/v1/document/chat/completions?channel=room-a", document_token="a" * 32)
    properties = json.loads(requests[0].content)["properties"]
    assert not properties["advanced_features"]["enable_tools"]
    assert properties["llm"]["vendor"] == "custom"
    assert properties["llm"]["params"]["model"] == "on-device-gemma"
    assert properties["llm"]["api_key"] == "a" * 32
    assert properties["llm"]["url"].startswith("https://example.com/v1/document/chat/completions")
    assert not properties["filler_words"]["enable"]


@pytest.mark.asyncio
async def test_timeout_returns_no_sources_and_discards_pending_request():
    bridge = DocumentBridge("a" * 32, timeout_seconds=0.01)
    result = await bridge.search("a question while phone is disconnected")
    assert result["sources"] == []
    assert "unavailable" in result["error"]
    assert bridge.pending == {}


@pytest.mark.asyncio
async def test_partial_answer_is_streamed_before_generation_finishes_without_duplicates():
    from app.schemas import LocalChatRequest
    settings = Settings(agora_app_id="0" * 32, agora_app_certificate="1" * 32, public_base_url="https://example.com")
    app = create_app(settings, FakeAgoraClient())
    async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="https://example.com") as client:
        boot = (await client.post("/v1/conversation/bootstrap", json={})).json()
        channel, token = boot["channel_name"], "a" * 32
        await client.post("/v1/conversation/join", json={"channel_name": channel, "requester_rtc_uid": boot["requester_rtc_uid"], "document_token": token})
        from app.routes import create_router
        router = create_router(settings, app.state.session_store, FakeAgoraClient())
        endpoint = next(route.endpoint for route in router.routes if getattr(route, "path", "") == "/v1/document/chat/completions")
        class Request:
            headers = {"authorization": "Bearer " + token}
            async def is_disconnected(self):
                return False
        response = await endpoint(LocalChatRequest(messages=[{"role": "user", "content": "battery"}]), channel, Request())
        stream = response.body_iterator
        await anext(stream)  # assistant role header
        first_delta = asyncio.create_task(anext(stream))
        request = (await client.post("/v1/document/poll", json={"channel_name": channel, "token": token})).json()
        body = {"channel_name": channel, "token": token, "request_id": request["request_id"], "answer": "The battery", "final": False}
        assert (await client.post("/v1/document/result", json=body)).status_code == 200
        first = await asyncio.wait_for(first_delta, timeout=2)
        assert json.loads(first.removeprefix("data: "))["choices"][0]["delta"]["content"] == "The battery"
        assert (await client.post("/v1/document/request-status", json={key: body[key] for key in ("channel_name", "token", "request_id")})).json()["active"]
        assert (await client.post("/v1/document/result", json={**body, "answer": "wrong prefix"})).status_code == 409
        await client.post("/v1/document/result", json={**body, "answer": "The battery lasts six hours.", "final": True})
        remaining = "".join([chunk async for chunk in stream])
        assert 'lasts six hours.' in remaining
        assert '"content": "The battery' not in remaining
        assert '[DONE]' in remaining
