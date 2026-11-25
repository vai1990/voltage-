package com.example.data

import android.content.Context
import android.util.Log
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

class MeterRepository(
    private val db: FirebaseFirestore,
    private val networkMonitor: NetworkMonitor,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        db = createConfiguredFirestore(context.applicationContext),
        networkMonitor = NetworkMonitor(context.applicationContext)
    )

    private fun requireUserId(): String {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        }
        return uid
    }

    private fun userDoc(uid: String) = db.collection("users").document(uid)
    private fun customersCol(uid: String) = userDoc(uid).collection("customers")
    private fun readingsCol(uid: String) = userDoc(uid).collection("readings")

    fun observeOnlineStatus(): Flow<Boolean> = networkMonitor.observeOnline()

    /**
     * Ensures the authenticated user's profile document exists for cloud sync metadata.
     */
    suspend fun ensureUserProfile(displayName: String, email: String?): Result<Unit> {
        var docPath = "users/unauthenticated"
        return try {
            val uid = requireUserId()
            val ref = userDoc(uid)
            docPath = ref.path
            val safeName = displayName.trim().ifBlank { "Utility Manager" }.take(120)
            val safeEmail = email?.trim()?.takeIf { it.isNotBlank() }?.take(200)

            val existing = if (networkMonitor.isCurrentlyOnline()) {
                withTimeoutOrNull(3000L) { ref.get().await() }
            } else {
                null
            }

            if (existing == null || !existing.exists()) {
                val payload = mapOf<String, Any?>(
                    "userId" to uid,
                    "displayName" to safeName,
                    "email" to safeEmail,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }.mapValues { it.value!! }

                val task = ref.set(payload).addOnFailureListener { e ->
                    handleFirestoreError(e, OperationType.CREATE, docPath)
                }
                if (networkMonitor.isCurrentlyOnline()) {
                    withTimeoutOrNull(3000L) { task.await() }
                }
            } else {
                val payload = mapOf<String, Any?>(
                    "displayName" to safeName,
                    "email" to safeEmail,
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }.mapValues { it.value!! }
                val task = ref.update(payload).addOnFailureListener { e ->
                    handleFirestoreError(e, OperationType.UPDATE, docPath)
                }
                if (networkMonitor.isCurrentlyOnline()) {
                    withTimeoutOrNull(3000L) { task.await() }
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    /**
     * Real-time stream of user-scoped customers with cache & pending-write metadata.
     */
    fun observeCustomers(userId: String): Flow<SyncSnapshot<Customer>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch." }
        val path = "users/$uid/customers"
        emitAll(
            customersCol(uid)
                .snapshots(MetadataChanges.INCLUDE)
                .map { snapshot ->
                    val records = snapshot.documents.mapNotNull { doc ->
                        val customer = doc.toObject(
                            Customer::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                        customer?.let {
                            SyncedRecord(
                                data = it,
                                pendingSync = doc.metadata.hasPendingWrites()
                            )
                        }
                    }.sortedBy { it.data.name.lowercase() }
                    SyncSnapshot(
                        items = records,
                        fromCache = snapshot.metadata.isFromCache,
                        hasPendingWrites = snapshot.metadata.hasPendingWrites()
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    /**
     * Real-time stream of all meter readings for the user, ordered newest-first.
     */
    fun observeReadings(userId: String): Flow<SyncSnapshot<MeterReading>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch." }
        val path = "users/$uid/readings"
        emitAll(
            readingsCol(uid)
                .orderBy("readingEpochMillis", Query.Direction.DESCENDING)
                .snapshots(MetadataChanges.INCLUDE)
                .map { snapshot ->
                    val records = snapshot.documents.mapNotNull { doc ->
                        val reading = doc.toObject(
                            MeterReading::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                        reading?.let {
                            SyncedRecord(
                                data = it,
                                pendingSync = doc.metadata.hasPendingWrites()
                            )
                        }
                    }
                    SyncSnapshot(
                        items = records,
                        fromCache = snapshot.metadata.isFromCache,
                        hasPendingWrites = snapshot.metadata.hasPendingWrites()
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createCustomer(
        name: String,
        meterNumber: String,
        tariffPlan: TariffPlan,
        sanctionedLoadKw: Double,
        accountCode: String?,
        location: String?,
        contactPhone: String?,
        notes: String?
    ): Result<Pair<String, WriteOutcome>> {
        var docPath = "users/unauthenticated/customers"
        return try {
            val uid = requireUserId()
            val cleanName = name.trim()
            val cleanMeter = meterNumber.trim()
            require(cleanName.length in 1..120) { "Customer name must be 1–120 characters." }
            require(cleanMeter.length in 1..60) { "Meter serial must be 1–60 characters." }
            require(sanctionedLoadKw in 0.0..1_000_000.0) { "Sanctioned load must be between 0 and 1,000,000 kW." }

            val cleanAccount = accountCode?.trim()?.takeIf { it.isNotEmpty() }?.take(40)
            val cleanLocation = location?.trim()?.takeIf { it.isNotEmpty() }?.take(160)
            val cleanPhone = contactPhone?.trim()?.takeIf { it.isNotEmpty() }?.take(30)
            val cleanNotes = notes?.trim()?.takeIf { it.isNotEmpty() }?.take(300)

            val docRef = customersCol(uid).document()
            docPath = docRef.path

            val payload = mapOf<String, Any?>(
                "userId" to uid,
                "name" to cleanName,
                "meterNumber" to cleanMeter,
                "tariffPlan" to tariffPlan.code,
                "sanctionedLoadKw" to sanctionedLoadKw,
                "accountCode" to cleanAccount,
                "location" to cleanLocation,
                "contactPhone" to cleanPhone,
                "notes" to cleanNotes,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }.mapValues { it.value!! }

            val task = docRef.set(payload).addOnFailureListener { e ->
                handleFirestoreError(e, OperationType.CREATE, docPath)
            }

            val outcome = awaitOfflineFriendly(task, OperationType.CREATE, docPath)
            Result.success(docRef.id to outcome)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateCustomer(
        customerId: String,
        name: String,
        meterNumber: String,
        tariffPlan: TariffPlan,
        sanctionedLoadKw: Double,
        accountCode: String?,
        location: String?,
        contactPhone: String?,
        notes: String?,
        relatedReadingIds: List<String> = emptyList()
    ): Result<WriteOutcome> {
        var docPath = "users/unauthenticated/customers/$customerId"
        return try {
            val uid = requireUserId()
            require(customerId.isNotBlank()) { "Customer ID is required." }
            val cleanName = name.trim()
            val cleanMeter = meterNumber.trim()
            require(cleanName.length in 1..120) { "Customer name must be 1–120 characters." }
            require(cleanMeter.length in 1..60) { "Meter serial must be 1–60 characters." }
            require(sanctionedLoadKw in 0.0..1_000_000.0) { "Sanctioned load must be between 0 and 1,000,000 kW." }

            val docRef = customersCol(uid).document(customerId)
            docPath = docRef.path

            val payload = mapOf<String, Any?>(
                "name" to cleanName,
                "meterNumber" to cleanMeter,
                "tariffPlan" to tariffPlan.code,
                "sanctionedLoadKw" to sanctionedLoadKw,
                "accountCode" to accountCode?.trim()?.takeIf { it.isNotEmpty() }?.take(40),
                "location" to location?.trim()?.takeIf { it.isNotEmpty() }?.take(160),
                "contactPhone" to contactPhone?.trim()?.takeIf { it.isNotEmpty() }?.take(30),
                "notes" to notes?.trim()?.takeIf { it.isNotEmpty() }?.take(300),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            val task = docRef.update(payload).addOnFailureListener { e ->
                handleFirestoreError(e, OperationType.UPDATE, docPath)
            }
            val outcome = awaitOfflineFriendly(task, OperationType.UPDATE, docPath)

            // Propagate customer name / meter number updates to existing readings in batches
            if (relatedReadingIds.isNotEmpty()) {
                relatedReadingIds.chunked(400).forEach { chunk ->
                    val batch = db.batch()
                    chunk.forEach { readingId ->
                        batch.update(
                            readingsCol(uid).document(readingId),
                            mapOf(
                                "customerName" to cleanName,
                                "meterNumber" to cleanMeter,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                    batch.commit().addOnFailureListener { e ->
                        handleFirestoreError(e, OperationType.UPDATE, "users/$uid/readings")
                    }
                }
            }

            Result.success(outcome)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteCustomer(
        customerId: String,
        relatedReadingIds: List<String>
    ): Result<WriteOutcome> {
        var docPath = "users/unauthenticated/customers/$customerId"
        return try {
            val uid = requireUserId()
            require(customerId.isNotBlank()) { "Customer ID is required." }
            val docRef = customersCol(uid).document(customerId)
            docPath = docRef.path

            val batch = db.batch()
            relatedReadingIds.take(450).forEach { readingId ->
                batch.delete(readingsCol(uid).document(readingId))
            }
            batch.delete(docRef)
            val task = batch.commit().addOnFailureListener { e ->
                handleFirestoreError(e, OperationType.DELETE, docPath)
            }
            val outcome = awaitOfflineFriendly(task, OperationType.DELETE, docPath)
            Result.success(outcome)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun createReading(
        customer: Customer,
        readingDate: String,
        readingTime: String,
        kwh: Double,
        kvarh: Double,
        kvah: Double,
        powerFactor: Double,
        notes: String?
    ): Result<WriteOutcome> {
        var docPath = "users/unauthenticated/readings"
        return try {
            val uid = requireUserId()
            require(customer.id.isNotBlank()) { "Please select a customer." }
            require(MeterMath.isValidDate(readingDate)) { "Date must be YYYY-MM-DD." }
            require(MeterMath.isValidTime(readingTime)) { "Time must be HH:mm." }
            require(kwh in 0.0..1_000_000_000.0) { "kWh must be non-negative." }
            require(kvarh in 0.0..1_000_000_000.0) { "kVArh must be non-negative." }
            require(kvah in 0.0..1_000_000_000.0) { "kVAh must be non-negative." }
            require(powerFactor in 0.0..1.0) { "Power Factor must be between 0.00 and 1.00." }

            val epochMillis = MeterMath.parseEpochMillis(readingDate, readingTime)
            val cleanNotes = notes?.trim()?.takeIf { it.isNotEmpty() }?.take(300)

            val docRef = readingsCol(uid).document()
            docPath = docRef.path

            val payload = mapOf<String, Any?>(
                "userId" to uid,
                "customerId" to customer.id,
                "customerName" to customer.name.take(120),
                "meterNumber" to customer.meterNumber.take(60),
                "readingDate" to readingDate,
                "readingTime" to readingTime,
                "readingEpochMillis" to epochMillis,
                "kwh" to kwh,
                "kvarh" to kvarh,
                "kvah" to kvah,
                "powerFactor" to powerFactor,
                "notes" to cleanNotes,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }.mapValues { it.value!! }

            val task = docRef.set(payload).addOnFailureListener { e ->
                handleFirestoreError(e, OperationType.CREATE, docPath)
            }
            val outcome = awaitOfflineFriendly(task, OperationType.CREATE, docPath)
            Result.success(outcome)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateReading(
        readingId: String,
        readingDate: String,
        readingTime: String,
        kwh: Double,
        kvarh: Double,
        kvah: Double,
        powerFactor: Double,
        notes: String?
    ): Result<WriteOutcome> {
        var docPath = "users/unauthenticated/readings/$readingId"
        return try {
            val uid = requireUserId()
            require(readingId.isNotBlank()) { "Reading ID is required." }
            require(MeterMath.isValidDate(readingDate)) { "Date must be YYYY-MM-DD." }
            require(MeterMath.isValidTime(readingTime)) { "Time must be HH:mm." }
            require(kwh in 0.0..1_000_000_000.0) { "kWh must be non-negative." }
            require(kvarh in 0.0..1_000_000_000.0) { "kVArh must be non-negative." }
            require(kvah in 0.0..1_000_000_000.0) { "kVAh must be non-negative." }
            require(powerFactor in 0.0..1.0) { "Power Factor must be between 0.00 and 1.00." }

            val epochMillis = MeterMath.parseEpochMillis(readingDate, readingTime)
            val docRef = readingsCol(uid).document(readingId)
            docPath = docRef.path

            val payload = mapOf<String, Any?>(
                "readingDate" to readingDate,
                "readingTime" to readingTime,
                "readingEpochMillis" to epochMillis,
                "kwh" to kwh,
                "kvarh" to kvarh,
                "kvah" to kvah,
                "powerFactor" to powerFactor,
                "notes" to notes?.trim()?.takeIf { it.isNotEmpty() }?.take(300),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            val task = docRef.update(payload).addOnFailureListener { e ->
                handleFirestoreError(e, OperationType.UPDATE, docPath)
            }
            val outcome = awaitOfflineFriendly(task, OperationType.UPDATE, docPath)
            Result.success(outcome)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteReading(readingId: String): Result<WriteOutcome> {
        var docPath = "users/unauthenticated/readings/$readingId"
        return try {
            val uid = requireUserId()
            require(readingId.isNotBlank()) { "Reading ID is required." }
            val docRef = readingsCol(uid).document(readingId)
            docPath = docRef.path

            val task = docRef.delete().addOnFailureListener { e ->
                handleFirestoreError(e, OperationType.DELETE, docPath)
            }
            val outcome = awaitOfflineFriendly(task, OperationType.DELETE, docPath)
            Result.success(outcome)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    /**
     * Triggers network reconnection and waits for all locally queued offline writes to sync to cloud.
     */
    suspend fun syncPendingWrites(): Result<Boolean> {
        return try {
            requireUserId()
            if (!networkMonitor.isCurrentlyOnline()) {
                return Result.success(false)
            }
            db.enableNetwork().await()
            val completed = withTimeoutOrNull(8000L) {
                db.waitForPendingWrites().await()
                true
            } ?: false
            Result.success(completed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "syncPendingWrites")
            Result.failure(e)
        }
    }

    private suspend fun awaitOfflineFriendly(
        task: com.google.android.gms.tasks.Task<Void>,
        operationType: OperationType,
        path: String
    ): WriteOutcome {
        if (!networkMonitor.isCurrentlyOnline()) {
            return WriteOutcome.QUEUED_OFFLINE
        }
        return try {
            val finished = withTimeoutOrNull(3000L) {
                task.await()
                true
            }
            if (finished == true) WriteOutcome.SYNCED else WriteOutcome.QUEUED_OFFLINE
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, operationType, path)
            throw e
        }
    }

    companion object {
        private const val TAG = "MeterRepository"

        fun createConfiguredFirestore(appContext: Context): FirebaseFirestore {
            val databaseId = appContext.getString(R.string.firestore_database_id)
            val instance = FirebaseFirestore.getInstance(databaseId)
            runCatching {
                val settings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(
                        PersistentCacheSettings.newBuilder()
                            .setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                            .build()
                    )
                    .build()
                instance.firestoreSettings = settings
            }.onFailure { err ->
                Log.d(TAG, "Firestore settings already initialized: ${err.message}")
            }
            return instance
        }
    }
}
