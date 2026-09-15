# Changelog

## 1.1.0 — 2026-09-15

Integrates features from [Agora Conversational AI Engine v2.12](https://docs.agora.io/en/ai/release-notes#v212).

### Added

- Android text controls: **Ask Ada**, **Read aloud**, and **Queue instead of interrupting**.
- Backend `think` and `speak` endpoints with session checks and input validation.
- Deferred instructions using `append` for listening, thinking, and speaking states.
- Generated filler phrases after a 1.5-second wait, with static fallback phrases.
- Optional `getProjectGuidance` custom tool for the public setup and troubleshooting guides.
- Request-level tests for ASR serialization, custom tools, speech, and instructions.

### Changed

- Uses structured `turn_detection.config` and explicit speech-triggered interruption.
- Pins the Python Agora Agents SDK to `2.8.1`; retains Android RTC `4.6.4` and RTM `2.3.0`.
- App version is `1.1.0` (version code `2`); backend default version is `1.1.0`.

### Fixed

- Rejects placeholder App ID and App Certificate values at backend startup, instead
  of returning an unhandled HTTP 500 during token generation.
- Corrects setup guidance to keep Agora credentials in `server/.env.local`.

### Upgrade

1. Install `server/requirements-dev.txt` in a Python 3.10+ virtual environment.
2. Keep your real Agora App ID and App Certificate in `server/.env.local`.
3. Optionally set `PUBLIC_BASE_URL` to the backend's public HTTPS base URL to enable
   project guidance. Deploy the repository's `docs` directory with the server.
4. Restart the backend, configure Android with `server/configure-android.sh`, and
   rebuild/reinstall the app. Start a new conversation to apply agent settings.

Queued instructions begin a new turn after the current LLM output finishes;
this can precede completion of audio playback. Queued direct speech waits for
current speech. These features use the existing Android-to-backend REST path.

### Validation

- 10 Python backend tests and 26 Android unit tests pass.
- Android debug build and lint pass.
- Live emulator testing confirmed session start/end, typed instructions, and
  direct speech requests; the user confirmed the app works.
- Generated-filler timing and LLM-driven guidance selection have request-level
  coverage but have not been independently verified in a live conversation.
