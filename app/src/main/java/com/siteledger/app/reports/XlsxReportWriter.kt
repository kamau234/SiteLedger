package com.siteledger.app.reports

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Minimal OOXML (.xlsx) writer — emits only the parts Excel/Sheets/LibreOffice need.
 * Kept dependency-free so the APK stays small and export works offline.
 */
class XlsxReportWriter {

    private data class SheetCell(val text: String, val style: Int, val numeric: Long? = null)

    fun write(document: ReportDocument, output: OutputStream) {
        val zip = ZipOutputStream(output)
        fun addEntry(name: String, content: String) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(content.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        addEntry("[Content_Types].xml", CONTENT_TYPES)
        addEntry("_rels/.rels", ROOT_RELS)
        addEntry("xl/workbook.xml", WORKBOOK)
        addEntry("xl/_rels/workbook.xml.rels", WORKBOOK_RELS)
        addEntry("xl/styles.xml", STYLES)
        addEntry("xl/worksheets/sheet1.xml", sheetXml(document))
        zip.finish()
    }

    private fun sheetXml(doc: ReportDocument): String {
        val rows = mutableListOf<List<SheetCell>>()
        fun addRow(vararg cells: SheetCell) {
            rows += cells.toList()
        }

        if (doc.companyName.isNotBlank()) addRow(SheetCell(doc.companyName, S_BOLD))
        addRow(SheetCell(doc.title, S_TITLE))
        doc.subtitle?.takeIf { it.isNotBlank() }?.let { addRow(SheetCell(it, S_NORMAL)) }
        addRow(SheetCell("${doc.periodLabel} · Generated ${doc.generatedAtLabel}", S_NORMAL))
        addRow()

        doc.summaryRows.forEach { (label, value) ->
            addRow(SheetCell(label, S_NORMAL), SheetCell(value, S_BOLD))
        }
        if (doc.summaryRows.isNotEmpty()) addRow()

        if (doc.tableColumns.isNotEmpty()) {
            addRow(*doc.tableColumns.map { SheetCell(it.title, S_BOLD) }.toTypedArray())
            doc.tableRows.forEach { row ->
                addRow(
                    *doc.tableColumns.indices.map { i ->
                        cellFor(row.getOrElse(i) { "" }, doc.tableColumns[i], bold = false)
                    }.toTypedArray(),
                )
            }
            doc.totalsRow?.let { totals ->
                addRow(
                    *doc.tableColumns.indices.map { i ->
                        cellFor(totals.getOrElse(i) { "" }, doc.tableColumns[i], bold = true)
                    }.toTypedArray(),
                )
            }
        }

        if (doc.notes.isNotEmpty()) {
            addRow()
            doc.notes.forEach { addRow(SheetCell("• $it", S_NORMAL)) }
        }

        val sheetData = buildString {
            append("<sheetData>")
            rows.forEachIndexed { rowIndex, cells ->
                val rowNumber = rowIndex + 1
                if (cells.isEmpty()) {
                    append("<row r=\"$rowNumber\"/>")
                } else {
                    append("<row r=\"$rowNumber\">")
                    cells.forEachIndexed { cellIndex, cell ->
                        append(cellXml("${columnLetter(cellIndex)}$rowNumber", cell))
                    }
                    append("</row>")
                }
            }
            append("</sheetData>")
        }

        val cols = if (doc.tableColumns.isEmpty()) {
            ""
        } else {
            buildString {
                append("<cols>")
                doc.tableColumns.forEachIndexed { index, column ->
                    val width = (column.weight * 14f).coerceIn(10f, 60f)
                    append("<col min=\"${index + 1}\" max=\"${index + 1}\" width=\"$width\" customWidth=\"1\"/>")
                }
                append("</cols>")
            }
        }

        return XML_HEADER +
            "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
            cols + sheetData +
            "</worksheet>"
    }

    private fun cellFor(text: String, column: ReportColumn, bold: Boolean): SheetCell {
        val value = if (column.numeric) text.replace(",", "").toLongOrNull() else null
        val style = when {
            value != null && bold -> S_BOLD_NUMBER
            value != null -> S_NUMBER
            bold -> S_BOLD
            else -> S_NORMAL
        }
        return SheetCell(text, style, value)
    }

    private fun cellXml(reference: String, cell: SheetCell): String = when {
        cell.numeric != null -> "<c r=\"$reference\" s=\"${cell.style}\"><v>${cell.numeric}</v></c>"
        cell.text.isEmpty() -> "<c r=\"$reference\" s=\"${cell.style}\"/>"
        else -> "<c r=\"$reference\" s=\"${cell.style}\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${escapeXml(cell.text)}</t></is></c>"
    }

    private fun columnLetter(index: Int): String {
        var i = index
        val sb = StringBuilder()
        while (true) {
            sb.insert(0, 'A' + i % 26)
            i = i / 26 - 1
            if (i < 0) break
        }
        return sb.toString()
    }

    private fun escapeXml(text: String): String = buildString(text.length) {
        for (ch in text) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(ch)
            }
        }
    }

    private companion object {
        const val XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"

        const val S_NORMAL = 0
        const val S_BOLD = 1
        const val S_NUMBER = 2
        const val S_TITLE = 3
        const val S_BOLD_NUMBER = 4

        const val CONTENT_TYPES = XML_HEADER +
            "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
            "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
            "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
            "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
            "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>" +
            "</Types>"

        const val ROOT_RELS = XML_HEADER +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
            "</Relationships>"

        const val WORKBOOK = XML_HEADER +
            "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" " +
            "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
            "<sheets><sheet name=\"Report\" sheetId=\"1\" r:id=\"rId1\"/></sheets>" +
            "</workbook>"

        const val WORKBOOK_RELS = XML_HEADER +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
            "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>" +
            "</Relationships>"

        // numFmtId 3 is the built-in "#,##0" whole-number format.
        const val STYLES = XML_HEADER +
            "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
            "<fonts count=\"3\">" +
            "<font><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
            "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
            "<font><b/><sz val=\"14\"/><name val=\"Calibri\"/></font>" +
            "</fonts>" +
            "<fills count=\"2\">" +
            "<fill><patternFill patternType=\"none\"/></fill>" +
            "<fill><patternFill patternType=\"gray125\"/></fill>" +
            "</fills>" +
            "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>" +
            "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
            "<cellXfs count=\"5\">" +
            "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
            "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>" +
            "<xf numFmtId=\"3\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>" +
            "<xf numFmtId=\"0\" fontId=\"2\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>" +
            "<xf numFmtId=\"3\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\" applyFont=\"1\"/>" +
            "</cellXfs>" +
            "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>" +
            "</styleSheet>"
    }
}
