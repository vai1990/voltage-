package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.Customer
import com.example.data.CustomerSummaryMetric
import com.example.data.DailyTrendPoint
import com.example.data.DashboardAnalytics
import com.example.data.DateRangePreset
import com.example.data.MeterMath
import com.example.data.MeterReading
import com.example.data.MeterRepository
import com.example.data.SyncSnapshot
import com.example.data.SyncedRecord
import com.example.data.TariffPlan
import com.example.data.WriteOutcome
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class SyncBannerState(
    val online: Boolean = true,
    val pendingUploads: Int = 0,
    val fromCache: Boolean = false,
    val syncingNow: Boolean = false
)

class VoltLedgerViewModel(
    private val repository: MeterRepository,
    private val currentUserId: String,
    private val userDisplayName: String,
    private val userEmail: String?
) : ViewModel() {

    private val _selectedCustomerFilterId = MutableStateFlow<String?>(null)
    val selectedCustomerFilterId: StateFlow<String?> = _selectedCustomerFilterId.asStateFlow()

    private val _selectedDateRange = MutableStateFlow(DateRangePreset.LAST_30_DAYS)
    val selectedDateRange: StateFlow<DateRangePreset> = _selectedDateRange.asStateFlow()

    private val _isSyncingNow = MutableStateFlow(false)
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    val isOnline: StateFlow<Boolean> = repository.observeOnlineStatus()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), true)

    val customersState: StateFlow<UiState<SyncSnapshot<Customer>>> =
        repository.observeCustomers(currentUserId)
            .map<SyncSnapshot<Customer>, UiState<SyncSnapshot<Customer>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing customers", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load customers"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val readingsState: StateFlow<UiState<SyncSnapshot<MeterReading>>> =
        repository.observeReadings(currentUserId)
            .map<SyncSnapshot<MeterReading>, UiState<SyncSnapshot<MeterReading>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing readings", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load meter readings"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val syncBannerState: StateFlow<SyncBannerState> = combine(
        isOnline,
        customersState,
        readingsState,
        _isSyncingNow
    ) { online, custState, readState, syncing ->
        val custSnap = (custState as? UiState.Success)?.data
        val readSnap = (readState as? UiState.Success)?.data
        val pendingCustomers = custSnap?.items?.count { it.pendingSync } ?: 0
        val pendingReadings = readSnap?.items?.count { it.pendingSync } ?: 0
        val fromCache = (custSnap?.fromCache == true) || (readSnap?.fromCache == true)
        SyncBannerState(
            online = online,
            pendingUploads = pendingCustomers + pendingReadings,
            fromCache = fromCache,
            syncingNow = syncing
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = SyncBannerState()
    )

    val dashboardAnalytics: StateFlow<DashboardAnalytics> = combine(
        customersState,
        readingsState,
        selectedCustomerFilterId,
        selectedDateRange
    ) { custState, readState, filterCustomerId, preset ->
        val customers = (custState as? UiState.Success)?.data?.items ?: emptyList()
        val allReadings = (readState as? UiState.Success)?.data?.items ?: emptyList()
        computeAnalytics(customers, allReadings, filterCustomerId, preset)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = DashboardAnalytics()
    )

    init {
        viewModelScope.launch {
            repository.ensureUserProfile(userDisplayName, userEmail)
        }
    }

    fun selectCustomerFilter(customerId: String?) {
        _selectedCustomerFilterId.value = customerId
    }

    fun selectDateRange(preset: DateRangePreset) {
        _selectedDateRange.value = preset
    }

    fun syncNow() {
        if (_isSyncingNow.value) return
        viewModelScope.launch {
            _isSyncingNow.value = true
            val result = repository.syncPendingWrites()
            _isSyncingNow.value = false
            result.fold(
                onSuccess = { synced ->
                    if (synced) {
                        _snackbarEvents.tryEmit("Cloud backup synchronized")
                    } else {
                        _snackbarEvents.tryEmit("Offline — queued readings will auto-sync when connected")
                    }
                },
                onFailure = { err ->
                    _snackbarEvents.tryEmit(err.localizedMessage ?: "Cloud sync interrupted")
                }
            )
        }
    }

    fun saveCustomer(
        editingCustomerId: String?,
        name: String,
        meterNumber: String,
        tariffPlan: TariffPlan,
        sanctionedLoadKw: Double,
        accountCode: String?,
        location: String?,
        contactPhone: String?,
        notes: String?,
        onSuccess: (String) -> Unit
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            if (editingCustomerId == null) {
                val res = repository.createCustomer(
                    name = name,
                    meterNumber = meterNumber,
                    tariffPlan = tariffPlan,
                    sanctionedLoadKw = sanctionedLoadKw,
                    accountCode = accountCode,
                    location = location,
                    contactPhone = contactPhone,
                    notes = notes
                )
                _isSaving.value = false
                res.fold(
                    onSuccess = { (newId, outcome) ->
                        val msg = if (outcome == WriteOutcome.SYNCED) {
                            "Customer saved & synced to cloud"
                        } else {
                            "Customer saved offline — queued for sync"
                        }
                        _snackbarEvents.tryEmit(msg)
                        onSuccess(newId)
                    },
                    onFailure = { err ->
                        _snackbarEvents.tryEmit(err.localizedMessage ?: "Could not save customer")
                    }
                )
            } else {
                val relatedIds = (readingsState.value as? UiState.Success)
                    ?.data?.items
                    ?.filter { it.data.customerId == editingCustomerId }
                    ?.map { it.data.id }
                    ?: emptyList()
                val res = repository.updateCustomer(
                    customerId = editingCustomerId,
                    name = name,
                    meterNumber = meterNumber,
                    tariffPlan = tariffPlan,
                    sanctionedLoadKw = sanctionedLoadKw,
                    accountCode = accountCode,
                    location = location,
                    contactPhone = contactPhone,
                    notes = notes,
                    relatedReadingIds = relatedIds
                )
                _isSaving.value = false
                res.fold(
                    onSuccess = { outcome ->
                        val msg = if (outcome == WriteOutcome.SYNCED) {
                            "Customer updated & synced"
                        } else {
                            "Customer updated offline — queued for sync"
                        }
                        _snackbarEvents.tryEmit(msg)
                        onSuccess(editingCustomerId)
                    },
                    onFailure = { err ->
                        _snackbarEvents.tryEmit(err.localizedMessage ?: "Could not update customer")
                    }
                )
            }
        }
    }

    fun deleteCustomer(customerId: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            val relatedIds = (readingsState.value as? UiState.Success)
                ?.data?.items
                ?.filter { it.data.customerId == customerId }
                ?.map { it.data.id }
                ?: emptyList()
            val res = repository.deleteCustomer(customerId, relatedIds)
            res.fold(
                onSuccess = {
                    if (_selectedCustomerFilterId.value == customerId) {
                        _selectedCustomerFilterId.value = null
                    }
                    _snackbarEvents.tryEmit("Customer and ${relatedIds.size} reading(s) removed")
                    onDeleted()
                },
                onFailure = { err ->
                    _snackbarEvents.tryEmit(err.localizedMessage ?: "Failed to delete customer")
                }
            )
        }
    }

    fun saveReading(
        editingReadingId: String?,
        customer: Customer,
        readingDate: String,
        readingTime: String,
        kwh: Double,
        kvarh: Double,
        kvah: Double,
        powerFactor: Double,
        notes: String?,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val res = if (editingReadingId == null) {
                repository.createReading(
                    customer = customer,
                    readingDate = readingDate,
                    readingTime = readingTime,
                    kwh = kwh,
                    kvarh = kvarh,
                    kvah = kvah,
                    powerFactor = powerFactor,
                    notes = notes
                )
            } else {
                repository.updateReading(
                    readingId = editingReadingId,
                    readingDate = readingDate,
                    readingTime = readingTime,
                    kwh = kwh,
                    kvarh = kvarh,
                    kvah = kvah,
                    powerFactor = powerFactor,
                    notes = notes
                )
            }
            _isSaving.value = false
            res.fold(
                onSuccess = { outcome ->
                    val msg = if (outcome == WriteOutcome.SYNCED) {
                        "Reading recorded & backed up to cloud"
                    } else {
                        "Reading saved locally — will sync automatically"
                    }
                    _snackbarEvents.tryEmit(msg)
                    onSuccess()
                },
                onFailure = { err ->
                    _snackbarEvents.tryEmit(err.localizedMessage ?: "Could not save reading")
                }
            )
        }
    }

    fun deleteReading(readingId: String) {
        viewModelScope.launch {
            val res = repository.deleteReading(readingId)
            res.fold(
                onSuccess = { _snackbarEvents.tryEmit("Reading deleted") },
                onFailure = { err ->
                    _snackbarEvents.tryEmit(err.localizedMessage ?: "Failed to delete reading")
                }
            )
        }
    }

    fun showMessage(message: String) {
        _snackbarEvents.tryEmit(message)
    }

    private fun computeAnalytics(
        customers: List<SyncedRecord<Customer>>,
        allReadings: List<SyncedRecord<MeterReading>>,
        filterCustomerId: String?,
        preset: DateRangePreset
    ): DashboardAnalytics {
        val cutoffMillis = preset.days?.let { MeterMath.cutoffEpochForDays(it) }
        val filtered = allReadings.filter { rec ->
            val matchesCustomer = filterCustomerId == null || rec.data.customerId == filterCustomerId
            val matchesDate = cutoffMillis == null || rec.data.readingEpochMillis >= cutoffMillis
            matchesCustomer && matchesDate
        }

        val rawReadings = filtered.map { it.data }
        val totalKwh = rawReadings.sumOf { it.kwh }
        val totalKvarh = rawReadings.sumOf { it.kvarh }
        val totalKvah = rawReadings.sumOf { it.kvah }
        val pfs = rawReadings.map { it.powerFactor }
        val avgPf = if (pfs.isEmpty()) 0.0 else pfs.average()
        val minPf = pfs.minOrNull() ?: 0.0
        val maxPf = pfs.maxOrNull() ?: 0.0
        val lowPfAlerts = rawReadings.count { it.powerFactor < 0.85 }

        val dailyTrend = rawReadings
            .groupBy { it.readingDate }
            .entries
            .sortedBy { it.key }
            .map { (date, list) ->
                DailyTrendPoint(
                    date = date,
                    label = MeterMath.shortDateLabel(date),
                    totalKwh = list.sumOf { it.kwh },
                    totalKvarh = list.sumOf { it.kvarh },
                    totalKvah = list.sumOf { it.kvah },
                    avgPowerFactor = list.map { it.powerFactor }.average(),
                    readingCount = list.size
                )
            }

        val readingsByCustomer = filtered.groupBy { it.data.customerId }
        val customerSummaries = customers.map { custRec ->
            val list = readingsByCustomer[custRec.data.id] ?: emptyList()
            val cReadings = list.map { it.data }
            CustomerSummaryMetric(
                customer = custRec.data,
                readingCount = cReadings.size,
                totalKwh = cReadings.sumOf { it.kwh },
                totalKvarh = cReadings.sumOf { it.kvarh },
                totalKvah = cReadings.sumOf { it.kvah },
                avgPowerFactor = if (cReadings.isEmpty()) 0.0 else cReadings.map { it.powerFactor }.average(),
                latestReading = cReadings.maxByOrNull { it.readingEpochMillis },
                pendingCount = list.count { it.pendingSync } + if (custRec.pendingSync) 1 else 0
            )
        }.sortedByDescending { it.totalKwh }

        return DashboardAnalytics(
            totalKwh = totalKwh,
            totalKvarh = totalKvarh,
            totalKvah = totalKvah,
            avgPowerFactor = avgPf,
            minPowerFactor = minPf,
            maxPowerFactor = maxPf,
            totalReadings = rawReadings.size,
            activeCustomerCount = customers.size,
            lowPfAlertCount = lowPfAlerts,
            dailyTrend = dailyTrend,
            customerSummaries = customerSummaries
        )
    }

    private companion object {
        const val TAG = "VoltLedgerVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
