package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.Customer
import com.example.data.CustomerSummaryMetric
import com.example.data.DateRangePreset
import com.example.data.MeterMath
import com.example.data.MeterReading
import com.example.data.SyncedRecord
import com.example.export.ReportExporter
import com.example.export.ReportPayload
import com.example.ui.charts.pfHealthColor
import com.example.ui.theme.AlertCoral
import com.example.ui.theme.BrightAmber
import com.example.ui.theme.BrightEmerald
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GridEmerald
import com.example.ui.theme.MonoMetricMedium
import com.example.ui.theme.MonoMetricSmall
import com.example.viewmodel.SyncBannerState
import kotlinx.coroutines.launch

@Composable
fun ReportsScreen(
    customers: List<Customer>,
    allReadings: List<SyncedRecord<MeterReading>>,
    syncState: SyncBannerState,
    userDisplayName: String,
    onSyncNowClick: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedCustomerId by remember { mutableStateOf<String?>(null) }
    var selectedRange by remember { mutableStateOf(DateRangePreset.LAST_30_DAYS) }
    var isExporting by remember { mutableStateOf(false) }

    val selectedCustomer = remember(customers, selectedCustomerId) {
        customers.firstOrNull { it.id == selectedCustomerId }
    }

    val filteredReadings = remember(allReadings, selectedCustomerId, selectedRange) {
        val cutoff = selectedRange.days?.let { MeterMath.cutoffEpochForDays(it) }
        allReadings.filter { rec ->
            val matchesCustomer = selectedCustomerId == null || rec.data.customerId == selectedCustomerId
            val matchesRange = cutoff == null || rec.data.readingEpochMillis >= cutoff
            matchesCustomer && matchesRange
        }
    }

    val customerMetrics = remember(customers, filteredReadings, selectedCustomerId) {
        val scopedCustomers = if (selectedCustomerId == null) customers else customers.filter { it.id == selectedCustomerId }
        val byCust = filteredReadings.groupBy { it.data.customerId }
        scopedCustomers.map { c ->
            val list = byCust[c.id] ?: emptyList()
            val raw = list.map { it.data }
            CustomerSummaryMetric(
                customer = c,
                readingCount = raw.size,
                totalKwh = raw.sumOf { it.kwh },
                totalKvarh = raw.sumOf { it.kvarh },
                totalKvah = raw.sumOf { it.kvah },
                avgPowerFactor = if (raw.isEmpty()) 0.0 else raw.map { it.powerFactor }.average(),
                latestReading = raw.maxByOrNull { it.readingEpochMillis },
                pendingCount = list.count { it.pendingSync }
            )
        }.sortedByDescending { it.totalKwh }
    }

    val currentPayload = remember(selectedCustomer, selectedRange, userDisplayName, filteredReadings, customers, customerMetrics) {
        val scopeLabel = selectedCustomer?.let { "${it.name} (${it.meterNumber})" } ?: "All Customers"
        val periodLabel = when (selectedRange) {
            DateRangePreset.LAST_7_DAYS -> "Last 7 Days"
            DateRangePreset.LAST_30_DAYS -> "Last 30 Days"
            DateRangePreset.LAST_90_DAYS -> "Last 90 Days"
            DateRangePreset.ALL_TIME -> "All Recorded Dates"
        }
        ReportPayload(
            title = "VoltLedger Meter Reading Report",
            scopeLabel = scopeLabel,
            periodLabel = periodLabel,
            generatedBy = userDisplayName,
            readings = filteredReadings,
            customers = customers,
            customerMetrics = customerMetrics
        )
    }

    // Zero-permission SAF Save-to-Device Launchers
    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(ReportExporter.MIME_PDF)
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isExporting = true
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        ReportExporter.writePdfToStream(currentPayload, out)
                    } ?: error("Unable to open destination PDF file")
                }.onSuccess {
                    onShowMessage("PDF report saved to device")
                }.onFailure { err ->
                    onShowMessage(err.localizedMessage ?: "Failed to save PDF")
                }
                isExporting = false
            }
        }
    }

    val saveExcelLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(ReportExporter.MIME_XLSX)
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isExporting = true
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        ReportExporter.writeExcelToStream(currentPayload, out)
                    } ?: error("Unable to open destination Excel file")
                }.onSuccess {
                    onShowMessage("Excel (.xlsx) workbook saved to device")
                }.onFailure { err ->
                    onShowMessage(err.localizedMessage ?: "Failed to save Excel file")
                }
                isExporting = false
            }
        }
    }

    val rawReadings = filteredReadings.map { it.data }
    val totalKwh = rawReadings.sumOf { it.kwh }
    val totalKvarh = rawReadings.sumOf { it.kvarh }
    val totalKvah = rawReadings.sumOf { it.kvah }
    val avgPf = if (rawReadings.isEmpty()) 0.0 else rawReadings.map { it.powerFactor }.average()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 860.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Report Configuration Card
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Report Scope & Period",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Text(
                            text = "Date Window",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(DateRangePreset.entries) { preset ->
                                FilterChip(
                                    selected = selectedRange == preset,
                                    onClick = { selectedRange = preset },
                                    label = { Text(preset.label) }
                                )
                            }
                        }

                        Text(
                            text = "Customer Filter",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    selected = selectedCustomerId == null,
                                    onClick = { selectedCustomerId = null },
                                    label = { Text("All Customers") }
                                )
                            }
                            items(customers, key = { it.id }) { c ->
                                FilterChip(
                                    selected = selectedCustomerId == c.id,
                                    onClick = {
                                        selectedCustomerId = if (selectedCustomerId == c.id) null else c.id
                                    },
                                    label = {
                                        Text(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Live Report Summary Preview
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Report Preview", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "${currentPayload.scopeLabel} • ${currentPayload.periodLabel}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "${filteredReadings.size} entries",
                                    style = MonoMetricSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                PreviewStat("kWh", MeterMath.formatCompactEnergy(totalKwh), ElectricCyan)
                                PreviewStat("kVArh", MeterMath.formatCompactEnergy(totalKvarh), MaterialTheme.colorScheme.onSurface)
                                PreviewStat("kVAh", MeterMath.formatCompactEnergy(totalKvah), BrightAmber)
                                PreviewStat(
                                    "Avg PF",
                                    if (rawReadings.isEmpty()) "—" else MeterMath.formatPf(avgPf),
                                    if (rawReadings.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else pfHealthColor(avgPf)
                                )
                            }
                        }

                        if (customerMetrics.any { it.readingCount > 0 }) {
                            HorizontalDivider()
                            customerMetrics.filter { it.readingCount > 0 }.take(5).forEach { m ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = m.customer.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${m.customer.meterNumber} • ${m.readingCount} entry(s)",
                                            style = MonoMetricSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "${MeterMath.formatCompactEnergy(m.totalKwh)} kWh",
                                            style = MonoMetricSmall,
                                            color = ElectricCyan,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "PF ${MeterMath.formatPf(m.avgPowerFactor)}",
                                            style = MonoMetricSmall,
                                            color = pfHealthColor(m.avgPowerFactor)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Export Actions Card: PDF & Excel (.xlsx)
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text("Export Management Reports", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "Includes KPI summary, daily trend chart (PDF), and multi-sheet ledger (Excel).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isExporting) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                            }
                        }

                        // PDF Export Row
                        Surface(
                            color = AlertCoral.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, AlertCoral.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PictureAsPdf,
                                        contentDescription = "PDF Report",
                                        tint = AlertCoral,
                                        modifier = Modifier.size(26.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Executive PDF Report (.pdf)",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Multi-page A4 document with embedded bar chart & tabular ledger",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            savePdfLauncher.launch(
                                                ReportExporter.suggestedFileName("pdf", currentPayload.scopeLabel)
                                            )
                                        },
                                        enabled = !isExporting,
                                        colors = ButtonDefaults.buttonColors(containerColor = AlertCoral),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("export_save_pdf_btn")
                                    ) {
                                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Save PDF")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                isExporting = true
                                                runCatching {
                                                    val uri = ReportExporter.createShareablePdf(context, currentPayload)
                                                    ReportExporter.launchShareIntent(
                                                        context,
                                                        uri,
                                                        ReportExporter.MIME_PDF,
                                                        "VoltLedger PDF Report — ${currentPayload.scopeLabel}"
                                                    )
                                                }.onFailure { err ->
                                                    onShowMessage(err.localizedMessage ?: "Unable to share PDF")
                                                }
                                                isExporting = false
                                            }
                                        },
                                        enabled = !isExporting,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("export_share_pdf_btn")
                                    ) {
                                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Share PDF")
                                    }
                                }
                            }
                        }

                        // Excel (.xlsx) Export Row
                        Surface(
                            color = GridEmerald.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, GridEmerald.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.TableChart,
                                        contentDescription = "Excel Workbook",
                                        tint = GridEmerald,
                                        modifier = Modifier.size(26.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Excel Spreadsheet (.xlsx)",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Two worksheets: Summary Rollup + Full Meter Readings with numeric columns",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            saveExcelLauncher.launch(
                                                ReportExporter.suggestedFileName("xlsx", currentPayload.scopeLabel)
                                            )
                                        },
                                        enabled = !isExporting,
                                        colors = ButtonDefaults.buttonColors(containerColor = GridEmerald),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("export_save_excel_btn")
                                    ) {
                                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Save Excel")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                isExporting = true
                                                runCatching {
                                                    val uri = ReportExporter.createShareableExcel(context, currentPayload)
                                                    ReportExporter.launchShareIntent(
                                                        context,
                                                        uri,
                                                        ReportExporter.MIME_XLSX,
                                                        "VoltLedger Excel Report — ${currentPayload.scopeLabel}"
                                                    )
                                                }.onFailure { err ->
                                                    onShowMessage(err.localizedMessage ?: "Unable to share Excel file")
                                                }
                                                isExporting = false
                                            }
                                        },
                                        enabled = !isExporting,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("export_share_excel_btn")
                                    ) {
                                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Share Excel")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Offline Cache & Cloud Backup Card
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = when {
                                    !syncState.online -> Icons.Rounded.CloudOff
                                    syncState.pendingUploads > 0 -> Icons.Rounded.CloudUpload
                                    else -> Icons.Rounded.CloudDone
                                },
                                contentDescription = null,
                                tint = when {
                                    !syncState.online -> BrightAmber
                                    syncState.pendingUploads > 0 -> ElectricCyan
                                    else -> BrightEmerald
                                }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Cloud Synchronization & Offline Cache", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = when {
                                        !syncState.online -> "Device is offline. All customers & readings remain available locally and new entries are queued for automatic upload."
                                        syncState.pendingUploads > 0 -> "${syncState.pendingUploads} local change(s) pending cloud backup."
                                        else -> "All records are backed up to Cloud Firestore with unlimited local offline cache enabled."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = onSyncNowClick,
                            enabled = !syncState.syncingNow,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("reports_sync_now_btn")
                        ) {
                            if (syncState.syncingNow) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Synchronizing...")
                            } else {
                                Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.sync_now))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewStat(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MonoMetricMedium,
            color = color
        )
    }
}
