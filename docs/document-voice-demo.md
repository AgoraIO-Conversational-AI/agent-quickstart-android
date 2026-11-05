# Agora + on-device RAG + Gemma

1. Obtain the Gemma `.litertlm` and EmbeddingGemma `.task` files using the main README links; import them in the document panel, or push them into scoped app storage.
2. Set `PUBLIC_BASE_URL` in `server/.env.local` to the HTTPS tunnel URL and restart the backend. Configure the same URL for Android and rebuild.
3. Open a short PDF with selectable text, TXT, or Markdown. Wait for document embeddings and Gemma loading.
4. Start the Agora session, allow microphone access, and keep talking naturally.
5. Reopen the document panel for the latest local answer, evidence, and timings.

Agora ASR supplies question text. Python relays the turn to Android. EmbeddingGemma + cosine/BM25 hybrid retrieval selects context, and Gemma generates the answer locally through LiteRT LM. Only the generated answer returns through the model relay for Agora TTS. There is no cloud generation fallback in document mode.

Keep the app open. A disconnected phone, model error, or inference timeout produces an availability message rather than a cloud-generated answer. End the session before replacing documents or models.

Use the Field Kit sample: ask how many minutes charging takes (90), then how to clean the filter. Ask about warranty price to evaluate refusal behavior. Model grounding is not guaranteed; verify answers against displayed evidence before presenting.

Gemma now streams partial output through the relay into Agora while generation continues. The UI shows first-text timing, retrieval time, and total generation time. Local CPU prefill and speech/network processing still add latency. The README explains device/storage requirements, model downloads, privacy boundaries, and the real-device smoke test.
