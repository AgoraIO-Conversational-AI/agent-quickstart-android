# Architecture

## Why This Template Is Useful

This repository is meant to help you move fast without having to invent the Agora wiring from scratch.

It shows how to:

- set up a realtime voice app in Android
- coordinate RTC, RTM, Kotlin toolkit callbacks, and backend agent lifecycle calls
- display transcript and agent state in Compose
- keep the app structure understandable for future contributors
- use Agora as the realtime backbone for a voice AI experience

## Repo Structure

### Android Client

- `app/src/main/java/com/androidengineers/agent_quickstart_android/MainActivity.kt`: app entry point and microphone permission handling
- `app/src/main/java/com/androidengineers/agent_quickstart_android/ui/ConversationScreen.kt`: Compose UI for the pre-session and active-session states
- `app/src/main/java/com/androidengineers/agent_quickstart_android/ui/ConversationViewModel.kt`: screen state and user actions
- `app/src/main/java/com/androidengineers/agent_quickstart_android/ui/ConversationUiStateMapper.kt`: pure mapping from session data to UI state
- `app/src/main/java/com/androidengineers/agent_quickstart_android/rtc/AgoraConversationSessionManager.kt`: RTC, RTM, Kotlin toolkit, transcript, and audio session lifecycle
- `app/src/main/java/com/androidengineers/agent_quickstart_android/data/ConversationAgoraApi.kt`: Python backend client
- `app/src/main/java/com/androidengineers/agent_quickstart_android/config/QuickstartConfig.kt`: configuration helpers
- `app/src/main/java/com/androidengineers/agent_quickstart_android/model/ConversationModels.kt`: shared data models

### Voice Logic

- `app/src/main/java/com/androidengineers/agent_quickstart_android/audio/AudioSessionManager.kt`
- `app/src/main/java/com/androidengineers/agent_quickstart_android/audio/TurnManager.kt`
- `app/src/main/java/com/androidengineers/agent_quickstart_android/audio/SelfSpeechFilter.kt`
- `app/src/main/java/com/androidengineers/agent_quickstart_android/audio/BargeInDetector.kt`
- `app/src/main/java/com/androidengineers/agent_quickstart_android/rtc/TranscriptAssembler.kt`

### Python Server

- `server/app/main.py`: FastAPI lifecycle, CORS, request IDs, and timing
- `server/app/routes.py`: public API contract and session validation
- `server/app/agora_client.py`: token generation and Agora Conversational AI REST calls
- `server/app/session_store.py`: in-memory TTL and idempotency state
- `server/run.sh`: local TLS server
- `server/tunnel.sh`: public HTTPS tunnel

### Tests

- `app/src/test/java/com/androidengineers/agent_quickstart_android/data/ConversationAgoraApiTest.kt`
- `app/src/test/java/com/androidengineers/agent_quickstart_android/data/AgoraLocalTokenFactoryTest.kt`
- `app/src/test/java/com/androidengineers/agent_quickstart_android/TranscriptAssemblerTest.kt`
- `app/src/test/java/com/androidengineers/agent_quickstart_android/audio/TurnManagerTest.kt`
- `app/src/test/java/com/androidengineers/agent_quickstart_android/audio/SelfSpeechFilterTest.kt`
- `app/src/test/java/com/androidengineers/agent_quickstart_android/ui/ConversationUiStateMapperTest.kt`

## Code Map

```mermaid
flowchart TD
    Screen["ConversationScreen"] --> VM["ConversationViewModel"]
    VM --> State["ConversationUiStateMapper"]
    VM --> API["Conversation backend API"]
    VM --> Session["AgoraConversationSessionManager"]
    Session --> Audio["AudioSessionManager"]
    Session --> Turns["TurnManager"]
    Session --> Transcript["TranscriptAssembler"]
    Session --> Toolkit["Kotlin client toolkit"]
    Toolkit --> RTM["RTM transcript + agent state + metrics"]
    API --> Server["Python FastAPI server"]
    Server --> Tokens["Agora token generation"]
    Server --> Rest["Agora Conversational AI REST"]
```

