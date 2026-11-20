package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.hardware.StandaloneExporter
import com.example.hardware.ThermalReceiptRasterizer
import com.example.hardware.ZatcaQrAndEscPos
import com.example.i18n.AppLang
import com.example.i18n.formatNumber
import com.example.i18n.stringsFor
import com.example.model.PosOrder
import com.example.model.ReceiptCopyMode
import com.example.model.StoreSettings
import com.example.ui.theme.CairoFontFamily

private val ReceiptInk = Color(0xFF000000)
private val ReceiptMuted = Color(0xFF334155)
private val ReceiptRuleDark = Color(0xFF0F172A)
private val ReceiptRuleLight = Color(0xFFCBD5E1)

@Composable
fun ThermalReceiptDialog(
    lang: AppLang,
    order: PosOrder,
    settings: StoreSettings,
    localIp: String,
    initialMode: ReceiptCopyMode = ReceiptCopyMode.BOTH,
    onDismiss: () -> Unit,
    onPrintEscPos: (PosOrder, ReceiptCopyMode, Boolean) -> Unit,
    onKickDrawer: () -> Unit
) {
    val s = stringsFor(lang)
    val context = LocalContext.current
    var activeMode by remember(order.id, initialMode) { mutableStateOf(initialMode) }

    val customerMenuUrl = remember(order.tableOrCarNumber, localIp, settings.qrServerPort) {
        val tbl = order.tableOrCarNumber.trim()
        if (tbl.isNotBlank()) "http://$localIp:${settings.qrServerPort}/?table=$tbl"
        else "http://$localIp:${settings.qrServerPort}/"
    }

    val menuQrBitmap = remember(customerMenuUrl) {
        ZatcaQrAndEscPos.generateQrBitmap(customerMenuUrl, 240)
    }

    val subtitleNote = when (activeMode) {
        ReceiptCopyMode.KITCHEN_ONLY -> s.kitchenCopyOnlyNote
        ReceiptCopyMode.CUSTOMER_ONLY -> s.customerCopyOnlyNote
        ReceiptCopyMode.BOTH -> s.dualMemoNote
    }
    val printButtonLabel = when (activeMode) {
        ReceiptCopyMode.KITCHEN_ONLY -> s.printKitchenCopy
        ReceiptCopyMode.CUSTOMER_ONLY -> s.printCustomerBill
        ReceiptCopyMode.BOTH -> s.printTwoCopies
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .padding(horizontal = 18.dp, vertical = 12.dp)
            .widthIn(max = 820.dp),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(s.receiptTitle, fontWeight = FontWeight.Bold)
                        Text(
                            text = subtitleNote,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "80mm • 576px Raster",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = activeMode == ReceiptCopyMode.CUSTOMER_ONLY,
                        onClick = { activeMode = ReceiptCopyMode.CUSTOMER_ONLY },
                        label = { Text(s.customerBillTab) }
                    )
                    FilterChip(
                        selected = activeMode == ReceiptCopyMode.KITCHEN_ONLY,
                        onClick = { activeMode = ReceiptCopyMode.KITCHEN_ONLY },
                        label = { Text(s.kitchenCopyTab) }
                    )
                    FilterChip(
                        selected = activeMode == ReceiptCopyMode.BOTH,
                        onClick = { activeMode = ReceiptCopyMode.BOTH },
                        label = { Text(s.bothCopiesTab) }
                    )
                }
            }
        },
        text = {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val showSideBySide = maxWidth >= 660.dp && activeMode == ReceiptCopyMode.BOTH
                val paperWidth = 360.dp
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (showSideBySide) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.Top
                        ) {
                            CustomerBillPaperCard(
                                lang = lang,
                                order = order,
                                settings = settings,
                                menuQrBitmap = menuQrBitmap,
                                modifier = Modifier.weight(1f)
                            )
                            RestaurantCopyPaperCard(
                                lang = lang,
                                order = order,
                                settings = settings,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        if (activeMode == ReceiptCopyMode.CUSTOMER_ONLY || activeMode == ReceiptCopyMode.BOTH) {
                            CustomerBillPaperCard(
                                lang = lang,
                                order = order,
                                settings = settings,
                                menuQrBitmap = menuQrBitmap,
                                modifier = Modifier.widthIn(max = paperWidth).fillMaxWidth()
                            )
                        }
                        if (activeMode == ReceiptCopyMode.BOTH) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                HorizontalDivider(modifier = Modifier.weight(1f))
                                Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Text("PAPER CUT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                HorizontalDivider(modifier = Modifier.weight(1f))
                            }
                        }
                        if (activeMode == ReceiptCopyMode.KITCHEN_ONLY || activeMode == ReceiptCopyMode.BOTH) {
                            RestaurantCopyPaperCard(
                                lang = lang,
                                order = order,
                                settings = settings,
                                modifier = Modifier.widthIn(max = paperWidth).fillMaxWidth()
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val kick = (activeMode != ReceiptCopyMode.KITCHEN_ONLY) && order.payment.usesCash && settings.autoKickDrawer
                                onPrintEscPos(order, activeMode, kick)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("receipt_print_btn")
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(printButtonLabel, maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = onKickDrawer,
                            modifier = Modifier.testTag("receipt_drawer_btn")
                        ) {
                            Icon(Icons.Default.PointOfSale, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(s.kickCashDrawer, maxLines = 1)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val txt = ZatcaQrAndEscPos.formatReceiptPreviewText(order, settings, lang, customerMenuUrl, activeMode)
                StandaloneExporter.shareTextFile(context, "${order.orderNumber}.txt", txt, "text/plain")
            }) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(s.shareReceipt)
            }
        },
        dismissButton = {
            Button(onClick = onDismiss, modifier = Modifier.testTag("receipt_close_btn")) { Text(s.close) }
        }
    )
}

