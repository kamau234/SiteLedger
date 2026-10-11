package com.siteledger.app.reports

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream

/** Writes generated reports to a user-chosen location via the Storage Access Framework. */
class ReportExporter(private val context: Context) {

    private val pdfWriter = PdfReportWriter()
    private val xlsxWriter = XlsxReportWriter()

    suspend fun exportPdf(document: ReportDocument, uri: Uri) =
        writeTo(uri) { pdfWriter.write(document, it) }

    suspend fun exportXlsx(document: ReportDocument, uri: Uri) =
        writeTo(uri) { xlsxWriter.write(document, it) }

    private suspend fun writeTo(uri: Uri, block: (OutputStream) -> Unit) {
        withContext(Dispatchers.IO) {
            val stream = context.contentResolver.openOutputStream(uri)
                ?: throw IOException("Could not open the selected file for writing.")
            stream.use { block(it) }
        }
    }
}
