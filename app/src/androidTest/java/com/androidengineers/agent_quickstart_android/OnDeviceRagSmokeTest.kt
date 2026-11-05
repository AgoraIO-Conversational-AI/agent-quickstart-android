package com.androidengineers.agent_quickstart_android

import androidx.test.platform.app.InstrumentationRegistry
import com.androidengineers.agent_quickstart_android.rag.LocalDocumentSearch
import com.androidengineers.agent_quickstart_android.rag.LocalModelFiles
import com.androidengineers.agent_quickstart_android.rag.OnDeviceRag
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Opt-in real-device test, never downloads or substitutes model responses. */
class OnDeviceRagSmokeTest {
    @Test fun actualModelsRetrieveAndGenerateOnPhone() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("runGemmaSmoke") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val activity = InstrumentationRegistry.getInstrumentation().startActivitySync(
            android.content.Intent(context, MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        val files = LocalModelFiles(context)
        val external = File(context.getExternalFilesDir(null), "models")
        if (!files.gemma.isFile) File(external, "gemma.litertlm").copyTo(files.gemma, overwrite = true)
        if (!files.embedding.isFile) File(external, "embedding_gemma.task").copyTo(files.embedding, overwrite = true)
        val gpu = InstrumentationRegistry.getArguments().getString("gemmaGpu") == "true"
        val runtime = OnDeviceRag(context, files, if (gpu) com.google.ai.edge.litertlm.Backend.GPU() else com.google.ai.edge.litertlm.Backend.CPU(threadCount = 4))
        android.util.Log.i("GemmaSmoke", "Backend: ${if (gpu) "GPU" else "CPU"}")
        try {
            runtime.prepare(LocalDocumentSearch.sample + "\n\n" + (
                "Garden shed notes. Store empty pots on the wooden shelf. Keep seed packets in a sealed container. " +
                "The green toolbox contains a hand rake, a brush, and spare labels. Sweep the floor before closing the shed. " +
                "Kitchen notes. Wash the mixing bowl after baking. Keep dry ingredients in labeled jars away from the sink. "
            ).repeat(8)) { android.util.Log.i("GemmaSmoke", it) }
            val snapshots = java.util.concurrent.atomic.AtomicInteger()
            val result = runtime.answer("How many minutes does a full charge take?", "", "") { snapshots.incrementAndGet() }
            android.util.Log.i("GemmaSmoke", "answer=${result.text}; retrievalMs=${result.retrievalMs}; generationMs=${result.generationMs}; firstTokenMs=${result.firstTokenMs}; snapshots=${snapshots.get()}")
            assertTrue("Model should produce incremental output before completion", snapshots.get() > 1 && result.firstTokenMs >= 0 && result.firstTokenMs < result.generationMs)
            assertTrue("The smoke test must retrieve among multiple indexed passages", result.evidence.size > 1)
            assertTrue(result.evidence.any { it.text.contains("90 minutes") })
            assertTrue("Answer must use the document's charging time: ${result.text}", result.text.contains("90") || result.text.contains("ninety", ignoreCase = true))
        } finally { runtime.release(); InstrumentationRegistry.getInstrumentation().runOnMainSync { activity.finish() } }
    }
}
