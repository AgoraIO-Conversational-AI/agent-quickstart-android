package com.androidengineers.agent_quickstart_android.rag

import android.content.Context
import com.google.ai.edge.litertlm.*
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder.EmbeddingType
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder.TextFormatContext
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder.TextRole
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Model work is serialized; no remote model or cloud-generation fallback. */
class OnDeviceRag(private val context: Context, private val files: LocalModelFiles, private val inferenceBackend: Backend = Backend.CPU(threadCount = 4)) {
    private val mutex = Mutex()
    private var embedder: TextEmbedder? = null
    private var engine: Engine? = null
    private var index: HybridDocumentIndex? = null

    suspend fun prepare(text: String, progress: (String) -> Unit) = withContext(Dispatchers.Default) {
        mutex.withLock {
            check(files.ready) { "Import both Gemma and EmbeddingGemma models first." }
            progress("Loading EmbeddingGemma on this phone…")
            ensureEmbedder()
            val passages = HybridDocumentIndex.chunks(text)
            val vectors = passages.mapIndexed { i, passage ->
                currentCoroutineContext().ensureActive()
                progress("Embedding document passage ${i + 1}/${passages.size} on device…")
                embed(passage, false)
            }
            embedder?.close()
            embedder = null
            if (engine == null) {
                progress("Loading Gemma with LiteRT LM on this phone…")
                engine = Engine(EngineConfig(modelPath = files.gemma.absolutePath,
                    backend = inferenceBackend, maxNumTokens = 2048, cacheDir = context.cacheDir.absolutePath)).also { it.initialize() }
            }
            index = HybridDocumentIndex(passages, vectors)
            progress("Ready • ${passages.size} passages indexed • Gemma runs on this phone")
        }
    }

    data class Answer(val text: String, val evidence: List<Evidence>, val retrievalMs: Long, val generationMs: Long, val firstTokenMs: Long)

    suspend fun answer(question: String, history: String, previousQuestion: String, onPartial: (String) -> Unit = {}): Answer = withContext(Dispatchers.Default) {
        mutex.withLock {
            val currentIndex = checkNotNull(index) { "Open and index a document first." }
            val started = System.nanoTime()
            val query = (previousQuestion.takeLast(500) + " " + question).trim()
            ensureEmbedder()
            val vector = try { embed(query, true) } finally { embedder?.close(); embedder = null }
            val evidence = currentIndex.search(query, vector).take(2)
            val retrievalMs = (System.nanoTime() - started) / 1_000_000
            val generated = System.nanoTime()
            val prompt = RagPrompt.build(question, history, evidence)
            val conversation = checkNotNull(engine).createConversation(ConversationConfig(
                samplerConfig = SamplerConfig(topK = 20, topP = 0.9, temperature = 0.2)))
            val done = CompletableDeferred<Unit>()
            val output = StringBuilder()
            var firstTokenMs = -1L
            try {
                conversation.sendMessageAsync(Contents.of(listOf(Content.Text(prompt))), object : MessageCallback {
                    override fun onMessage(message: Message) {
                        message.contents.contents.filterIsInstance<Content.Text>().forEach { output.append(it.text) }
                        if (output.isNotEmpty()) {
                            if (firstTokenMs < 0) firstTokenMs = (System.nanoTime() - generated) / 1_000_000
                            onPartial(output.toString().take(2000))
                        }
                        // Bound excessively long answers even if the model ignores the requested length.
                        if (output.length > 1800) conversation.cancelProcess()
                    }
                    override fun onDone() { done.complete(Unit) }
                    override fun onError(throwable: Throwable) {
                        if (output.length > 1800) done.complete(Unit) else done.completeExceptionally(throwable)
                    }
                })
                withTimeout(120_000) { done.await() }
                val answer = output.toString().take(2000)
                check(answer.isNotBlank()) { "Gemma produced no answer. Please try again." }
                Answer(answer, evidence, retrievalMs, (System.nanoTime() - generated) / 1_000_000, firstTokenMs)
            } finally {
                if (!done.isCompleted) {
                    conversation.cancelProcess()
                    // Wait for native inference to finish before closing its conversation or starting another.
                    withContext(NonCancellable) { runCatching { done.await() } }
                }
                conversation.close()
            }
        }
    }

    private fun ensureEmbedder() {
        if (embedder == null) {
            embedder = TextEmbedder.createFromOptions(context, TextEmbedder.TextEmbedderOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath(files.embedding.absolutePath).build())
                .setQuantize(false).build())
        }
    }

    private fun embed(text: String, query: Boolean): FloatArray {
        val format = TextFormatContext.builder()
            .setTaskType(if (query) EmbeddingType.RETRIEVAL_QUERY else EmbeddingType.RETRIEVAL_DOCUMENT)
            .setRole(if (query) TextRole.QUERY else TextRole.DOCUMENT)
            .apply { if (!query) setTitle("User document") }.build()
        return checkNotNull(embedder).embed(text, format).embeddingResult().embeddings().first().floatEmbedding().copyOf()
    }

    suspend fun release() = withContext(Dispatchers.Default) {
        mutex.withLock {
            engine?.close(); engine = null
            embedder?.close(); embedder = null
            index = null
        }
    }
}
