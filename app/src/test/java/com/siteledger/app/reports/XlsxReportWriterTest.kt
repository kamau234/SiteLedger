package com.siteledger.app.reports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class XlsxReportWriterTest {

    private fun document(
        tableRows: List<List<String>> = listOf(
            listOf("Materials", "2", "12,500"),
            listOf("Labour", "1", "1,200"),
        ),
        totalsRow: List<String>? = listOf("Total", "3", "13,700"),
        notes: List<String> = listOf("All amounts are in Kenyan Shillings (KES)."),
    ) = ReportDocument(
        title = "Project Financial Summary",
        fileSlug = "Project_Summary",
        subtitle = "Riverside Villas",
        periodLabel = "All time",
        generatedAtLabel = "11 Oct 2026 09:30",
        fileStamp = "20261011_0930",
        companyName = "Mwangaza Builders",
        summaryRows = listOf(
            "Approved budget" to "KSh 500,000",
            "Budget used" to "80.0%",
        ),
        tableColumns = listOf(
            ReportColumn("Category", 3f),
            ReportColumn("Expenses", 1.2f, alignEnd = true, numeric = true),
            ReportColumn("Total (KES)", 2f, alignEnd = true, numeric = true),
        ),
        tableRows = tableRows,
        totalsRow = totalsRow,
        notes = notes,
    )

    private fun writeToZip(document: ReportDocument): Map<String, String> {
        val bytes = ByteArrayOutputStream()
        XlsxReportWriter().write(document, bytes)
        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes.toByteArray())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        return entries
    }

    @Test
    fun `emits every part required by excel`() {
        val parts = writeToZip(document()).keys
        assertEquals(
            setOf(
                "[Content_Types].xml",
                "_rels/.rels",
                "xl/workbook.xml",
                "xl/_rels/workbook.xml.rels",
                "xl/styles.xml",
                "xl/worksheets/sheet1.xml",
            ),
            parts,
        )
    }

    @Test
    fun `sheet contains header summary and title content`() {
        val sheet = writeToZip(document()).getValue("xl/worksheets/sheet1.xml")
        assertTrue(sheet.contains("Mwangaza Builders"))
        assertTrue(sheet.contains("Project Financial Summary"))
        assertTrue(sheet.contains("Riverside Villas"))
        assertTrue(sheet.contains("All time · Generated 11 Oct 2026 09:30"))
        assertTrue(sheet.contains("Approved budget"))
        assertTrue(sheet.contains("Budget used"))
        assertTrue(sheet.contains("All amounts are in Kenyan Shillings (KES)."))
    }

    @Test
    fun `numeric columns become real numbers with the grouping number format`() {
        val sheet = writeToZip(document()).getValue("xl/worksheets/sheet1.xml")
        assertTrue(sheet.contains("<v>12500</v>"))
        assertTrue(sheet.contains("<v>1200</v>"))
        // Cells in numeric columns use the "#,##0" style (s=2) and totals use bold-number (s=4).
        assertTrue(sheet.contains("s=\"2\""))
        assertTrue(sheet.contains("<v>13700</v>"))
        assertTrue(sheet.contains("s=\"4\""))
    }

    @Test
    fun `text values are inline strings and xml is escaped`() {
        val doc = document(
            tableRows = listOf(listOf("A & B <C> \"D\" 'E'", "1", "2,000")),
            totalsRow = null,
        )
        val sheet = writeToZip(doc).getValue("xl/worksheets/sheet1.xml")
        assertTrue(sheet.contains("t=\"inlineStr\""))
        assertTrue(sheet.contains("A &amp; B &lt;C&gt; &quot;D&quot; &apos;E&apos;"))
    }

    @Test
    fun `empty cells are emitted as styled but empty cells`() {
        val doc = document(
            tableRows = listOf(listOf("No expenses recorded for this period", "", "")),
            totalsRow = null,
        )
        val sheet = writeToZip(doc).getValue("xl/worksheets/sheet1.xml")
        // Row 10 is the data row; blank numeric-column cells stay as empty styled cells.
        assertTrue(sheet.contains("<c r=\"B10\" s=\"0\"/>"))
        assertTrue(sheet.contains("<c r=\"C10\" s=\"0\"/>"))
    }
}
