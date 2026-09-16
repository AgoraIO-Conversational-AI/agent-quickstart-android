# Setup

## Prerequisites

- Android Studio with JDK 17+
- An Android physical device with camera and microphone access
- [Agora CLI](https://github.com/AgoraIO/cli)
- Python 3.10+
- A Google Gemini API key with access to the configured Live and vision models
- Android platform-tools (`adb`) for the easiest physical-device flow
- Optional: a development tunnel provider such as Cloudflare Tunnel, ngrok, Tailscale Funnel, or LocalTunnel

## Recommended Setup

The easiest path is to let the Agora CLI scaffold the app, bind an Agora project, and write the App ID and Certificate to the Python server environment.

```bash
curl -fsSL https://dl.agora.io/cli/install.sh | sh
agora --help
agora login
agora init my-android-demo --template android
cd my-android-demo
python3 -m venv server/.venv
source server/.venv/bin/activate
pip install -r server/requirements-dev.txt
cp -n server/.env.example server/.env.local
agora project env write server/.env.local
```

Add your Gemini key to `server/.env.local`:

```properties
GEMINI_API_KEY=your_google_gemini_api_key
LLM_MODEL=models/gemini-3.8-live
GEMINI_VISION_MODEL=models/gemini-3.8-flash
```

Start the backend:

```bash
./server/run.sh
```

In another terminal, configure Android for a USB-connected device and build:

```bash
adb reverse tcp:8000 tcp:8000
cat > local.properties <<'EOF'
QUICKSTART_SERVER_URL=http://127.0.0.1:8000
EOF
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug
```

If you cannot use `adb reverse`, start a tunnel instead and configure Android with its public HTTPS URL:

```bash
./server/tunnel.sh --provider ngrok
./server/configure-android.sh https://your-public-host
```

`agora init` clones this starter, selects or creates an Agora project, and writes `.agora/project.json`. Agora credentials remain in `server/.env.local`.

## Working From A Clone

Use this if you already cloned this repository:

```bash
git clone https://github.com/AgoraIO-Conversational-AI/agent-quickstart-android.git
cd agent-quickstart-android
agora login
python3 -m venv server/.venv
source server/.venv/bin/activate
pip install -r server/requirements-dev.txt
cp -n server/.env.example server/.env.local
agora project env write server/.env.local --project <your-project> --template standard
agora project doctor --deep
./server/run.sh
```

In another terminal, use `adb reverse tcp:8000 tcp:8000`, write `QUICKSTART_SERVER_URL=http://127.0.0.1:8000` to root `local.properties`, then build the app.

If you cannot use USB/adb reverse, run `./server/tunnel.sh --provider <provider>` and `./server/configure-android.sh https://your-public-host` instead. The helper supports `cloudflare`, `ngrok`, `tailscale`, and `localtunnel`. [Local HTTPS tunnels](local-tunnels.md) documents the requirements and direct commands for each provider.

## Manual Setup

Use this only if you are not using the Agora CLI.

### 1. Create An Agora Project

Create or choose an Agora project with Conversational AI enabled.

You need:

- `App ID`
- `App Certificate`
- access to RTC and RTM for the project

### 2. Clone This Repo

```bash
git clone <your-fork-or-repo-url>
cd agent-quickstart-android
```

### 3. Add Server Config

Put server-only credentials in `server/.env.local`:

```properties
AGORA_APP_ID=your_agora_app_id
AGORA_APP_CERTIFICATE=your_agora_app_certificate
AGORA_AGENT_UID=123456
LLM_MODEL=models/gemini-3.8-live
GEMINI_API_KEY=your_google_gemini_api_key
GEMINI_VISION_MODEL=models/gemini-3.8-flash
```

For a USB-connected physical device, put only this value in root `local.properties`:

```properties
QUICKSTART_SERVER_URL=http://127.0.0.1:8000
```

Then run `adb reverse tcp:8000 tcp:8000`. If you use a tunnel, set `QUICKSTART_SERVER_URL=https://your-public-host` instead. If the URL changes, rebuild or reinstall the Android app because this value is compiled into `BuildConfig`.

### 4. Build And Run

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug
```

Open the project in Android Studio, or install from the command line, then launch it on a physical device.

Tap **Start Live Session**, allow camera and microphone permissions, point the rear camera at your workspace, and speak to the agent. The app refreshes camera context periodically; tap the eye button to refresh visual context immediately. Use the end-session control to stop cleanly.

## Required Configuration

Required in `local.properties`:

- `QUICKSTART_SERVER_URL`

Required in `server/.env.local`:

- `AGORA_APP_ID`
- `AGORA_APP_CERTIFICATE`
- `GEMINI_API_KEY`

`AGORA_AREA` selects the Agora API routing region. Supported values are `NORTH_AMERICA`, `US`, `EUROPE`, `EU`, `ASIA_PACIFIC`, `AP`, `CHINA`, and `CN`.

## Default Agent Setup

The demo starts an Agora Conversational AI agent with Gemini Live MLLM for realtime audio conversation. Camera understanding is implemented separately: the Android app captures periodic camera snapshots, the backend summarizes them with Gemini Vision, and that summary is injected into the active Agora agent as context.

It also enables:

- RTM event delivery
- RTM data channel transcripts and agent state
- agent subscription scoped to the generated requester RTC UID
- chorus audio scenario for the agent and local RTC engine
- Agora VAD turn detection for Gemini Live
- rear-camera default, manual camera switch, torch, zoom, and visual-context refresh

## Text Controls And Visual Context

During a session, text controls can send instructions or speech through the backend:

- **Ask Live Lens** sends an instruction for the agent to process.
- **Read aloud** sends text directly to speech synthesis.
- **Queue instead of interrupting** defaults to enabled. For instructions, the
  `append` action starts a new turn after the current turn's LLM output finishes;
  it does not guarantee waiting for all current audio playback to finish. For
  direct speech, `APPEND` queues after current speech. Clear the checkbox to
  interrupt immediately.

These controls use the Python backend's `/v1/conversation/think` and
`/v1/conversation/speak` endpoints. RTC/RTM still carries audio and agent events.
The Android Client Toolkit is not required by this implementation.

The camera path uses `POST /v1/conversation/visual-context`. Android sends a base64 JPEG snapshot; the backend calls Gemini Vision and injects the visual summary into the active Agora agent with `/think`. No Gemini key is sent to Android.

The server pins `agora-agents==2.9.0`. After pulling updates, install `server/requirements-dev.txt` in a Python 3.10+ environment and restart the server.

## Production Security

This repo uses a backend-orchestrated flow.

It is useful when you want to:

- learn how Agora Conversational AI works end to end
- ship a quick prototype with a minimal backend
- build a reusable Android template for your team
- understand the minimum code needed for a voice AI app

The App Certificate is backend-only. The quickstart endpoints intentionally omit application-user authentication to keep the local demo focused on Agora token generation and agent lifecycle. Add your product's user authentication and authorization at the server boundary before a public production launch.
