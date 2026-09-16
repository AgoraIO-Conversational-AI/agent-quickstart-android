# Setup

## Prerequisites

- Android Studio with JDK 17+
- An Android device or emulator with microphone support
- [Agora CLI](https://github.com/AgoraIO/cli)
- Python 3.10+
- A development tunnel provider such as Cloudflare Tunnel, ngrok, Tailscale Funnel, or LocalTunnel

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
./server/run.sh
```

In another terminal, start a tunnel, configure Android with its public HTTPS URL, and build:

```bash
./server/tunnel.sh --provider ngrok
./server/configure-android.sh https://your-public-host
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug
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

In another terminal, run `./server/tunnel.sh --provider <provider>`, use `./server/configure-android.sh` to write the public URL to root `local.properties`, then build the app.

The helper supports `cloudflare`, `ngrok`, `tailscale`, and `localtunnel`. [Local HTTPS tunnels](local-tunnels.md) documents the requirements and direct commands for each provider.

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

Put Agora credentials in `server/.env.local`:

```properties
AGORA_APP_ID=your_agora_app_id
AGORA_APP_CERTIFICATE=your_agora_app_certificate
AGORA_AGENT_UID=123456
```

After starting the local HTTP server and public HTTPS tunnel, put only this value in root `local.properties`:

```properties
QUICKSTART_SERVER_URL=https://your-public-host
```

If the tunnel assigns a new URL, run `server/configure-android.sh` again and rebuild or reinstall the Android app because these values are compiled into `BuildConfig`.

### 4. Build And Run

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug
```

Open the project in Android Studio, or run it from the command line, then launch it on a device or emulator.

Tap **Start voice session**, allow microphone permission, speak to the agent, and watch transcripts appear in realtime. Use the end-session control to stop the conversation cleanly.

## Required Configuration

Required in `local.properties`:

- `QUICKSTART_SERVER_URL`

Required in `server/.env.local`:

- `AGORA_APP_ID`
- `AGORA_APP_CERTIFICATE`

`AGORA_AREA` selects the Agora API routing region. Supported values are `NORTH_AMERICA`, `US`, `EUROPE`, `EU`, `ASIA_PACIFIC`, `AP`, `CHINA`, and `CN`.

## Default Agent Setup

The demo starts the agent with the default Agora-managed stack:

- `deepgram_nova_3`
- `openai_gpt_4o_mini`
- `minimax_speech_2_6_turbo`

It also enables:

- RTM event delivery
- RTM data channel transcripts
- RTM pipeline metrics
- agent subscription scoped to the generated requester RTC UID
- `AUDIO_SCENARIO_AI_CLIENT` for the local RTC engine through the Kotlin toolkit
- explicit VAD turn detection and speech-triggered interruption
- generated filler phrases after 1.5 seconds, with static fallback phrases

## Text Controls And Project Guidance

During a session, use **Send text to Ada**:

- **Ask Ada** sends an instruction for the LLM to process.
- **Read aloud** sends text directly to speech synthesis.
- **Queue instead of interrupting** defaults to enabled. For instructions, the
  `append` action starts a new turn after the current turn's LLM output finishes;
  it does not guarantee waiting for all current audio playback to finish. For
  direct speech, `APPEND` queues after current speech. Clear the checkbox to
  interrupt immediately.

These controls use the Kotlin client toolkit's RTM `think` and `speak` helpers
to publish directly to the live agent user. The Python backend still starts and
stops the agent and keeps token generation off the Android device.

To let Ada look up this project's setup and troubleshooting guidance, set
`PUBLIC_BASE_URL=https://your-public-host` in `server/.env.local` and restart the
server. Use the same reachable HTTPS backend base URL configured for Android.
Update it and restart after changing tunnel URLs. Start a new conversation to
apply agent configuration changes.

The `getProjectGuidance` tool calls `GET /v1/tools/guidance?topic=setup` or
`topic=troubleshooting`. It serves only these two public project documents;
include the repository's `docs` directory when deploying the server. When
`PUBLIC_BASE_URL` is empty, no custom tool is advertised to the agent.

The server pins `agora-agents==2.8.1`. After pulling the update, install
`server/requirements-dev.txt` in a Python 3.10+ environment and restart the server.

## Production Security

This repo uses a backend-orchestrated flow.

It is useful when you want to:

- learn how Agora Conversational AI works end to end
- ship a quick prototype with a minimal backend
- build a reusable Android template for your team
- understand the minimum code needed for a voice AI app

The App Certificate is backend-only. The quickstart endpoints intentionally omit application-user authentication to keep the local demo focused on Agora token generation and agent lifecycle. Add your product's user authentication and authorization at the server boundary before a public production launch.
