package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.hardware.StandaloneExporter
import com.example.i18n.AppLang
import com.example.i18n.formatNumber
import com.example.i18n.formatSar
import com.example.i18n.stringsFor
import com.example.model.*
import com.example.ui.theme.StatusDanger
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SalesHistoryScreen(
    lang: AppLang,
    orders: List<PosOrder>,
    refunds: List<RefundRecord>,
    shiftHistory: List<ShiftSession> = emptyList(),
    canProcessRefunds: Boolean,
    onBack: () -> Unit,
    onViewReceipt: (PosOrder) -> Unit,
    onViewShiftMemo: (ShiftSession) -> Unit = {},
    onProcessRefund: (order: PosOrder, amount: Double, method: RefundMethod, reason: String) -> Unit
) {
    val s = stringsFor(lang)
    BackHandler(onBack = onBack)

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Invoices, 1 = Refunds, 2 = Shift Z-Reports
    var refundTarget by remember { mutableStateOf<PosOrder?>(null) }
    val completedOrders = remember(orders) {
        orders.filter { it.status in listOf(OrderStatus.COMPLETED, OrderStatus.PARTIALLY_REFUNDED, OrderStatus.REFUNDED) }
    }
    val closedShifts = remember(shiftHistory) {
        shiftHistory.filter { !it.isOpen }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.close)
                }
                Text(s.salesHistory, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    label = { Text("Invoices (${completedOrders.size})") }
                )
                FilterChip(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    label = { Text("Refunds (${refunds.size})") }
                )
                FilterChip(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    label = { Text("Shift Memos (${closedShifts.size})") }
                )
            }
        }

        val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

        if (selectedTab == 0) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 360.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(completedOrders, key = { it.id }) { order ->
                    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("${order.orderNumber} • ${order.sourceDisplayName(lang)}", fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "${df.format(Date(order.createdAt))} • ${order.cashierName}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = formatSar(order.grandTotal, lang),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (order.refundedAmount > 0.001) {
                                        Text(
                                            text = "Refunded: ${formatSar(order.refundedAmount, lang)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = StatusDanger
                                        )
                                    }
                                }
                            }

                            // Payment bucket breakdown badge
                            val p = order.payment
                            val breakdownText = buildList {
                                if (p.cashAmount > 0.001) add("${s.cash}: ${formatNumber(p.cashAmount)}")
                                if (p.madaAmount > 0.001) add("${s.mada}: ${formatNumber(p.madaAmount)}")
                                if (p.visaAmount > 0.001) add("${s.visa}: ${formatNumber(p.visaAmount)}")
                                if (p.onlineAmount > 0.001) add("${s.onlinePlatform}: ${formatNumber(p.onlineAmount)}")
                                if (p.dueAmount > 0.001) add("${s.creditDue}: ${formatNumber(p.dueAmount)}")
                            }.joinToString(" | ")

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = breakdownText,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { onViewReceipt(order) }) {
                                    Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Receipt")
                                }
                                if (canProcessRefunds && order.status != OrderStatus.REFUNDED) {
                                    OutlinedButton(
                                        onClick = { refundTarget = order },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusDanger),
                                        modifier = Modifier.testTag("refund_order_${order.id}")
                                    ) {
                                        Icon(Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(s.refundOrder)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else if (selectedTab == 1) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 360.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(refunds, key = { it.id }) { ref ->
                    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${ref.orderNumber} • ${ref.method.name}", fontWeight = FontWeight.Bold)
                                Text("-${formatSar(ref.amount, lang)}", fontWeight = FontWeight.ExtraBold, color = StatusDanger)
                            }
                            Text(ref.reason, style = MaterialTheme.typography.bodySmall)
                            Text(df.format(Date(ref.createdAt)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 360.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(closedShifts, key = { it.id }) { sh ->
                    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text(sh.cashierName, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "${df.format(Date(sh.openedAt))} → ${df.format(Date(sh.closedAt ?: sh.openedAt))}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = formatSar(sh.countedCash, lang),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = "Start Cash: ${formatNumber(sh.openingFloat)} SAR • Closing Cash: ${formatNumber(sh.countedCash)} SAR",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Expected: ${formatNumber(sh.expectedCash)} SAR • Variance: ${if (sh.variance >= 0) "+" else ""}${formatNumber(sh.variance)} SAR",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (sh.variance < -0.01) StatusDanger else StatusSuccess
                            )
                            OutlinedButton(onClick = { onViewShiftMemo(sh) }) {
                                Icon(Icons.Default.Summarize, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(s.shiftSummaryMemoTitle)
                            }
                        }
                    }
                }
            }
        }
    }

    refundTarget?.let { order ->
        RefundDialog(
            lang = lang,
            order = order,
            onDismiss = { refundTarget = null },
            onConfirm = { amount, method, reason ->
                onProcessRefund(order, amount, method, reason)
                refundTarget = null
            }
        )
    }
}

