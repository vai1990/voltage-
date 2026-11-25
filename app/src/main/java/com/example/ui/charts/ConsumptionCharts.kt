package com.example.ui.charts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.CustomerSummaryMetric
import com.example.data.DailyTrendPoint
import com.example.data.MeterMath
import com.example.ui.theme.AlertCoral
import com.example.ui.theme.BrightAmber
import com.example.ui.theme.BrightEmerald
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GridEmerald
import com.example.ui.theme.HighVoltageAmber
import com.example.ui.theme.LuminousCyan
import com.example.ui.theme.MonoMetricMedium
import com.example.ui.theme.MonoMetricSmall
import kotlin.math.roundToInt

/**
 * Interactive dual-series Active (kWh) vs. Apparent (kVAh) Energy trend chart.
 * Tap anywhere along the chart to inspect exact daily kWh, kVArh, kVAh, and Power Factor.
 */
@Composable
fun EnergyTrendLineChart(
    points: List<DailyTrendPoint>,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) return

    var selectedIndex by remember(points) { mutableIntStateOf(points.lastIndex) }
    val clampedIndex = selectedIndex.coerceIn(0, points.lastIndex)
    val selected = points[clampedIndex]

    val kwhColor = ElectricCyan
    val kvahColor = HighVoltageAmber
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    val surfaceBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    val textMuted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Selected Day Telemetry Inspector
        Surface(
            color = surfaceBg,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = selected.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${selected.readingCount} entry(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = textMuted
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ChartMetricTag("kWh", MeterMath.formatCompactEnergy(selected.totalKwh), kwhColor)
                    ChartMetricTag("kVAh", MeterMath.formatCompactEnergy(selected.totalKvah), kvahColor)
                    ChartMetricTag("kVArh", MeterMath.formatCompactEnergy(selected.totalKvarh), textMuted)
                    ChartMetricTag("PF", MeterMath.formatPf(selected.avgPowerFactor), pfHealthColor(selected.avgPowerFactor))
                }
            }
        }

        val maxVal = remember(points) {
            points.maxOf { maxOf(it.totalKwh, it.totalKvah) }.coerceAtLeast(1.0) * 1.12
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .pointerInput(points) {
                    detectTapGestures { offset ->
                        if (points.size > 1) {
                            val padX = 16.dp.toPx()
                            val usableW = (size.width - padX * 2).coerceAtLeast(1f)
                            val fraction = ((offset.x - padX) / usableW).coerceIn(0f, 1f)
                            selectedIndex = (fraction * (points.size - 1)).roundToInt()
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val padLeft = 12.dp.toPx()
                val padRight = 12.dp.toPx()
                val padTop = 14.dp.toPx()
                val padBottom = 18.dp.toPx()
                val chartW = (size.width - padLeft - padRight).coerceAtLeast(1f)
                val chartH = (size.height - padTop - padBottom).coerceAtLeast(1f)

                // Horizontal reference grid lines
                val gridSteps = 4
                for (i in 0..gridSteps) {
                    val y = padTop + chartH * (i / gridSteps.toFloat())
                    drawLine(
                        color = gridColor,
                        start = Offset(padLeft, y),
                        end = Offset(size.width - padRight, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = if (i < gridSteps) PathEffect.dashPathEffect(floatArrayOf(8f, 8f)) else null
                    )
                }

                fun xFor(index: Int): Float {
                    return if (points.size == 1) {
                        padLeft + chartW / 2f
                    } else {
                        padLeft + chartW * (index.toFloat() / (points.size - 1))
                    }
                }

                fun yFor(value: Double): Float {
                    val norm = (value / maxVal).toFloat().coerceIn(0f, 1f)
                    return padTop + chartH * (1f - norm)
                }

                // Vertical cursor for selected point
                val selX = xFor(clampedIndex)
                drawLine(
                    color = kwhColor.copy(alpha = 0.35f),
                    start = Offset(selX, padTop),
                    end = Offset(selX, padTop + chartH),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                )

                if (points.size > 1) {
                    // Build smooth kVAh path
                    val kvahPath = Path()
                    points.forEachIndexed { idx, p ->
                        val pt = Offset(xFor(idx), yFor(p.totalKvah))
                        if (idx == 0) kvahPath.moveTo(pt.x, pt.y)
                        else {
                            val prev = Offset(xFor(idx - 1), yFor(points[idx - 1].totalKvah))
                            val midX = (prev.x + pt.x) / 2f
                            kvahPath.cubicTo(midX, prev.y, midX, pt.y, pt.x, pt.y)
                        }
                    }
                    drawPath(
                        path = kvahPath,
                        color = kvahColor,
                        style = Stroke(
                            width = 2.2.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 6f))
                        )
                    )

                    // Build smooth kWh line + area gradient fill
                    val kwhLinePath = Path()
                    val kwhAreaPath = Path()
                    points.forEachIndexed { idx, p ->
                        val pt = Offset(xFor(idx), yFor(p.totalKwh))
                        if (idx == 0) {
                            kwhLinePath.moveTo(pt.x, pt.y)
                            kwhAreaPath.moveTo(pt.x, padTop + chartH)
                            kwhAreaPath.lineTo(pt.x, pt.y)
                        } else {
                            val prev = Offset(xFor(idx - 1), yFor(points[idx - 1].totalKwh))
                            val midX = (prev.x + pt.x) / 2f
                            kwhLinePath.cubicTo(midX, prev.y, midX, pt.y, pt.x, pt.y)
                            kwhAreaPath.cubicTo(midX, prev.y, midX, pt.y, pt.x, pt.y)
                        }
                    }
                    kwhAreaPath.lineTo(xFor(points.lastIndex), padTop + chartH)
                    kwhAreaPath.close()

                    drawPath(
                        path = kwhAreaPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(kwhColor.copy(alpha = 0.28f), kwhColor.copy(alpha = 0.0f)),
                            startY = padTop,
                            endY = padTop + chartH
                        )
                    )
                    drawPath(
                        path = kwhLinePath,
                        color = kwhColor,
                        style = Stroke(
                            width = 3.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }

                // Data nodes
                points.forEachIndexed { idx, p ->
                    val cx = xFor(idx)
                    val yKwh = yFor(p.totalKwh)
                    val yKvah = yFor(p.totalKvah)
                    val isSel = idx == clampedIndex

                    drawCircle(
                        color = kvahColor,
                        radius = if (isSel) 5.dp.toPx() else 3.dp.toPx(),
                        center = Offset(cx, yKvah)
                    )
                    if (isSel) {
                        drawCircle(
                            color = kwhColor.copy(alpha = 0.25f),
                            radius = 10.dp.toPx(),
                            center = Offset(cx, yKwh)
                        )
                    }
                    drawCircle(
                        color = kwhColor,
                        radius = if (isSel) 5.5.dp.toPx() else 3.5.dp.toPx(),
                        center = Offset(cx, yKwh)
                    )
                }
            }
        }

        // X-axis endpoints & Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = points.first().label,
                style = MaterialTheme.typography.labelSmall,
                color = textMuted
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendChip("Active kWh", kwhColor)
                LegendChip("Apparent kVAh", kvahColor)
            }
            Text(
                text = points.last().label,
                style = MaterialTheme.typography.labelSmall,
                color = textMuted
            )
        }
    }
}

