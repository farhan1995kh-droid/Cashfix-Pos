package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.hardware.StandaloneExporter
import com.example.hardware.ZatcaQrAndEscPos
import com.example.i18n.AppLang
import com.example.i18n.formatNumber
import com.example.i18n.stringsFor
import com.example.model.ShiftSession
import com.example.model.ShiftSummaryMetrics
import com.example.model.StoreSettings
import com.example.ui.theme.CairoFontFamily
import com.example.ui.theme.StatusDanger
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ShiftMemoInk = Color(0xFF111827)
private val ShiftMemoMuted = Color(0xFF475569)
private val ShiftMemoRuleDark = Color(0xFF334155)
private val ShiftMemoRuleLight = Color(0xFFE2E8F0)

@Composable
fun ShiftSummaryMemoDialog(
    lang: AppLang,
    shift: ShiftSession,
    metrics: ShiftSummaryMetrics,
    settings: StoreSettings,
    branchNameEn: String,
    branchNameAr: String,
    onDismiss: () -> Unit,
    onPrintEscPos: (paperWidthMm: Int) -> Unit
) {
    val s = stringsFor(lang)
    val context = LocalContext.current
    val df = remember { SimpleDateFormat("HH:mm:ss - dd.MM.yyyy", Locale.US) }
    val closedAt = shift.closedAt ?: System.currentTimeMillis()
    val variancePrefix = if (shift.variance >= 0.0) "+" else ""
    val varianceColor = when {
        shift.variance < -0.01 -> StatusDanger
        shift.variance > 0.01 -> StatusWarning
        else -> StatusSuccess
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .padding(horizontal = 18.dp, vertical = 12.dp)
            .widthIn(max = 560.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(s.shiftSummaryMemoTitle, fontWeight = FontWeight.Bold)
                    Text(
                        text = s.shiftSummaryMemoSubtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "80mm • 576px",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        },
        text = {
            val paperWidth = 380.dp
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White,
                    shadowElevation = 4.dp,
                    border = BorderStroke(1.dp, ShiftMemoRuleLight),
                    modifier = Modifier
                        .widthIn(max = paperWidth)
                        .fillMaxWidth()
                        .testTag("shift_summary_memo_paper")
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFF1D4ED8)) {
                            Text(
                                text = "Z-REPORT • ملخص إغلاق الوردية",
                                fontFamily = CairoFontFamily,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        Text(
                            text = settings.shopNameAr.ifBlank { settings.shopNameEn },
                            fontFamily = CairoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 25.sp,
                            color = ShiftMemoInk,
                            textAlign = TextAlign.Center
                        )
                        if (settings.shopNameEn.isNotBlank() && settings.shopNameEn != settings.shopNameAr) {
                            Text(
                                text = settings.shopNameEn,
                                fontFamily = CairoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = ShiftMemoMuted,
                                textAlign = TextAlign.Center
                            )
                        }
                        if (settings.addressAr.isNotBlank() || settings.addressEn.isNotBlank()) {
                            Text(
                                text = listOf(settings.addressAr, settings.addressEn)
                                    .filter { it.isNotBlank() }
                                    .distinct()
                                    .joinToString("\n"),
                                fontFamily = CairoFontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                color = ShiftMemoMuted,
                                textAlign = TextAlign.Center
                            )
                        }
                        if (settings.vatNumber.isNotBlank()) {
                            Text(
                                text = "VAT #: ${settings.vatNumber}",
                                fontFamily = CairoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = ShiftMemoInk,
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(Modifier.height(2.dp))
                        HorizontalDivider(color = ShiftMemoRuleDark, thickness = 0.8.dp)

                        ShiftMemoRow("الكاشير / Cashier", shift.cashierName, bold = true)
                        ShiftMemoRow("بداية الوردية / Opened", df.format(Date(shift.openedAt)))
                        ShiftMemoRow("إغلاق الوردية / Closed", df.format(Date(closedAt)))

                        HorizontalDivider(color = ShiftMemoRuleDark, thickness = 0.8.dp)
                        Text(
                            text = "ملخص المبيعات • SALES SUMMARY",
                            fontFamily = CairoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp,
                            color = ShiftMemoInk
                        )

                        ShiftMemoRow("عدد الطلبات / Orders", "${metrics.orderCount}")
                        ShiftMemoRow("إجمالي المبيعات / Gross Sales", "${formatNumber(metrics.grossSales)} SAR")
                        if (metrics.totalDiscounts > 0.001) {
                            ShiftMemoRow("الخصومات / Discounts", "-${formatNumber(metrics.totalDiscounts)} SAR")
                        }
                        ShiftMemoRow("ضريبة القيمة المضافة / VAT 15%", "${formatNumber(metrics.totalVat)} SAR")
                        if (metrics.totalRefunds > 0.001) {
                            ShiftMemoRow("المرتجعات / Refunds", "-${formatNumber(metrics.totalRefunds)} SAR", valueColor = StatusDanger)
                        }
                        ShiftMemoRow("صافي المبيعات / Net Sales", "${formatNumber(metrics.netSales)} SAR", bold = true)

                        HorizontalDivider(color = ShiftMemoRuleDark, thickness = 0.8.dp)
                        Text(
                            text = "تفصيل المدفوعات • PAYMENT CHANNELS",
                            fontFamily = CairoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp,
                            color = ShiftMemoInk
                        )

                        ShiftMemoRow("نقدي / Cash Sales", "${formatNumber(metrics.cashSales)} SAR")
                        ShiftMemoRow("مدى / Mada Sales", "${formatNumber(metrics.madaSales)} SAR")
                        ShiftMemoRow("فيزا / Visa Sales", "${formatNumber(metrics.visaSales)} SAR")
                        ShiftMemoRow("تطبيقات التوصيل / Delivery Apps", "${formatNumber(metrics.onlineSales)} SAR")
                        if (metrics.dueSales > 0.001) {
                            ShiftMemoRow("آجل / Credit Due", "${formatNumber(metrics.dueSales)} SAR")
                        }

                        HorizontalDivider(color = ShiftMemoRuleDark, thickness = 0.8.dp)
                        Text(
                            text = "مطابقة الصندوق • CASH DRAWER",
                            fontFamily = CairoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp,
                            color = ShiftMemoInk
                        )

                        ShiftMemoRow("العهدة الافتتاحية / Opening Float", "${formatNumber(metrics.openingFloat)} SAR")
                        ShiftMemoRow("مبيعات النقد / Cash Sales", "+${formatNumber(metrics.cashSales)} SAR")
                        if (metrics.cashRefunds > 0.001) {
                            ShiftMemoRow("مرتجعات النقد / Cash Refunds", "-${formatNumber(metrics.cashRefunds)} SAR", valueColor = StatusDanger)
                        }
                        ShiftMemoRow("النقد المتوقع / Expected Cash", "${formatNumber(shift.expectedCash)} SAR")
                        ShiftMemoRow("النقد المعدود / Counted Cash", "${formatNumber(shift.countedCash)} SAR", bold = true)
                        ShiftMemoRow(
                            label = "العجز أو الزيادة / Variance",
                            value = "$variancePrefix${formatNumber(shift.variance)} SAR",
                            valueColor = varianceColor,
                            bold = true
                        )

                        HorizontalDivider(color = ShiftMemoRuleLight, thickness = 0.8.dp)
                        Text(
                            text = "******",
                            fontFamily = CairoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = ShiftMemoInk
                        )
                        if (settings.phone.isNotBlank()) {
                            Text(
                                text = settings.phone,
                                fontFamily = CairoFontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                color = ShiftMemoMuted
                            )
                        }
                    }
                }

                Button(
                    onClick = { onPrintEscPos(80) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("print_shift_memo_btn")
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(s.printShiftMemo, maxLines = 1)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val txt = ZatcaQrAndEscPos.formatShiftSummaryPreviewText(shift, metrics, settings, branchNameEn)
                    StandaloneExporter.shareTextFile(context, "ShiftSummary_${shift.id}.txt", txt, "text/plain")
                },
                modifier = Modifier.testTag("share_shift_memo_btn")
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(s.shareReceipt)
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.testTag("close_shift_memo_btn")
            ) { Text(s.close) }
        }
    )
}

@Composable
private fun ShiftMemoRow(
    label: String,
    value: String,
    valueColor: Color = ShiftMemoInk,
    bold: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontFamily = CairoFontFamily,
            color = if (bold) ShiftMemoInk else ShiftMemoMuted,
            fontSize = if (bold) 16.sp else 13.5.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            fontFamily = CairoFontFamily,
            color = valueColor,
            fontSize = if (bold) 16.5.sp else 14.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold
        )
    }
}
