package com.example.export

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.Customer
import com.example.data.CustomerSummaryMetric
import com.example.data.MeterMath
import com.example.data.SyncedRecord
import com.example.data.MeterReading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class ReportPayload(
    val title: String,
    val scopeLabel: String,
    val periodLabel: String,
    val generatedBy: String,
    val readings: List<SyncedRecord<MeterReading>>,
    val customers: List<Customer>,
    val customerMetrics: List<CustomerSummaryMetric>
)

object ReportExporter {
    const val MIME_PDF = "application/pdf"
    const val MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    fun suggestedFileName(extension: String, scopeLabel: String): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val slug = scopeLabel.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .take(24)
            .ifBlank { "all_customers" }
        return "VoltLedger_${slug}_$stamp.$extension"
    }

    suspend fun writePdfToStream(payload: ReportPayload, outputStream: OutputStream) =
        withContext(Dispatchers.IO) {
            val document = buildPdfDocument(payload)
            try {
                document.writeTo(outputStream)
                outputStream.flush()
            } finally {
                document.close()
            }
        }

    suspend fun writeExcelToStream(payload: ReportPayload, outputStream: OutputStream) =
        withContext(Dispatchers.IO) {
            writeXlsxWorkbook(payload, outputStream)
        }

    suspend fun createShareablePdf(context: Context, payload: ReportPayload): Uri =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "reports").apply { mkdirs() }
            val file = File(dir, suggestedFileName("pdf", payload.scopeLabel))
            FileOutputStream(file).use { out ->
                val document = buildPdfDocument(payload)
                try {
                    document.writeTo(out)
                    out.flush()
                } finally {
                    document.close()
                }
            }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }

    suspend fun createShareableExcel(context: Context, payload: ReportPayload): Uri =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "reports").apply { mkdirs() }
            val file = File(dir, suggestedFileName("xlsx", payload.scopeLabel))
            FileOutputStream(file).use { out ->
                writeXlsxWorkbook(payload, out)
            }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }

    fun launchShareIntent(context: Context, uri: Uri, mimeType: String, subject: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share VoltLedger Report"))
    }

    // -------------------------------------------------------------------------
    // Native Multi-Page PDF Report Renderer (A4 Portrait 595 x 842 pt)
    // -------------------------------------------------------------------------
    private fun buildPdfDocument(payload: ReportPayload): PdfDocument {
        val pdf = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 36f
        val contentWidth = pageWidth - margin * 2

        val navyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0B1E33") }
        val cyanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#00A6C7") }
        val amberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#D97706") }
        val emeraldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#059669") }
        val surfacePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F3F6FA") }
        val stripePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F8FAFC") }
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CBD5E1")
            strokeWidth = 0.8f
        }

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 18f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            textSize = 9.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0B1E33")
            textSize = 11.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#475569")
            textSize = 8.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val metricPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0B1E33")
            textSize = 14f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val tableHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 8f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val cellTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 8f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val cellMonoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 7.8f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 7.5f
        }

        val readings = payload.readings.map { it.data }
        val totalKwh = readings.sumOf { it.kwh }
        val totalKvarh = readings.sumOf { it.kvarh }
        val totalKvah = readings.sumOf { it.kvah }
        val avgPf = if (readings.isEmpty()) 0.0 else readings.map { it.powerFactor }.average()
        val generatedAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())

        var pageNum = 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
        var canvas = page.canvas

        // Top Navy Banner
        canvas.drawRoundRect(RectF(margin, margin, pageWidth - margin, margin + 74f), 10f, 10f, navyPaint)
        canvas.drawRect(margin, margin + 70f, pageWidth - margin, margin + 74f, cyanPaint)
        canvas.drawText("VOLTLEDGER  |  METER READING REPORT", margin + 16f, margin + 28f, titlePaint)
        canvas.drawText(
            "Scope: ${payload.scopeLabel}   •   Period: ${payload.periodLabel}",
            margin + 16f,
            margin + 46f,
            subtitlePaint
        )
        canvas.drawText(
            "Prepared by: ${payload.generatedBy}   •   Generated: $generatedAt",
            margin + 16f,
            margin + 60f,
            subtitlePaint
        )

        var y = margin + 90f

        // 4 KPI Summary Cards
        val kpiGap = 8f
        val kpiWidth = (contentWidth - kpiGap * 3) / 4f
        val kpis = listOf(
            Triple("TOTAL ACTIVE", "${MeterMath.formatCompactEnergy(totalKwh)} kWh", cyanPaint),
            Triple("TOTAL REACTIVE", "${MeterMath.formatCompactEnergy(totalKvarh)} kVArh", amberPaint),
            Triple("TOTAL APPARENT", "${MeterMath.formatCompactEnergy(totalKvah)} kVAh", navyPaint),
            Triple("AVG POWER FACTOR", MeterMath.formatPf(avgPf), emeraldPaint)
        )
        kpis.forEachIndexed { idx, (label, value, accent) ->
            val left = margin + idx * (kpiWidth + kpiGap)
            val rect = RectF(left, y, left + kpiWidth, y + 52f)
            canvas.drawRoundRect(rect, 8f, 8f, surfacePaint)
            canvas.drawRect(left, y + 10f, left + 3.5f, y + 42f, accent)
            canvas.drawText(label, left + 10f, y + 18f, labelPaint)
            canvas.drawText(value, left + 10f, y + 39f, metricPaint)
        }
        y += 68f

        // Embedded Daily Consumption Bar Chart (last 10 days with data)
        val dailyGroups = readings
            .groupBy { it.readingDate }
            .entries
            .sortedBy { it.key }
            .takeLast(10)

        if (dailyGroups.isNotEmpty()) {
            canvas.drawText("Daily Consumption Trend (kWh vs. kVAh)", margin, y + 12f, sectionPaint)
            y += 20f
            val chartHeight = 96f
            val chartRect = RectF(margin, y, pageWidth - margin, y + chartHeight)
            canvas.drawRoundRect(chartRect, 8f, 8f, surfacePaint)

            val maxVal = dailyGroups.maxOf { g ->
                maxOf(g.value.sumOf { it.kwh }, g.value.sumOf { it.kvah })
            }.coerceAtLeast(1.0)

            val plotLeft = margin + 16f
            val plotRight = pageWidth - margin - 16f
            val plotTop = y + 14f
            val plotBottom = y + chartHeight - 20f
            val slotW = (plotRight - plotLeft) / dailyGroups.size.coerceAtLeast(1)

            canvas.drawLine(plotLeft, plotBottom, plotRight, plotBottom, dividerPaint)

            dailyGroups.forEachIndexed { index, (date, list) ->
                val sumKwh = list.sumOf { it.kwh }
                val sumKvah = list.sumOf { it.kvah }
                val barW = (slotW * 0.30f).coerceIn(6f, 16f)
                val centerX = plotLeft + slotW * (index + 0.5f)
                val kwhH = ((sumKwh / maxVal) * (plotBottom - plotTop)).toFloat()
                val kvahH = ((sumKvah / maxVal) * (plotBottom - plotTop)).toFloat()

                canvas.drawRoundRect(
                    RectF(centerX - barW - 1.5f, plotBottom - kwhH, centerX - 1.5f, plotBottom),
                    2f, 2f, cyanPaint
                )
                canvas.drawRoundRect(
                    RectF(centerX + 1.5f, plotBottom - kvahH, centerX + barW + 1.5f, plotBottom),
                    2f, 2f, amberPaint
                )
                val shortLbl = date.takeLast(5)
                canvas.drawText(shortLbl, centerX - 13f, plotBottom + 12f, footerPaint)
            }
            y += chartHeight + 16f
        }

        // Readings Ledger Table
        canvas.drawText("Meter Readings Ledger (${readings.size} entries)", margin, y + 12f, sectionPaint)
        y += 20f

        val colOffsets = floatArrayOf(0f, 64f, 100f, 206f, 276f, 336f, 396f, 456f)
        val headers = arrayOf("Date", "Time", "Customer", "Meter #", "kWh", "kVArh", "kVAh", "PF")

        fun drawTableHeader(c: android.graphics.Canvas, topY: Float): Float {
            c.drawRoundRect(RectF(margin, topY, pageWidth - margin, topY + 20f), 4f, 4f, navyPaint)
            headers.forEachIndexed { i, h ->
                c.drawText(h, margin + colOffsets[i] + 4f, topY + 13f, tableHeaderPaint)
            }
            return topY + 22f
        }

        fun drawFooter(c: android.graphics.Canvas, pNum: Int) {
            c.drawLine(margin, pageHeight - 32f, pageWidth - margin, pageHeight - 32f, dividerPaint)
            c.drawText("VoltLedger Utility Telemetry  •  Confidential Management Report", margin, pageHeight - 18f, footerPaint)
            c.drawText("Page $pNum", pageWidth - margin - 34f, pageHeight - 18f, footerPaint)
        }

        y = drawTableHeader(canvas, y)

        if (readings.isEmpty()) {
            canvas.drawText("No readings matched the selected filters.", margin + 6f, y + 16f, labelPaint)
        } else {
            val rowHeight = 18f
            readings.forEachIndexed { rowIdx, r ->
                if (y + rowHeight > pageHeight - 46f) {
                    drawFooter(canvas, pageNum)
                    pdf.finishPage(page)
                    pageNum++
                    page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
                    canvas = page.canvas
                    y = margin
                    canvas.drawText("VoltLedger Meter Readings (continued)", margin, y + 10f, sectionPaint)
                    y += 18f
                    y = drawTableHeader(canvas, y)
                }

                if (rowIdx % 2 == 1) {
                    canvas.drawRect(margin, y, pageWidth - margin, y + rowHeight, stripePaint)
                }
                val baseline = y + 12.5f
                canvas.drawText(r.readingDate, margin + colOffsets[0] + 4f, baseline, cellMonoPaint)
                canvas.drawText(r.readingTime, margin + colOffsets[1] + 4f, baseline, cellMonoPaint)
                canvas.drawText(r.customerName.take(18), margin + colOffsets[2] + 4f, baseline, cellTextPaint)
                canvas.drawText(r.meterNumber.take(12), margin + colOffsets[3] + 4f, baseline, cellMonoPaint)
                canvas.drawText(MeterMath.formatEnergy(r.kwh), margin + colOffsets[4] + 4f, baseline, cellMonoPaint)
                canvas.drawText(MeterMath.formatEnergy(r.kvarh), margin + colOffsets[5] + 4f, baseline, cellMonoPaint)
                canvas.drawText(MeterMath.formatEnergy(r.kvah), margin + colOffsets[6] + 4f, baseline, cellMonoPaint)
                canvas.drawText(MeterMath.formatPf(r.powerFactor), margin + colOffsets[7] + 4f, baseline, cellMonoPaint)
                canvas.drawLine(margin, y + rowHeight, pageWidth - margin, y + rowHeight, dividerPaint)
                y += rowHeight
            }
        }

        drawFooter(canvas, pageNum)
        pdf.finishPage(page)
        return pdf
    }

    // -------------------------------------------------------------------------
    // Standards-Compliant Office Open XML (.xlsx) Workbook Writer
    // Opens natively in Microsoft Excel, Google Sheets, and LibreOffice Calc
    // -------------------------------------------------------------------------
    private fun writeXlsxWorkbook(payload: ReportPayload, outputStream: OutputStream) {
        ZipOutputStream(outputStream).use { zip ->
            fun putEntry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            putEntry(
                "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>"""
            )

            putEntry(
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""
            )

            putEntry(
                "xl/workbook.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="Summary" sheetId="1" r:id="rId1"/>
    <sheet name="Meter Readings" sheetId="2" r:id="rId2"/>
  </sheets>
</workbook>"""
            )

            putEntry(
                "xl/_rels/workbook.xml.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""
            )

            putEntry(
                "xl/styles.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="2">
    <font><sz val="11"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
  </fonts>
  <fills count="3">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF0B1E33"/><bgColor indexed="64"/></patternFill></fill>
  </fills>
  <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
  <cellXfs count="2">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
  </cellXfs>
</styleSheet>"""
            )

            putEntry("xl/worksheets/sheet1.xml", buildSummarySheetXml(payload))
            putEntry("xl/worksheets/sheet2.xml", buildReadingsSheetXml(payload))
        }
    }

    private fun buildSummarySheetXml(payload: ReportPayload): String {
        val readings = payload.readings.map { it.data }
        val totalKwh = readings.sumOf { it.kwh }
        val totalKvarh = readings.sumOf { it.kvarh }
        val totalKvah = readings.sumOf { it.kvah }
        val avgPf = if (readings.isEmpty()) 0.0 else readings.map { it.powerFactor }.average()
        val generatedAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())

        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sb.append("""<cols><col min="1" max="1" width="26" customWidth="1"/><col min="2" max="2" width="20" customWidth="1"/><col min="3" max="7" width="16" customWidth="1"/></cols>""")
        sb.append("<sheetData>")

        var row = 1
        sb.append(xmlRow(row++, listOf(CellSpec.str("VoltLedger Consumption Report", style = 1), CellSpec.str("", style = 1))))
        sb.append(xmlRow(row++, listOf(CellSpec.str("Scope"), CellSpec.str(payload.scopeLabel))))
        sb.append(xmlRow(row++, listOf(CellSpec.str("Period"), CellSpec.str(payload.periodLabel))))
        sb.append(xmlRow(row++, listOf(CellSpec.str("Prepared By"), CellSpec.str(payload.generatedBy))))
        sb.append(xmlRow(row++, listOf(CellSpec.str("Generated At"), CellSpec.str(generatedAt))))
        row++
        sb.append(xmlRow(row++, listOf(CellSpec.str("KPI Metric", style = 1), CellSpec.str("Value", style = 1))))
        sb.append(xmlRow(row++, listOf(CellSpec.str("Total Readings"), CellSpec.num(readings.size.toDouble()))))
        sb.append(xmlRow(row++, listOf(CellSpec.str("Total Active Energy (kWh)"), CellSpec.num(totalKwh))))
        sb.append(xmlRow(row++, listOf(CellSpec.str("Total Reactive Energy (kVArh)"), CellSpec.num(totalKvarh))))
        sb.append(xmlRow(row++, listOf(CellSpec.str("Total Apparent Energy (kVAh)"), CellSpec.num(totalKvah))))
        sb.append(xmlRow(row++, listOf(CellSpec.str("Average Power Factor"), CellSpec.num(avgPf))))
        row++

        sb.append(
            xmlRow(
                row++,
                listOf(
                    CellSpec.str("Customer", style = 1),
                    CellSpec.str("Meter #", style = 1),
                    CellSpec.str("Tariff", style = 1),
                    CellSpec.str("Readings", style = 1),
                    CellSpec.str("Total kWh", style = 1),
                    CellSpec.str("Total kVArh", style = 1),
                    CellSpec.str("Total kVAh", style = 1),
                    CellSpec.str("Avg PF", style = 1)
                )
            )
        )
        payload.customerMetrics.forEach { m ->
            sb.append(
                xmlRow(
                    row++,
                    listOf(
                        CellSpec.str(m.customer.name),
                        CellSpec.str(m.customer.meterNumber),
                        CellSpec.str(m.customer.parsedTariff.label),
                        CellSpec.num(m.readingCount.toDouble()),
                        CellSpec.num(m.totalKwh),
                        CellSpec.num(m.totalKvarh),
                        CellSpec.num(m.totalKvah),
                        CellSpec.num(m.avgPowerFactor)
                    )
                )
            )
        }

        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private fun buildReadingsSheetXml(payload: ReportPayload): String {
        val customersById = payload.customers.associateBy { it.id }
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sb.append("""<cols><col min="1" max="2" width="13" customWidth="1"/><col min="3" max="3" width="24" customWidth="1"/><col min="4" max="5" width="16" customWidth="1"/><col min="6" max="9" width="14" customWidth="1"/><col min="10" max="10" width="28" customWidth="1"/><col min="11" max="11" width="14" customWidth="1"/></cols>""")
        sb.append("<sheetData>")

        val headers = listOf(
            "Date", "Time", "Customer", "Meter Number", "Account Code",
            "kWh", "kVArh", "kVAh", "Power Factor", "Notes", "Sync Status"
        ).map { CellSpec.str(it, style = 1) }
        sb.append(xmlRow(1, headers))

        payload.readings.forEachIndexed { idx, rec ->
            val r = rec.data
            val accountCode = customersById[r.customerId]?.accountCode.orEmpty()
            sb.append(
                xmlRow(
                    idx + 2,
                    listOf(
                        CellSpec.str(r.readingDate),
                        CellSpec.str(r.readingTime),
                        CellSpec.str(r.customerName),
                        CellSpec.str(r.meterNumber),
                        CellSpec.str(accountCode),
                        CellSpec.num(r.kwh),
                        CellSpec.num(r.kvarh),
                        CellSpec.num(r.kvah),
                        CellSpec.num(r.powerFactor),
                        CellSpec.str(r.notes.orEmpty()),
                        CellSpec.str(if (rec.pendingSync) "Pending Sync" else "Synced")
                    )
                )
            )
        }

        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private sealed class CellSpec(val style: Int) {
        class Str(val value: String, style: Int) : CellSpec(style)
        class Num(val value: Double, style: Int) : CellSpec(style)

        companion object {
            fun str(v: String, style: Int = 0) = Str(v, style)
            fun num(v: Double, style: Int = 0) = Num(v, style)
        }
    }

    private fun xmlRow(rowNum: Int, cells: List<CellSpec>): String {
        val sb = StringBuilder("<row r=\"$rowNum\">")
        cells.forEachIndexed { colIdx, cell ->
            val ref = "${colLetters(colIdx)}$rowNum"
            val styleAttr = if (cell.style > 0) " s=\"${cell.style}\"" else ""
            when (cell) {
                is CellSpec.Str -> {
                    sb.append("<c r=\"$ref\"$styleAttr t=\"inlineStr\"><is><t>${escapeXml(cell.value)}</t></is></c>")
                }
                is CellSpec.Num -> {
                    val safeNum = if (cell.value.isFinite()) cell.value else 0.0
                    sb.append("<c r=\"$ref\"$styleAttr><v>$safeNum</v></c>")
                }
            }
        }
        sb.append("</row>")
        return sb.toString()
    }

    private fun colLetters(zeroBasedIndex: Int): String {
        var idx = zeroBasedIndex + 1
        val sb = StringBuilder()
        while (idx > 0) {
            val rem = (idx - 1) % 26
            sb.insert(0, ('A'.code + rem).toChar())
            idx = (idx - 1) / 26
        }
        return sb.toString()
    }

    private fun escapeXml(raw: String): String = buildString(raw.length) {
        raw.forEach { ch ->
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> if (ch.code >= 0x20 || ch == '\t' || ch == '\n') append(ch)
            }
        }
    }
}