/**
 * Daily Power Factor Efficiency Bar Chart with 0.95 target line.
 */
@Composable
fun PowerFactorHealthChart(
    points: List<DailyTrendPoint>,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) return

    val visiblePoints = remember(points) { points.takeLast(12) }
    val targetLineColor = GridEmerald
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
    val textMuted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val padLeft = 8.dp.toPx()
                val padRight = 8.dp.toPx()
                val padTop = 10.dp.toPx()
                val padBottom = 10.dp.toPx()
                val chartW = (size.width - padLeft - padRight).coerceAtLeast(1f)
                val chartH = (size.height - padTop - padBottom).coerceAtLeast(1f)

                // Scale 0.50 to 1.00 so variations in PF are visually clear
                val minPf = 0.50
                val maxPf = 1.00
                fun yForPf(pf: Double): Float {
                    val norm = ((pf.coerceIn(minPf, maxPf) - minPf) / (maxPf - minPf)).toFloat()
                    return padTop + chartH * (1f - norm)
                }

                // Baseline & 0.85 line
                val y085 = yForPf(0.85)
                val y095 = yForPf(0.95)
                drawLine(
                    color = gridColor,
                    start = Offset(padLeft, padTop + chartH),
                    end = Offset(size.width - padRight, padTop + chartH),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = HighVoltageAmber.copy(alpha = 0.4f),
                    start = Offset(padLeft, y085),
                    end = Offset(size.width - padRight, y085),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                )
                // 0.95 Utility Optimal Target Line
                drawLine(
                    color = targetLineColor.copy(alpha = 0.85f),
                    start = Offset(padLeft, y095),
                    end = Offset(size.width - padRight, y095),
                    strokeWidth = 1.6.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))
                )

                val slotW = chartW / visiblePoints.size.coerceAtLeast(1)
                val barW = (slotW * 0.52f).coerceIn(8.dp.toPx(), 26.dp.toPx())

                visiblePoints.forEachIndexed { idx, pt ->
                    val cx = padLeft + slotW * (idx + 0.5f)
                    val topY = yForPf(pt.avgPowerFactor)
                    val barH = (padTop + chartH - topY).coerceAtLeast(4.dp.toPx())
                    val color = pfHealthColor(pt.avgPowerFactor)

                    drawRoundRect(
                        color = color,
                        topLeft = Offset(cx - barW / 2f, topY),
                        size = Size(barW, barH),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendChip("Optimal ≥0.95", GridEmerald)
            LegendChip("Fair 0.85–0.94", HighVoltageAmber)
            LegendChip("Penalty <0.85", AlertCoral)
        }
        Text(
            text = "Dashed emerald line marks 0.95 utility target benchmark (${visiblePoints.first().label} – ${visiblePoints.last().label}).",
            style = MaterialTheme.typography.bodySmall,
            color = textMuted
        )
    }
}

