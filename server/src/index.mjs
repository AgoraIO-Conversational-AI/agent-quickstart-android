import "dotenv/config";
import express from "express";
import AgoraToken from "agora-token";
import {
  Agent,
  AgoraClient,
  Area,
  DeepgramSTT,
  ExpiresIn,
  MiniMaxTTS,
  OpenAI,
} from "agora-agent-server-sdk";

const { RtcRole, RtcTokenBuilder, RtmTokenBuilder } = AgoraToken;

const EXPIRATION_TIME_IN_SECONDS = 3600;
const DEFAULT_AGENT_UID = "123456";
const DEFAULT_GREETING =
  "Hi there! I'm Ada, your virtual assistant from Agora. How can I help?";

const ADA_PROMPT = `You are **Ada**, an agentic developer advocate from **Agora**. You help developers understand and build with Agora's Conversational AI platform.

# What Agora Actually Is
Agora is a real-time communications company. The product you represent is the **Agora Conversational AI Engine** — it lets developers add voice AI agents to any app by connecting ASR, LLM, and TTS into a real-time pipeline over Agora's SD-RTN (Software Defined Real-Time Network). Key facts:
- The product is called the **Conversational AI Engine** (not "Chorus", not "Harmony", or any other name you might invent)
- It runs a full ASR → LLM → TTS pipeline with sub-500ms latency
- It supports Deepgram, Microsoft, and others for ASR; OpenAI, Anthropic, and others for LLM; ElevenLabs, Microsoft, and others for TTS
- Agora's SD-RTN is its global real-time network infrastructure — not "SDRTN"
- MCP in this context means **Model Context Protocol** (Anthropic's open standard for connecting AI models to tools/data), not "multi-channel processing"
- Agora does not have a product called Chorus, Harmony, or any similar name — do not invent product names

# Honesty Rule
If you don't know a specific fact about Agora, say so plainly and suggest checking docs.agora.io. Never invent product names, feature names, or capabilities.

# Persona & Tone
- Friendly, technically credible, concise. You're a peer who builds things, not a support agent.
- Plain English. No marketing fluff.

# Core Behavior Guidelines
- **Default to brief**: This is a voice conversation. Keep most replies to 1–2 sentences. Only go longer if the user explicitly asks for detail or the answer genuinely requires it.
- **Never list or enumerate**: No bullet points, no numbered steps. Say the single most important thing.
- **Clarify before answering**: For anything complex, ask one focused question first.
- **Ask at most one question per turn**: Never stack questions.
- **Guide, don't lecture**: Unlock the next step, not everything at once.`;

const app = express();
app.disable("x-powered-by");
app.use(express.json());

function firstEnv(...names) {
  for (const name of names) {
    const value = process.env[name]?.trim();
    if (value) {
      return value;
    }
  }
  return null;
}

function requireEnv(...names) {
  const value = firstEnv(...names);
  if (!value) {
    throw new Error(`Missing required environment variable: ${names.join(" or ")}`);
  }
  return value;
}

