package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.i18n.AppLang
import com.example.i18n.formatNumber
import com.example.i18n.stringsFor
import com.example.model.Branch
import com.example.model.Cashier
import com.example.model.StoreSettings
import com.example.ui.theme.StatusSuccess

@Composable
fun DayStartScreen(
    lang: AppLang,
    settings: StoreSettings,
    cashiers: List<Cashier>,
    onToggleLang: () -> Unit,
    onOpenCustomerQrMode: () -> Unit,
    onStartShift: (cashier: Cashier, pin: String, openingFloat: Double, branchId: String) -> Boolean
) {
    val s = stringsFor(lang)
    var selectedCashier by remember(cashiers) {
        mutableStateOf(cashiers.firstOrNull { !it.isAdminOrManager } ?: cashiers.firstOrNull())
    }
    var pin by remember { mutableStateOf("") }
    var openingFloatText by remember { mutableStateOf("500") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val requiresAdminPin = selectedCashier?.isAdminOrManager == true
    val parsedFloat = openingFloatText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        val isWideTablet = maxWidth >= 800.dp
        val heroHeight = if (isWideTablet && maxHeight < 650.dp) 124.dp else 156.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (isWideTablet) 24.dp else 16.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top CashFix POS Theme Banner Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Box(modifier = Modifier.height(heroHeight).fillMaxWidth()) {
                    Image(
                        painter = painterResource(id = R.drawable.img_apk_banner),
                        contentDescription = settings.shopDisplayName(lang),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color(0xFF001B5E).copy(alpha = 0.08f),
                                        Color(0xFF001B5E).copy(alpha = 0.78f)
                                    )
                                )
                            )
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = settings.shopDisplayName(lang),
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = s.dayStartTitle,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFFBAE6FD)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = onOpenCustomerQrMode,
                                modifier = Modifier.testTag("day_start_qr_mode_btn")
                            ) {
                                Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("QR Menu")
                            }
                            FilledTonalButton(
                                onClick = onToggleLang,
                                modifier = Modifier.testTag("day_start_lang_btn")
                            ) {
                                Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (lang == AppLang.EN) "عربي" else "EN")
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Main Unlocked Start Shift Panel (Cashier + Starting Cash Entry)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                BoxWithConstraints(modifier = Modifier.padding(20.dp)) {
                    val isWide = maxWidth >= 620.dp
                    val submitShift = {
                        val c = selectedCashier
                        if (c != null) {
                            val ok = onStartShift(c, pin, parsedFloat, "br_hofuf")
                            if (!ok) {
                                errorMsg = s.invalidPin
                                pin = ""
                            }
                        }
                    }
                    if (isWide) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            DayStartCashierColumn(
                                modifier = Modifier.weight(1f),
                                lang = lang,
                                settings = settings,
                                cashiers = cashiers,
                                selectedCashier = selectedCashier,
                                onSelectCashier = { c ->
                                    selectedCashier = c
                                    pin = ""
                                    errorMsg = null
                                }
                            )
                            DayStartCashEntryColumn(
                                modifier = Modifier.weight(1f),
                                lang = lang,
                                openingFloatText = openingFloatText,
                                parsedFloat = parsedFloat,
                                requiresAdminPin = requiresAdminPin,
                                pin = pin,
                                errorMsg = errorMsg,
                                selectedCashier = selectedCashier,
                                onFloatChange = { openingFloatText = it },
                                onPinChange = {
                                    errorMsg = null
                                    pin = it
                                },
                                onSubmit = submitShift
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            DayStartCashierColumn(
                                modifier = Modifier.fillMaxWidth(),
                                lang = lang,
                                settings = settings,
                                cashiers = cashiers,
                                selectedCashier = selectedCashier,
                                onSelectCashier = { c ->
                                    selectedCashier = c
                                    pin = ""
                                    errorMsg = null
                                }
                            )
                            HorizontalDivider()
                            DayStartCashEntryColumn(
                                modifier = Modifier.fillMaxWidth(),
                                lang = lang,
                                openingFloatText = openingFloatText,
                                parsedFloat = parsedFloat,
                                requiresAdminPin = requiresAdminPin,
                                pin = pin,
                                errorMsg = errorMsg,
                                selectedCashier = selectedCashier,
                                onFloatChange = { openingFloatText = it },
                                onPinChange = {
                                    errorMsg = null
                                    pin = it
                                },
                                onSubmit = submitShift
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayStartCashierColumn(
    modifier: Modifier,
    lang: AppLang,
    settings: StoreSettings,
    cashiers: List<Cashier>,
    selectedCashier: Cashier?,
    onSelectCashier: (Cashier) -> Unit
) {
    val s = stringsFor(lang)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(s.dayStartSubtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Text(s.selectCashier, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        cashiers.forEach { cashier ->
            val selected = cashier.id == selectedCashier?.id
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("day_start_cashier_${cashier.id}")
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelectCashier(cashier) },
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(
                            imageVector = if (cashier.isAdminOrManager) Icons.Default.AdminPanelSettings else Icons.Default.Badge,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(cashier.displayName(lang), fontWeight = FontWeight.SemiBold)
                            Text(
                                text = if (cashier.isAdminOrManager) {
                                    if (lang == AppLang.AR) "المدير • محمي برمز الإدارة" else "Admin • Admin Lock"
                                } else {
                                    if (lang == AppLang.AR) "كاشير • بدون قفل" else "Cashier • No Lock"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (cashier.isAdminOrManager) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Admin Lock",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (selected) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        // Single-Branch Store Location Card
        Text(
            text = if (lang == AppLang.AR) "موقع المتجر" else "Store Location",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text(settings.shopDisplayName(lang), fontWeight = FontWeight.Bold)
                    Text(
                        text = settings.addressDisplay(lang),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DayStartCashEntryColumn(
    modifier: Modifier,
    lang: AppLang,
    openingFloatText: String,
    parsedFloat: Double,
    requiresAdminPin: Boolean,
    pin: String,
    errorMsg: String?,
    selectedCashier: Cashier?,
    onFloatChange: (String) -> Unit,
    onPinChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    val s = stringsFor(lang)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = StatusSuccess.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, StatusSuccess.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Payments, contentDescription = null, tint = StatusSuccess)
                    Text(
                        text = s.openingCashFloat,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                OutlinedTextField(
                    value = openingFloatText,
                    onValueChange = { value ->
                        if (value.all { it.isDigit() || it == '.' } && value.count { it == '.' } <= 1) {
                            onFloatChange(value)
                        }
                    },
                    label = { Text(s.startingCashLabel) },
                    placeholder = { Text("0.00") },
                    trailingIcon = {
                        if (openingFloatText.isNotEmpty()) {
                            IconButton(onClick = { onFloatChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = s.clear)
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("opening_float_input")
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(0.0, 200.0, 500.0, 1000.0, 2000.0).forEach { preset ->
                        val presetStr = if (preset == 0.0) "0" else preset.toInt().toString()
                        FilterChip(
                            selected = kotlin.math.abs(parsedFloat - preset) < 0.01 && openingFloatText.isNotEmpty(),
                            onClick = { onFloatChange(presetStr) },
                            label = { Text("$presetStr ${s.sar}") }
                        )
                    }
                }
            }
        }

        if (requiresAdminPin) {
            HorizontalDivider()
            Text(s.enterPin, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            PinNumPad(
                pin = pin,
                onPinChange = onPinChange,
                errorText = errorMsg
            )
        }

        Button(
            onClick = onSubmit,
            enabled = selectedCashier != null && (!requiresAdminPin || pin.length == 4),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("start_shift_button"),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${s.startShiftBtn} (${formatNumber(parsedFloat)} ${s.sar})",
                fontWeight = FontWeight.Bold
            )
        }
    }
}
