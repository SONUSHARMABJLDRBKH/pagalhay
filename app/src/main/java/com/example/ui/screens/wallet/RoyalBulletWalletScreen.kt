package com.example.ui.screens.wallet

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.RechargeEntity
import com.example.data.local.UserEntity
import com.example.data.local.WithdrawalEntity
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.RoyalCard
import com.example.ui.components.RoyalTopBar
import com.example.ui.components.StatusPill
import com.example.ui.components.formatInr
import com.example.ui.components.formatTimestamp
import com.example.ui.theme.ActionRedBright
import com.example.ui.theme.ActionRedPrimary
import com.example.ui.theme.IceBackground
import com.example.ui.theme.RoyalBlueDeep
import com.example.ui.theme.RoyalBluePrimary
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.max
import kotlin.math.min

private val MarketDarkBgTop = Color(0xFF0B1220)
private val MarketDarkBgBottom = Color(0xFF172033)
private val MarketYellowUp = Color(0xFFFACC15)
private val MarketYellowSoft = Color(0xFFFEF08A)
private val MarketRedDown = Color(0xFFEF4444)

data class WalletMarketCandle(
    val open: Double,
    val close: Double,
    val high: Double,
    val low: Double,
    val delta: Double,
    val isUp: Boolean,
    val title: String,
    val timestamp: Long,
    val status: String,
    val isRealTx: Boolean
)

fun buildWalletMarketCandles(
    recharges: List<RechargeEntity>,
    withdrawals: List<WithdrawalEntity>
): List<WalletMarketCandle> {
    data class RawTx(
        val amount: Double,
        val isUp: Boolean,
        val title: String,
        val timestamp: Long,
        val status: String
    )

    val txs = mutableListOf<RawTx>()
    recharges.filter { it.status == "Successful" }.forEach { rec ->
        txs.add(
            RawTx(
                amount = rec.amount,
                isUp = true,
                title = "Deposit (${rec.method})",
                timestamp = rec.verifiedAt ?: rec.createdAt,
                status = rec.status
            )
        )
    }
    withdrawals.filter { it.status != "Rejected" }.forEach { w ->
        txs.add(
            RawTx(
                amount = w.amount,
                isUp = false,
                title = "Withdrawal (${w.payoutMethod})",
                timestamp = w.processedAt ?: w.createdAt,
                status = w.status
            )
        )
    }

    val sorted = txs.sortedBy { it.timestamp }
    val result = mutableListOf<WalletMarketCandle>()
    var running = 0.0

    for (tx in sorted) {
        val open = running
        val close = if (tx.isUp) open + tx.amount else (open - tx.amount).coerceAtLeast(0.0)
        val spread = max(tx.amount * 0.12, 40.0)
        val high = max(open, close) + spread
        val low = (min(open, close) - spread * 0.6).coerceAtLeast(0.0)
        result.add(
            WalletMarketCandle(
                open = open,
                close = close,
                high = high,
                low = low,
                delta = if (tx.isUp) tx.amount else -tx.amount,
                isUp = tx.isUp,
                title = tx.title,
                timestamp = tx.timestamp,
                status = tx.status,
                isRealTx = true
            )
        )
        running = close
    }

    if (result.size < 6) {
        // Prepend subtle baseline candles before real transactions so the share-market chart has visual context
        val baselineDeltas = listOf(350.0, -140.0, 480.0, -210.0, 290.0, -110.0)
        val needed = 6 - result.size
        val prefix = mutableListOf<WalletMarketCandle>()
        var sim = 200.0
        for (i in 0 until needed) {
            val d = baselineDeltas[i % baselineDeltas.size]
            val isUp = d >= 0.0
            val open = sim
            val close = (open + d).coerceAtLeast(50.0)
            prefix.add(
                WalletMarketCandle(
                    open = open,
                    close = close,
                    high = max(open, close) + 60.0,
                    low = (min(open, close) - 45.0).coerceAtLeast(0.0),
                    delta = d,
                    isUp = isUp,
                    title = if (isUp) "Market Index Up" else "Market Index Pullback",
                    timestamp = 0L,
                    status = "Index",
                    isRealTx = false
                )
            )
            sim = close
        }
        return prefix + result
    }

    return result.takeLast(18)
}

