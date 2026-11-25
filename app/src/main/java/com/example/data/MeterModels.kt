package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.round

enum class TariffPlan(val code: String, val label: String, val shortBadge: String) {
    INDUSTRIAL_HT("INDUSTRIAL_HT", "Industrial HT", "HT-IND"),
    COMMERCIAL_LT("COMMERCIAL_LT", "Commercial LT", "LT-COM"),
    SOLAR_NET("SOLAR_NET", "Solar Net-Meter", "SOLAR"),
    STANDARD("STANDARD", "Standard Supply", "STD");

    companion object {
        fun fromCode(raw: String?): TariffPlan =
            entries.firstOrNull { it.code == raw } ?: STANDARD
    }
}

@Keep
@IgnoreExtraProperties
data class Customer(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val meterNumber: String = "",
    val tariffPlan: String = TariffPlan.INDUSTRIAL_HT.code,
    val sanctionedLoadKw: Double = 0.0,
    val accountCode: String? = null,
    val location: String? = null,
    val contactPhone: String? = null,
    val notes: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val parsedTariff: TariffPlan
        get() = TariffPlan.fromCode(tariffPlan)
}

@Keep
@IgnoreExtraProperties
data class MeterReading(
    @DocumentId val id: String = "",
    val userId: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val meterNumber: String = "",
    val readingDate: String = "",
    val readingTime: String = "",
    val readingEpochMillis: Long = 0L,
    val kwh: Double = 0.0,
    val kvarh: Double = 0.0,
    val kvah: Double = 0.0,
    val powerFactor: Double = 0.0,
    val notes: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class SyncedRecord<T>(
    val data: T,
    val pendingSync: Boolean = false
)

data class SyncSnapshot<T>(
    val items: List<SyncedRecord<T>>,
    val fromCache: Boolean,
    val hasPendingWrites: Boolean
)

enum class WriteOutcome {
    SYNCED,
    QUEUED_OFFLINE
}

enum class DateRangePreset(val label: String, val days: Int?) {
    LAST_7_DAYS("7D", 7),
    LAST_30_DAYS("30D", 30),
    LAST_90_DAYS("90D", 90),
    ALL_TIME("All", null)
}

data class DailyTrendPoint(
    val date: String,
    val label: String,
    val totalKwh: Double,
    val totalKvarh: Double,
    val totalKvah: Double,
    val avgPowerFactor: Double,
    val readingCount: Int
)

data class CustomerSummaryMetric(
    val customer: Customer,
    val readingCount: Int,
    val totalKwh: Double,
    val totalKvarh: Double,
    val totalKvah: Double,
    val avgPowerFactor: Double,
    val latestReading: MeterReading?,
    val pendingCount: Int
)

data class DashboardAnalytics(
    val totalKwh: Double = 0.0,
    val totalKvarh: Double = 0.0,
    val totalKvah: Double = 0.0,
    val avgPowerFactor: Double = 0.0,
    val minPowerFactor: Double = 0.0,
    val maxPowerFactor: Double = 0.0,
    val totalReadings: Int = 0,
    val activeCustomerCount: Int = 0,
    val lowPfAlertCount: Int = 0,
    val dailyTrend: List<DailyTrendPoint> = emptyList(),
    val customerSummaries: List<CustomerSummaryMetric> = emptyList()
)

object MeterMath {
    private val dateRegex = Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}$")
    private val timeRegex = Regex("^[0-9]{2}:[0-9]{2}$")

    fun isValidDate(date: String): Boolean = dateRegex.matches(date)
    fun isValidTime(time: String): Boolean = timeRegex.matches(time)

    fun todayIsoDate(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date())
    }

    fun currentTime24h(): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.US)
        return sdf.format(Date())
    }

    fun formatDateFromMillis(millis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date(millis))
    }

    fun parseEpochMillis(dateStr: String, timeStr: String): Long {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            sdf.isLenient = false
            sdf.parse("$dateStr $timeStr")?.time ?: System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }

    fun shortDateLabel(isoDate: String): String {
        if (isoDate.length < 10) return isoDate
        val parts = isoDate.split("-")
        if (parts.size != 3) return isoDate
        val monthIdx = parts[1].toIntOrNull()?.minus(1) ?: return isoDate
        val months = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        val m = months.getOrElse(monthIdx) { parts[1] }
        return "$m ${parts[2].trimStart('0').ifEmpty { "0" }}"
    }

    fun cutoffEpochForDays(days: Int): Long {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -days)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /**
     * Computes power factor from kWh, kVArh, and kVAh.
     * Prefers kWh / kVAh when kVAh > 0, otherwise falls back to cos(atan2(kVArh, kWh)).
     */
    fun computePowerFactor(kwh: Double, kvarh: Double, kvah: Double): Double? {
        val raw = when {
            kvah > 0.0 && kwh >= 0.0 -> (kwh / kvah).coerceIn(0.0, 1.0)
            kwh > 0.0 || kvarh > 0.0 -> cos(atan2(kvarh.coerceAtLeast(0.0), kwh.coerceAtLeast(0.0))).coerceIn(0.0, 1.0)
            else -> return null
        }
        return round(raw * 1000.0) / 1000.0
    }

    /**
     * Suggests apparent energy kVAh = sqrt(kWh^2 + kVArh^2).
     */
    fun computeKvah(kwh: Double, kvarh: Double): Double {
        val raw = hypot(kwh.coerceAtLeast(0.0), kvarh.coerceAtLeast(0.0))
        return round(raw * 100.0) / 100.0
    }

    fun formatEnergy(value: Double): String = String.format(Locale.US, "%,.2f", value)

    fun formatCompactEnergy(value: Double): String = when {
        value >= 1_000_000.0 -> String.format(Locale.US, "%.2fM", value / 1_000_000.0)
        value >= 10_000.0 -> String.format(Locale.US, "%.1fk", value / 1_000.0)
        value >= 1_000.0 -> String.format(Locale.US, "%.2fk", value / 1_000.0)
        else -> String.format(Locale.US, "%.1f", value)
    }

    fun formatPf(value: Double): String = String.format(Locale.US, "%.3f", value)
}
