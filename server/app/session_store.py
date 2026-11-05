from __future__ import annotations

import asyncio
import time
from contextlib import asynccontextmanager
from dataclasses import dataclass


@dataclass
class SessionRecord:
    channel_name: str
    requester_rtc_uid: int
    requester_rtm_user_id: str
    token_expires_at_unix: int
    created_at_unix: int
    agent_id: str | None = None
    agent_state: str = "bootstrapped"
    document_bridge: object | None = None


class SessionStore:
    def __init__(self, ttl_seconds: int) -> None:
        self._ttl_seconds = ttl_seconds
        self._sessions: dict[str, SessionRecord] = {}
        self._lock = asyncio.Lock()
        self._join_locks: dict[str, asyncio.Lock] = {}

    async def put(self, record: SessionRecord) -> SessionRecord:
        async with self._lock:
            self._cleanup_locked()
            self._sessions[record.channel_name] = record
            return record

    async def get(self, channel_name: str) -> SessionRecord | None:
        async with self._lock:
            self._cleanup_locked()
            return self._sessions.get(channel_name)

    async def set_agent(self, channel_name: str, agent_id: str, state: str) -> SessionRecord:
        async with self._lock:
            record = self._sessions[channel_name]
            record.agent_id = agent_id
            record.agent_state = state
            return record

    async def remove(self, channel_name: str) -> None:
        async with self._lock:
            record = self._sessions.pop(channel_name, None)
            if record and record.document_bridge:
                record.document_bridge.close()
            self._join_locks.pop(channel_name, None)

    async def count(self) -> int:
        async with self._lock:
            self._cleanup_locked()
            return len(self._sessions)

    @asynccontextmanager
    async def join_guard(self, channel_name: str):
        async with self._lock:
            lock = self._join_locks.setdefault(channel_name, asyncio.Lock())
        async with lock:
            yield

    def _cleanup_locked(self) -> None:
        cutoff = int(time.time()) - self._ttl_seconds
        expired = [
            channel
            for channel, record in self._sessions.items()
            if record.created_at_unix < cutoff
        ]
        for channel in expired:
            record = self._sessions.pop(channel, None)
            if record and record.document_bridge:
                record.document_bridge.close()
            self._join_locks.pop(channel, None)
