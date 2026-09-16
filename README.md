# Agora Conversational AI Android Quickstart

This repository is a template-style Android starter for building a Voice AI app with Agora Conversational AI.

It gives you a Kotlin + Jetpack Compose app backed by a small Python service that:

- joins an Agora RTC channel
- starts and manages an Agora Conversational AI agent through the backend
- listens for transcript, agent state, and pipeline metrics over RTM
- lets the user talk, mute, interrupt, and end the session
- supports typed instructions, direct speech, and queued requests
- fills long response pauses with generated phrases and static fallbacks
- optionally looks up the project's setup and troubleshooting guidance
- keeps the App Certificate and token generation off the Android device

## Release 1.1.0 — Agora Conversational AI v2.12

This release integrates the Conversational AI Engine's v2.12 features with
`agora-agents==2.8.1` on the Python backend. Android uses RTC `4.6.4` and RTM
`2.3.0`. Engine, Python SDK, and Android SDK versions are independent.

## Kotlin Toolkit Demo Branch

This branch demonstrates the standalone
[`agent-client-toolkit-kotlin`](https://github.com/AgoraIO-Conversational-AI/agent-client-toolkit-kotlin)
library in the Android quickstart. The backend still owns secure token
generation and agent start/stop, while the app gives its logged-in RTC/RTM
instances to `ConversationalAIAPIImpl` for realtime client behavior.

The main integration point is
`app/src/main/java/com/androidengineers/agent_quickstart_android/rtc/AgoraConversationSessionManager.kt`.
It now uses the toolkit to:

- subscribe to RTM transcript, state, error, receipt, and latency callbacks
- apply `AUDIO_SCENARIO_AI_CLIENT` before joining the RTC channel
- send text-box actions through toolkit `speak` and `think`
- send barge-in interrupts through toolkit `interrupt`
- surface toolkit metrics in the connected session details

The toolkit artifact requires Android `minSdk 26`, so this branch raises the
app minimum SDK from 24 to 26.

See [the changelog](CHANGELOG.md) for the changes and [setup instructions](docs/setup.md#text-controls-and-project-guidance)
for the new text controls and optional custom tool. Each developer runs their
own backend and configures its HTTPS URL before building the Android app.

> [!NOTE]
> This quickstart requires the included Python backend. Local mobile testing uses a temporary public HTTPS tunnel so the Android device can reach the development server.

## Prerequisites

- Android Studio with JDK 17 or newer
- Python 3.10 or newer
- Bash for the scripts in `server/`
- An Android device or emulator with microphone support
- An Agora account with access to Conversational AI
- A development tunnel provider; the included helper supports Cloudflare Tunnel, ngrok, Tailscale Funnel, and LocalTunnel

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
./server/run.sh
```

The command above uses the project selected by `agora init`. For an existing clone, select the project explicitly instead:

```bash
agora project env write server/.env.local \
  --project <project-name-or-id> \
  --template standard
./server/run.sh
```

The server listens on `http://127.0.0.1:8000` and keeps `AGORA_APP_CERTIFICATE` off the Android device. Leave this terminal running. The local endpoint uses HTTP; the selected tunnel provider supplies the public HTTPS endpoint required by Android.

### 4. Create a temporary public HTTPS URL

In another terminal, run:

```bash
./server/tunnel.sh --provider ngrok
```

Choose `cloudflare`, `ngrok`, `tailscale`, or `localtunnel` with `--provider`. Keep the tunnel running and copy its generated `https://` URL.

Verify that the public endpoint reaches the Python server:

```bash
curl https://your-public-host/health
```

The response must be backend health JSON, not a tunnel-provider login or warning page. See [Local HTTPS tunnels](docs/local-tunnels.md) for explicit ngrok, Cloudflare Tunnel, Tailscale Funnel, and LocalTunnel commands.

### 5. Configure Android

Write the public server URL to root `local.properties`:

```bash
./server/configure-android.sh https://your-public-host
```

The script writes only this client value:

```properties
QUICKSTART_SERVER_URL=https://your-public-host
```

Do not put `AGORA_APP_CERTIFICATE` in `local.properties`.

### 6. Build the app

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug
```

If your shell already uses JDK 17 or newer, `./gradlew :app:assembleDebug` is sufficient.

### 7. Run it

Open the project in Android Studio, or run it from the command line, then launch it on a device or emulator.

Tap **Start voice session**, allow microphone permission, speak to the agent, and watch transcripts appear in real time.

If the agent does not join or transcripts do not appear, run:

```bash
agora project doctor --deep
```

If the temporary tunnel URL changes, run `server/configure-android.sh` again, rebuild, and reinstall the app. For manual setup, optional configuration, and production notes, see [docs/setup.md](docs/setup.md).

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

- [Setup](docs/setup.md): CLI, server, tunnel, and Android configuration
- [Local HTTPS tunnels](docs/local-tunnels.md): expose the development server to a physical device
- [Backend runbook](docs/backend-runbook.md): local server, public tunnel, API contract, deployment, and smoke checks
- [Architecture](docs/architecture.md): app structure, code map, session lifecycle, and state flow
- [Troubleshooting](docs/troubleshooting.md): common setup, agent, RTM, metrics, and microphone issues
- [Agent coding guidance](docs/agent-guidance.md): Agora CLI skills and guidance for AI coding agents

## Security Note

`AGORA_APP_CERTIFICATE` stays in `server/.env.local` and is never compiled into Android. The Python server generates the Android user's RTC/RTM token and uses the Agora Python SDK to start and stop the agent. The Android app uses the Kotlin client toolkit over RTM for realtime transcripts, text controls, metrics, and interruption. A development tunnel URL is public while the tunnel is running, so stop the tunnel when testing is complete and add appropriate application authentication before adapting this demo for production.
