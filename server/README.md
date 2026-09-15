# Python conversation server

This FastAPI service keeps the Agora App Certificate and Conversational AI REST calls off the Android device. It exposes bootstrap, join, interrupt, leave, refresh, speak, think, project guidance, and health endpoints on `http://127.0.0.1:8000` by default. The tunnel provider terminates public HTTPS.

Version 1.1.0 pins `agora-agents==2.8.1` and adds Conversational AI v2.12 features.
See [text controls and project guidance](../docs/setup.md#text-controls-and-project-guidance)
for behavior and configuration. Invalid credential lengths are rejected at startup.

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

Run the loopback HTTP server:

```bash
./server/run.sh
```

In another terminal, expose it through a public HTTPS tunnel:

```bash
./server/tunnel.sh --provider ngrok
```

Choose `cloudflare`, `ngrok`, `tailscale`, or `localtunnel` with the `--provider` flag. You can also run any provider directly; see [`docs/local-tunnels.md`](../docs/local-tunnels.md).

Write the tunnel URL into Android configuration with:

```bash
./server/configure-android.sh https://your-public-host
```

Android connects only to the tunnel provider's publicly trusted HTTPS endpoint. The loopback HTTP server is not exposed directly to the device or local network.

See `docs/backend-runbook.md` for the API contract, deployment alternatives, and smoke checks.