@Composable
private fun RefundDialog(
    lang: AppLang,
    order: PosOrder,
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, method: RefundMethod, reason: String) -> Unit
) {
    val s = stringsFor(lang)
    val remainingRefundable = (order.grandTotal - order.refundedAmount).coerceAtLeast(0.0)
    var isPartial by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf(formatNumber(remainingRefundable)) }
    var method by remember { mutableStateOf(RefundMethod.CASH) }
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${s.refundOrder} — ${order.orderNumber}", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !isPartial,
                        onClick = {
                            isPartial = false
                            amountText = formatNumber(remainingRefundable)
                        },
                        label = { Text(s.fullRefund) }
                    )
                    FilterChip(
                        selected = isPartial,
                        onClick = { isPartial = true },
                        label = { Text(s.partialRefund) }
                    )
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) amountText = it },
                    enabled = isPartial,
                    label = { Text("Max ${formatSar(remainingRefundable, lang)}") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                Text(s.refundMethod, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RefundMethod.entries.forEach { m ->
                        FilterChip(
                            selected = method == m,
                            onClick = { method = m },
                            label = { Text(m.name) }
                        )
                    }
                }
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text(s.refundReason) },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = StatusDanger),
                onClick = {
                    val amt = amountText.toDoubleOrNull() ?: remainingRefundable
                    onConfirm(amt.coerceIn(0.01, remainingRefundable), method, reason)
                }
            ) { Text(s.processRefund) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } }
    )
}

@Composable
fun LiveShiftSummaryDialog(
    lang: AppLang,
    metrics: ShiftSummaryMetrics,
    onDismiss: () -> Unit,
    onEditOpeningFloat: (() -> Unit)? = null
) {
    val s = stringsFor(lang)
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 520.dp),
        title = { Text(s.liveShiftSummary, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MetricRow("Orders", "${metrics.orderCount}")
                MetricRow(s.cashSales, formatSar(metrics.cashSales, lang))
                MetricRow(s.madaSales, formatSar(metrics.madaSales, lang))
                MetricRow(s.visaSales, formatSar(metrics.visaSales, lang))
                MetricRow(s.onlineSales, formatSar(metrics.onlineSales, lang))
                MetricRow(s.dueSales, formatSar(metrics.dueSales, lang))
                HorizontalDivider()
                MetricRow(s.vat15, formatSar(metrics.totalVat, lang))
                MetricRow(s.cashRefunds, "-${formatSar(metrics.cashRefunds, lang)}", StatusDanger)
                MetricRow(s.totalRefunds, "-${formatSar(metrics.totalRefunds, lang)}", StatusDanger)
                MetricRow(s.netSales, formatSar(metrics.netSales, lang), MaterialTheme.colorScheme.primary, bold = true)
                HorizontalDivider()
                MetricRow(s.openingCashFloat, formatSar(metrics.openingFloat, lang))
                MetricRow(s.expectedCashInDrawer, formatSar(metrics.expectedCashInDrawer, lang), StatusSuccess, bold = true)
                if (onEditOpeningFloat != null) {
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = onEditOpeningFloat,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("live_shift_edit_float_btn")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(s.setOpeningCashTitle)
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text(s.close) } }
    )
}

