package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.i18n.AppLang
import com.example.i18n.stringsFor
import com.example.model.Cashier

@Composable
fun PinNumPad(
    pin: String,
    onPinChange: (String) -> Unit,
    errorText: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 4 PIN dots
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(4) { idx ->
                val filled = idx < pin.length
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(
                            if (filled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .border(
                            width = 1.5.dp,
                            color = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            shape = CircleShape
                        )
                )
            }
        }

        if (errorText != null) {
            Text(
                text = errorText,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }

        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("C", "0", "DEL")
        )
        rows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                row.forEach { key ->
                    val isAction = key == "C" || key == "DEL"
                    Surface(
                        modifier = Modifier
                            .size(width = 74.dp, height = 52.dp)
                            .testTag("pin_key_$key")
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                when (key) {
                                    "C" -> onPinChange("")
                                    "DEL" -> if (pin.isNotEmpty()) onPinChange(pin.dropLast(1))
                                    else -> if (pin.length < 4) onPinChange(pin + key)
                                }
                            },
                        color = if (isAction) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp,
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                        )
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (key == "DEL") {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                                    contentDescription = "Backspace",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            } else {
                                Text(
                                    text = key,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (key == "C") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminPinLockDialog(
    lang: AppLang,
    onDismiss: () -> Unit,
    onVerifyPin: (String) -> Boolean,
    onUnlocked: () -> Unit
) {
    val s = stringsFor(lang)
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pin) {
        if (pin.length == 4) {
            if (onVerifyPin(pin)) {
                onUnlocked()
            } else {
                error = s.invalidPin
                pin = ""
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text(s.adminPinRequired, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = s.adminPinSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PinNumPad(
                    pin = pin,
                    onPinChange = {
                        error = null
                        pin = it
                    },
                    errorText = error
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(s.cancel)
            }
        }
    )
}

@Composable
fun SwitchCashierPinDialog(
    lang: AppLang,
    cashiers: List<Cashier>,
    activeCashierId: String?,
    onDismiss: () -> Unit,
    onSwitchWithoutPin: (Cashier) -> Unit,
    onSwitchWithPin: (Cashier, String) -> Boolean
) {
    val s = stringsFor(lang)
    var adminTarget by remember { mutableStateOf<Cashier?>(null) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pin, adminTarget) {
        val target = adminTarget
        if (target != null && pin.length == 4) {
            if (onSwitchWithPin(target, pin)) {
                onDismiss()
            } else {
                error = s.invalidPin
                pin = ""
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(s.switchCashier, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = s.switchCashierNoLockHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    cashiers.forEach { c ->
                        val isActive = c.id == activeCashierId
                        val isSelectedAdmin = adminTarget?.id == c.id
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("switch_cashier_${c.id}")
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    if (!c.isAdminOrManager) {
                                        onSwitchWithoutPin(c)
                                        onDismiss()
                                    } else {
                                        adminTarget = c
                                        pin = ""
                                        error = null
                                    }
                                },
                            color = when {
                                isSelectedAdmin -> MaterialTheme.colorScheme.primaryContainer
                                isActive -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            border = if (isSelectedAdmin || isActive) {
                                androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                            } else null
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (c.isAdminOrManager) Icons.Default.AdminPanelSettings else Icons.Default.Person,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = c.displayName(lang),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isActive || isSelectedAdmin) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (c.isAdminOrManager) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = "Admin Lock",
                                            modifier = Modifier.size(15.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    if (isActive) {
                                        Badge { Text(s.activeBadge) }
                                    }
                                }
                            }
                        }
                    }
                }
                if (adminTarget != null) {
                    HorizontalDivider()
                    Text(
                        text = "${s.adminPinRequired}: ${adminTarget!!.displayName(lang)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    PinNumPad(
                        pin = pin,
                        onPinChange = {
                            error = null
                            pin = it
                        },
                        errorText = error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(s.cancel) }
        }
    )
}
