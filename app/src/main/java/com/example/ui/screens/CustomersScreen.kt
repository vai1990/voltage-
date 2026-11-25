package com.example.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ElectricMeter
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.Customer
import com.example.data.CustomerSummaryMetric
import com.example.data.MeterMath
import com.example.data.MeterReading
import com.example.data.SyncedRecord
import com.example.data.TariffPlan
import com.example.ui.charts.pfHealthColor
import com.example.ui.theme.AlertCoral
import com.example.ui.theme.BrightAmber
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MonoMetricMedium
import com.example.ui.theme.MonoMetricSmall

@Composable
fun CustomersScreen(
    customerMetrics: List<CustomerSummaryMetric>,
    onAddCustomerClick: () -> Unit,
    onEditCustomer: (Customer) -> Unit,
    onDeleteCustomer: (Customer) -> Unit,
    onLogReadingForCustomer: (Customer) -> Unit,
    onSelectCustomerDetail: (Customer) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTariff by remember { mutableStateOf<TariffPlan?>(null) }

    val filtered = remember(customerMetrics, searchQuery, selectedTariff) {
        val q = searchQuery.trim().lowercase()
        customerMetrics.filter { item ->
            val c = item.customer
            val matchesQuery = q.isEmpty() ||
                c.name.lowercase().contains(q) ||
                c.meterNumber.lowercase().contains(q) ||
                (c.accountCode?.lowercase()?.contains(q) == true) ||
                (c.location?.lowercase()?.contains(q) == true)
            val matchesTariff = selectedTariff == null || c.parsedTariff == selectedTariff
            matchesQuery && matchesTariff
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 860.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Search & Tariff Filter
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search customer, meter #, or site...") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("customers_search_input")
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = selectedTariff == null,
                                onClick = { selectedTariff = null },
                                label = { Text("All (${customerMetrics.size})") }
                            )
                        }
                        items(TariffPlan.entries) { tariff ->
                            FilterChip(
                                selected = selectedTariff == tariff,
                                onClick = {
                                    selectedTariff = if (selectedTariff == tariff) null else tariff
                                },
                                label = { Text(tariff.label) }
                            )
                        }
                    }
                }
            }

            if (filtered.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ElectricMeter,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = if (customerMetrics.isEmpty()) {
                                    stringResource(R.string.empty_customers_title)
                                } else {
                                    "No Matching Customers"
                                },
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = if (customerMetrics.isEmpty()) {
                                    stringResource(R.string.empty_customers_body)
                                } else {
                                    "Try clearing your search filter or register a new customer meter."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Button(
                                onClick = onAddCustomerClick,
                                modifier = Modifier.testTag("customers_empty_add_btn")
                            ) {
                                Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.action_add_customer))
                            }
                        }
                    }
                }
            } else {
                items(filtered, key = { it.customer.id }) { metric ->
                    CustomerCard(
                        metric = metric,
                        onOpenDetail = { onSelectCustomerDetail(metric.customer) },
                        onLogReading = { onLogReadingForCustomer(metric.customer) },
                        onEdit = { onEditCustomer(metric.customer) },
                        onDelete = { onDeleteCustomer(metric.customer) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomerCard(
    metric: CustomerSummaryMetric,
    onOpenDetail: () -> Unit,
    onLogReading: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val c = metric.customer
    val latest = metric.latestReading

    Card(
        onClick = onOpenDetail,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("customer_card_${c.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = c.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = c.parsedTariff.shortBadge,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "MTR: ${c.meterNumber}",
                            style = MonoMetricSmall,
                            color = ElectricCyan
                        )
                        if (!c.accountCode.isNullOrBlank()) {
                            Text(
                                text = "• ${c.accountCode}",
                                style = MonoMetricSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (!c.location.isNullOrBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = c.location,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (metric.pendingCount > 0) {
                    Surface(
                        color = BrightAmber.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CloudUpload,
                                contentDescription = "Pending Sync",
                                tint = BrightAmber,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Queued",
                                style = MaterialTheme.typography.labelSmall,
                                color = BrightAmber
                            )
                        }
                    }
                }
            }

            // Telemetry Snapshot Row
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    CustomerMiniMetric(
                        label = "LATEST kWh",
                        value = latest?.let { MeterMath.formatEnergy(it.kwh) } ?: "—"
                    )
                    CustomerMiniMetric(
                        label = "AVG PF",
                        value = if (metric.readingCount > 0) MeterMath.formatPf(metric.avgPowerFactor) else "—",
                        valueColor = if (metric.readingCount > 0) pfHealthColor(metric.avgPowerFactor) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    CustomerMiniMetric(
                        label = "ENTRIES",
                        value = "${metric.readingCount}"
                    )
                }
            }

            // Card Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onEdit, modifier = Modifier.testTag("edit_customer_${c.id}")) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Edit Customer",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.testTag("delete_customer_${c.id}")) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "Delete Customer",
                            tint = AlertCoral
                        )
                    }
                }
                Button(
                    onClick = onLogReading,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("log_for_customer_${c.id}")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_log_reading))
                }
            }
        }
    }
}

@Composable
private fun CustomerMiniMetric(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MonoMetricMedium,
            color = valueColor
        )
    }
}