/**
 * 80mm Customer Bill Preview matching the printed 576px RTL raster memo:
 * - Physical LEFT column = Amount (مبلغ) / SAR figures
 * - Physical RIGHT column = Product (منتج) / Arabic & bilingual labels
 */
@Composable
private fun CustomerBillPaperCard(
    lang: AppLang,
    order: PosOrder,
    settings: StoreSettings,
    menuQrBitmap: Bitmap?,
    modifier: Modifier = Modifier
) {
    val s = stringsFor(lang)
    val totalItemQty = order.items.sumOf { it.qty }
    val netAfterDiscount = (order.subtotal - order.discountAmount).coerceAtLeast(0.0)
    val taxableAmount = if (order.vatInclusive) netAfterDiscount / (1.0 + order.vatRate) else netAfterDiscount
    val preTaxSubtotal = if (order.vatInclusive) order.subtotal / (1.0 + order.vatRate) else order.subtotal
    val formattedDate = remember(order.createdAt) {
        ThermalReceiptRasterizer.formatReceiptTimestamp(order.createdAt)
    }
    val orderTypeText = remember(order) {
        ThermalReceiptRasterizer.resolveOrderTypeBilingual(order)
    }

    // Force LTR layout coordinates so physical LEFT is always Amount (مبلغ) & physical RIGHT is always Product (منتج)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color.White,
            shadowElevation = 4.dp,
            border = BorderStroke(1.dp, ReceiptRuleLight),
            modifier = modifier.testTag("customer_bill_paper_card")
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                // 1. Centered Bold Arabic & English Brand Header
                Text(
                    text = settings.shopNameAr.ifBlank { settings.shopNameEn },
                    fontFamily = CairoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 27.sp,
                    lineHeight = 33.sp,
                    color = ReceiptInk,
                    textAlign = TextAlign.Center
                )
                if (settings.shopNameEn.isNotBlank() && settings.shopNameEn != settings.shopNameAr) {
                    Text(
                        text = settings.shopNameEn,
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = ReceiptInk,
                        textAlign = TextAlign.Center
                    )
                }

                // 2. Centered Al Hofuf Address (Arabic & English) & 15-Digit VAT Number
                if (settings.addressAr.isNotBlank()) {
                    Text(
                        text = settings.addressAr,
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = 14.5.sp,
                        lineHeight = 19.sp,
                        color = ReceiptMuted,
                        textAlign = TextAlign.Center
                    )
                }
                if (settings.addressEn.isNotBlank() && settings.addressEn != settings.addressAr) {
                    Text(
                        text = settings.addressEn,
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        color = ReceiptMuted,
                        textAlign = TextAlign.Center
                    )
                }
                if (settings.vatNumber.isNotBlank()) {
                    Text(
                        text = "الرقم الضريبي: ${settings.vatNumber}",
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp,
                        color = ReceiptInk,
                        textAlign = TextAlign.Center
                    )
                }

                // 3. Invoice Number & Date/Time (HH:MM:SS - DD.MM.YYYY)
                Text(
                    text = "رقم الفاتورة : ${order.orderNumber}",
                    fontFamily = CairoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = ReceiptInk,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = formattedDate,
                    fontFamily = CairoFontFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 14.sp,
                    color = ReceiptMuted,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(2.dp))
                HorizontalDivider(color = ReceiptRuleDark, thickness = 1.dp)

                // 4. Order Type Label & Customer Details
                Text(
                    text = orderTypeText,
                    fontFamily = CairoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = ReceiptInk,
                    textAlign = TextAlign.Center
                )
                val customerLine = listOfNotNull(
                    order.customerName.takeIf { it.isNotBlank() },
                    order.tableOrCarNumber.takeIf { it.isNotBlank() },
                    order.customerPhone.takeIf { it.isNotBlank() }
                ).joinToString(" • ")
                if (customerLine.isNotBlank()) {
                    Text(
                        text = customerLine,
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = ReceiptMuted,
                        textAlign = TextAlign.Center
                    )
                }

                HorizontalDivider(color = ReceiptRuleDark, thickness = 1.dp)

                // 5. Two-Column Items Table Header: LEFT = مبلغ (Amount) | RIGHT = منتج (Product)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "مبلغ / Amount",
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp,
                        color = ReceiptInk,
                        textAlign = TextAlign.Start
                    )
                    Text(
                        text = "منتج / Product",
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp,
                        color = ReceiptInk,
                        textAlign = TextAlign.End
                    )
                }

                HorizontalDivider(color = ReceiptRuleDark, thickness = 1.dp)

                // 6. Two-Column Items Rows: LEFT = SAR Amount (Bold), RIGHT = Bilingual Name + Qty (Bold)
                order.items.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "${formatNumber(item.lineTotal)} SAR",
                            fontFamily = CairoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.5.sp,
                            color = ReceiptInk,
                            textAlign = TextAlign.Start
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = "${item.qty} × ${item.nameAr.ifBlank { item.nameEn }}",
                                fontFamily = CairoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                lineHeight = 21.sp,
                                color = ReceiptInk,
                                textAlign = TextAlign.End
                            )
                            if (item.nameEn.isNotBlank() && item.nameEn != item.nameAr) {
                                Text(
                                    text = item.nameEn,
                                    fontFamily = CairoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.5.sp,
                                    lineHeight = 19.sp,
                                    color = ReceiptInk,
                                    textAlign = TextAlign.End
                                )
                            }
                            if (item.note.isNotBlank()) {
                                Text(
                                    text = "* ${item.note}",
                                    fontFamily = CairoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    color = Color(0xFFB91C1C),
                                    textAlign = TextAlign.End
                                )
                            }
                        }
                    }
                    if (index < order.items.lastIndex) {
                        HorizontalDivider(color = ReceiptRuleLight, thickness = 0.7.dp)
                    }
                }

                HorizontalDivider(color = ReceiptRuleDark, thickness = 1.dp)

                // 7. Total Item Count
                Text(
                    text = "عدد المنتجات : $totalItemQty",
                    fontFamily = CairoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = ReceiptInk,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 2.dp)
                )

                HorizontalDivider(color = ReceiptRuleDark, thickness = 1.dp)

                // 8. Summary Block (LEFT = SAR, RIGHT = Arabic/English label)
                RtlMemoSummaryRow(
                    leftValue = "${formatNumber(preTaxSubtotal)} SAR",
                    rightLabel = "المجموع الفرعي / Subtotal"
                )
                if (order.discountAmount > 0.001) {
                    RtlMemoSummaryRow(
                        leftValue = "-${formatNumber(order.discountAmount)} SAR",
                        rightLabel = "الخصم / Discount"
                    )
                }
                RtlMemoSummaryRow(
                    leftValue = "${formatNumber(taxableAmount)} SAR",
                    rightLabel = "المبلغ الخاضع للضريبة / Taxable"
                )
                RtlMemoSummaryRow(
                    leftValue = "${formatNumber(order.vatAmount)} SAR",
                    rightLabel = "Tax 15% / الضريبة ١٥٪"
                )
                Spacer(Modifier.height(2.dp))
                RtlMemoSummaryRow(
                    leftValue = "${formatNumber(order.grandTotal)} SAR",
                    rightLabel = "المجموع / Total",
                    bold = true
                )

                HorizontalDivider(color = ReceiptRuleDark, thickness = 1.dp)

                // 9. Payment Method & Amount Paid
                val p = order.payment
                if (p.isSplit) {
                    if (p.cashAmount > 0.001) RtlMemoSummaryRow("${formatNumber(p.cashAmount)} SAR", "طريقة الدفع: كاش / Cash")
                    if (p.madaAmount > 0.001) RtlMemoSummaryRow("${formatNumber(p.madaAmount)} SAR", "طريقة الدفع: مدى / Mada")
                    if (p.visaAmount > 0.001) RtlMemoSummaryRow("${formatNumber(p.visaAmount)} SAR", "طريقة الدفع: فيزا / Visa")
                    if (p.onlineAmount > 0.001) {
                        val cmp = p.onlineCompanyEn.ifBlank { "Delivery App" }
                        RtlMemoSummaryRow("${formatNumber(p.onlineAmount)} SAR", "طريقة الدفع: $cmp")
                    }
                    if (p.dueAmount > 0.001) RtlMemoSummaryRow("${formatNumber(p.dueAmount)} SAR", "آجل / Credit Due")
                } else {
                    RtlMemoSummaryRow(
                        leftValue = ThermalReceiptRasterizer.resolvePaymentMethodBilingual(order),
                        rightLabel = "طريقة الدفع",
                        bold = true
                    )
                    RtlMemoSummaryRow(
                        leftValue = "${formatNumber(order.grandTotal)} SAR",
                        rightLabel = "مبلغ"
                    )
                }
                if (p.cashTendered > 0.001) {
                    RtlMemoSummaryRow("${formatNumber(p.cashTendered)} SAR", "المستلم نقداً / Cash Tendered")
                    RtlMemoSummaryRow("${formatNumber(p.changeDue)} SAR", "الباقي / Change Due", bold = true)
                }

                HorizontalDivider(color = ReceiptRuleDark, thickness = 1.dp)

                // 10. Cashier Served-By + Centered Thank-You / Establishment / Phone Footer
                Text(
                    text = "تمت خدمتك من قبل: ${order.cashierName}",
                    fontFamily = CairoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.5.sp,
                    color = ReceiptInk,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "******",
                    fontFamily = CairoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.5.sp,
                    color = ReceiptInk,
                    textAlign = TextAlign.Center
                )
                if (settings.receiptFooterAr.isNotBlank()) {
                    Text(
                        text = settings.receiptFooterAr,
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp,
                        color = ReceiptInk,
                        textAlign = TextAlign.Center
                    )
                }
                if (settings.receiptFooterEn.isNotBlank() && settings.receiptFooterEn != settings.receiptFooterAr) {
                    Text(
                        text = settings.receiptFooterEn,
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = 13.5.sp,
                        color = ReceiptMuted,
                        textAlign = TextAlign.Center
                    )
                }
                Text(
                    text = settings.shopNameAr.ifBlank { settings.shopNameEn },
                    fontFamily = CairoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = ReceiptInk,
                    textAlign = TextAlign.Center
                )
                if (settings.phone.isNotBlank()) {
                    Text(
                        text = settings.phone,
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp,
                        color = ReceiptInk,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(Modifier.height(4.dp))

                // 11. Compact Customer Menu QR Footer
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, ReceiptRuleLight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = s.scanMenuNextTimeAr,
                            fontFamily = CairoFontFamily,
                            color = ReceiptInk,
                            fontSize = 13.sp,
                            lineHeight = 17.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        if (menuQrBitmap != null) {
                            Image(
                                bitmap = menuQrBitmap.asImageBitmap(),
                                contentDescription = "Customer Order Menu QR Code",
                                modifier = Modifier
                                    .size(110.dp)
                                    .background(Color.White)
                                    .padding(4.dp)
                            )
                        }
                        Text(
                            text = s.scanMenuNextTimeEn,
                            fontFamily = CairoFontFamily,
                            color = ReceiptMuted,
                            fontSize = 12.5.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RestaurantCopyPaperCard(
    lang: AppLang,
    order: PosOrder,
    settings: StoreSettings,
    modifier: Modifier = Modifier
) {
    val s = stringsFor(lang)
    val formattedDate = remember(order.createdAt) {
        ThermalReceiptRasterizer.formatReceiptTimestamp(order.createdAt)
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFFFFFDF5),
            shadowElevation = 4.dp,
            border = BorderStroke(1.dp, Color(0xFFFDE68A)),
            modifier = modifier
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFF1D4ED8)) {
                    Text(
                        text = s.restaurantCopy,
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
                    fontSize = 19.5.sp,
                    color = ReceiptInk,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = formattedDate,
                    fontFamily = CairoFontFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 14.sp,
                    color = ReceiptMuted,
                    textAlign = TextAlign.Center
                )

                HorizontalDivider(color = ReceiptRuleDark, thickness = 1.dp)

                RtlMemoSummaryRow(order.orderNumber, "رقم الطلب / Order #", bold = true)
                RtlMemoSummaryRow(ThermalReceiptRasterizer.resolveOrderTypeBilingual(order), "نوع الطلب / Type")
                if (order.customerName.isNotBlank()) {
                    RtlMemoSummaryRow(order.customerName, "العميل / Customer", bold = true)
                }
                if (order.tableOrCarNumber.isNotBlank()) {
                    RtlMemoSummaryRow(order.tableOrCarNumber, "طاولة / سيارة", bold = true)
                }
                if (order.customerPhone.isNotBlank()) {
                    RtlMemoSummaryRow(order.customerPhone, "الجوال / Mobile")
                }
                RtlMemoSummaryRow(order.cashierName, "الكاشير / Cashier")

                if (order.notes.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFFEE2E2),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "NOTE: ${order.notes}",
                            fontFamily = CairoFontFamily,
                            color = Color(0xFFB91C1C),
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                HorizontalDivider(color = ReceiptRuleDark, thickness = 1.dp)

                order.items.forEachIndexed { index, item ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "[ ${item.qty}x ]  ${item.nameAr.ifBlank { item.nameEn }}",
                            fontFamily = CairoFontFamily,
                            color = ReceiptInk,
                            fontSize = 17.5.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End
                        )
                        if (item.nameEn.isNotBlank() && item.nameEn != item.nameAr) {
                            Text(
                                text = item.nameEn,
                                fontFamily = CairoFontFamily,
                                color = ReceiptInk,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.End
                            )
                        }
                        if (item.note.isNotBlank()) {
                            Text(
                                text = ">> ${item.note}",
                                fontFamily = CairoFontFamily,
                                color = Color(0xFFB91C1C),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.End
                            )
                        }
                    }
                    if (index < order.items.lastIndex) {
                        HorizontalDivider(color = ReceiptRuleLight, thickness = 0.7.dp)
                    }
                }

                HorizontalDivider(color = ReceiptRuleDark, thickness = 1.dp)
                RtlMemoSummaryRow("${order.items.sumOf { it.qty }}", "عدد المنتجات / TOTAL ITEMS", bold = true)
            }
        }
    }
}

@Composable
private fun RtlMemoSummaryRow(leftValue: String, rightLabel: String, bold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = leftValue,
            fontFamily = CairoFontFamily,
            color = ReceiptInk,
            fontSize = if (bold) 17.sp else 14.5.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
            textAlign = TextAlign.Start
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = rightLabel,
            fontFamily = CairoFontFamily,
            color = if (bold) ReceiptInk else ReceiptMuted,
            fontSize = if (bold) 16.5.sp else 14.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}