/**
 * Horizontal ranked breakdown of consumption by customer.
 */
@Composable
fun CustomerBreakdownChart(
    metrics: List<CustomerSummaryMetric>,
    onCustomerClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val active = remember(metrics) { metrics.filter { it.readingCount > 0 }.take(6) }
    if (active.isEmpty()) return

    val maxKwh = active.maxOf { it.totalKwh }.coerceAtLeast(1.0)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        active.forEach { item ->
            val fraction = (item.totalKwh / maxKwh).toFloat().coerceIn(0.05f, 1f)
            val animatedFrac by animateFloatAsState(
                targetValue = fraction,
                animationSpec = spring(stiffness = 300f),
                label = "bar_fraction"
            )
            Surface(
                onClick = { onCustomerClick(item.customer.id) },
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.customer.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${item.customer.meterNumber}  •  ${item.readingCount} reading(s)",
                                style = MonoMetricSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${MeterMath.formatCompactEnergy(item.totalKwh)} kWh",
                                style = MonoMetricMedium,
                                color = ElectricCyan
                            )
                            Text(
                                text = "PF ${MeterMath.formatPf(item.avgPowerFactor)}",
                                style = MonoMetricSmall,
                                color = pfHealthColor(item.avgPowerFactor)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedFrac)
                                .height(8.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(ElectricCyan, LuminousCyan)
                                    )
                                )
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compact semicircular precision dial for Average Power Factor on the Dashboard hero card.
 */
@Composable
fun PowerFactorGauge(
    powerFactor: Double,
    modifier: Modifier = Modifier
) {
    val clamped = powerFactor.coerceIn(0.0, 1.0).toFloat()
    val animatedSweep by animateFloatAsState(
        targetValue = clamped * 180f,
        animationSpec = spring(stiffness = 220f),
        label = "pf_gauge"
    )
    val gaugeColor = if (powerFactor <= 0.0) Color(0xFF64748B) else pfHealthColor(powerFactor)

    Box(
        modifier = modifier.size(width = 104.dp, height = 64.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeW = 9.dp.toPx()
            val arcSize = Size(size.width - strokeW, (size.width - strokeW))
            val topLeft = Offset(strokeW / 2f, strokeW / 2f)

            drawArc(
                color = Color.White.copy(alpha = 0.14f),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )
            drawArc(
                color = gaugeColor,
                startAngle = 180f,
                sweepAngle = animatedSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (powerFactor <= 0.0) "--" else MeterMath.formatPf(powerFactor),
                style = MonoMetricMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "AVG PF",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF94A3B8)
            )
        }
    }
}

fun pfHealthColor(pf: Double): Color = when {
    pf >= 0.95 -> BrightEmerald
    pf >= 0.85 -> BrightAmber
    else -> AlertCoral
}

fun pfHealthLabel(pf: Double): String = when {
    pf >= 0.95 -> "Optimal"
    pf >= 0.85 -> "Fair"
    else -> "Low PF"
}

@Composable
private fun ChartMetricTag(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MonoMetricSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun LegendChip(label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
