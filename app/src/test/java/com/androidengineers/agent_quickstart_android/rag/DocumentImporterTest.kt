package com.androidengineers.agent_quickstart_android.rag

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@org.robolectric.annotation.Config(sdk = [34])
@RunWith(RobolectricTestRunner::class)
class DocumentImporterTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test fun extractsPdfText() {
        PDFBoxResourceLoader.init(context)
        val file = File(context.cacheDir, "manual.pdf")
        PDDocument().use { pdf ->
            val page = PDPage()
            pdf.addPage(page)
            PDPageContentStream(pdf, page).use { content ->
                content.beginText()
                content.setFont(PDType1Font.HELVETICA, 12f)
                content.newLineAtOffset(50f, 700f)
                content.showText("Amber indicator means clean the filter.")
                content.endText()
            }
            pdf.save(file)
        }
        assertTrue(DocumentImporter.read(context, Uri.fromFile(file)).contains("clean the filter"))
    }

    @Test fun rejectsPdfWithoutText() {
        PDFBoxResourceLoader.init(context)
        val file = File(context.cacheDir, "blank.pdf")
        PDDocument().use { pdf -> pdf.addPage(PDPage()); pdf.save(file) }
        val error = runCatching { DocumentImporter.read(context, Uri.fromFile(file)) }.exceptionOrNull()
        assertTrue(error?.message.orEmpty().contains("OCR"))
    }

    @Test fun readsPlainText() {
        val file = File(context.cacheDir, "manual.txt").apply { writeText("Filter cleaning instructions") }
        assertEquals("Filter cleaning instructions", DocumentImporter.read(context, Uri.fromFile(file)))
    }
}