@Composable
fun RoyalBulletWalletMarketCard(
    user: UserEntity,
    allRecharges: List<RechargeEntity>,
    allWithdrawals: List<WithdrawalEntity>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalDeposit = remember(allRecharges) {
        allRecharges.filter { it.status == "Successful" }.sumOf { it.amount }
    }
    val totalWithdrawal = remember(allWithdrawals) {
        allWithdrawals.filter { it.status != "Rejected" }.sumOf { it.amount }
    }
    val candles = remember(allRecharges, allWithdrawals) {
        buildWalletMarketCandles(allRecharges, allWithdrawals)
    }
    val latestReal = remember(candles) { candles.lastOrNull { it.isRealTx } }
    val isLastUp = latestReal?.isUp ?: (totalDeposit >= totalWithdrawal)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(MarketDarkBgTop, MarketDarkBgBottom)))
            .border(1.dp, MarketYellowUp.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
            .testTag("royal_bullet_wallet_card")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MarketYellowUp.copy(alpha = 0.16f))
                            .border(1.dp, MarketYellowUp.copy(alpha = 0.55f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ShowChart,
                            contentDescription = "Royal Bullet 350 Wallet Chart",
                            tint = MarketYellowUp,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Royal Bullet 350 Wallet",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Wallet: ${formatInr(user.depositBalance)} • Live Market Graph",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.72f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    color = if (isLastUp) MarketYellowUp.copy(alpha = 0.2f) else MarketRedDown.copy(alpha = 0.22f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.border(
                        1.dp,
                        if (isLastUp) MarketYellowUp else MarketRedDown,
                        RoundedCornerShape(50)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isLastUp) {
                                Icons.AutoMirrored.Filled.TrendingUp
                            } else {
                                Icons.AutoMirrored.Filled.TrendingDown
                            },
                            contentDescription = null,
                            tint = if (isLastUp) MarketYellowUp else MarketRedDown,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (latestReal != null) {
                                if (latestReal.isUp) "+₹${latestReal.delta.toInt()}" else "-₹${(-latestReal.delta).toInt()}"
                            } else {
                                "LIVE"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (isLastUp) MarketYellowUp else MarketRedDown
                        )
                    }
                }
            }

            StockMarketYellowRedChart(
                candles = candles,
                height = 128.dp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(MarketYellowUp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Deposit ${formatInr(totalDeposit)}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MarketYellowSoft
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(MarketRedDown)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Withdraw ${formatInr(totalWithdrawal)}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFFCA5A5)
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Filled.ArrowForward,
                    contentDescription = "Open Royal Bullet 350 Wallet",
                    tint = MarketYellowUp,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun StockMarketYellowRedChart(
    candles: List<WalletMarketCandle>,
    height: Dp = 180.dp,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF070D19))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 12.dp)
    ) {
        val count = candles.size
        if (count == 0) return@Canvas

        val minVal = candles.minOf { it.low }
        val maxVal = candles.maxOf { it.high }
        val range = (maxVal - minVal).coerceAtLeast(100.0)

        fun yFor(value: Double): Float {
            val ratio = ((value - minVal) / range).toFloat().coerceIn(0f, 1f)
            return size.height - ratio * size.height
        }

        // Subtle stock-chart dashed grid lines
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
        for (i in 1..3) {
            val y = size.height * (i / 4f)
            drawLine(
                color = Color.White.copy(alpha = 0.08f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dashEffect
            )
        }

        val stepX = size.width / count.toFloat()
        val bodyWidth = (stepX * 0.52f).coerceIn(6.dp.toPx(), 20.dp.toPx())

        // Draw Yellow/Red Candlesticks + Wicks
        val linePoints = mutableListOf<Pair<Offset, Boolean>>()
        candles.forEachIndexed { idx, candle ->
            val centerX = stepX * idx + stepX / 2f
            val candleColor = if (candle.isUp) MarketYellowUp else MarketRedDown
            val wickColor = if (candle.isUp) MarketYellowSoft else Color(0xFFFCA5A5)

            val yHigh = yFor(candle.high)
            val yLow = yFor(candle.low)
            val yOpen = yFor(candle.open)
            val yClose = yFor(candle.close)

            drawLine(
                color = wickColor.copy(alpha = if (candle.isRealTx) 0.95f else 0.45f),
                start = Offset(centerX, yHigh),
                end = Offset(centerX, yLow),
                strokeWidth = 1.8.dp.toPx(),
                cap = StrokeCap.Round
            )

            val top = min(yOpen, yClose)
            val rectHeight = max(kotlin.math.abs(yClose - yOpen), 5.dp.toPx())
            drawRoundRect(
                color = candleColor.copy(alpha = if (candle.isRealTx) 1f else 0.55f),
                topLeft = Offset(centerX - bodyWidth / 2f, top),
                size = Size(bodyWidth, rectHeight),
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
            )

            linePoints.add(Offset(centerX, yClose) to candle.isUp)
        }

        // Draw crisp Yellow (Up) / Red (Down) trend segments connecting candle closes
        for (i in 0 until linePoints.size - 1) {
            val (p1, _) = linePoints[i]
            val (p2, nextIsUp) = linePoints[i + 1]
            val segColor = if (nextIsUp) MarketYellowUp else MarketRedDown
            drawLine(
                color = segColor,
                start = p1,
                end = p2,
                strokeWidth = 2.4.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // Draw continuous subtle area under line for market depth
        if (linePoints.size >= 2) {
            val areaPath = Path().apply {
                moveTo(linePoints.first().first.x, size.height)
                linePoints.forEach { (pt, _) -> lineTo(pt.x, pt.y) }
                lineTo(linePoints.last().first.x, size.height)
                close()
            }
            val latestColor = if (candles.last().isUp) MarketYellowUp else MarketRedDown
            drawPath(
                path = areaPath,
                brush = Brush.verticalGradient(
                    listOf(latestColor.copy(alpha = 0.22f), Color.Transparent)
                )
            )
            // Highlight latest candle close dot
            val lastPt = linePoints.last().first
            drawCircle(
                color = latestColor.copy(alpha = 0.35f),
                radius = 7.dp.toPx(),
                center = lastPt
            )
            drawCircle(
                color = latestColor,
                radius = 3.8.dp.toPx(),
                center = lastPt
            )
        }
    }
}

@Composable
fun RoyalBulletWalletScreen(
    user: UserEntity?,
    allRecharges: List<RechargeEntity>,
    allWithdrawals: List<WithdrawalEntity>,
    onBack: () -> Unit,
    onOpenRecharge: () -> Unit,
    onOpenWithdraw: () -> Unit
) {
    val totalDeposit = remember(allRecharges) {
        allRecharges.filter { it.status == "Successful" }.sumOf { it.amount }
    }
    val depositCount = remember(allRecharges) {
        allRecharges.count { it.status == "Successful" }
    }
    val totalWithdrawal = remember(allWithdrawals) {
        allWithdrawals.filter { it.status != "Rejected" }.sumOf { it.amount }
    }
    val withdrawalCount = remember(allWithdrawals) {
        allWithdrawals.count { it.status != "Rejected" }
    }
    val candles = remember(allRecharges, allWithdrawals) {
        buildWalletMarketCandles(allRecharges, allWithdrawals)
    }
    val realMoves = remember(candles) {
        candles.filter { it.isRealTx }.reversed()
    }
    val latestReal = realMoves.firstOrNull()
    val isLastUp = latestReal?.isUp ?: (totalDeposit >= totalWithdrawal)

    Scaffold(
        topBar = { RoyalTopBar(title = "Royal Bullet 350 Wallet", onBack = onBack) },
        containerColor = IceBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Total Deposit & Total Withdrawal (shown immediately on click)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        color = MarketDarkBgTop,
                        shape = RoundedCornerShape(18.dp),
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .weight(1f)
                            .border(1.5.dp, MarketYellowUp.copy(alpha = 0.65f), RoundedCornerShape(18.dp))
                            .testTag("royal_wallet_total_deposit")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                    contentDescription = "Total Deposit",
                                    tint = MarketYellowUp,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Total Deposit",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MarketYellowSoft
                                )
                            }
                            Text(
                                text = formatInr(totalDeposit),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = MarketYellowUp
                            )
                            Text(
                                text = "$depositCount verified deposits",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.72f)
                            )
                        }
                    }

                    Surface(
                        color = MarketDarkBgTop,
                        shape = RoundedCornerShape(18.dp),
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .weight(1f)
                            .border(1.5.dp, MarketRedDown.copy(alpha = 0.65f), RoundedCornerShape(18.dp))
                            .testTag("royal_wallet_total_withdrawal")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.TrendingDown,
                                    contentDescription = "Total Withdrawal",
                                    tint = MarketRedDown,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Total Withdrawal",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFFFCA5A5)
                                )
                            }
                            Text(
                                text = formatInr(totalWithdrawal),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = MarketRedDown
                            )
                            Text(
                                text = "$withdrawalCount withdrawals",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.72f)
                            )
                        }
                    }
                }
            }

            // 2. Your Instant Wallet Card + Quick Recharge / Withdraw
            item {
                RoyalCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Your Wallet Balance",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = TextSecondary
                                )
                                Text(
                                    text = formatInr(user?.depositBalance ?: 0.0),
                                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                    color = RoyalBlueDeep
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Account Balance",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                                Text(
                                    text = formatInr(user?.availableBalance ?: 0.0),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = RoyalBluePrimary
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onOpenRecharge,
                                colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Recharge", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = onOpenWithdraw,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Withdraw", color = ActionRedPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 3. Full Share-Market Style Red & Yellow Up/Down Graph
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(10.dp, RoundedCornerShape(20.dp))
                        .clip(RoundedCornerShape(20.dp))
                        .background(Brush.verticalGradient(listOf(MarketDarkBgTop, MarketDarkBgBottom)))
                        .border(1.dp, MarketYellowUp.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Live Wallet Index Graph",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                                Text(
                                    text = "Yellow = Graph Up (Deposit) • Red = Graph Down (Withdraw)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.72f)
                                )
                            }
                            Surface(
                                color = if (isLastUp) MarketYellowUp.copy(alpha = 0.2f) else MarketRedDown.copy(alpha = 0.22f),
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.border(
                                    1.dp,
                                    if (isLastUp) MarketYellowUp else MarketRedDown,
                                    RoundedCornerShape(50)
                                )
                            ) {
                                Text(
                                    text = if (isLastUp) "▲ GRAPH UP" else "▼ GRAPH DOWN",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (isLastUp) MarketYellowUp else MarketRedDown,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        StockMarketYellowRedChart(
                            candles = candles,
                            height = 220.dp,
                            modifier = Modifier.testTag("royal_wallet_full_chart")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(MarketYellowUp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Graph Up (Deposit)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MarketYellowSoft
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(MarketRedDown)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Graph Down (Withdraw)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFFCA5A5)
                                )
                            }
                        }
                    }
                }
            }

            // 4. Live Movement Ledger (All Recharges & Withdrawals)
            item {
                Text(
                    text = "Live Deposit & Withdrawal Movements",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
            }

            if (realMoves.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Filled.AccountBalanceWallet,
                        title = "No Live Transactions Yet",
                        subtitle = "Recharge ₹700+ or place a withdrawal to see live Yellow (Up) & Red (Down) spikes on the graph."
                    )
                }
            } else {
                items(realMoves) { move ->
                    RoyalCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (move.isUp) MarketYellowUp.copy(alpha = 0.2f)
                                            else MarketRedDown.copy(alpha = 0.16f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (move.isUp) {
                                            Icons.AutoMirrored.Filled.TrendingUp
                                        } else {
                                            Icons.AutoMirrored.Filled.TrendingDown
                                        },
                                        contentDescription = null,
                                        tint = if (move.isUp) Color(0xFFCA8A04) else ActionRedBright,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(move.title, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = if (move.timestamp > 0L) formatTimestamp(move.timestamp) else "Live",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextMuted
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = if (move.isUp) "+${formatInr(move.delta)}" else "-${formatInr(-move.delta)}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (move.isUp) Color(0xFFCA8A04) else ActionRedPrimary
                                )
                                StatusPill(move.status)
                            }
                        }
                    }
                }
            }
        }
    }
}