This is the easiest way to understand where the code lives:

- the screen renders state
- the ViewModel coordinates actions
- the session manager owns realtime behavior
- the API talks to the Python backend for tokens and agent lifecycle
- the Kotlin toolkit handles realtime RTM transcript, state, text, interrupt, and metrics behavior
- the audio helpers handle the voice edge cases

## How It Works

1. The app reads the Python server URL from `local.properties`.
2. The server generates short-lived RTC and RTM tokens and returns bootstrap data to Android.
3. The server calls Agora REST to start the Conversational AI agent and scopes the agent to the generated requester RTC UID.
4. The Android app gives its logged-in RTC/RTM instances to `ConversationalAIAPIImpl`.
5. The toolkit applies AI client audio settings, subscribes to RTM, and emits transcript, state, error, receipt, and metric callbacks.
6. The user can speak, send text, mute, interrupt, and end the session from the UI.

## Architecture At A Glance

```mermaid
flowchart LR
    U["User"] --> UI["Compose UI"]
    UI --> VM["ConversationViewModel"]
    VM --> API["Conversation backend API"]
    API --> SERVER["Python FastAPI server"]
    SERVER --> TOKENS["Token generation"]
    SERVER --> REST["Agora Conversational AI REST"]
    VM --> RTC["AgoraConversationSessionManager"]
    RTC --> AUDIO["AudioSessionManager"]
    RTC --> TOOLKIT["Kotlin client toolkit"]
    TOOLKIT --> RTM["RTM transcript + agent state + metrics"]
    REST --> AGENT["Agora Agent Runtime"]
    AGENT --> RTC
    AGENT --> RTM
```

The app keeps the template simple by using one Android client, one direct Agora REST client, and one session manager that owns the realtime media lifecycle.

## Session Lifecycle

```mermaid
sequenceDiagram
    participant U as User
    participant A as Android App
    participant S as Python Server
    participant R as Agora REST
    participant C as Agora RTC
    participant M as Agora RTM
    participant G as Agent Runtime

    U->>A: Tap Start voice session
    A->>S: POST /bootstrap
    S-->>A: App ID + RTC/RTM tokens
    A->>S: POST /join with requester RTC UID
    S->>R: POST /join
    R-->>S: agent_id
    S-->>A: agent_id
    R->>G: Start agent
    A->>M: Login RTM
    A->>M: Subscribe via Kotlin toolkit
    A->>C: Join RTC and publish mic with AI client audio settings
    G-->>M: Transcript, state, error, and metrics events
    U->>A: Speak, send text, mute, interrupt
    A->>M: Toolkit speak, think, and interrupt messages
    U->>A: End session
    A->>S: POST /leave
    A->>C: Leave RTC
    A->>M: Logout
```

## Session States

```mermaid
stateDiagram-v2
    [*] --> Idle
    Idle --> Starting: Start session
    Starting --> InSession: RTC + RTM connected
    Starting --> Error: join/token failure
    InSession --> Ending: End session
    InSession --> Interrupted: Agent interrupted
    Interrupted --> InSession: Agent resumes
    Ending --> Idle: cleanup complete
    Error --> Idle: user retries
```

## Suggested Learning Path

If you are new to Agora, this is the best order:

1. See the UI first.
2. Learn how the ViewModel wires actions.
3. Inspect how the session manager wires RTC/RTM into the Kotlin client toolkit.
4. Read the Android backend API layer.
5. Read the Python routes and Agora client that own credentials and agent lifecycle.

## What To Edit First

```mermaid
flowchart LR
    Start["Clone the repo"] --> UIEdit["Edit ConversationScreen"]
    UIEdit --> LogicEdit["Edit ConversationViewModel"]
    LogicEdit --> AgentEdit["Edit AgoraConversationSessionManager"]
    AgentEdit --> Prod["Move auth to backend later"]
```

If you are building a product around a voice assistant, this gives you a practical starting point for:

- support assistants
- call-center copilots
- interactive voice demos
- realtime conversational agents
- internal voice workflows and prototypes
