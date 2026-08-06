# Setup

## Prerequisites

- Android Studio with JDK 17+
- An Android device or emulator with microphone support
- [Agora CLI](https://github.com/AgoraIO/cli)

## Recommended Setup

The easiest path is to let the Agora CLI scaffold the app and write Android credentials to `local.properties`.

```bash
curl -fsSL https://dl.agora.io/cli/install.sh | sh
agora --help
agora login
agora init my-android-demo --template android
cd my-android-demo
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug
```

`agora init` clones this starter, selects or creates an Agora project, writes `.agora/project.json`, and writes Agora credentials to root `local.properties`.

## Working From A Clone

Use this if you already cloned this repository:

```bash
git clone https://github.com/AgoraIO-Conversational-AI/agent-quickstart-android.git
cd agent-quickstart-android
agora login
agora quickstart env write . --template android --project <your-project>
agora project doctor --deep
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug
```

The env command writes:

```properties
AGORA_APP_ID=...
AGORA_APP_CERTIFICATE=...
```

to root `local.properties`.

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

### 3. Add Agora And Murf Config

Put the following values in `local.properties` at the repo root:

```properties
AGORA_APP_ID=your_agora_app_id
AGORA_APP_CERTIFICATE=your_agora_app_certificate
AGORA_AGENT_UID=123456
MURF_API_KEY=your_murf_api_key
```

Optional values:

```properties
AGORA_CONVOAI_BASE_URL=https://api.agora.io/api/conversational-ai-agent/v2/projects
AGORA_AREA=US
MURF_VOICE_ID=Anisha
MURF_LOCALE=en-IN
MURF_MODEL=FALCON
MURF_BASE_URL=wss://in.api.murf.ai/v1/speech/stream-input
AGORA_ASR_VENDOR=sarvam
AGORA_ASR_LANGUAGE=unknown
SARVAM_API_KEY=your_sarvam_api_key
```

### 4. Build And Run

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug
```

Open the project in Android Studio, or run it from the command line, then launch it on a device or emulator.

Tap **Start voice session**, allow microphone permission, speak to the agent, and watch transcripts appear in realtime. Use the end-session control to stop the conversation cleanly.

## Required Configuration

Required in `local.properties`:

- `AGORA_APP_ID`
- `AGORA_APP_CERTIFICATE`
- `MURF_API_KEY`

Optional in `local.properties`:

- `AGORA_AGENT_UID`, defaults to `123456`
- `AGORA_CONVOAI_BASE_URL`, defaults to `https://api.agora.io/api/conversational-ai-agent/v2/projects`
- `AGORA_AREA`, defaults to `US`
- `MURF_VOICE_ID`, defaults to `Anisha`
- `MURF_LOCALE`, defaults to `en-IN`
- `MURF_MODEL`, defaults to `FALCON`
- `MURF_BASE_URL`, defaults to `wss://in.api.murf.ai/v1/speech/stream-input`
- `AGORA_ASR_VENDOR`, defaults to `deepgram`; use `sarvam` for Hindi/Hinglish demos
- `AGORA_ASR_LANGUAGE`, defaults to `en`; use `unknown` with Sarvam for automatic language detection
- `SARVAM_API_KEY`, required only when `AGORA_ASR_VENDOR=sarvam`

Notes:

- `AGORA_APP_ID` also supports the legacy key `agora.app.id`
- `AGORA_AREA` maps to the ConvoAI REST `geofence.area` value
- Deepgram with `AGORA_ASR_LANGUAGE=en` is best for English-only demos. Hindi words can be dropped before they reach the agent.
- `MURF_BASE_URL` defaults to Murf's India WebSocket endpoint for lower-latency demos in India

## Default Agent Setup

The demo starts the agent with Agora Conversational AI and Murf Falcon TTS:

- `deepgram_nova_3`
- `openai_gpt_4o_mini`
- `murf` with `MURF_MODEL=FALCON`

It also enables:

- RTM event delivery
- RTM data channel transcripts
- RTM pipeline metrics
- agent subscription scoped to the generated requester RTC UID
- chorus audio scenario for the agent and local RTC engine
- start-of-speech interruption
- VAD-based end-of-speech detection

## Production Security

This repo is intentionally a direct REST demo.

It is useful when you want to:

- learn how Agora Conversational AI works end to end
- ship a quick prototype without a backend
- build a reusable Android template for your team
- understand the minimum code needed for a voice AI app

It is not production-safe as-is because the App Certificate is packaged into the Android app and used for local token generation.

For production:

- move token generation to your backend
- move REST `join`, `interrupt`, and `leave` calls to your backend
- keep the Android app as a thin UI client
- never ship the App Certificate inside the app
