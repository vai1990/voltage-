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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ElectricBolt
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.data.MeterMath
import com.example.data.MeterReading
import com.example.data.SyncedRecord
import com.example.ui.charts.pfHealthColor
import com.example.ui.charts.pfHealthLabel
import com.example.ui.theme.AlertCoral
import com.example.ui.theme.BrightAmber
import com.example.ui.theme.BrightEmerald
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MonoMetricMedium
import com.example.ui.theme.MonoMetricSmall
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

@Composable
fun ReadingsScreen(
    readings: List<SyncedRecord<MeterReading>>,
    customers: List<Customer>,
    onLogReadingClick: () -> Unit,
    onEditReading: (MeterReading) -> Unit,
    onDeleteReading: (MeterReading) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var filterCustomerId by remember { mutableStateOf<String?>(null) }

    val filtered = remember(readings, searchQuery, filterCustomerId) {
        val q = searchQuery.trim().lowercase()
        readings.filter { rec ->
            val r = rec.data
            val matchesCustomer = filterCustomerId == null || r.customerId == filterCustomerId
            val matchesQuery = q.isEmpty() ||
                r.customerName.lowercase().contains(q) ||
                r.meterNumber.lowercase().contains(q) ||
                r.readingDate.contains(q) ||
                (r.notes?.lowercase()?.contains(q) == true)
            matchesCustomer && matchesQuery
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
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search by customer, meter #, or date...") },
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
                            .testTag("readings_search_input")
                    )

                    if (customers.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    selected = filterCustomerId == null,
                                    onClick = { filterCustomerId = null },
                                    label = { Text("All (${readings.size})") }
                                )
                            }
                            items(customers, key = { it.id }) { c ->
                                FilterChip(
                                    selected = filterCustomerId == c.id,
                                    onClick = {
                                        filterCustomerId = if (filterCustomerId == c.id) null else c.id
                                    },
                                    label = {
                                        Text(
                                            text = c.name,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                )
                            }
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
                                imageVector = Icons.Rounded.ElectricBolt,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = stringResource(R.string.empty_readings_title),
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = stringResource(R.string.empty_readings_body),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Button(
                                onClick = onLogReadingClick,
                                modifier = Modifier.testTag("readings_empty_log_btn")
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.action_log_reading))
                            }
                        }
                    }
                }
            } else {
                items(filtered, key = { it.data.id }) { record ->
                    ReadingLedgerCard(
                        record = record,
                        onEdit = { onEditReading(record.data) },
                        onDelete = { onDeleteReading(record.data) }
                    )
                }
            }
        }
    }
}

