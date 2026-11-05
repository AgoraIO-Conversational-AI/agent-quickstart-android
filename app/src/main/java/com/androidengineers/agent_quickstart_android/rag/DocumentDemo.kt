package com.androidengineers.agent_quickstart_android.rag

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

data class DocumentDemoState(
    val loaded: Boolean = false,
    val gemmaImported: Boolean = false,
    val embeddingImported: Boolean = false,
    val answer: String = "",
    val busy: Boolean = false,
    val status: String = "Open a PDF or text document to begin.",
    val question: String = "",
    val sources: List<Evidence> = emptyList(),
)

@Composable
fun DocumentDemo(
    state: DocumentDemoState,
    inSession: Boolean,
    starting: Boolean,
    error: String?,
    onOpenDocument: (Uri) -> Unit,
    onImportModel: (Uri, Boolean) -> Unit,
    onStartVoice: () -> Unit,
    openRequest: Int = 0,
) {
    var open by remember { mutableStateOf(false) }
    var showModelSettings by remember { mutableStateOf(false) }
    LaunchedEffect(openRequest) { if (openRequest > 0) open = true }
    val gemmaImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImportModel(uri, true)
    }
    val embeddingImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImportModel(uri, false)
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onOpenDocument(uri)
    }
    FilledTonalButton(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        onClick = { open = true },
    ) { Text(if (state.loaded) "Your document • voice conversation" else "Open document") }
    if (open) Dialog(onDismissRequest = { open = false }) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(
                Modifier.heightIn(max = 620.dp).padding(20.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Talk to your document", style = MaterialTheme.typography.headlineSmall)
                Text(state.status)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (state.gemmaImported && state.embeddingImported) {
                    Text("Models ready on this phone", style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = { showModelSettings = !showModelSettings }) { Text("Model settings") }
                }
                if (!state.gemmaImported || !state.embeddingImported || showModelSettings) {
                Text("On-device models", style = MaterialTheme.typography.titleSmall)
                Text("Gemma: ${if (state.gemmaImported) "imported" else "required (.litertlm)"} • EmbeddingGemma: ${if (state.embeddingImported) "imported" else "required (.task)"}")
                OutlinedButton(enabled = !state.busy && !inSession && !starting, onClick = { gemmaImporter.launch(arrayOf("*/*")) }) {
                    Text(if (state.gemmaImported) "Replace Gemma model" else "Import Gemma model")
                }
                OutlinedButton(enabled = !state.busy && !inSession && !starting, onClick = { embeddingImporter.launch(arrayOf("*/*")) }) {
                    Text(if (state.embeddingImported) "Replace embedding model" else "Import embedding model")
                }
                }
                OutlinedButton(enabled = state.gemmaImported && state.embeddingImported && !state.busy && !inSession && !starting, onClick = {
                    importer.launch(arrayOf("text/plain", "text/markdown", "application/pdf"))
                }) { Text(if (state.loaded) "Change document" else "Open PDF or text document") }
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Button(enabled = state.loaded && !state.busy && !inSession && !starting, onClick = onStartVoice) {
                    Text(if (starting) "Connecting…" else if (inSession) "Voice session active" else "Start voice session")
                }
                Text(if (inSession) "Keep talking naturally through Agora. EmbeddingGemma searches your document and Gemma generates answers on this phone. Close this panel to see the conversation and microphone controls."
                    else "Open your document, start the voice session, then ask questions aloud. End an existing session before changing the document.")
                Text("The PDF, embeddings, retrieval, and Gemma generation stay on this phone. Question transcripts and generated answers travel through the backend and Agora. Voice needs internet. Keep this app open.")
                if (state.answer.isNotBlank()) Text("Gemma answer: ${state.answer}")
                if (state.question.isNotBlank()) {
                    Text("Latest document search: ${state.question}", style = MaterialTheme.typography.titleSmall)
                    state.sources.forEach { source ->
                        Text("Passage ${source.paragraph}: ${source.text}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                TextButton(onClick = { open = false }) { Text("Back to conversation") }
            }
        }
    }
}