@Composable
fun CustomerFormDialog(
    editingCustomer: Customer?,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        meterNumber: String,
        tariffPlan: TariffPlan,
        sanctionedLoadKw: Double,
        accountCode: String?,
        location: String?,
        contactPhone: String?,
        notes: String?
    ) -> Unit
) {
    var name by remember(editingCustomer) { mutableStateOf(editingCustomer?.name.orEmpty()) }
    var meterNumber by remember(editingCustomer) { mutableStateOf(editingCustomer?.meterNumber.orEmpty()) }
    var tariffPlan by remember(editingCustomer) {
        mutableStateOf(editingCustomer?.parsedTariff ?: TariffPlan.INDUSTRIAL_HT)
    }
    var sanctionedLoad by remember(editingCustomer) {
        mutableStateOf(
            if (editingCustomer != null && editingCustomer.sanctionedLoadKw > 0.0) {
                editingCustomer.sanctionedLoadKw.toString()
            } else ""
        )
    }
    var accountCode by remember(editingCustomer) { mutableStateOf(editingCustomer?.accountCode.orEmpty()) }
    var location by remember(editingCustomer) { mutableStateOf(editingCustomer?.location.orEmpty()) }
    var contactPhone by remember(editingCustomer) { mutableStateOf(editingCustomer?.contactPhone.orEmpty()) }
    var notes by remember(editingCustomer) { mutableStateOf(editingCustomer?.notes.orEmpty()) }
    var validationError by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 540.dp)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (editingCustomer == null) "Register Customer Meter" else "Edit Customer Meter",
                        style = MaterialTheme.typography.titleLarge
                    )
                    IconButton(onClick = onDismiss, enabled = !isSaving) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                }

                if (validationError != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = validationError ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 120) name = it },
                    label = { Text(stringResource(R.string.label_customer_name)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("customer_name_input")
                )

                OutlinedTextField(
                    value = meterNumber,
                    onValueChange = { if (it.length <= 60) meterNumber = it },
                    label = { Text(stringResource(R.string.label_meter_number)) },
                    placeholder = { Text("e.g., MTR-HT-4029") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("customer_meter_input")
                )

                Text(
                    text = stringResource(R.string.label_tariff_plan),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(TariffPlan.entries) { plan ->
                        FilterChip(
                            selected = tariffPlan == plan,
                            onClick = { tariffPlan = plan },
                            label = { Text(plan.label) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = sanctionedLoad,
                        onValueChange = { sanctionedLoad = it },
                        label = { Text("Load (kW)") },
                        placeholder = { Text("250") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = accountCode,
                        onValueChange = { if (it.length <= 40) accountCode = it },
                        label = { Text("Account #") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = location,
                    onValueChange = { if (it.length <= 160) location = it },
                    label = { Text(stringResource(R.string.label_location)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = contactPhone,
                    onValueChange = { if (it.length <= 30) contactPhone = it },
                    label = { Text(stringResource(R.string.label_contact_phone)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { if (it.length <= 300) notes = it },
                    label = { Text(stringResource(R.string.label_notes)) },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss, enabled = !isSaving) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val trimmedName = name.trim()
                            val trimmedMeter = meterNumber.trim()
                            val loadVal = sanctionedLoad.trim().let {
                                if (it.isEmpty()) 0.0 else it.toDoubleOrNull()
                            }
                            when {
                                trimmedName.isEmpty() -> validationError = "Customer name is required."
                                trimmedMeter.isEmpty() -> validationError = "Meter serial number is required."
                                loadVal == null || loadVal < 0.0 || loadVal > 1_000_000.0 ->
                                    validationError = "Enter a valid non-negative sanctioned load."
                                else -> {
                                    validationError = null
                                    onSave(
                                        trimmedName,
                                        trimmedMeter,
                                        tariffPlan,
                                        loadVal,
                                        accountCode.trim().ifBlank { null },
                                        location.trim().ifBlank { null },
                                        contactPhone.trim().ifBlank { null },
                                        notes.trim().ifBlank { null }
                                    )
                                }
                            }
                        },
                        enabled = !isSaving,
                        modifier = Modifier.testTag("save_customer_button")
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(stringResource(R.string.action_save))
                    }
                }
            }
        }
    }
}

@Composable
fun CustomerDetailDialog(
    customer: Customer,
    readings: List<SyncedRecord<MeterReading>>,
    onDismiss: () -> Unit,
    onLogReading: () -> Unit,
    onEditCustomer: () -> Unit,
    onEditReading: (MeterReading) -> Unit,
    onDeleteReading: (MeterReading) -> Unit
) {
    val avgPf = remember(readings) {
        if (readings.isEmpty()) 0.0 else readings.map { it.data.powerFactor }.average()
    }
    val totalKwh = remember(readings) { readings.sumOf { it.data.kwh } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 580.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = customer.name, style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "${customer.meterNumber}  •  ${customer.parsedTariff.label}",
                            style = MonoMetricSmall,
                            color = ElectricCyan
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close detail")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("TOTAL kWh", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(MeterMath.formatCompactEnergy(totalKwh), style = MonoMetricMedium, color = ElectricCyan)
                        }
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("AVG PF", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                if (readings.isEmpty()) "—" else MeterMath.formatPf(avgPf),
                                style = MonoMetricMedium,
                                color = if (readings.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else pfHealthColor(avgPf)
                            )
                        }
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("ENTRIES", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${readings.size}", style = MonoMetricMedium)
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onLogReading,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_log_reading))
                    }
                    OutlinedButton(onClick = onEditCustomer) {
                        Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_edit))
                    }
                }

                Text(
                    text = "Reading History",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (readings.isEmpty()) {
                    Text(
                        text = "No readings logged for this customer yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(readings, key = { it.data.id }) { rec ->
                            ReadingLedgerCard(
                                record = rec,
                                onEdit = { onEditReading(rec.data) },
                                onDelete = { onDeleteReading(rec.data) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConfirmDeleteDialog(
    title: String,
    body: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(body, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = AlertCoral)
            ) {
                Text(stringResource(R.string.action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
