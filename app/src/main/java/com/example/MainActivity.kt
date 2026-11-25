package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.ElectricMeter
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.ElectricMeter
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.Customer
import com.example.data.MeterReading
import com.example.data.MeterRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOutUser
import com.example.ui.screens.ConfirmDeleteDialog
import com.example.ui.screens.CustomerDetailDialog
import com.example.ui.screens.CustomerFormDialog
import com.example.ui.screens.CustomersScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ReadingEntryDialog
import com.example.ui.screens.ReadingsScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.theme.AlertCoral
import com.example.ui.theme.BrightAmber
import com.example.ui.theme.BrightEmerald
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MonoMetricSmall
import com.example.ui.theme.VoltLedgerTheme
import com.example.viewmodel.UiState
import com.example.viewmodel.VoltLedgerViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth

enum class AppTab(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    DASHBOARD("dashboard", R.string.nav_dashboard, Icons.Rounded.Dashboard, Icons.Outlined.Dashboard),
    CUSTOMERS("customers", R.string.nav_customers, Icons.Rounded.ElectricMeter, Icons.Outlined.ElectricMeter),
    READINGS("readings", R.string.nav_readings, Icons.Rounded.ReceiptLong, Icons.Outlined.ReceiptLong),
    REPORTS("reports", R.string.nav_reports, Icons.Rounded.Assessment, Icons.Outlined.Assessment)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VoltLedgerTheme {
                VoltLedgerRoot()
            }
        }
    }
}