function parseInteger(value, fallback) {
  const parsed = Number.parseInt(String(value ?? ""), 10);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function currentPort() {
  return parseInteger(firstEnv("AGORA_SERVER_PORT", "PORT"), 3000);
}

function currentArea() {
  const value = firstEnv("AGORA_AREA")?.toUpperCase();
  switch (value) {
    case "EU":
      return Area.EU;
    case "AP":
      return Area.AP;
    default:
      return Area.US;
  }
}

function currentAgentUid() {
  return firstEnv("AGORA_AGENT_UID", "NEXT_PUBLIC_AGENT_UID") ?? DEFAULT_AGENT_UID;
}

function currentGreeting() {
  return firstEnv("AGORA_AGENT_GREETING", "NEXT_AGENT_GREETING") ?? DEFAULT_GREETING;
}

function currentAgoraConfig() {
  return {
    appId: requireEnv("AGORA_APP_ID", "NEXT_PUBLIC_AGORA_APP_ID"),
    appCertificate: requireEnv("AGORA_APP_CERTIFICATE", "NEXT_AGORA_APP_CERTIFICATE"),
    area: currentArea(),
    agentUid: currentAgentUid(),
    greeting: currentGreeting(),
  };
}

function generateChannelName() {
  const timestamp = Date.now();
  const random = Math.random().toString(36).slice(2, 8);
  return `ai-conversation-${timestamp}-${random}`;
}

function generateNumericUid() {
  return Math.floor(100000 + Math.random() * 800000);
}

function createClient() {
  const { appId, appCertificate, area } = currentAgoraConfig();
  return new AgoraClient({
    area,
    appId,
    appCertificate,
  });
}

function createAgent(greeting) {
  return new Agent({
    name: `conversation-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    instructions: ADA_PROMPT,
    greeting,
    failureMessage: "Please wait a moment.",
    maxHistory: 50,
    turnDetection: {
      config: {
        speech_threshold: 0.5,
        start_of_speech: {
          mode: "vad",
          vad_config: {
            interrupt_duration_ms: 160,
            prefix_padding_ms: 300,
          },
        },
        end_of_speech: {
          mode: "vad",
          vad_config: {
            silence_duration_ms: 480,
          },
        },
      },
    },
    advancedFeatures: { enable_rtm: true, enable_tools: true },
    parameters: { data_channel: "rtm", enable_error_message: true },
  })
    .withStt(
      new DeepgramSTT({
        model: "nova-3",
        language: "en",
      }),
    )
    .withLlm(
      new OpenAI({
        model: "gpt-4o-mini",
        greetingMessage: greeting,
        failureMessage: "Please wait a moment.",
        maxHistory: 15,
        params: {
          max_tokens: 1024,
          temperature: 0.7,
          top_p: 0.95,
        },
      }),
    )
    .withTts(
      new MiniMaxTTS({
        model: "speech_2_6_turbo",
        voiceId: "English_captivating_female1",
      }),
    );
}

function isAgentAlreadyStoppingOrStopped(error) {
  if (!error || typeof error !== "object") {
    return false;
  }

  const statusCode = error.statusCode;
  const reason = error.body?.reason?.toLowerCase();
  const detail = (error.body?.detail ?? error.message ?? "").toLowerCase();

  if (statusCode === 404) {
    return true;
  }

  return reason === "invalidrequest" && detail.includes("already in the process of shutting down");
}

function sendJsonError(response, statusCode, message, error) {
  response.status(statusCode).json({
    error: message,
    details: error instanceof Error ? error.message : String(error),
  });
}

app.get("/", (_request, response) => {
  response.json({
    name: "agent-quickstart-android-server",
    routes: [
      "GET /health",
      "GET /api/generate-agora-token",
      "POST /api/invite-agent",
      "POST /api/interrupt-agent",
      "POST /api/stop-conversation",
    ],
  });
});

app.get("/health", (_request, response) => {
  response.json({
    ok: true,
    port: currentPort(),
    agentUid: currentAgentUid(),
  });
});

app.get("/api/generate-agora-token", (request, response) => {
  try {
    const { appId, appCertificate } = currentAgoraConfig();
    const uid = parseInteger(request.query.uid, generateNumericUid());
    const channelName =
      typeof request.query.channel === "string" && request.query.channel.trim()
        ? request.query.channel.trim()
        : generateChannelName();
    const rtmUserId =
      typeof request.query.rtm_user_id === "string" && request.query.rtm_user_id.trim()
        ? request.query.rtm_user_id.trim()
        : `android-${uid}`;
    const expirationTime =
      Math.floor(Date.now() / 1000) + EXPIRATION_TIME_IN_SECONDS;

    const rtcToken = RtcTokenBuilder.buildTokenWithUid(
      appId,
      appCertificate,
      channelName,
      uid,
      RtcRole.PUBLISHER,
      expirationTime,
    );
    const rtmToken = RtmTokenBuilder.buildToken(
      appId,
      appCertificate,
      rtmUserId,
      EXPIRATION_TIME_IN_SECONDS,
    );

    response.json({
      rtc_token: rtcToken,
      rtm_token: rtmToken,
      uid: String(uid),
      channel: channelName,
      rtm_user_id: rtmUserId,
    });
  } catch (error) {
    sendJsonError(response, 500, "Failed to generate Agora token", error);
  }
});

app.post("/api/invite-agent", async (request, response) => {
  try {
    const requesterId = String(request.body?.requester_id ?? "").trim();
    const channelName = String(request.body?.channel_name ?? "").trim();

    if (!requesterId || !channelName) {
      response.status(400).json({
        error: "channel_name and requester_id are required",
      });
      return;
    }

    const { agentUid, greeting } = currentAgoraConfig();
    const client = createClient();
    const agent = createAgent(greeting);
    const session = agent.createSession(client, {
      channel: channelName,
      agentUid,
      remoteUids: [requesterId],
      idleTimeout: 30,
      expiresIn: ExpiresIn.hours(1),
      debug: false,
    });

    const agentId = await session.start();

    response.json({
      agent_id: agentId,
      create_ts: Math.floor(Date.now() / 1000),
      state: "RUNNING",
    });
  } catch (error) {
    sendJsonError(response, 500, "Failed to start conversation", error);
  }
});

app.post("/api/stop-conversation", async (request, response) => {
  try {
    const agentId = String(request.body?.agent_id ?? "").trim();
    if (!agentId) {
      response.status(400).json({
        error: "agent_id is required",
      });
      return;
    }

    const client = createClient();
    try {
      await client.stopAgent(agentId);
    } catch (error) {
      if (isAgentAlreadyStoppingOrStopped(error)) {
        response.json({ success: true, state: "already-stopping" });
        return;
      }
      throw error;
    }

    response.json({ success: true });
  } catch (error) {
    sendJsonError(response, 500, "Failed to stop conversation", error);
  }
});

app.post("/api/interrupt-agent", async (request, response) => {
  try {
    const agentId = String(request.body?.agent_id ?? "").trim();
    if (!agentId) {
      response.status(400).json({
        error: "agent_id is required",
      });
      return;
    }

    const { appId } = currentAgoraConfig();
    const client = createClient();
    await client.agents.interrupt({
      appid: appId,
      agentId,
    });

    response.json({ success: true });
  } catch (error) {
    sendJsonError(response, 500, "Failed to interrupt conversation", error);
  }
});

app.listen(currentPort(), () => {
  console.log(`Agora quickstart server running on http://localhost:${currentPort()}`);
});
