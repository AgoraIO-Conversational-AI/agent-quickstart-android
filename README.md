# Agora Conversational AI Android Quickstart

This repository is a template-style Android starter for building a Live Lens style AI app with Agora Conversational AI.

It gives you a Kotlin + Jetpack Compose app backed by a small Python service that:

- joins an Agora RTC channel with microphone and rear-camera preview
- starts and manages an Agora Conversational AI agent through the backend
- uses Gemini Live MLLM for low-latency voice conversation
- listens for transcript, agent state, and pipeline events over RTM
- periodically snapshots the phone camera, summarizes the frame with Gemini Vision, and injects that visual context into the voice agent
- lets the user talk, mute, interrupt, manually refresh camera context, and end the session
- keeps the Agora App Certificate, Gemini API key, and token generation off the Android device

## Live Lens demo branch

This branch uses `agora-agents==2.9.0` on the Python backend with Android RTC `4.6.4` and RTM `2.3.0`. Agora Conversational AI provides the realtime voice/session backbone. Camera understanding is implemented as periodic frame analysis plus context injection because Agora's current Gemini Live MLLM documentation supports audio/text input, not direct RTC video ingestion.

Each developer runs their own backend and configures the Android app with only the backend URL before building.

> [!NOTE]
> For a USB-connected physical device, use `adb reverse tcp:8000 tcp:8000` and `QUICKSTART_SERVER_URL=http://127.0.0.1:8000`. Use a public HTTPS tunnel only when the device cannot reach the local machine through adb reverse.

## Prerequisites

- Android Studio with JDK 17 or newer
- Python 3.10 or newer
- Bash for the scripts in `server/`
- An Android physical device with camera and microphone access
- An Agora account with access to Conversational AI
- A Google Gemini API key with access to the configured Gemini Live and vision models
- Android platform-tools (`adb`) for the easiest physical-device setup
- Optional: a development tunnel provider if you are not using `adb reverse`

The commands below assume a macOS or Linux shell. On Windows, run the backend scripts from WSL or an equivalent Bash environment.

## Quick Start

### 1. Install the Agora CLI and sign in

Skip this step if `agora` is already on your `PATH`.

```bash
curl -fsSL https://dl.agora.io/cli/install.sh | sh
agora --help
agora login
```

### 2. Get the quickstart

The recommended path lets the CLI clone the template and bind an Agora project. Replace `my-android-demo` with your app folder name:

```bash
agora init my-android-demo --template android
cd my-android-demo
```

`agora init` selects or creates an Agora project and records the project binding in `.agora/project.json`.

To work from an existing clone instead:

```bash
git clone https://github.com/AgoraIO-Conversational-AI/agent-quickstart-android.git
cd agent-quickstart-android
```

If you use an existing clone, you will select the Agora project when you configure the server in the next step.

### 3. Configure and run the Python server

```bash
python3 -m venv server/.venv
source server/.venv/bin/activate
pip install -r server/requirements-dev.txt
cp -n server/.env.example server/.env.local
agora project env write server/.env.local --template standard
```

Add your Gemini key to `server/.env.local`:

```properties
GEMINI_API_KEY=your_google_gemini_api_key
LLM_MODEL=models/gemini-3.8-live
GEMINI_VISION_MODEL=models/gemini-3.8-flash
```

The command above uses the project selected by `agora init`. For an existing clone, select the project explicitly instead:

```bash
agora project env write server/.env.local \
  --project <project-name-or-id> \
  --template standard
```

Start the backend and leave it running:

```bash
./server/run.sh
```

The server listens on `http://127.0.0.1:8000` and keeps `AGORA_APP_CERTIFICATE` and `GEMINI_API_KEY` off the Android device.

### 4. Configure Android for a physical USB device

In another terminal, forward the device's localhost port to the backend:

```bash
adb reverse tcp:8000 tcp:8000
```

