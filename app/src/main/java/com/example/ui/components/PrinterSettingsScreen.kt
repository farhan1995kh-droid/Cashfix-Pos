package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.i18n.AppLang
import com.example.i18n.stringsFor
import com.example.model.DiscoveredPrinter
import com.example.model.StoreSettings

@Composable
fun PrinterSettingsScreen(
    lang: AppLang,
    settings: StoreSettings,
    discoveredPrinters: List<DiscoveredPrinter>,
    isScanningPrinters: Boolean,
    qrGatewayRunning: Boolean,
    localIp: String,
    onBack: () -> Unit,
    onSaveSettings: (StoreSettings) -> Unit,
    onScanPrinters: () -> Unit,
    onTestDrawerKick: () -> Unit
) {
    val s = stringsFor(lang)
    BackHandler(onBack = onBack)

    var ip by remember(settings) { mutableStateOf(settings.printerIp) }
    var port by remember(settings) { mutableStateOf(settings.printerPort.toString()) }
    var autoKick by remember(settings) { mutableStateOf(settings.autoKickDrawer) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("printer_back_btn")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.close)
            }
            Text(s.printerSettingsTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "LAN QR Self-Order Gateway: ${if (qrGatewayRunning) "ACTIVE" else "STANDBY"}",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "http://$localIp:${settings.qrServerPort}/?table=T1",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (lang == AppLang.AR) "طباعة 80mm حرارية (576px) — دعم عربي كامل" else "80mm Thermal Printer (576px RTL Raster Mode)",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = if (lang == AppLang.AR) {
                            "يتم تحويل الفاتورة إلى صورة نقطية عالية التباين وإرسالها عبر المنفذ 9100 بأمر ESC/POS GS v 0 لضمان طباعة الحروف العربية متصلة وواضحة."
                        } else {
                            "Renders Arabic & English receipts onto a 576px high-contrast RTL monochrome bitmap and sends ESC/POS GS v 0 over TCP Port 9100 with feed & auto-cut."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = { Text("80mm Wi-Fi Printer IP") },
                    modifier = Modifier.weight(1.5f).testTag("printer_ip_input"),
                    singleLine = true
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter(Char::isDigit) },
                    label = { Text("Port (9100)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(0.7f).testTag("printer_port_input"),
                    singleLine = true
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Auto-Kick Cash Drawer on Cash Payments", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = autoKick, onCheckedChange = { autoKick = it })
            }

            Button(
                onClick = {
                    onSaveSettings(
                        settings.copy(
                            printerIp = ip.trim(),
                            printerPort = port.toIntOrNull() ?: 9100,
                            paperWidthMm = 80,
                            autoKickDrawer = autoKick
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("save_printer_btn")
            ) {
                Icon(Icons.Default.Print, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(s.savePrinterSettings, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onScanPrinters,
                enabled = !isScanningPrinters,
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("scan_printers_btn")
            ) {
                if (isScanningPrinters) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Scanning LAN Subnet...")
                } else {
                    Icon(Icons.Default.WifiFind, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(s.scanWifiPrinters)
                }
            }

            discoveredPrinters.forEach { p ->
                Card(
                    onClick = {
                        ip = p.ip
                        port = p.port.toString()
                        onSaveSettings(settings.copy(printerIp = p.ip, printerPort = p.port))
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(p.label, fontWeight = FontWeight.SemiBold)
                        Text("Connect", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }

            FilledTonalButton(
                onClick = onTestDrawerKick,
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("test_drawer_btn")
            ) {
                Icon(Icons.Default.PointOfSale, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(s.testDrawerPulse)
            }
        }
    }
}
