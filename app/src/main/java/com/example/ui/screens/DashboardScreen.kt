package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.ElectricMeter
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.Customer
import com.example.data.DashboardAnalytics
import com.example.data.DateRangePreset
import com.example.data.MeterMath
import com.example.data.SyncSnapshot
import com.example.ui.charts.CustomerBreakdownChart
import com.example.ui.charts.EnergyTrendLineChart
import com.example.ui.charts.PowerFactorGauge
import com.example.ui.charts.PowerFactorHealthChart
import com.example.ui.theme.AlertCoral
import com.example.ui.theme.BrightAmber
import com.example.ui.theme.BrightEmerald
import com.example.ui.theme.DeepCobalt
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.LuminousCyan
import com.example.ui.theme.MidnightSlate
import com.example.ui.theme.MonoMetricLarge
import com.example.ui.theme.MonoMetricMedium
import com.example.ui.theme.MonoMetricSmall
import com.example.viewmodel.SyncBannerState
import com.example.viewmodel.UiState

@Composable
fun DashboardScreen(
    analytics: DashboardAnalytics,
    customersState: UiState<SyncSnapshot<Customer>>,
    selectedCustomerId: String?,
    selectedDateRange: DateRangePreset,
    syncState: SyncBannerState,
    onSelectCustomer: (String?) -> Unit,
    onSelectDateRange: (DateRangePreset) -> Unit,
    onLogReadingClick: () -> Unit,
    onAddCustomerClick: () -> Unit,
    onOpenReportsClick: () -> Unit,
    onSyncNowClick: () -> Unit,
    onInspectCustomer: (String) -> Unit
) {
    val customers = (customersState as? UiState.Success)?.data?.items?.map { it.data } ?: emptyList()

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
            if (customersState is UiState.Error) {
                item {
                    ErrorNoticeCard(message = customersState.message, onRetry = onSyncNowClick)
                }
            }

            // Hero Telemetry Summary Card
            item {
                DashboardHeroCard(
                    analytics = analytics,
                    syncState = syncState,
                    onSyncNowClick = onSyncNowClick
                )
            }

            // Quick Action Strip
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickActionTile(
                        icon = Icons.Rounded.Add,
                        label = stringResource(R.string.action_log_reading),
                        accent = ElectricCyan,
                        onClick = onLogReadingClick,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dashboard_log_reading_btn")
                    )
                    QuickActionTile(
                        icon = Icons.Rounded.PersonAdd,
                        label = stringResource(R.string.action_add_customer),
                        accent = BrightAmber,
                        onClick = onAddCustomerClick,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dashboard_add_customer_btn")
                    )
                    QuickActionTile(
                        icon = Icons.Rounded.Assessment,
                        label = "Export",
                        accent = BrightEmerald,
                        onClick = onOpenReportsClick,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dashboard_export_btn")
                    )
                }
            }

            // Filters: Date Range + Customer Scope
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Consumption Window",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            DateRangePreset.entries.forEach { preset ->
                                val selected = preset == selectedDateRange
                                Surface(
                                    onClick = { onSelectDateRange(preset) },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                    border = if (selected) null else androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier.testTag("range_${preset.name.lowercase()}")
                                ) {
                                    Text(
                                        text = preset.label,
                                        style = MonoMetricSmall,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (customers.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    selected = selectedCustomerId == null,
                                    onClick = { onSelectCustomer(null) },
                                    label = { Text("All Customers (${customers.size})") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    modifier = Modifier.testTag("filter_customer_all")
                                )
                            }
                            items(customers, key = { it.id }) { customer ->
                                FilterChip(
                                    selected = selectedCustomerId == customer.id,
                                    onClick = {
                                        onSelectCustomer(
                                            if (selectedCustomerId == customer.id) null else customer.id
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = customer.name,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Low Power Factor Alert Strip (when applicable)
            if (analytics.lowPfAlertCount > 0) {
                item {
                    Surface(
                        color = AlertCoral.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, AlertCoral.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WarningAmber,
                                contentDescription = "Low Power Factor Alert",
                                tint = AlertCoral,
                                modifier = Modifier.size(22.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${analytics.lowPfAlertCount} Low Power Factor Reading(s) (< 0.85)",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = AlertCoral,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Inspect capacitor banks or reactive kVArh load to avoid utility penalties.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Visual Trend Charts
            if (analytics.dailyTrend.isEmpty()) {
                item {
                    DashboardEmptyChartState(
                        hasCustomers = customers.isNotEmpty(),
                        onAddCustomerClick = onAddCustomerClick,
                        onLogReadingClick = onLogReadingClick
                    )
                }
            } else {
                // Chart 1: Active vs Apparent Energy Trend
                item {
                    SectionCard(
                        title = "Active vs. Apparent Energy Trend",
                        subtitle = "Tap any day to inspect kWh, kVArh, kVAh & Power Factor",
                        icon = Icons.Rounded.Insights
                    ) {
                        EnergyTrendLineChart(points = analytics.dailyTrend)
                    }
                }

                // Chart 2: Power Factor Efficiency Bars
                item {
                    SectionCard(
                        title = "Daily Power Factor Efficiency",
                        subtitle = "Target ≥ 0.95  •  Range ${MeterMath.formatPf(analytics.minPowerFactor)} – ${MeterMath.formatPf(analytics.maxPowerFactor)}",
                        icon = Icons.Rounded.Bolt
                    ) {
                        PowerFactorHealthChart(points = analytics.dailyTrend)
                    }
                }

                // Chart 3: Customer Consumption Ranking
                if (selectedCustomerId == null && analytics.customerSummaries.any { it.readingCount > 0 }) {
                    item {
                        SectionCard(
                            title = "Consumption by Customer",
                            subtitle = "Tap a customer to inspect full meter history",
                            icon = Icons.Rounded.ElectricMeter
                        ) {
                            CustomerBreakdownChart(
                                metrics = analytics.customerSummaries,
                                onCustomerClick = onInspectCustomer
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardHeroCard(
    analytics: DashboardAnalytics,
    syncState: SyncBannerState,
    onSyncNowClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(DeepCobalt, Color(0xFF102A45), MidnightSlate)
                )
            )
            .border(1.dp, LuminousCyan.copy(alpha = 0.22f), RoundedCornerShape(22.dp))
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "TOTAL ACTIVE CONSUMPTION",
                        style = MaterialTheme.typography.labelSmall,
                        color = LuminousCyan
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = MeterMath.formatCompactEnergy(analytics.totalKwh),
                            style = MaterialTheme.typography.displayLarge,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "kWh",
                            style = MonoMetricMedium,
                            color = LuminousCyan,
                            modifier = Modifier.padding(bottom = 5.dp)
                        )
                    }
                    Text(
                        text = "${analytics.totalReadings} reading(s) across ${analytics.activeCustomerCount} meter(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8)
                    )
                }

                PowerFactorGauge(powerFactor = analytics.avgPowerFactor)
            }

            // Secondary Metric Strip (kVArh, kVAh, Sync Status)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                HeroMiniStat(
                    label = "REACTIVE",
                    value = MeterMath.formatCompactEnergy(analytics.totalKvarh),
                    unit = "kVArh",
                    accent = BrightAmber
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(28.dp)
                        .background(Color.White.copy(alpha = 0.12f))
                )
                HeroMiniStat(
                    label = "APPARENT",
                    value = MeterMath.formatCompactEnergy(analytics.totalKvah),
                    unit = "kVAh",
                    accent = Color(0xFF93C5FD)
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(28.dp)
                        .background(Color.White.copy(alpha = 0.12f))
                )
                SyncPillCompact(
                    syncState = syncState,
                    onClick = onSyncNowClick
                )
            }
        }
    }
}

@Composable
private fun HeroMiniStat(label: String, value: String, unit: String, accent: Color) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF94A3B8)
        )
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = value,
                style = MonoMetricMedium,
                color = Color.White
            )
            Text(
                text = unit,
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                modifier = Modifier.padding(bottom = 1.dp)
            )
        }
    }
}

