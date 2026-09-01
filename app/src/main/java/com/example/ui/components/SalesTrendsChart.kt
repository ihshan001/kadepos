package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SaleEntity
import com.example.ui.theme.*
import com.example.ui.util.CurrencyUtils
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

enum class ChartPeriod {
    DAILY_7_DAYS,
    WEEKLY_4_WEEKS
}

data class ChartDataPoint(
    val label: String,
    val fullDateLabel: String,
    val amount: Double,
    val orderCount: Int,
    val isPeak: Boolean = false
)

@Composable
fun SalesTrendsChart(
    sales: List<SaleEntity>,
    modifier: Modifier = Modifier,
    initialPeriod: ChartPeriod = ChartPeriod.DAILY_7_DAYS,
    title: String = "Sales Trends & Growth",
    showSummaryCards: Boolean = true
) {
    var period by remember { mutableStateOf(initialPeriod) }
    var selectedPointIndex by remember { mutableStateOf<Int?>(null) }

    // Aggregate data based on period
    val validSales = remember(sales) { sales.filter { it.status != "VOID" } }

    val dataPoints: List<ChartDataPoint> = remember(validSales, period) {
        val calendar = Calendar.getInstance()
        val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
        val fullDateFormat = SimpleDateFormat("dd MMM, yyyy", Locale.getDefault())
        val weekFormat = SimpleDateFormat("dd MMM", Locale.getDefault())

        when (period) {
            ChartPeriod.DAILY_7_DAYS -> {
                // Generate last 7 days
                val list = mutableListOf<ChartDataPoint>()
                val daysSales = mutableMapOf<String, Pair<Double, Int>>()

                for (i in 6 downTo 0) {
                    val cal = Calendar.getInstance()
                    cal.add(Calendar.DAY_OF_YEAR, -i)
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    val startOfDay = cal.timeInMillis
                    cal.set(Calendar.HOUR_OF_DAY, 23)
                    cal.set(Calendar.MINUTE, 59)
                    cal.set(Calendar.SECOND, 59)
                    cal.set(Calendar.MILLISECOND, 999)
                    val endOfDay = cal.timeInMillis

                    val matchingSales = validSales.filter { it.timestamp in startOfDay..endOfDay }
                    val total = matchingSales.sumOf { it.totalAmount }
                    val count = matchingSales.size

                    val label = if (i == 0) "Today" else dayFormat.format(Date(startOfDay))
                    val fullDate = fullDateFormat.format(Date(startOfDay))
                    list.add(ChartDataPoint(label = label, fullDateLabel = fullDate, amount = total, orderCount = count))
                }

                val maxAmt = list.maxOfOrNull { it.amount } ?: 0.0
                list.map { it.copy(isPeak = it.amount > 0 && it.amount == maxAmt) }
            }
            ChartPeriod.WEEKLY_4_WEEKS -> {
                // Generate last 4 weeks
                val list = mutableListOf<ChartDataPoint>()
                for (w in 3 downTo 0) {
                    val calStart = Calendar.getInstance()
                    calStart.add(Calendar.DAY_OF_YEAR, -(w * 7 + 6))
                    calStart.set(Calendar.HOUR_OF_DAY, 0)
                    calStart.set(Calendar.MINUTE, 0)
                    calStart.set(Calendar.SECOND, 0)
                    val startTime = calStart.timeInMillis

                    val calEnd = Calendar.getInstance()
                    calEnd.add(Calendar.DAY_OF_YEAR, -(w * 7))
                    calEnd.set(Calendar.HOUR_OF_DAY, 23)
                    calEnd.set(Calendar.MINUTE, 59)
                    calEnd.set(Calendar.SECOND, 59)
                    val endTime = calEnd.timeInMillis

                    val matchingSales = validSales.filter { it.timestamp in startTime..endTime }
                    val total = matchingSales.sumOf { it.totalAmount }
                    val count = matchingSales.size

                    val label = if (w == 0) "This Wk" else "Wk -${w}"
                    val fullDate = "${weekFormat.format(Date(startTime))} - ${weekFormat.format(Date(endTime))}"
                    list.add(ChartDataPoint(label = label, fullDateLabel = fullDate, amount = total, orderCount = count))
                }
                val maxAmt = list.maxOfOrNull { it.amount } ?: 0.0
                list.map { it.copy(isPeak = it.amount > 0 && it.amount == maxAmt) }
            }
        }
    }

    val totalPeriodSales = remember(dataPoints) { dataPoints.sumOf { it.amount } }
    val totalPeriodOrders = remember(dataPoints) { dataPoints.sumOf { it.orderCount } }
    val averageSales = remember(dataPoints) { if (dataPoints.isNotEmpty()) totalPeriodSales / dataPoints.size else 0.0 }
    val maxSales = remember(dataPoints) { dataPoints.maxOfOrNull { it.amount } ?: 0.0 }
    val peakPoint = remember(dataPoints) { dataPoints.maxByOrNull { it.amount } }

    // Growth comparison calculation
    val growthRate = remember(dataPoints) {
        if (dataPoints.size >= 4) {
            val half = dataPoints.size / 2
            val recentSum = dataPoints.takeLast(half).sumOf { it.amount }
            val olderSum = dataPoints.take(half).sumOf { it.amount }
            if (olderSum > 0) {
                ((recentSum - olderSum) / olderSum) * 100.0
            } else if (recentSum > 0) {
                100.0
            } else {
                0.0
            }
        } else {
            0.0
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = LightSurface),
        border = CardDefaults.outlinedCardBorder(),
        modifier = modifier.fillMaxWidth().testTag("sales_trends_chart_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Title & Period Switcher
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.TrendingUp,
                            contentDescription = null,
                            tint = BrandPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextPrimary
                        )
                    }
                    Text(
                        if (period == ChartPeriod.DAILY_7_DAYS) "Daily performance (Last 7 days)" else "Weekly breakdown (Last 4 weeks)",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }

                // Period Toggle Chips
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BrandSurface,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp))
                ) {
                    Row(modifier = Modifier.padding(2.dp)) {
                        PeriodToggleButton(
                            label = "7 Days",
                            isSelected = period == ChartPeriod.DAILY_7_DAYS,
                            onClick = {
                                period = ChartPeriod.DAILY_7_DAYS
                                selectedPointIndex = null
                            }
                        )
                        PeriodToggleButton(
                            label = "4 Weeks",
                            isSelected = period == ChartPeriod.WEEKLY_4_WEEKS,
                            onClick = {
                                period = ChartPeriod.WEEKLY_4_WEEKS
                                selectedPointIndex = null
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Main KPI & Growth Banner
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        "REVENUE IN PERIOD",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        CurrencyUtils.formatLkr(totalPeriodSales),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = BrandPrimary
                    )
                }

                // Growth Badge
                if (growthRate != 0.0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (growthRate > 0) StatusGreenBg else StatusRedBg
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                if (growthRate > 0) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                contentDescription = null,
                                tint = if (growthRate > 0) StatusGreen else StatusRed,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                String.format(Locale.US, "%+.1f%% vs Prev", growthRate),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (growthRate > 0) StatusGreen else StatusRed
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Selected Data Point Tooltip (if tapped)
            val selectedPoint = selectedPointIndex?.let { if (it in dataPoints.indices) dataPoints[it] else null }
            AnimatedVisibility(visible = selectedPoint != null) {
                selectedPoint?.let { pt ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BrandSurface,
                        border = CardDefaults.outlinedCardBorder(),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(pt.fullDateLabel, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                Text("${pt.orderCount} ${if (pt.orderCount == 1) "bill completed" else "bills completed"}", fontSize = 11.sp, color = TextSecondary)
                            }
                            Text(
                                CurrencyUtils.formatLkr(pt.amount),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = BrandPrimary
                            )
                        }
                    }
                }
            }

            // Interactive Compose Canvas Chart
            val textMeasurer = rememberTextMeasurer()
            val animatedProgress by animateFloatAsState(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
                label = "chart_bars_anim"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .pointerInput(dataPoints) {
                        detectTapGestures { offset ->
                            val width = size.width
                            val step = width / dataPoints.size
                            val tappedIndex = (offset.x / step).toInt().coerceIn(0, dataPoints.lastIndex)
                            selectedPointIndex = if (selectedPointIndex == tappedIndex) null else tappedIndex
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height
                    val bottomPadding = 24.dp.toPx()
                    val chartHeight = canvasHeight - bottomPadding
                    val count = dataPoints.size
                    if (count == 0) return@Canvas

                    val maxVal = if (maxSales > 0) maxSales * 1.15 else 1000.0
                    val barSpacing = 12.dp.toPx()
                    val totalSpacing = barSpacing * (count + 1)
                    val barWidth = ((canvasWidth - totalSpacing) / count).coerceAtLeast(14.dp.toPx())

                    // Draw 3 horizontal grid lines (0%, 50%, 100%)
                    val dashPathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                    val gridSteps = listOf(0f, 0.5f, 1f)
                    for (stepRatio in gridSteps) {
                        val y = chartHeight - (chartHeight * stepRatio)
                        drawLine(
                            color = BrandSurface.copy(alpha=0.9f),
                            start = Offset(0f, y),
                            end = Offset(canvasWidth, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = dashPathEffect
                        )
                    }

                    // Prepare Points for Bezier Trend Curve
                    val pointOffsets = mutableListOf<Offset>()

                    // Draw Bars & Record Trend Line Points
                    dataPoints.forEachIndexed { index, point ->
                        val barHeight = ((point.amount / maxVal) * chartHeight * animatedProgress).toFloat().coerceAtLeast(4.dp.toPx())
                        val x = barSpacing + index * (barWidth + barSpacing)
                        val y = chartHeight - barHeight
                        val centerX = x + barWidth / 2

                        pointOffsets.add(Offset(centerX, y))

                        val isSelected = selectedPointIndex == index
                        val isPeak = point.isPeak

                        // Determine bar gradient
                        val barBrush = if (isSelected) {
                            Brush.verticalGradient(
                                colors = listOf(BrandPrimaryDark, BrandPrimary),
                                startY = y,
                                endY = chartHeight
                            )
                        } else if (isPeak) {
                            Brush.verticalGradient(
                                colors = listOf(BrandPrimary, BrandSurface),
                                startY = y,
                                endY = chartHeight
                            )
                        } else {
                            Brush.verticalGradient(
                                colors = listOf(BrandPrimary.copy(alpha = 0.75f), BrandSurface.copy(alpha = 0.4f)),
                                startY = y,
                                endY = chartHeight
                            )
                        }

                        // Draw Bar
                        drawRoundRect(
                            brush = barBrush,
                            topLeft = Offset(x, y),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                        )

                        // If selected, draw highlight outline
                        if (isSelected) {
                            drawRoundRect(
                                color = BrandPrimaryDark,
                                topLeft = Offset(x - 1.5.dp.toPx(), y - 1.5.dp.toPx()),
                                size = Size(barWidth + 3.dp.toPx(), barHeight + 3.dp.toPx()),
                                cornerRadius = CornerRadius(7.dp.toPx(), 7.dp.toPx()),
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }

                        // Draw X-axis label
                        val textStyle = TextStyle(
                            color = if (isSelected) BrandPrimaryDark else TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected || isPeak) FontWeight.Bold else FontWeight.Normal
                        )
                        val textLayoutResult = textMeasurer.measure(point.label, textStyle)
                        val textX = centerX - (textLayoutResult.size.width / 2f)
                        val textY = chartHeight + 6.dp.toPx()
                        drawText(
                            textMeasurer = textMeasurer,
                            text = point.label,
                            topLeft = Offset(textX, textY),
                            style = textStyle
                        )
                    }

                    // Draw Smooth Bezier Trendline Over Bars
                    if (pointOffsets.size > 1) {
                        val trendPath = Path()
                        trendPath.moveTo(pointOffsets[0].x, pointOffsets[0].y)

                        for (i in 0 until pointOffsets.size - 1) {
                            val p0 = pointOffsets[i]
                            val p1 = pointOffsets[i + 1]
                            val controlX1 = (p0.x + p1.x) / 2
                            val controlY1 = p0.y
                            val controlX2 = (p0.x + p1.x) / 2
                            val controlY2 = p1.y
                            trendPath.cubicTo(controlX1, controlY1, controlX2, controlY2, p1.x, p1.y)
                        }

                        // Draw trend line
                        drawPath(
                            path = trendPath,
                            color = BrandPrimaryDark.copy(alpha = 0.85f),
                            style = Stroke(
                                width = 2.5.dp.toPx(),
                                pathEffect = PathEffect.cornerPathEffect(8f)
                            )
                        )

                        // Draw node circles
                        pointOffsets.forEachIndexed { idx, pt ->
                            val isSel = selectedPointIndex == idx
                            drawCircle(
                                color = BrandSurface,
                                radius = if (isSel) 6.dp.toPx() else 4.dp.toPx(),
                                center = pt
                            )
                            drawCircle(
                                color = if (isSel) StatusAmber else BrandPrimaryDark,
                                radius = if (isSel) 4.dp.toPx() else 2.5.dp.toPx(),
                                center = pt
                            )
                        }
                    }
                }
            }

            if (showSummaryCards) {
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = LightBorder)
                Spacer(modifier = Modifier.height(10.dp))

                // Mini Stat Counters
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MiniStatItem(
                        label = "Daily Avg",
                        value = CurrencyUtils.formatLkr(averageSales)
                    )
                    MiniStatItem(
                        label = "Peak Day",
                        value = if (peakPoint != null && peakPoint.amount > 0) "${peakPoint.label} (${CurrencyUtils.formatLkr(peakPoint.amount)})" else "—"
                    )
                    MiniStatItem(
                        label = "Orders",
                        value = "$totalPeriodOrders bills"
                    )
                }
            }
        }
    }
}

@Composable
private fun PeriodToggleButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) BrandPrimary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) BrandOnPrimary else TextSecondary
        )
    }
}

@Composable
private fun MiniStatItem(
    label: String,
    value: String
) {
    Column {
        Text(label, fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Medium)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
    }
}