Write only the backend URL to root `local.properties`:

```properties
QUICKSTART_SERVER_URL=http://127.0.0.1:8000
```

Do not put `AGORA_APP_CERTIFICATE`, `GEMINI_API_KEY`, or any other server credential in `local.properties`.

If you are not using USB/adb reverse, create a temporary public HTTPS tunnel instead:

```bash
./server/tunnel.sh --provider ngrok
./server/configure-android.sh https://your-public-host
curl https://your-public-host/health
```

The response must be backend health JSON, not a tunnel-provider login or warning page. See [Local HTTPS tunnels](docs/local-tunnels.md) for provider-specific commands.

### 5. Build the app

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug
```

If your shell already uses JDK 17 or newer, `./gradlew :app:assembleDebug` is sufficient.

### 6. Run it

Open the project in Android Studio, or install from the command line:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:installDebug
adb shell monkey -p com.androidengineers.agent_quickstart_android -c android.intent.category.LAUNCHER 1
```

Tap **Start Live Session**, allow camera and microphone permissions, point the rear camera at your workspace, and talk to the agent. The app refreshes camera context in the background and the eye button forces an immediate refresh.

If the agent does not join or transcripts do not appear, run:

```bash
agora project doctor --deep
```

If you disconnect the phone, run `adb reverse tcp:8000 tcp:8000` again before starting the app. If a tunnel URL changes, run `server/configure-android.sh` again, rebuild, and reinstall the app. For manual setup, optional configuration, and production notes, see [docs/setup.md](docs/setup.md).

## What To Read First

If you are using this as a template, start here:

- [ConversationScreen.kt](app/src/main/java/com/androidengineers/agent_quickstart_android/ui/ConversationScreen.kt)
- [ConversationViewModel.kt](app/src/main/java/com/androidengineers/agent_quickstart_android/ui/ConversationViewModel.kt)
- [AgoraConversationSessionManager.kt](app/src/main/java/com/androidengineers/agent_quickstart_android/rtc/AgoraConversationSessionManager.kt)
- [ConversationAgoraApi.kt](app/src/main/java/com/androidengineers/agent_quickstart_android/data/ConversationAgoraApi.kt)
- [Python backend](server/app/main.py)

Those files show the full flow from UI action to Agora session setup.

## What To Customize First

Most teams will customize these pieces first:

1. `ConversationScreen.kt` for UI layout, branding, and session cards
2. `ConversationViewModel.kt` for app state, button actions, and session orchestration
3. `AgoraConversationSessionManager.kt` for RTC, RTM, and media behavior
4. `server/app/agora_client.py` for agent presets, geofence, and model configuration

## Build And Test

Run the Python server tests:

```bash
server/.venv/bin/python -m pytest server/tests
```

Compile Kotlin:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:compileDebugKotlin
```

Run unit tests:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest
```

Assemble debug APK:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug
```

## Docs

- [Setup](docs/setup.md): CLI, server, adb reverse, tunnel, and Android configuration
- [Local HTTPS tunnels](docs/local-tunnels.md): expose the development server when adb reverse is not available
- [Backend runbook](docs/backend-runbook.md): local server, public tunnel, API contract, deployment, and smoke checks
- [Architecture](docs/architecture.md): app structure, code map, session lifecycle, and state flow
- [Troubleshooting](docs/troubleshooting.md): common setup, agent, RTM, metrics, and microphone issues
- [Agent coding guidance](docs/agent-guidance.md): Agora CLI skills and guidance for AI coding agents

## Security Note

`AGORA_APP_CERTIFICATE` and `GEMINI_API_KEY` stay in `server/.env.local` and are never compiled into Android. The Python server generates the Android user's RTC/RTM token, starts/stops the Agora agent, and calls Gemini Vision for frame summaries. A development tunnel URL is public while the tunnel is running, so stop the tunnel when testing is complete and add product authentication before adapting this demo for production.