@Composable
fun VoltLedgerRoot() {
    var currentUser by remember { mutableStateOf<FirebaseUser?>(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            onAuthSuccess = {
                currentUser = Firebase.auth.currentUser
            }
        )
    } else {
        key(user.uid) {
            VoltLedgerMainShell(
                userId = user.uid,
                userDisplayName = user.displayName ?: "Utility Manager",
                userEmail = user.email
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoltLedgerMainShell(
    userId: String,
    userDisplayName: String,
    userEmail: String?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: VoltLedgerViewModel = viewModel(
        key = userId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                VoltLedgerViewModel(
                    repository = MeterRepository(app.applicationContext),
                    currentUserId = userId,
                    userDisplayName = userDisplayName,
                    userEmail = userEmail
                )
            }
        }
    )

    val customersState by viewModel.customersState.collectAsStateWithLifecycle()
    val readingsState by viewModel.readingsState.collectAsStateWithLifecycle()
    val analytics by viewModel.dashboardAnalytics.collectAsStateWithLifecycle()
    val syncState by viewModel.syncBannerState.collectAsStateWithLifecycle()
    val selectedCustomerFilterId by viewModel.selectedCustomerFilterId.collectAsStateWithLifecycle()
    val selectedDateRange by viewModel.selectedDateRange.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.snackbarEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    var currentTab by remember { mutableStateOf(AppTab.DASHBOARD) }
    var showAccountDialog by remember { mutableStateOf(false) }

    // Modal workflow states
    var showCustomerForm by remember { mutableStateOf(false) }
    var editingCustomer by remember { mutableStateOf<Customer?>(null) }
    var customerPendingDelete by remember { mutableStateOf<Customer?>(null) }
    var inspectingCustomerId by remember { mutableStateOf<String?>(null) }

    var showReadingForm by remember { mutableStateOf(false) }
    var preselectedReadingCustomerId by remember { mutableStateOf<String?>(null) }
    var editingReading by remember { mutableStateOf<MeterReading?>(null) }
    var readingPendingDelete by remember { mutableStateOf<MeterReading?>(null) }

    // Support system Back navigation cleanly across secondary tabs
    BackHandler(enabled = currentTab != AppTab.DASHBOARD) {
        currentTab = AppTab.DASHBOARD
    }

    val customerRecords = (customersState as? UiState.Success)?.data?.items ?: emptyList()
    val customers = remember(customerRecords) { customerRecords.map { it.data } }
    val readingRecords = (readingsState as? UiState.Success)?.data?.items ?: emptyList()
    val allReadings = remember(readingRecords) { readingRecords.map { it.data } }

    fun openLogReading(customerId: String? = null) {
        editingReading = null
        preselectedReadingCustomerId = customerId ?: selectedCustomerFilterId
        showReadingForm = true
    }

    fun openAddCustomer() {
        editingCustomer = null
        showCustomerForm = true
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 680.dp

        Row(modifier = Modifier.fillMaxSize()) {
            if (useNavRail) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Spacer(Modifier.height(16.dp))
                    AppTab.entries.forEach { tab ->
                        val selected = tab == currentTab
                        NavigationRailItem(
                            selected = selected,
                            onClick = { currentTab = tab },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = stringResource(tab.labelRes)
                                )
                            },
                            label = { Text(stringResource(tab.labelRes)) },
                            modifier = Modifier.testTag("nav_${tab.route}")
                        )
                    }
                }
            }

            Scaffold(
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                            titleContentColor = MaterialTheme.colorScheme.onBackground
                        ),
                        title = {
                            Column {
                                Text(
                                    text = stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = stringResource(currentTab.labelRes).uppercase(),
                                    style = MonoMetricSmall,
                                    color = ElectricCyan
                                )
                            }
                        },
                        actions = {
                            // Live Offline / Cloud Sync Status Chip
                            val (tint, icon, label) = when {
                                !syncState.online -> Triple(BrightAmber, Icons.Rounded.CloudOff, "Offline")
                                syncState.pendingUploads > 0 -> Triple(ElectricCyan, Icons.Rounded.CloudUpload, "${syncState.pendingUploads} Syncing")
                                else -> Triple(BrightEmerald, Icons.Rounded.CloudDone, "Synced")
                            }
                            Surface(
                                onClick = { viewModel.syncNow() },
                                color = tint.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("topbar_sync_chip")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = "Sync status",
                                        tint = tint,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = label,
                                        style = MonoMetricSmall,
                                        color = tint,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(Modifier.width(4.dp))
                            IconButton(
                                onClick = { showAccountDialog = true },
                                modifier = Modifier.testTag("account_menu_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AccountCircle,
                                    contentDescription = "Account and Sync Settings",
                                    tint = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    )
                },
                bottomBar = {
                    if (!useNavRail) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 4.dp
                        ) {
                            AppTab.entries.forEach { tab ->
                                val selected = tab == currentTab
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { currentTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = stringResource(tab.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(tab.labelRes)) },
                                    modifier = Modifier.testTag("nav_${tab.route}")
                                )
                            }
                        }
                    }
                },
                floatingActionButton = {
                    when (currentTab) {
                        AppTab.CUSTOMERS -> {
                            ExtendedFloatingActionButton(
                                onClick = { openAddCustomer() },
                                icon = { Icon(Icons.Rounded.PersonAdd, contentDescription = null) },
                                text = { Text(stringResource(R.string.action_add_customer)) },
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.testTag("fab_add_customer")
                            )
                        }
                        AppTab.DASHBOARD, AppTab.READINGS -> {
                            ExtendedFloatingActionButton(
                                onClick = { openLogReading() },
                                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                                text = { Text(stringResource(R.string.action_log_reading)) },
                                containerColor = ElectricCyan,
                                contentColor = MaterialTheme.colorScheme.onSecondary,
                                modifier = Modifier.testTag("fab_log_reading")
                            )
                        }
                        AppTab.REPORTS -> Unit
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (currentTab) {
                        AppTab.DASHBOARD -> DashboardScreen(
                            analytics = analytics,
                            customersState = customersState,
                            selectedCustomerId = selectedCustomerFilterId,
                            selectedDateRange = selectedDateRange,
                            syncState = syncState,
                            onSelectCustomer = { viewModel.selectCustomerFilter(it) },
                            onSelectDateRange = { viewModel.selectDateRange(it) },
                            onLogReadingClick = { openLogReading() },
                            onAddCustomerClick = { openAddCustomer() },
                            onOpenReportsClick = { currentTab = AppTab.REPORTS },
                            onSyncNowClick = { viewModel.syncNow() },
                            onInspectCustomer = { cid -> inspectingCustomerId = cid }
                        )
                        AppTab.CUSTOMERS -> CustomersScreen(
                            customerMetrics = analytics.customerSummaries,
                            onAddCustomerClick = { openAddCustomer() },
                            onEditCustomer = { c ->
                                editingCustomer = c
                                showCustomerForm = true
                            },
                            onDeleteCustomer = { c -> customerPendingDelete = c },
                            onLogReadingForCustomer = { c -> openLogReading(c.id) },
                            onSelectCustomerDetail = { c -> inspectingCustomerId = c.id }
                        )
                        AppTab.READINGS -> ReadingsScreen(
                            readings = readingRecords,
                            customers = customers,
                            onLogReadingClick = { openLogReading() },
                            onEditReading = { r ->
                                editingReading = r
                                showReadingForm = true
                            },
                            onDeleteReading = { r -> readingPendingDelete = r }
                        )
                        AppTab.REPORTS -> ReportsScreen(
                            customers = customers,
                            allReadings = readingRecords,
                            syncState = syncState,
                            userDisplayName = userDisplayName,
                            onSyncNowClick = { viewModel.syncNow() },
                            onShowMessage = { viewModel.showMessage(it) }
                        )
                    }
                }
            }
        }
    }

    // --- DIALOGS & MODALS ---

    if (showCustomerForm) {
        CustomerFormDialog(
            editingCustomer = editingCustomer,
            isSaving = isSaving,
            onDismiss = { showCustomerForm = false },
            onSave = { name, meterNum, tariff, loadKw, accCode, loc, phone, notes ->
                viewModel.saveCustomer(
                    editingCustomerId = editingCustomer?.id,
                    name = name,
                    meterNumber = meterNum,
                    tariffPlan = tariff,
                    sanctionedLoadKw = loadKw,
                    accountCode = accCode,
                    location = loc,
                    contactPhone = phone,
                    notes = notes,
                    onSuccess = { showCustomerForm = false }
                )
            }
        )
    }

    if (showReadingForm) {
        ReadingEntryDialog(
            customers = customers,
            allReadings = allReadings,
            initialCustomerId = preselectedReadingCustomerId,
            editingReading = editingReading,
            isSaving = isSaving,
            onDismiss = { showReadingForm = false },
            onAddCustomerShortcut = {
                showReadingForm = false
                openAddCustomer()
            },
            onSave = { customer, date, time, kwh, kvarh, kvah, pf, notes ->
                viewModel.saveReading(
                    editingReadingId = editingReading?.id,
                    customer = customer,
                    readingDate = date,
                    readingTime = time,
                    kwh = kwh,
                    kvarh = kvarh,
                    kvah = kvah,
                    powerFactor = pf,
                    notes = notes,
                    onSuccess = { showReadingForm = false }
                )
            }
        )
    }

    inspectingCustomerId?.let { cid ->
        val customer = customers.firstOrNull { it.id == cid }
        if (customer != null) {
            val custReadings = remember(readingRecords, cid) {
                readingRecords.filter { it.data.customerId == cid }
            }
            CustomerDetailDialog(
                customer = customer,
                readings = custReadings,
                onDismiss = { inspectingCustomerId = null },
                onLogReading = {
                    inspectingCustomerId = null
                    openLogReading(customer.id)
                },
                onEditCustomer = {
                    inspectingCustomerId = null
                    editingCustomer = customer
                    showCustomerForm = true
                },
                onEditReading = { reading ->
                    inspectingCustomerId = null
                    editingReading = reading
                    showReadingForm = true
                },
                onDeleteReading = { reading ->
                    readingPendingDelete = reading
                }
            )
        }
    }

    customerPendingDelete?.let { cust ->
        ConfirmDeleteDialog(
            title = "Delete ${cust.name}?",
            body = "This removes meter ${cust.meterNumber} and all of its recorded daily readings from local cache and cloud backup.",
            onDismiss = { customerPendingDelete = null },
            onConfirm = {
                viewModel.deleteCustomer(cust.id)
                customerPendingDelete = null
            }
        )
    }

    readingPendingDelete?.let { reading ->
        ConfirmDeleteDialog(
            title = "Delete Meter Reading?",
            body = "Remove the ${reading.readingDate} ${reading.readingTime} reading (${reading.kwh} kWh) for ${reading.customerName}?",
            onDismiss = { readingPendingDelete = null },
            onConfirm = {
                viewModel.deleteReading(reading.id)
                readingPendingDelete = null
            }
        )
    }

    if (showAccountDialog) {
        AlertDialog(
            onDismissRequest = { showAccountDialog = false },
            title = { Text("Account & Cloud Backup") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = userDisplayName,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (!userEmail.isNullOrBlank()) {
                        Text(
                            text = userEmail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (syncState.online) "Online • Cloud Sync Active" else "Offline • Saving to Local Cache",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = "Pending uploads: ${syncState.pendingUploads}",
                                style = MonoMetricSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.syncNow()
                            showAccountDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.sync_now))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAccountDialog = false
                        signOutUser(credentialManager, scope)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AlertCoral),
                    modifier = Modifier.testTag("sign_out_button")
                ) {
                    Icon(Icons.Rounded.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.auth_sign_out))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAccountDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}
