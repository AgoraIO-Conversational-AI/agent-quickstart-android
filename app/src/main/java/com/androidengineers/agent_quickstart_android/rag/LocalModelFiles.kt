package com.androidengineers.agent_quickstart_android.rag

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.security.MessageDigest

class LocalModelFiles(private val context: Context) {
    private val directory = (context.getExternalFilesDir("models") ?: File(context.filesDir, "models")).apply { mkdirs() }
    val gemma = File(directory, "gemma.litertlm")
    val embedding = File(directory, "embedding_gemma.task")
    val gemmaReady: Boolean get() = gemma.isFile && gemma.length() == GEMMA_SIZE
    val embeddingReady: Boolean get() = embedding.isFile && embedding.length() == EMBEDDING_SIZE
    val ready: Boolean get() = gemmaReady && embeddingReady

    suspend fun import(uri: Uri, generation: Boolean, progress: (String) -> Unit) {
        val target = if (generation) gemma else embedding
        val temporary = File(directory, "${target.name}.part")
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    var total = 0L
                    var reported = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= 4L * 1024 * 1024 * 1024) { "Model exceeds the 4 GB import limit." }
                        check(directory.usableSpace > count + 32L * 1024 * 1024) { "Not enough free storage to import the model." }
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        if (total - reported >= 16 * 1024 * 1024) {
                            progress("Importing ${target.name}: ${total / (1024 * 1024)} MB")
                            reported = total
                        }
                    }
                    require(total > 1024 * 1024) { "This file is too small to be the model. Select the downloaded model binary." }
                }
            } ?: error("Unable to open the model file.")
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            val expectedSize = if (generation) GEMMA_SIZE else EMBEDDING_SIZE
            val expectedHash = if (generation) GEMMA_SHA256 else EMBEDDING_SHA256
            require(temporary.length() == expectedSize && hash == expectedHash) {
                "This is not the supported model file, or it is incomplete. Use the exact model download linked in the README."
            }
            check(temporary.renameTo(target)) { "Could not save the model." }
            File(directory, "${target.name}.sha256").writeText(hash)
        } finally { temporary.delete() }
    }

    companion object {
        const val GEMMA_SIZE = 2588147712L
        const val EMBEDDING_SIZE = 183816181L
        const val GEMMA_SHA256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"
        const val EMBEDDING_SHA256 = "913b7a1edc7c7c3d1da3979ec1d0648ed9e0a370f181bb59ab177ca4b97707ad"
    }
}
