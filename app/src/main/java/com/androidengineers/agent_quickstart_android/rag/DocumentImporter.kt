package com.androidengineers.agent_quickstart_android.rag

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayOutputStream

object DocumentImporter {
    /** Call on the IO dispatcher. Documents are never uploaded. */
    fun read(context: Context, uri: Uri): String {
        val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                require(output.size() + count <= 10 * 1024 * 1024) { "Choose a document under 10 MB." }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: error("Unable to open this document.")
        val pdf = context.contentResolver.getType(uri) == "application/pdf" ||
            bytes.take(5).toByteArray().toString(Charsets.US_ASCII) == "%PDF-"
        val text = if (pdf) {
            PDFBoxResourceLoader.init(context.applicationContext)
            PDDocument.load(bytes).use { document ->
                require(!document.isEncrypted) { "Password-protected PDFs are not supported. Export an unlocked copy." }
                require(document.numberOfPages <= 100) { "Choose a PDF with at most 100 pages." }
                val stripper = PDFTextStripper().apply { sortByPosition = true }
                buildString {
                    for (page in 1..document.numberOfPages) {
                        stripper.startPage = page
                        stripper.endPage = page
                        append(stripper.getText(document))
                        append("\n\n")
                        require(length <= 256 * 1024) { "Extracted text exceeds 256 KB. Choose a shorter document." }
                    }
                }
            }
        } else {
            require(bytes.size <= 256 * 1024) { "Choose a text file under 256 KB." }
            bytes.toString(Charsets.UTF_8)
        }
        require(text.isNotBlank()) {
            if (pdf) "No readable text found. Scanned PDFs need OCR; choose a PDF with selectable text."
            else "The document is empty."
        }
        return text
    }
}
