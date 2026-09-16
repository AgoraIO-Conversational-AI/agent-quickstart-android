# Python conversation server

This FastAPI service keeps the Agora App Certificate, Gemini API key, and Conversational AI REST calls off the Android device. It exposes bootstrap, join, interrupt, leave, refresh, speak, think, visual-context, guidance, and health endpoints on `http://127.0.0.1:8000` by default.

This demo branch pins `agora-agents==2.9.0`, uses Gemini Live MLLM for realtime voice, and uses Gemini Vision to summarize Android camera snapshots before injecting them into the active Agora agent as context. Invalid credential lengths are rejected at startup.

## Setup

```bash
python3 -m venv server/.venv
source server/.venv/bin/activate
pip install -r server/requirements-dev.txt
cp -n server/.env.example server/.env.local
```

Use the Agora CLI to seed the backend credentials. The App Certificate remains on this server and is used to generate the mobile user's RTC/RTM token and the agent's Agora credentials:

```bash
agora project env write server/.env.local
```

Add your Gemini key to `server/.env.local`:

```properties
GEMINI_API_KEY=your_google_gemini_api_key
LLM_MODEL=models/gemini-3.8-live
GEMINI_VISION_MODEL=models/gemini-3.8-flash
```

Run the loopback HTTP server:

```bash
./server/run.sh
```

For a USB-connected physical device, forward the port and configure Android with loopback:

```bash
adb reverse tcp:8000 tcp:8000
cat > ../local.properties <<'EOF'
QUICKSTART_SERVER_URL=http://127.0.0.1:8000
EOF
```

If adb reverse is not available, expose the backend through a public HTTPS tunnel:

```bash
./server/tunnel.sh --provider ngrok
./server/configure-android.sh https://your-public-host
```

Choose `cloudflare`, `ngrok`, `tailscale`, or `localtunnel` with the `--provider` flag. You can also run any provider directly; see [`docs/local-tunnels.md`](../docs/local-tunnels.md).

See `docs/backend-runbook.md` for the API contract, deployment alternatives, and smoke checks.
