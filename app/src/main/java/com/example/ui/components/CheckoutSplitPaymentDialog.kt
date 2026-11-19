package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.i18n.AppLang
import com.example.i18n.formatNumber
import com.example.i18n.formatSar
import com.example.i18n.stringsFor
import com.example.model.DefaultOnlineDeliveryCompanies
import com.example.model.OnlineDeliveryCompany
import com.example.model.PaymentBreakdown
import com.example.model.PaymentMethod
import com.example.model.PricingMode
import com.example.ui.theme.StatusDanger
import com.example.ui.theme.StatusSuccess
import kotlin.math.abs

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CheckoutSplitPaymentDialog(
    lang: AppLang,
    grandTotal: Double,
    pricingMode: PricingMode = PricingMode.OFFLINE,
    initialCustomerName: String,
    initialCustomerPhone: String,
    autoKickDrawer: Boolean,
    onDismiss: () -> Unit,
    onConfirmPayment: (payment: PaymentBreakdown, customerName: String, customerPhone: String) -> Unit
) {
    val s = stringsFor(lang)
    var isSplitMode by remember { mutableStateOf(false) }
    var singleMethod by remember(pricingMode) {
        mutableStateOf(if (pricingMode == PricingMode.ONLINE) PaymentMethod.ONLINE else PaymentMethod.CASH)
    }
    var selectedDeliveryCompany by remember { mutableStateOf<OnlineDeliveryCompany?>(DefaultOnlineDeliveryCompanies.first()) }
    var customCompanyName by remember { mutableStateOf("") }

    // Single Cash Tendered
    var singleCashTendered by remember { mutableStateOf(formatNumber(grandTotal)) }

    // Split inputs
    var splitCash by remember { mutableStateOf("") }
    var splitMada by remember { mutableStateOf("") }
    var splitVisa by remember { mutableStateOf("") }
    var splitOnline by remember { mutableStateOf("") }
    var splitDue by remember { mutableStateOf("") }
    var splitCashTendered by remember { mutableStateOf("") }

    // Customer details (required when DUE > 0)
    var custName by remember { mutableStateOf(initialCustomerName) }
    var custPhone by remember { mutableStateOf(initialCustomerPhone) }

    val cashAmt = if (isSplitMode) splitCash.toDoubleOrNull() ?: 0.0 else if (singleMethod == PaymentMethod.CASH) grandTotal else 0.0
    val madaAmt = if (isSplitMode) splitMada.toDoubleOrNull() ?: 0.0 else if (singleMethod == PaymentMethod.MADA) grandTotal else 0.0
    val visaAmt = if (isSplitMode) splitVisa.toDoubleOrNull() ?: 0.0 else if (singleMethod == PaymentMethod.VISA) grandTotal else 0.0
    val onlineAmt = if (isSplitMode) splitOnline.toDoubleOrNull() ?: 0.0 else if (singleMethod == PaymentMethod.ONLINE) grandTotal else 0.0
    val dueAmt = if (isSplitMode) splitDue.toDoubleOrNull() ?: 0.0 else if (singleMethod == PaymentMethod.DUE) grandTotal else 0.0

    val allocatedSum = cashAmt + madaAmt + visaAmt + onlineAmt + dueAmt
    val diff = grandTotal - allocatedSum
    val isSplitExact = abs(diff) < 0.01

    val activeTendered = if (isSplitMode) {
        splitCashTendered.toDoubleOrNull() ?: cashAmt
    } else {
        singleCashTendered.toDoubleOrNull() ?: grandTotal
    }
    val changeDue = if (cashAmt > 0.001) (activeTendered - cashAmt).coerceAtLeast(0.0) else 0.0
    val dueMissingInfo = dueAmt > 0.001 && (custName.isBlank() || custPhone.isBlank())
    val cashTenderedInsufficient = cashAmt > 0.001 && activeTendered + 0.001 < cashAmt
    val canSubmit = (!isSplitMode || isSplitExact) && !dueMissingInfo && !cashTenderedInsufficient

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .widthIn(max = 680.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(s.paymentTitle, fontWeight = FontWeight.Bold)
                Text(
                    text = formatSar(grandTotal, lang),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Single vs Split Mode Switcher
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !isSplitMode,
                        onClick = { isSplitMode = false },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("payment_single_tab"),
                        leadingIcon = { Icon(Icons.Default.CreditCard, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text(s.singlePayment, fontWeight = FontWeight.Bold) }
                    )
                    FilterChip(
                        selected = isSplitMode,
                        onClick = { isSplitMode = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("payment_split_tab"),
                        leadingIcon = { Icon(Icons.Default.CallSplit, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text(s.splitPayment, fontWeight = FontWeight.Bold) }
                    )
                }

                if (!isSplitMode) {
                    val methods = listOf(
                        PaymentMethod.CASH to s.cash,
                        PaymentMethod.MADA to s.mada,
                        PaymentMethod.VISA to s.visa,
                        PaymentMethod.ONLINE to s.onlinePlatform,
                        PaymentMethod.DUE to s.creditDue
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        methods.chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                pair.forEach { (m, label) ->
                                    val selected = singleMethod == m
                                    OutlinedButton(
                                        onClick = { singleMethod = m },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("single_method_${m.name}"),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                        ),
                                        border = androidx.compose.foundation.BorderStroke(
                                            if (selected) 2.dp else 1.dp,
                                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                        )
                                    ) {
                                        Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }

                    if (singleMethod == PaymentMethod.CASH) {
                        OutlinedTextField(
                            value = singleCashTendered,
                            onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) singleCashTendered = it },
                            label = { Text(s.cashTendered) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("single_cash_tendered_input")
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(grandTotal, 20.0, 50.0, 100.0, 200.0).forEach { amt ->
                                SuggestionChip(
                                    onClick = { singleCashTendered = formatNumber(amt) },
                                    label = { Text(if (amt == grandTotal) "Exact" else "${amt.toInt()}") }
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(s.changeDue, fontWeight = FontWeight.Bold)
                                Text(formatSar(changeDue, lang), fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                } else {
                    // SPLIT PAYMENT MODE
                    SplitMethodRow(
                        label = s.cash,
                        value = splitCash,
                        remaining = diff,
                        testTag = "split_cash_input",
                        onValueChange = { splitCash = it },
                        onFillRemaining = {
                            val current = splitCash.toDoubleOrNull() ?: 0.0
                            splitCash = formatNumber((current + diff).coerceAtLeast(0.0))
                        }
                    )
                    if (cashAmt > 0.001) {
                        OutlinedTextField(
                            value = splitCashTendered,
                            onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) splitCashTendered = it },
                            label = { Text("${s.cashTendered} (>= ${formatNumber(cashAmt)})") },
                            placeholder = { Text(formatNumber(cashAmt)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("split_cash_tendered_input")
                        )
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(s.changeDue, fontWeight = FontWeight.SemiBold)
                                Text(formatSar(changeDue, lang), fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    SplitMethodRow(
                        label = s.mada,
                        value = splitMada,
                        remaining = diff,
                        testTag = "split_mada_input",
                        onValueChange = { splitMada = it },
                        onFillRemaining = {
                            val current = splitMada.toDoubleOrNull() ?: 0.0
                            splitMada = formatNumber((current + diff).coerceAtLeast(0.0))
                        }
                    )
                    SplitMethodRow(
                        label = s.visa,
                        value = splitVisa,
                        remaining = diff,
                        testTag = "split_visa_input",
                        onValueChange = { splitVisa = it },
                        onFillRemaining = {
                            val current = splitVisa.toDoubleOrNull() ?: 0.0
                            splitVisa = formatNumber((current + diff).coerceAtLeast(0.0))
                        }
                    )
                    SplitMethodRow(
                        label = s.onlinePlatform,
                        value = splitOnline,
                        remaining = diff,
                        testTag = "split_online_input",
                        onValueChange = { splitOnline = it },
                        onFillRemaining = {
                            val current = splitOnline.toDoubleOrNull() ?: 0.0
                            splitOnline = formatNumber((current + diff).coerceAtLeast(0.0))
                        }
                    )
                    SplitMethodRow(
                        label = s.creditDue,
                        value = splitDue,
                        remaining = diff,
                        testTag = "split_due_input",
                        onValueChange = { splitDue = it },
                        onFillRemaining = {
                            val current = splitDue.toDoubleOrNull() ?: 0.0
                            splitDue = formatNumber((current + diff).coerceAtLeast(0.0))
                        }
                    )

                    // Split validation banner
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSplitExact) StatusSuccess.copy(alpha = 0.16f) else StatusDanger.copy(alpha = 0.14f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(
                                    imageVector = if (isSplitExact) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = if (isSplitExact) StatusSuccess else StatusDanger,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (isSplitExact) s.exactMatch else s.splitMismatch,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSplitExact) StatusSuccess else StatusDanger
                                )
                            }
                            if (!isSplitExact) {
                                Text(
                                    text = if (diff > 0) "${s.remainingToAllocate}: ${formatNumber(diff)}"
                                    else "${s.overAllocated}: ${formatNumber(abs(diff))}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = StatusDanger
                                )
                            }
                        }
                    }
                }

                if (pricingMode == PricingMode.ONLINE || onlineAmt > 0.001) {
                    HorizontalDivider()
                    Text(
                        text = s.selectDeliveryCompany,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        DefaultOnlineDeliveryCompanies.forEach { company ->
                            val isSelected = selectedDeliveryCompany?.id == company.id
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedDeliveryCompany = company },
                                modifier = Modifier.testTag("delivery_company_${company.id}"),
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null,
                                label = { Text(company.displayName(lang), fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium) }
                            )
                        }
                        val isOtherSelected = selectedDeliveryCompany == null
                        FilterChip(
                            selected = isOtherSelected,
                            onClick = { selectedDeliveryCompany = null },
                            modifier = Modifier.testTag("delivery_company_other"),
                            label = { Text(s.customDeliveryCompany) }
                        )
                    }
                    if (selectedDeliveryCompany == null) {
                        OutlinedTextField(
                            value = customCompanyName,
                            onValueChange = { customCompanyName = it },
                            label = { Text(s.customDeliveryCompany) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                if (dueAmt > 0.001) {
                    HorizontalDivider()
                    Text(s.dueRequiresCustomer, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                    OutlinedTextField(
                        value = custName,
                        onValueChange = { custName = it },
                        label = { Text(s.customerName) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = custPhone,
                        onValueChange = { custPhone = it },
                        label = { Text(s.customerPhone) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (cashAmt > 0.001 && autoKickDrawer) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.PointOfSale, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(s.drawerAutoKickNote, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = canSubmit,
                modifier = Modifier.testTag("complete_payment_btn"),
                onClick = {
                    val usesOnlineCompany = pricingMode == PricingMode.ONLINE || onlineAmt > 0.001
                    val companyEn = if (usesOnlineCompany) {
                        selectedDeliveryCompany?.nameEn ?: customCompanyName.trim().ifBlank { "Online Delivery" }
                    } else ""
                    val companyAr = if (usesOnlineCompany) {
                        selectedDeliveryCompany?.nameAr ?: customCompanyName.trim().ifBlank { "توصيل أونلاين" }
                    } else ""
                    val breakdown = PaymentBreakdown(
                        primaryMethod = if (isSplitMode) PaymentMethod.SPLIT else singleMethod,
                        isSplit = isSplitMode,
                        cashAmount = cashAmt,
                        madaAmount = madaAmt,
                        visaAmount = visaAmt,
                        onlineAmount = onlineAmt,
                        dueAmount = dueAmt,
                        cashTendered = if (cashAmt > 0.001) activeTendered else 0.0,
                        changeDue = changeDue,
                        onlineCompanyEn = companyEn,
                        onlineCompanyAr = companyAr
                    )
                    onConfirmPayment(breakdown, custName, custPhone)
                }
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(s.completePayment, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(s.cancel) }
        }
    )
}

@Composable
private fun SplitMethodRow(
    label: String,
    value: String,
    remaining: Double,
    testTag: String,
    onValueChange: (String) -> Unit,
    onFillRemaining: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) onValueChange(it) },
            label = { Text(label) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .testTag(testTag)
        )
        FilledTonalButton(
            onClick = onFillRemaining,
            enabled = remaining > 0.005,
            modifier = Modifier.height(48.dp),
            contentPadding = PaddingValues(horizontal = 10.dp)
        ) {
            Text("Fill", style = MaterialTheme.typography.labelMedium)
        }
    }
}