@Composable
fun SetOpeningFloatDialog(
    lang: AppLang,
    currentFloat: Double,
    onDismiss: () -> Unit,
    onSaveFloat: (Double) -> Unit
) {
    val s = stringsFor(lang)
    var amountText by remember(currentFloat) {
        mutableStateOf(
            if (currentFloat > 0.0) {
                if (currentFloat % 1.0 == 0.0) currentFloat.toInt().toString() else formatNumber(currentFloat)
            } else {
                ""
            }
        )
    }
    val parsed = (amountText.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 460.dp),
        icon = { Icon(Icons.Default.Payments, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(s.setOpeningCashTitle, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { value ->
                        if (value.all { c -> c.isDigit() || c == '.' } && value.count { it == '.' } <= 1) {
                            amountText = value
                        }
                    },
                    label = { Text(s.startingCashLabel) },
                    placeholder = { Text("0.00") },
                    trailingIcon = {
                        if (amountText.isNotEmpty()) {
                            IconButton(onClick = { amountText = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = s.clear)
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("opening_float_input")
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(0.0, 200.0, 500.0, 1000.0, 2000.0).forEach { preset ->
                        val labelStr = if (preset == 0.0) "0" else preset.toInt().toString()
                        FilterChip(
                            selected = kotlin.math.abs(parsed - preset) < 0.01 && (amountText.isNotEmpty() || preset == 0.0),
                            onClick = { amountText = labelStr },
                            label = { Text("$labelStr ${s.sar}") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSaveFloat(parsed) },
                modifier = Modifier.testTag("save_opening_float_btn")
            ) { Text("${s.confirm} (${formatNumber(parsed)} ${s.sar})") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } }
    )
}

@Composable
fun ZReportCloseDayDialog(
    lang: AppLang,
    settings: StoreSettings,
    shift: ShiftSession,
    metrics: ShiftSummaryMetrics,
    onDismiss: () -> Unit,
    onCloseShift: (countedCash: Double, openingCashOverride: Double) -> Unit
) {
    val s = stringsFor(lang)
    val context = LocalContext.current
    var openingCashText by remember(shift.id, shift.openingFloat) {
        mutableStateOf(
            if (shift.openingFloat > 0.0 && shift.openingFloat % 1.0 == 0.0) {
                shift.openingFloat.toInt().toString()
            } else {
                formatNumber(shift.openingFloat)
            }
        )
    }
    val openingCash = (openingCashText.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
    val expectedCashAtClose = openingCash + metrics.cashSales - metrics.cashRefunds

    var countedManuallyEdited by remember(shift.id) { mutableStateOf(false) }
    var countedText by remember(shift.id) { mutableStateOf(formatNumber(metrics.expectedCashInDrawer)) }
    LaunchedEffect(expectedCashAtClose) {
        if (!countedManuallyEdited) {
            countedText = formatNumber(expectedCashAtClose)
        }
    }
    val counted = (countedText.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
    val variance = counted - expectedCashAtClose

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 560.dp),
        title = { Text(s.zReportDayClose, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricRow(s.cashierLabel, shift.cashierName)
                OutlinedTextField(
                    value = openingCashText,
                    onValueChange = { value ->
                        if (value.all { c -> c.isDigit() || c == '.' } && value.count { it == '.' } <= 1) {
                            openingCashText = value
                        }
                    },
                    label = { Text(s.startingCashLabel) },
                    placeholder = { Text("0.00") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("zreport_opening_cash_input")
                )
                MetricRow(s.cashSales, "+${formatSar(metrics.cashSales, lang)}")
                MetricRow(s.madaSales, formatSar(metrics.madaSales, lang))
                MetricRow(s.visaSales, formatSar(metrics.visaSales, lang))
                MetricRow(s.onlineSales, formatSar(metrics.onlineSales, lang))
                MetricRow(s.dueSales, formatSar(metrics.dueSales, lang))
                MetricRow(s.cashRefunds, "-${formatSar(metrics.cashRefunds, lang)}", StatusDanger)
                MetricRow(s.netSales, formatSar(metrics.netSales, lang), MaterialTheme.colorScheme.primary, bold = true)
                HorizontalDivider()
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(s.expectedCashInDrawer, fontWeight = FontWeight.Bold)
                        Text(formatSar(expectedCashAtClose, lang), fontWeight = FontWeight.ExtraBold)
                    }
                }
                OutlinedTextField(
                    value = countedText,
                    onValueChange = { value ->
                        if (value.all { c -> c.isDigit() || c == '.' } && value.count { it == '.' } <= 1) {
                            countedManuallyEdited = true
                            countedText = value
                        }
                    },
                    label = { Text(s.closingCashLabel) },
                    placeholder = { Text("0.00") },
                    trailingIcon = {
                        TextButton(onClick = {
                            countedManuallyEdited = false
                            countedText = formatNumber(expectedCashAtClose)
                        }) {
                            Text(s.matchExpectedLabel, style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("zreport_counted_cash_input")
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = when {
                        variance < -0.01 -> StatusDanger.copy(alpha = 0.14f)
                        variance > 0.01 -> StatusWarning.copy(alpha = 0.16f)
                        else -> StatusSuccess.copy(alpha = 0.14f)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(s.varianceLabel, fontWeight = FontWeight.Bold)
                        Text(
                            text = "${if (variance >= 0) "+" else ""}${formatSar(variance, lang)}",
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
                OutlinedButton(
                    onClick = {
                        val summary = """
                            ${settings.shopDisplayName(lang)} — Z-REPORT
                            Cashier: ${shift.cashierName}
                            Orders: ${metrics.orderCount}
                            Starting Cash: ${formatNumber(openingCash)} SAR
                            Cash: ${formatNumber(metrics.cashSales)} | Mada: ${formatNumber(metrics.madaSales)} | Visa: ${formatNumber(metrics.visaSales)} | App: ${formatNumber(metrics.onlineSales)}
                            Refunds: -${formatNumber(metrics.totalRefunds)}
                            Net Sales: ${formatNumber(metrics.netSales)} SAR
                            Expected Closing Cash: ${formatNumber(expectedCashAtClose)} SAR
                            Actual Closing Cash: ${formatNumber(counted)} SAR
                            Variance: ${if (variance >= 0) "+" else ""}${formatNumber(variance)} SAR
                        """.trimIndent()
                        StandaloneExporter.shareTextFile(context, "ZReport.txt", summary, "text/plain")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(s.shareWhatsappSummary)
                }
            }
        },
        confirmButton = {
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = StatusDanger),
                modifier = Modifier.testTag("zreport_close_day_btn"),
                onClick = { onCloseShift(counted, openingCash) }
            ) {
                Text(s.closeDayShiftBtn, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } }
    )
}

@Composable
private fun MetricRow(label: String, value: String, color: Color = MaterialTheme.colorScheme.onSurface, bold: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.SemiBold, color = color)
    }
}