@Composable
fun ReadingLedgerCard(
    record: SyncedRecord<MeterReading>,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val r = record.data
    val pfColor = pfHealthColor(r.powerFactor)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("reading_card_${r.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Customer + Timestamp + Sync Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = r.customerName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${r.meterNumber}  •  ${r.readingDate}  ${r.readingTime}",
                        style = MonoMetricSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        color = if (record.pendingSync) BrightAmber.copy(alpha = 0.16f) else BrightEmerald.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (record.pendingSync) Icons.Rounded.CloudUpload else Icons.Rounded.CloudDone,
                                contentDescription = if (record.pendingSync) "Pending Sync" else "Synced",
                                tint = if (record.pendingSync) BrightAmber else BrightEmerald,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = if (record.pendingSync) "Offline" else "Synced",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (record.pendingSync) BrightAmber else BrightEmerald
                            )
                        }
                    }
                    IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Edit Reading",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "Delete Reading",
                            tint = AlertCoral,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // 4-Column Telemetry Values Grid
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TelemetryCell("kWh", MeterMath.formatEnergy(r.kwh), ElectricCyan)
                    TelemetryCell("kVArh", MeterMath.formatEnergy(r.kvarh), MaterialTheme.colorScheme.onSurface)
                    TelemetryCell("kVAh", MeterMath.formatEnergy(r.kvah), BrightAmber)
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "PF (${pfHealthLabel(r.powerFactor)})",
                            style = MaterialTheme.typography.labelSmall,
                            color = pfColor
                        )
                        Text(
                            text = MeterMath.formatPf(r.powerFactor),
                            style = MonoMetricMedium,
                            color = pfColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (!r.notes.isNullOrBlank()) {
                Text(
                    text = r.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TelemetryCell(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingEntryDialog(
    customers: List<Customer>,
    allReadings: List<MeterReading>,
    initialCustomerId: String?,
    editingReading: MeterReading?,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onAddCustomerShortcut: () -> Unit,
    onSave: (
        customer: Customer,
        readingDate: String,
        readingTime: String,
        kwh: Double,
        kvarh: Double,
        kvah: Double,
        powerFactor: Double,
        notes: String?
    ) -> Unit
) {
    val defaultCustomer = remember(customers, initialCustomerId, editingReading) {
        val targetId = editingReading?.customerId ?: initialCustomerId
        customers.firstOrNull { it.id == targetId } ?: customers.firstOrNull()
    }

    var selectedCustomer by remember(defaultCustomer) { mutableStateOf(defaultCustomer) }
    var customerDropdownExpanded by remember { mutableStateOf(false) }

    var readingDate by remember(editingReading) {
        mutableStateOf(editingReading?.readingDate ?: MeterMath.todayIsoDate())
    }
    var readingTime by remember(editingReading) {
        mutableStateOf(editingReading?.readingTime ?: MeterMath.currentTime24h())
    }
    var kwhInput by remember(editingReading) {
        mutableStateOf(editingReading?.kwh?.toString().orEmpty())
    }
    var kvarhInput by remember(editingReading) {
        mutableStateOf(editingReading?.kvarh?.toString().orEmpty())
    }
    var kvahInput by remember(editingReading) {
        mutableStateOf(editingReading?.kvah?.toString().orEmpty())
    }
    var pfInput by remember(editingReading) {
        mutableStateOf(editingReading?.powerFactor?.let { MeterMath.formatPf(it) }.orEmpty())
    }
    var notesInput by remember(editingReading) {
        mutableStateOf(editingReading?.notes.orEmpty())
    }
    var validationError by remember { mutableStateOf<String?>(null) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    // Previous reading reference for selected customer (to show delta)
    val previousReading = remember(selectedCustomer, allReadings, editingReading) {
        val cid = selectedCustomer?.id ?: return@remember null
        allReadings
            .filter { it.customerId == cid && it.id != editingReading?.id }
            .maxByOrNull { it.readingEpochMillis }
    }

    val currentKwhVal = kwhInput.trim().toDoubleOrNull()
    val deltaKwh = if (previousReading != null && currentKwhVal != null) {
        currentKwhVal - previousReading.kwh
    } else null

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = MeterMath.parseEpochMillis(readingDate, "12:00")
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { utcMillis ->
                            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                            cal.timeInMillis = utcMillis
                            readingDate = String.format(
                                Locale.US,
                                "%04d-%02d-%02d",
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH) + 1,
                                cal.get(Calendar.DAY_OF_MONTH)
                            )
                        }
                        showDatePicker = false
                    }
                ) { Text("Select") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val parts = readingTime.split(":")
        val initHour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 8
        val initMin = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        val timePickerState = rememberTimePickerState(
            initialHour = initHour,
            initialMinute = initMin,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Select Reading Time") },
            text = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        readingTime = String.format(
                            Locale.US,
                            "%02d:%02d",
                            timePickerState.hour,
                            timePickerState.minute
                        )
                        showTimePicker = false
                    }
                ) { Text("Select") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            }
        )
    }

    Dialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .widthIn(max = 560.dp)
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
                        text = if (editingReading == null) "Log Daily Meter Reading" else "Edit Meter Reading",
                        style = MaterialTheme.typography.titleLarge
                    )
                    IconButton(onClick = onDismiss, enabled = !isSaving) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                }

                if (customers.isEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Add a Customer First",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Meter readings are recorded per customer. Register at least one customer account to begin.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(onClick = onAddCustomerShortcut) {
                                Text(stringResource(R.string.action_add_customer))
                            }
                        }
                    }
                } else {
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

                    // Customer Selector Dropdown
                    ExposedDropdownMenuBox(
                        expanded = customerDropdownExpanded && editingReading == null,
                        onExpandedChange = {
                            if (editingReading == null) customerDropdownExpanded = it
                        }
                    ) {
                        OutlinedTextField(
                            value = selectedCustomer?.let { "${it.name} (${it.meterNumber})" } ?: "Select Customer",
                            onValueChange = {},
                            readOnly = true,
                            enabled = editingReading == null,
                            label = { Text(stringResource(R.string.label_customer)) },
                            trailingIcon = {
                                if (editingReading == null) {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = customerDropdownExpanded)
                                }
                            },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                                .testTag("reading_customer_selector")
                        )
                        ExposedDropdownMenu(
                            expanded = customerDropdownExpanded && editingReading == null,
                            onDismissRequest = { customerDropdownExpanded = false }
                        ) {
                            customers.forEach { customer ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(customer.name, style = MaterialTheme.typography.bodyMedium)
                                            Text(
                                                customer.meterNumber,
                                                style = MonoMetricSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedCustomer = customer
                                        customerDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Previous reading reference strip
                    if (previousReading != null) {
                        Surface(
                            color = ElectricCyan.copy(alpha = 0.09f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, ElectricCyan.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "PREVIOUS (${previousReading.readingDate})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ElectricCyan
                                    )
                                    Text(
                                        text = "${MeterMath.formatEnergy(previousReading.kwh)} kWh  •  PF ${MeterMath.formatPf(previousReading.powerFactor)}",
                                        style = MonoMetricSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                if (deltaKwh != null) {
                                    Text(
                                        text = "${if (deltaKwh >= 0) "+" else ""}${MeterMath.formatEnergy(deltaKwh)} kWh",
                                        style = MonoMetricSmall,
                                        color = if (deltaKwh >= 0) BrightEmerald else AlertCoral,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Date & Time Picker Row
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = readingDate,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.label_date)) },
                                trailingIcon = { Icon(Icons.Rounded.CalendarMonth, contentDescription = "Pick date") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("reading_date_input")
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable { showDatePicker = true }
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = readingTime,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.label_time)) },
                                trailingIcon = { Icon(Icons.Rounded.Schedule, contentDescription = "Pick time") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("reading_time_input")
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable { showTimePicker = true }
                            )
                        }
                    }

                    // Energy Parameters: kWh & kVArh
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = kwhInput,
                            onValueChange = { kwhInput = it },
                            label = { Text("kWh *") },
                            placeholder = { Text("0.00") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("reading_kwh_input")
                        )
                        OutlinedTextField(
                            value = kvarhInput,
                            onValueChange = { kvarhInput = it },
                            label = { Text("kVArh *") },
                            placeholder = { Text("0.00") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("reading_kvarh_input")
                        )
                    }

                    // Apparent Energy kVAh & Power Factor
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = kvahInput,
                            onValueChange = { kvahInput = it },
                            label = { Text("kVAh *") },
                            placeholder = { Text("0.00") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("reading_kvah_input")
                        )
                        OutlinedTextField(
                            value = pfInput,
                            onValueChange = { pfInput = it },
                            label = { Text("Power Factor *") },
                            placeholder = { Text("0.980") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("reading_pf_input")
                        )
                    }

                    // Smart Electrical Auto-Compute Helper Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val kwh = kwhInput.trim().toDoubleOrNull()
                                val kvarh = kvarhInput.trim().toDoubleOrNull()
                                if (kwh != null && kvarh != null && kwh >= 0 && kvarh >= 0) {
                                    val calcKvah = MeterMath.computeKvah(kwh, kvarh)
                                    kvahInput = String.format(Locale.US, "%.2f", calcKvah)
                                    MeterMath.computePowerFactor(kwh, kvarh, calcKvah)?.let {
                                        pfInput = MeterMath.formatPf(it)
                                    }
                                    validationError = null
                                } else {
                                    validationError = "Enter valid kWh and kVArh first to auto-compute kVAh & PF."
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("auto_calc_kvah_pf_btn")
                        ) {
                            Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Auto kVAh & PF", style = MaterialTheme.typography.labelMedium)
                        }

                        OutlinedButton(
                            onClick = {
                                val kwh = kwhInput.trim().toDoubleOrNull() ?: -1.0
                                val kvarh = kvarhInput.trim().toDoubleOrNull() ?: 0.0
                                val kvah = kvahInput.trim().toDoubleOrNull() ?: 0.0
                                val pf = MeterMath.computePowerFactor(kwh, kvarh, kvah)
                                if (pf != null && kwh >= 0.0) {
                                    pfInput = MeterMath.formatPf(pf)
                                    validationError = null
                                } else {
                                    validationError = "Enter kWh and kVAh (or kVArh) to compute Power Factor."
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("auto_calc_pf_btn")
                        ) {
                            Icon(Icons.Rounded.ElectricBolt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.action_auto_calc_pf), style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    OutlinedTextField(
                        value = notesInput,
                        onValueChange = { if (it.length <= 300) notesInput = it },
                        label = { Text(stringResource(R.string.label_notes)) },
                        placeholder = { Text("Load shift, capacitor bank state, anomalies...") },
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
                                val customer = selectedCustomer
                                val kwh = kwhInput.trim().toDoubleOrNull()
                                val kvarh = kvarhInput.trim().toDoubleOrNull()
                                val kvah = kvahInput.trim().toDoubleOrNull()
                                val pf = pfInput.trim().toDoubleOrNull()

                                when {
                                    customer == null -> validationError = "Please select a customer."
                                    !MeterMath.isValidDate(readingDate) -> validationError = "Select a valid date."
                                    !MeterMath.isValidTime(readingTime) -> validationError = "Select a valid time."
                                    kwh == null || kwh < 0.0 || kwh > 1_000_000_000.0 ->
                                        validationError = "Enter a valid non-negative kWh value."
                                    kvarh == null || kvarh < 0.0 || kvarh > 1_000_000_000.0 ->
                                        validationError = "Enter a valid non-negative kVArh value."
                                    kvah == null || kvah < 0.0 || kvah > 1_000_000_000.0 ->
                                        validationError = "Enter a valid non-negative kVAh value."
                                    pf == null || pf < 0.0 || pf > 1.0 ->
                                        validationError = "Power Factor must be between 0.00 and 1.00."
                                    else -> {
                                        validationError = null
                                        onSave(
                                            customer,
                                            readingDate,
                                            readingTime,
                                            kwh,
                                            kvarh,
                                            kvah,
                                            pf,
                                            notesInput.trim().ifBlank { null }
                                        )
                                    }
                                }
                            },
                            enabled = !isSaving,
                            modifier = Modifier.testTag("save_reading_button")
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
}