@Composable
private fun SyncPillCompact(
    syncState: SyncBannerState,
    onClick: () -> Unit
) {
    val (bg, tint, icon, text) = when {
        !syncState.online -> Quad(
            BrightAmber.copy(alpha = 0.18f),
            BrightAmber,
            Icons.Rounded.CloudOff,
            if (syncState.pendingUploads > 0) "Offline (${syncState.pendingUploads})" else "Offline"
        )
        syncState.pendingUploads > 0 -> Quad(
            LuminousCyan.copy(alpha = 0.18f),
            LuminousCyan,
            Icons.Rounded.CloudUpload,
            "${syncState.pendingUploads} Queued"
        )
        else -> Quad(
            BrightEmerald.copy(alpha = 0.18f),
            BrightEmerald,
            Icons.Rounded.CloudDone,
            "Synced"
        )
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp)
            .testTag("hero_sync_pill"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (syncState.syncingNow) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                color = tint,
                strokeWidth = 2.dp
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = "Cloud Sync Status",
                tint = tint,
                modifier = Modifier.size(15.dp)
            )
        }
        Text(
            text = text,
            style = MonoMetricSmall,
            color = tint,
            fontWeight = FontWeight.Bold
        )
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

@Composable
private fun QuickActionTile(
    icon: ImageVector,
    label: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 1.dp,
        modifier = modifier
            .height(74.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun SectionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun DashboardEmptyChartState(
    hasCustomers: Boolean,
    onAddCustomerClick: () -> Unit,
    onLogReadingClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(ElectricCyan.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Insights,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = stringResource(R.string.empty_charts_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (hasCustomers) {
                    stringResource(R.string.empty_charts_body)
                } else {
                    "Start by adding your first utility customer, then record daily kWh, kVArh, kVAh, and power factor readings."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!hasCustomers) {
                    Button(
                        onClick = onAddCustomerClick,
                        modifier = Modifier.testTag("empty_add_customer_btn")
                    ) {
                        Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.action_add_customer))
                    }
                } else {
                    Button(
                        onClick = onLogReadingClick,
                        modifier = Modifier.testTag("empty_log_reading_btn")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.action_log_reading))
                    }
                }
            }
        }
    }
}

@Composable
fun ErrorNoticeCard(message: String, onRetry: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = "Error",
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(onClick = onRetry) {
                Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.action_retry))
            }
        }
    }
}
