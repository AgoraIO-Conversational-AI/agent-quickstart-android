"""Bounded, ephemeral rendezvous between an Agora tool call and Android retrieval."""
import asyncio
import secrets
from dataclasses import dataclass, field


@dataclass
class SearchRequest:
    request_id: str
    question: str
    result: asyncio.Future
    history: str = ""
    previous_question: str = ""
    updates: asyncio.Queue | None = None
    latest_answer: str = ""


@dataclass
class DocumentBridge:
    token: str
    timeout_seconds: float = 150
    queue: asyncio.Queue = field(default_factory=lambda: asyncio.Queue(maxsize=4))
    pending: dict = field(default_factory=dict)

    async def search(self, question: str, history: str = "", previous_question: str = "", updates: asyncio.Queue | None = None) -> dict:
        request = SearchRequest(secrets.token_hex(16), question, asyncio.get_running_loop().create_future(), history, previous_question, updates)
        self.pending[request.request_id] = request
        try:
            self.queue.put_nowait(request)
            return await asyncio.wait_for(request.result, timeout=self.timeout_seconds)
        except (asyncio.TimeoutError, asyncio.QueueFull):
            return {"error": "On-device Gemma timed out or is unavailable. Keep the Android app open and retry.", "sources": []}
        finally:
            self.pending.pop(request.request_id, None)

    async def poll(self) -> dict:
        deadline = asyncio.get_running_loop().time() + 10
        try:
            while True:
                remaining = deadline - asyncio.get_running_loop().time()
                if remaining <= 0:
                    return {"request_id": None, "question": None}
                request = await asyncio.wait_for(self.queue.get(), timeout=remaining)
                if not request.result.done():
                    return {"request_id": request.request_id, "question": request.question, "history": request.history, "previous_question": request.previous_question}
        except asyncio.TimeoutError:
            return {"request_id": None, "question": None}

    def close(self):
        for request in self.pending.values():
            if not request.result.done():
                request.result.set_result({"error": "Document session ended", "sources": []})
