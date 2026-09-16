# Python backend runbook

## Architecture

The Android app calls this FastAPI service for session bootstrap, token refresh, agent lifecycle operations, and visual-context analysis. Only the Python process has `AGORA_APP_CERTIFICATE` and `GEMINI_API_KEY`. The bootstrap response includes the Agora App ID because the RTC and RTM SDKs need it to initialize; it is a public identifier, not a credential.

## Configure

```bash
python3 -m venv server/.venv
source server/.venv/bin/activate
pip install -r server/requirements-dev.txt
cp server/.env.example server/.env.local
agora project env write server/.env.local
```

Add Gemini settings to `server/.env.local`:

```properties
GEMINI_API_KEY=your_google_gemini_api_key
LLM_MODEL=models/gemini-3.8-live
GEMINI_VISION_MODEL=models/gemini-3.8-flash
```

Required server credentials are `AGORA_APP_ID`, `AGORA_APP_CERTIFICATE`, and `GEMINI_API_KEY`.

## Run locally and create a public HTTPS URL

```bash
./server/run.sh
```

`run.sh` listens on `http://127.0.0.1:8000`. It intentionally uses loopback HTTP because the tunnel process runs on the same machine and terminates public TLS.

For a USB-connected physical device, use adb reverse in a second terminal:

```bash
adb reverse tcp:8000 tcp:8000
cat > local.properties <<'EOF'
QUICKSTART_SERVER_URL=http://127.0.0.1:8000
EOF
```

If adb reverse is not available, use a public HTTPS tunnel instead:

```bash
./server/tunnel.sh --provider ngrok
./server/configure-android.sh https://generated-public-host
```

Choose `cloudflare`, `ngrok`, `tailscale`, or `localtunnel` with `--provider`. See [Local HTTPS tunnels](local-tunnels.md) for manual provider commands, public health verification, URL rotation, and troubleshooting. Rebuild or reinstall the app whenever the configured URL changes.

For a stable production URL, deploy the same ASGI app behind a managed HTTPS load balancer instead of using a development tunnel.

## API

- `GET /health` is public and reports service version and active-session count.
- `POST /v1/conversation/bootstrap` creates a channel and short-lived RTC/RTM token.
- `POST /v1/conversation/join` starts an agent and is idempotent by channel.
- `POST /v1/conversation/interrupt` interrupts the active agent.
- `POST /v1/conversation/leave` stops the agent and removes server session state.
- `POST /v1/conversation/refresh` rotates the user token after validating the session identity.
- `POST /v1/conversation/visual-context` accepts a camera snapshot, summarizes it with Gemini Vision, and injects the summary into the active agent context.

The Android app does not send Agora credentials, Gemini credentials, or a custom bearer token. The backend generates the user's RTC/RTM token, starts and controls the agent through `agora-agents`, calls Gemini Vision, and returns only values required by the RTC/RTM SDKs or UI. The in-memory session and rate-limit stores are suitable for one process. Use Redis or another shared store and add product-level user authentication before scaling or deploying publicly.

## Smoke check

```bash
curl http://127.0.0.1:8000/health
curl -X POST http://127.0.0.1:8000/v1/conversation/bootstrap \
  -H "Content-Type: application/json" \
  -d '{}'
```
