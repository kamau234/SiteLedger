package com.siteledger.app.reports

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream

/**
 * Renders a [ReportDocument] to a multi-page A4 PDF with the platform PdfDocument API,
 * so exports work fully offline without any third-party PDF dependency.
 */
class PdfReportWriter {

    private val pageWidth = 595
    private val pageHeight = 842
    private val margin = 40f
    private val footerHeight = 46f
    private val contentWidth = pageWidth - 2 * margin
    private val cellGap = 8f
    private val sectionTable = 1

    private val titlePaint = paint(20f, TEXT, bold = true)
    private val companyPaint = paint(11f, TEXT, bold = true)
    private val subtitlePaint = paint(13f, TEXT)
    private val metaPaint = paint(9.5f, MUTED)
    private val bodyPaint = paint(10f, TEXT)
    private val bodyBoldPaint = paint(10f, TEXT, bold = true)
    private val headerPaint = paint(9.5f, MUTED, bold = true)
    private val notePaint = paint(8.5f, MUTED)
    private val footerPaint = paint(8.5f, MUTED)
    private val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = RULE
        strokeWidth = 0.7f
        style = Paint.Style.STROKE
    }

    fun write(document: ReportDocument, output: OutputStream) {
        val built = buildLines(document)
        val pages = paginate(built.lines, built.tableHeaders)
        val pdf = PdfDocument()
        try {
            pages.forEachIndexed { index, lines ->
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                val page = pdf.startPage(pageInfo)
                drawPage(page.canvas, lines, document, index + 1, pages.size)
                pdf.finishPage(page)
            }
            pdf.writeTo(output)
        } finally {
            pdf.close()
        }
    }

    // ---- Layout construction -------------------------------------------------

    private class PdfCell(
        val text: String,
        val x: Float,
        val width: Float,
        val paint: Paint,
        val alignEnd: Boolean,
    )

    private class RenderLine(
        val cells: List<PdfCell>,
        val height: Float,
        val spacingAfter: Float,
        val sectionId: Int? = null,
        val isHeader: Boolean = false,
        val isRule: Boolean = false,
    )

    private class BuiltDocument(
        val lines: List<RenderLine>,
        val tableHeaders: Map<Int, RenderLine>,
    )

    private fun buildLines(doc: ReportDocument): BuiltDocument {
        val lines = mutableListOf<RenderLine>()
        val tableHeaders = mutableMapOf<Int, RenderLine>()

        if (doc.companyName.isNotBlank()) {
            wrapText(doc.companyName, companyPaint, contentWidth).forEach { text ->
                lines += RenderLine(listOf(PdfCell(text, margin, contentWidth, companyPaint, false)), 15f, 2f)
            }
        }
        wrapText(doc.title, titlePaint, contentWidth).forEach { text ->
            lines += RenderLine(listOf(PdfCell(text, margin, contentWidth, titlePaint, false)), 25f, 2f)
        }
        doc.subtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
            wrapText(subtitle, subtitlePaint, contentWidth).forEach { text ->
                lines += RenderLine(listOf(PdfCell(text, margin, contentWidth, subtitlePaint, false)), 18f, 1f)
            }
        }
        lines += RenderLine(
            listOf(PdfCell("${doc.periodLabel}  ·  Generated ${doc.generatedAtLabel}", margin, contentWidth, metaPaint, false)),
            14f, 8f,
        )

        doc.summaryRows.forEach { (label, value) ->
            val labelWidth = contentWidth * 0.62f
            val valueWidth = contentWidth - labelWidth
            lines += RenderLine(
                cells = listOf(
                    PdfCell(label, margin, labelWidth, bodyPaint, false),
                    PdfCell(value, margin + labelWidth, valueWidth, bodyBoldPaint, true),
                ),
                height = 15f,
                spacingAfter = 1.5f,
            )
        }
        if (doc.summaryRows.isNotEmpty()) {
            lines += spacer(8f)
        }

        if (doc.tableColumns.isNotEmpty()) {
            val widths = resolveWidths(doc.tableColumns)
            val headerLine = RenderLine(
                cells = layoutRow(doc.tableColumns.map { it.title.uppercase() }, doc.tableColumns, widths, headerPaint),
                height = 15f,
                spacingAfter = 1f,
                sectionId = sectionTable,
                isHeader = true,
            )
            tableHeaders[sectionTable] = headerLine
            lines += headerLine
            lines += ruleLine(sectionTable)

            doc.tableRows.forEach { row ->
                val wrapped = wrapRow(row, doc.tableColumns, widths)
                wrapped.forEach { cells ->
                    lines += RenderLine(cells, height = 14.5f, spacingAfter = 1.5f, sectionId = sectionTable)
                }
            }

            doc.totalsRow?.let { totals ->
                lines += ruleLine(sectionTable)
                lines += RenderLine(
                    cells = layoutRow(totals, doc.tableColumns, widths, bodyBoldPaint),
                    height = 15f,
                    spacingAfter = 1f,
                    sectionId = sectionTable,
                )
            }
        }

        if (doc.notes.isNotEmpty()) {
            lines += spacer(10f)
            doc.notes.forEach { note ->
                wrapText("• $note", notePaint, contentWidth).forEach { text ->
                    lines += RenderLine(listOf(PdfCell(text, margin, contentWidth, notePaint, false)), 12.5f, 1f)
                }
            }
        }
        return BuiltDocument(lines, tableHeaders)
    }

    private fun resolveWidths(columns: List<ReportColumn>): FloatArray {
        val totalWeight = columns.sumOf { it.weight.toDouble() }.toFloat()
        val usable = contentWidth - cellGap * (columns.size - 1)
        return FloatArray(columns.size) { usable * columns[it].weight / totalWeight }
    }

    private fun layoutRow(
        texts: List<String>,
        columns: List<ReportColumn>,
        widths: FloatArray,
        paint: Paint,
    ): List<PdfCell> {
        var x = margin
        return columns.indices.map { i ->
            val cell = PdfCell(texts.getOrElse(i) { "" }, x, widths[i], paint, columns[i].alignEnd)
            x += widths[i] + cellGap
            cell
        }
    }

    private fun wrapRow(row: List<String>, columns: List<ReportColumn>, widths: FloatArray): List<List<PdfCell>> {
        val wrappedCells = columns.indices.map { i ->
            wrapText(row.getOrElse(i) { "" }, bodyPaint, widths[i])
        }
        val lineCount = wrappedCells.maxOf { it.size }
        return (0 until lineCount).map { lineIndex ->
            var x = margin
            wrappedCells.mapIndexed { i, cellLines ->
                val cell = PdfCell(
                    cellLines.getOrElse(lineIndex) { "" },
                    x, widths[i], bodyPaint, columns[i].alignEnd,
                )
                x += widths[i] + cellGap
                cell
            }
        }
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isEmpty()) return listOf("")
        if (paint.measureText(text) <= maxWidth) return listOf(text)
        val lines = mutableListOf<String>()
        var remaining = text
        while (remaining.isNotEmpty()) {
            val count = paint.breakText(remaining, true, maxWidth, null)
            if (count >= remaining.length) {
                lines += remaining
                break
            }
            val chunk = remaining.substring(0, count)
            val breakIndex = chunk.lastIndexOf(' ')
            val cut = when {
                breakIndex > 0 -> breakIndex
                count > 0 -> count
                else -> {
                    lines += remaining
                    break
                }
            }
            lines += remaining.substring(0, cut).trimEnd()
            remaining = remaining.substring(cut).trimStart()
        }
        return lines
    }

    private fun spacer(height: Float): RenderLine = RenderLine(emptyList(), height, 0f)

    private fun ruleLine(sectionId: Int?): RenderLine =
        RenderLine(emptyList(), 0.5f, 3f, sectionId = sectionId, isRule = true)

    // ---- Pagination ----------------------------------------------------------

    private fun paginate(lines: List<RenderLine>, tableHeaders: Map<Int, RenderLine>): List<List<RenderLine>> {
        val maxY = pageHeight - footerHeight
        val pages = mutableListOf<MutableList<RenderLine>>()
        var current = mutableListOf<RenderLine>()
        pages += current
        var y = margin

        for (line in lines) {
            val lineHeight = line.height + line.spacingAfter
            if (y + lineHeight > maxY && current.isNotEmpty()) {
                current = mutableListOf()
                pages += current
                y = margin
                val sectionId = line.sectionId
                if (sectionId != null && !line.isHeader) {
                    tableHeaders[sectionId]?.let { header ->
                        current += header
                        y += header.height + header.spacingAfter
                    }
                    if (!line.isRule) {
                        val rule = ruleLine(sectionId)
                        current += rule
                        y += rule.height + rule.spacingAfter
                    }
                }
            }
            current += line
            y += lineHeight
        }
        return pages
    }

    // ---- Drawing -------------------------------------------------------------

    private fun drawPage(
        canvas: Canvas,
        lines: List<RenderLine>,
        doc: ReportDocument,
        pageNumber: Int,
        pageCount: Int,
    ) {
        var y = margin
        lines.forEach { line ->
            if (line.isRule) {
                canvas.drawLine(margin, y, margin + contentWidth, y, rulePaint)
            } else {
                val baseline = y + line.height * 0.75f
                line.cells.forEach { cell ->
                    val x = if (cell.alignEnd) {
                        cell.x + cell.width - cell.paint.measureText(cell.text)
                    } else {
                        cell.x
                    }
                    canvas.drawText(cell.text, x, baseline, cell.paint)
                }
            }
            y += line.height + line.spacingAfter
        }
        drawFooter(canvas, doc, pageNumber, pageCount)
    }

    private fun drawFooter(canvas: Canvas, doc: ReportDocument, pageNumber: Int, pageCount: Int) {
        canvas.drawLine(margin, pageHeight - 38f, margin + contentWidth, pageHeight - 38f, rulePaint)
        val baseline = pageHeight - 20f
        canvas.drawText(doc.companyName.ifBlank { "SiteLedger" }, margin, baseline, footerPaint)
        val pageText = "Page $pageNumber of $pageCount"
        canvas.drawText(pageText, margin + contentWidth / 2 - footerPaint.measureText(pageText) / 2, baseline, footerPaint)
        val generated = "Generated ${doc.generatedAtLabel}"
        canvas.drawText(generated, margin + contentWidth - footerPaint.measureText(generated), baseline, footerPaint)
    }

    private fun paint(size: Float, color: Int, bold: Boolean = false): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = if (bold) Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) else Typeface.SANS_SERIF
        }

    private companion object {
        val TEXT = Color.rgb(0x1F, 0x29, 0x33)
        val MUTED = Color.rgb(0x6B, 0x72, 0x80)
        val RULE = Color.rgb(0xD1, 0xD5, 0xDB)
    }
}
