from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import urlsplit

from dotenv import load_dotenv


SERVER_DIR = Path(__file__).resolve().parents[1]


def _csv(value: str) -> tuple[str, ...]:
    return tuple(item.strip() for item in value.split(",") if item.strip())


@dataclass(frozen=True)
class Settings:
    agora_app_id: str
    agora_app_certificate: str
    asr_model: str = "nova-3"
    llm_model: str = "gpt-4o-mini"
    tts_model: str = "speech_2_6_turbo"
    tts_voice_id: str = "English_captivating_female1"
    agora_area: str = "NORTH_AMERICA"
    agent_uid: int = 123456
    host: str = "127.0.0.1"
    port: int = 8000
    allowed_origins: tuple[str, ...] = ("*",)
    token_expiry_seconds: int = 3600
    session_ttl_seconds: int = 7200
    requests_per_minute: int = 60
    build_version: str = "1.1.0"
    public_base_url: str = ""

    @classmethod
    def from_env(cls) -> "Settings":
        load_dotenv(SERVER_DIR / ".env", override=False)
        load_dotenv(SERVER_DIR / ".env.local", override=True)
        return cls(
            agora_app_id=os.getenv("AGORA_APP_ID", "").strip(),
            agora_app_certificate=os.getenv("AGORA_APP_CERTIFICATE", "").strip(),
            asr_model=os.getenv("ASR_MODEL", "nova-3"),
            llm_model=os.getenv("LLM_MODEL", "gpt-4o-mini"),
            tts_model=os.getenv("TTS_MODEL", "speech_2_6_turbo"),
            tts_voice_id=os.getenv("TTS_VOICE_ID", "English_captivating_female1"),
            agora_area=os.getenv("AGORA_AREA", "NORTH_AMERICA"),
            agent_uid=int(os.getenv("AGORA_AGENT_UID", "123456")),
            host=os.getenv("HOST", "127.0.0.1"),
            port=int(os.getenv("PORT", "8000")),
            allowed_origins=_csv(os.getenv("ALLOWED_ORIGINS", "*")),
            token_expiry_seconds=int(os.getenv("TOKEN_EXPIRY_SECONDS", "3600")),
            session_ttl_seconds=int(os.getenv("SESSION_TTL_SECONDS", "7200")),
            requests_per_minute=int(os.getenv("REQUESTS_PER_MINUTE", "60")),
            build_version=os.getenv("BUILD_VERSION", "1.1.0"),
            public_base_url=os.getenv("PUBLIC_BASE_URL", "").strip().rstrip("/"),
        )

    def validate(self) -> None:
        if self.public_base_url:
            url = urlsplit(self.public_base_url)
            if url.scheme != "https" or not url.hostname or url.username or url.password or url.query or url.fragment:
                raise RuntimeError("PUBLIC_BASE_URL must be an HTTPS URL without credentials, query, or fragment.")
        missing = [
            name
            for name, value in (
                ("AGORA_APP_ID", self.agora_app_id),
                ("AGORA_APP_CERTIFICATE", self.agora_app_certificate),
            )
            if not value
        ]
        if missing:
            raise RuntimeError(f"Missing required server configuration: {', '.join(missing)}")
        invalid = [
            name
            for name, value in (
                ("AGORA_APP_ID", self.agora_app_id),
                ("AGORA_APP_CERTIFICATE", self.agora_app_certificate),
            )
            if len(value) != 32
        ]
        if invalid:
            raise RuntimeError(
                f"Invalid server configuration: {', '.join(invalid)} must each be exactly "
                "32 characters. Replace the example placeholders in server/.env.local "
                "with your Agora project's App ID and App Certificate, then restart the server."
            )
