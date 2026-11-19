package com.example.hardware

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import com.example.i18n.formatNumber
import com.example.model.PaymentMethod
import com.example.model.PosOrder
import com.example.model.PricingMode
import com.example.model.ShiftSession
import com.example.model.ShiftSummaryMetrics
import com.example.model.StoreSettings
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Permanent 80mm Wi-Fi ESC/POS Thermal Receipt Rasterizer (576px standard width).
 *
 * Renders Arabic & English text onto a 576px high-contrast monochrome Canvas using Android's
 * native HarfBuzz/Minikin text shaper with explicit RTL direction so Arabic letters always join
 * properly without garbling on TCP port 9100 printers, then encodes the bitmap into ESC/POS
 * GS v 0 raster bit-image slices followed by paper feed & cut commands.
 */
object ThermalReceiptRasterizer {

    const val PAPER_WIDTH_PX = 576
    const val MARGIN_X = 24
    const val CONTENT_WIDTH_PX = PAPER_WIDTH_PX - (MARGIN_X * 2) // 528px
    private const val MAX_SLICE_HEIGHT = 256
    private const val LUMINANCE_THRESHOLD = 165

    private val RECEIPT_DATE_FORMAT = SimpleDateFormat("HH:mm:ss - dd.MM.yyyy", Locale.US)

    fun formatReceiptTimestamp(millis: Long): String {
        return synchronized(RECEIPT_DATE_FORMAT) {
            RECEIPT_DATE_FORMAT.format(Date(millis))
        }
    }

    fun resolveOrderTypeBilingual(order: PosOrder): String {
        val companyEn = order.payment.onlineCompanyEn.trim()
        val companyAr = order.payment.onlineCompanyAr.trim()
        return when {
            order.pricingMode == PricingMode.ONLINE || order.sourceId == "src_online" -> {
                if (companyEn.isNotBlank()) {
                    val arPart = companyAr.ifBlank { companyEn }
                    "Delivery ($companyEn) • توصيل ($arPart)"
                } else {
                    "Delivery • توصيل"
                }
            }
            order.sourceId == "src_qr" -> "Dine-in (QR) • محلي (QR)"
            order.tableOrCarNumber.trim().startsWith("T", ignoreCase = true) ||
                order.tableOrCarNumber.contains("table", ignoreCase = true) ||
                order.tableOrCarNumber.contains("طاولة") -> "Dine-in • محلي"
            else -> "Takeaway • سفري"
        }
    }

    fun resolvePaymentMethodBilingual(order: PosOrder): String {
        val p = order.payment
        return when {
            p.primaryMethod == PaymentMethod.ONLINE && p.onlineCompanyEn.isNotBlank() -> {
                val ar = p.onlineCompanyAr.ifBlank { p.onlineCompanyEn }
                if (ar != p.onlineCompanyEn) "$ar / ${p.onlineCompanyEn}" else p.onlineCompanyEn
            }
            p.primaryMethod == PaymentMethod.MADA -> "مدى / Mada"
            p.primaryMethod == PaymentMethod.CASH -> "كاش / Cash"
            p.primaryMethod == PaymentMethod.VISA -> "فيزا / Visa"
            p.primaryMethod == PaymentMethod.DUE -> "آجل / Credit Due"
            p.primaryMethod == PaymentMethod.ONLINE -> "تطبيق توصيل / Delivery App"
            else -> p.primaryMethod.name
        }
    }

    /**
     * Converts any 576px-wide ARGB bitmap into ESC/POS GS v 0 monochrome raster command bytes,
     * sliced into safe vertical bands (max 256px tall) to prevent Wi-Fi printer buffer overflows.
     */
    fun bitmapToEscPosRasterBytes(
        bitmap: Bitmap,
        feedLines: Int = 5,
        cutPaper: Boolean = true,
        kickDrawer: Boolean = false
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val width = bitmap.width
        val height = bitmap.height
        val widthBytes = (width + 7) / 8
        val xL = widthBytes and 0xFF
        val xH = (widthBytes shr 8) and 0xFF

        // ESC @ (Initialize printer) + ESC a 1 (Center align)
        out.write(byteArrayOf(0x1B, 0x40, 0x1B, 0x61, 0x01))

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var yStart = 0
        while (yStart < height) {
            val sliceHeight = minOf(MAX_SLICE_HEIGHT, height - yStart)
            val yL = sliceHeight and 0xFF
            val yH = (sliceHeight shr 8) and 0xFF

            // GS v 0 m xL xH yL yH (1D 76 30 00)
            out.write(
                byteArrayOf(
                    0x1D, 0x76, 0x30, 0x00,
                    xL.toByte(), xH.toByte(),
                    yL.toByte(), yH.toByte()
                )
            )

            val rowBuffer = ByteArray(widthBytes)
            for (row in 0 until sliceHeight) {
                val y = yStart + row
                val rowOffset = y * width
                rowBuffer.fill(0)
                for (x in 0 until width) {
                    val argb = pixels[rowOffset + x]
                    val alpha = (argb ushr 24) and 0xFF
                    if (alpha < 40) continue
                    val r = (argb ushr 16) and 0xFF
                    val g = (argb ushr 8) and 0xFF
                    val b = argb and 0xFF
                    val luminance = (r * 299 + g * 587 + b * 114) / 1000
                    if (luminance < LUMINANCE_THRESHOLD) {
                        val byteIndex = x ushr 3
                        val bitMask = 0x80 ushr (x and 7)
                        rowBuffer[byteIndex] = (rowBuffer[byteIndex].toInt() or bitMask).toByte()
                    }
                }
                out.write(rowBuffer)
            }
            yStart += sliceHeight
        }

        // Feed paper past cutter bar (ESC d n)
        if (feedLines > 0) {
            out.write(byteArrayOf(0x1B, 0x64, feedLines.coerceIn(1, 20).toByte()))
        }

        // Full paper cut (GS V 0)
        if (cutPaper) {
            out.write(byteArrayOf(0x1D, 0x56, 0x00))
        }

        // Optional RJ-11 cash drawer pulse
        if (kickDrawer) {
            out.write(ZatcaQrAndEscPos.CASH_DRAWER_KICK_BYTES)
        }

        return out.toByteArray()
    }

    /**
     * Renders the 80mm (576px) Customer Bill inspired by the Saudi bilingual thermal memo layout:
     * - Centered bold Arabic & English brand header
     * - Centered Al Hofuf address (Arabic & English) and 15-digit VAT number
     * - Invoice number (رقم الفاتورة) and HH:MM:SS - DD.MM.YYYY timestamp
     * - Order type label (Takeaway / Dine-in / Delivery) + optional customer info
     * - Two-column items table: RIGHT = منتج (Arabic/bilingual name + qty), LEFT = مبلغ (SAR)
     * - Total item count (عدد المنتجات)
     * - Summary block: Subtotal, Discount (if any), Taxable Amount, Tax 15%, bold Grand Total
     * - Payment method (طريقة الدفع) & Amount Paid (مبلغ)
     * - Cashier served-by (تمت خدمتك من قبل), thank-you footer, brand, phone, and Menu QR
     */
    fun renderCustomerBillBitmap(
        order: PosOrder,
        settings: StoreSettings,
        customerMenuUrl: String = "",
        typeface: Typeface? = null
    ): Bitmap {
        val builder = ReceiptCanvasBuilder(typeface)
        val totalItemQty = order.items.sumOf { it.qty }
        val netAfterDiscount = (order.subtotal - order.discountAmount).coerceAtLeast(0.0)
        val taxableAmount = if (order.vatInclusive) netAfterDiscount / (1.0 + order.vatRate) else netAfterDiscount
        val preTaxSubtotal = if (order.vatInclusive) order.subtotal / (1.0 + order.vatRate) else order.subtotal

        // 1. Top Centered Bold Arabic & English Brand Header
        builder.addSpacer(14)
        val primaryTitle = settings.shopNameAr.ifBlank { settings.shopNameEn }
        builder.addCenteredText(primaryTitle, sizePx = 36f, bold = true, rtl = true)
        if (settings.shopNameEn.isNotBlank() && settings.shopNameEn != primaryTitle) {
            builder.addCenteredText(settings.shopNameEn, sizePx = 24f, bold = true, rtl = false)
        }

        // 2. Centered Al Hofuf Address (Arabic & English) & 15-Digit VAT
        builder.addSpacer(4)
        if (settings.addressAr.isNotBlank()) {
            builder.addCenteredText(settings.addressAr, sizePx = 21f, bold = false, rtl = true)
        }
        if (settings.addressEn.isNotBlank() && settings.addressEn != settings.addressAr) {
            builder.addCenteredText(settings.addressEn, sizePx = 19f, bold = false, rtl = false)
        }
        if (settings.vatNumber.isNotBlank()) {
            builder.addSpacer(2)
            builder.addCenteredText("الرقم الضريبي: ${settings.vatNumber}", sizePx = 21f, bold = true, rtl = true)
        }

        // 3. Invoice Number & Timestamp (HH:MM:SS - DD.MM.YYYY)
        builder.addSpacer(4)
        builder.addCenteredText("رقم الفاتورة : ${order.orderNumber}", sizePx = 23f, bold = true, rtl = true)
        builder.addCenteredText(formatReceiptTimestamp(order.createdAt), sizePx = 20f, bold = false, rtl = false)

        // 4. Order Type Label & Customer Details
        builder.addDivider(thicknessPx = 2f)
        builder.addCenteredText(resolveOrderTypeBilingual(order), sizePx = 23f, bold = true, rtl = true)
        val customerMeta = listOfNotNull(
            order.customerName.takeIf { it.isNotBlank() },
            order.tableOrCarNumber.takeIf { it.isNotBlank() },
            order.customerPhone.takeIf { it.isNotBlank() }
        ).joinToString(" • ")
        if (customerMeta.isNotBlank()) {
            builder.addCenteredText(customerMeta, sizePx = 20f, bold = false, rtl = true)
        }

        // 5. Two-Column Items Table Header (LEFT = مبلغ Amount, RIGHT = منتج Product)
        builder.addDivider(thicknessPx = 2f)
        builder.addTwoColumnRtlRow(
            rightText = "منتج / Product",
            leftText = "مبلغ / Amount",
            sizePx = 21f,
            bold = true
        )
        builder.addDivider(thicknessPx = 2f)

        // 6. Items Rows
        order.items.forEachIndexed { idx, item ->
            val rightPrimary = "${item.qty} × ${item.nameAr.ifBlank { item.nameEn }}"
            val rightSecondary = buildString {
                if (item.nameEn.isNotBlank() && item.nameEn != item.nameAr) {
                    append(item.nameEn)
                }
                if (item.note.isNotBlank()) {
                    if (isNotEmpty()) append("\n")
                    append("* ${item.note}")
                }
            }
            builder.addItemRow(
                rightPrimary = rightPrimary,
                rightSecondary = rightSecondary,
                leftAmount = "${formatNumber(item.lineTotal)} SAR"
            )
            if (idx < order.items.lastIndex) {
                builder.addDashedDivider()
            }
        }

        // 7. Total Item Count
        builder.addDivider(thicknessPx = 2f)
        builder.addCenteredText("عدد المنتجات : $totalItemQty", sizePx = 22f, bold = true, rtl = true)
        builder.addDivider(thicknessPx = 2f)

        // 8. Summary Block (Subtotal, Discount, Taxable Amount, Tax 15%, Bold Grand Total)
        builder.addTwoColumnRtlRow("المجموع الفرعي / Subtotal", "${formatNumber(preTaxSubtotal)} SAR", sizePx = 21f, bold = false)
        if (order.discountAmount > 0.001) {
            builder.addTwoColumnRtlRow("الخصم / Discount", "-${formatNumber(order.discountAmount)} SAR", sizePx = 21f, bold = false)
        }
        builder.addTwoColumnRtlRow("المبلغ الخاضع للضريبة / Taxable", "${formatNumber(taxableAmount)} SAR", sizePx = 21f, bold = false)
        builder.addTwoColumnRtlRow("Tax 15% / الضريبة ١٥٪", "${formatNumber(order.vatAmount)} SAR", sizePx = 21f, bold = false)
        builder.addSpacer(4)
        builder.addTwoColumnRtlRow("المجموع / Total", "${formatNumber(order.grandTotal)} SAR", sizePx = 27f, bold = true)

        // 9. Payment Method & Amount Paid
        builder.addDivider(thicknessPx = 2f)
        val p = order.payment
        if (p.isSplit) {
            if (p.cashAmount > 0.001) builder.addTwoColumnRtlRow("طريقة الدفع: كاش / Cash", "${formatNumber(p.cashAmount)} SAR", sizePx = 20f, bold = false)
            if (p.madaAmount > 0.001) builder.addTwoColumnRtlRow("طريقة الدفع: مدى / Mada", "${formatNumber(p.madaAmount)} SAR", sizePx = 20f, bold = false)
            if (p.visaAmount > 0.001) builder.addTwoColumnRtlRow("طريقة الدفع: فيزا / Visa", "${formatNumber(p.visaAmount)} SAR", sizePx = 20f, bold = false)
            if (p.onlineAmount > 0.001) {
                val appLabel = p.onlineCompanyEn.ifBlank { "Delivery App" }
                builder.addTwoColumnRtlRow("طريقة الدفع: $appLabel", "${formatNumber(p.onlineAmount)} SAR", sizePx = 20f, bold = false)
            }
            if (p.dueAmount > 0.001) builder.addTwoColumnRtlRow("آجل / Credit Due", "${formatNumber(p.dueAmount)} SAR", sizePx = 20f, bold = false)
        } else {
            builder.addTwoColumnRtlRow("طريقة الدفع", resolvePaymentMethodBilingual(order), sizePx = 21f, bold = true)
            builder.addTwoColumnRtlRow("مبلغ", "${formatNumber(order.grandTotal)} SAR", sizePx = 21f, bold = false)
        }
        if (p.cashTendered > 0.001) {
            builder.addTwoColumnRtlRow("المستلم نقداً / Cash Tendered", "${formatNumber(p.cashTendered)} SAR", sizePx = 20f, bold = false)
            builder.addTwoColumnRtlRow("الباقي / Change Due", "${formatNumber(p.changeDue)} SAR", sizePx = 20f, bold = true)
        }

        // 10. Served By + Thank-You Footer + Brand + Phone
        builder.addDivider(thicknessPx = 2f)
        builder.addCenteredText("تمت خدمتك من قبل: ${order.cashierName}", sizePx = 21f, bold = true, rtl = true)
        builder.addCenteredText("******", sizePx = 22f, bold = true, rtl = false)
        if (settings.receiptFooterAr.isNotBlank()) {
            builder.addCenteredText(settings.receiptFooterAr, sizePx = 21f, bold = true, rtl = true)
        }
        if (settings.receiptFooterEn.isNotBlank() && settings.receiptFooterEn != settings.receiptFooterAr) {
            builder.addCenteredText(settings.receiptFooterEn, sizePx = 19f, bold = false, rtl = false)
        }
        builder.addSpacer(4)
        builder.addCenteredText(primaryTitle, sizePx = 21f, bold = true, rtl = true)
        if (settings.phone.isNotBlank()) {
            builder.addCenteredText(settings.phone, sizePx = 21f, bold = true, rtl = false)
        }

        // 11. Customer Menu QR Code (drawn directly as monochrome bitmap pixels)
        if (customerMenuUrl.isNotBlank()) {
            val qrBitmap = ZatcaQrAndEscPos.generateQrBitmap(customerMenuUrl, 200)
            if (qrBitmap != null) {
                builder.addSpacer(10)
                builder.addCenteredText("امسح رمز QR للطلب من قائمتنا الرقمية", sizePx = 18f, bold = true, rtl = true)
                builder.addCenteredBitmap(qrBitmap)
            }
        }

        builder.addSpacer(20)
        return builder.build()
    }

    /**
     * Renders the 80mm (576px) Restaurant / Kitchen Copy with crisp RTL Arabic + English items.
     */
    fun renderRestaurantCopyBitmap(
        order: PosOrder,
        settings: StoreSettings,
        typeface: Typeface? = null
    ): Bitmap {
        val builder = ReceiptCanvasBuilder(typeface)
        builder.addSpacer(12)
        builder.addCenteredText("نسخة المطبخ • RESTAURANT COPY", sizePx = 26f, bold = true, rtl = true)
        builder.addCenteredText("لتحضير الطلب • FOR MAKING ORDER", sizePx = 21f, bold = true, rtl = true)
        builder.addDivider(thicknessPx = 3f)

        builder.addCenteredText(settings.shopNameAr.ifBlank { settings.shopNameEn }, sizePx = 25f, bold = true, rtl = true)
        builder.addCenteredText(formatReceiptTimestamp(order.createdAt), sizePx = 20f, bold = false, rtl = false)
        builder.addDivider(thicknessPx = 2f)

        builder.addTwoColumnRtlRow("رقم الطلب / Order #", order.orderNumber, sizePx = 24f, bold = true)
        builder.addTwoColumnRtlRow("نوع الطلب / Type", resolveOrderTypeBilingual(order), sizePx = 21f, bold = true)
        if (order.customerName.isNotBlank()) {
            builder.addTwoColumnRtlRow("العميل / Customer", order.customerName, sizePx = 22f, bold = true)
        }
        if (order.tableOrCarNumber.isNotBlank()) {
            builder.addTwoColumnRtlRow("طاولة / سيارة", order.tableOrCarNumber, sizePx = 22f, bold = true)
        }
        if (order.customerPhone.isNotBlank()) {
            builder.addTwoColumnRtlRow("الجوال / Mobile", order.customerPhone, sizePx = 20f, bold = false)
        }
        builder.addTwoColumnRtlRow("الكاشير / Cashier", order.cashierName, sizePx = 20f, bold = false)

        if (order.notes.isNotBlank()) {
            builder.addSpacer(4)
            builder.addCenteredText("ملاحظة الطلب: ${order.notes}", sizePx = 22f, bold = true, rtl = true)
        }

        builder.addDivider(thicknessPx = 2f)
        order.items.forEachIndexed { idx, item ->
            val primary = "[ ${item.qty}x ]  ${item.nameAr.ifBlank { item.nameEn }}"
            builder.addRightAlignedText(primary, sizePx = 25f, bold = true, rtl = true)
            if (item.nameEn.isNotBlank() && item.nameEn != item.nameAr) {
                builder.addRightAlignedText(item.nameEn, sizePx = 21f, bold = false, rtl = false)
            }
            if (item.note.isNotBlank()) {
                builder.addRightAlignedText(">> ${item.note}", sizePx = 21f, bold = true, rtl = true)
            }
            if (idx < order.items.lastIndex) {
                builder.addDashedDivider()
            }
        }

        builder.addDivider(thicknessPx = 2f)
        val totalQty = order.items.sumOf { it.qty }
        builder.addTwoColumnRtlRow("عدد المنتجات / TOTAL ITEMS", "$totalQty", sizePx = 24f, bold = true)
        builder.addSpacer(20)
        return builder.build()
    }

    /**
     * Renders the 80mm (576px) Shift Closing Summary (Z-Report) with shaped RTL Arabic & English.
     */
    fun renderShiftSummaryBitmap(
        shift: ShiftSession,
        metrics: ShiftSummaryMetrics,
        settings: StoreSettings,
        typeface: Typeface? = null
    ): Bitmap {
        val builder = ReceiptCanvasBuilder(typeface)
        val closedTime = shift.closedAt ?: System.currentTimeMillis()
        val variancePrefix = if (shift.variance >= 0.0) "+" else ""

        builder.addSpacer(12)
        builder.addCenteredText("ملخص إغلاق الوردية • Z-REPORT", sizePx = 26f, bold = true, rtl = true)
        builder.addCenteredText(settings.shopNameAr.ifBlank { settings.shopNameEn }, sizePx = 30f, bold = true, rtl = true)
        if (settings.shopNameEn.isNotBlank() && settings.shopNameEn != settings.shopNameAr) {
            builder.addCenteredText(settings.shopNameEn, sizePx = 22f, bold = true, rtl = false)
        }
        if (settings.addressAr.isNotBlank()) {
            builder.addCenteredText(settings.addressAr, sizePx = 19f, bold = false, rtl = true)
        }
        if (settings.addressEn.isNotBlank() && settings.addressEn != settings.addressAr) {
            builder.addCenteredText(settings.addressEn, sizePx = 18f, bold = false, rtl = false)
        }
        if (settings.vatNumber.isNotBlank()) {
            builder.addCenteredText("الرقم الضريبي: ${settings.vatNumber}", sizePx = 20f, bold = true, rtl = true)
        }
        builder.addDivider(thicknessPx = 2f)

        builder.addTwoColumnRtlRow("الكاشير / Cashier", shift.cashierName, sizePx = 21f, bold = true)
        builder.addTwoColumnRtlRow("بداية الوردية / Opened", formatReceiptTimestamp(shift.openedAt), sizePx = 19f, bold = false)
        builder.addTwoColumnRtlRow("إغلاق الوردية / Closed", formatReceiptTimestamp(closedTime), sizePx = 19f, bold = false)
        builder.addDivider(thicknessPx = 2f)

        builder.addCenteredText("ملخص المبيعات • SALES SUMMARY", sizePx = 21f, bold = true, rtl = true)
        builder.addTwoColumnRtlRow("عدد الطلبات / Orders", "${metrics.orderCount}", sizePx = 20f, bold = false)
        builder.addTwoColumnRtlRow("إجمالي المبيعات / Gross Sales", "${formatNumber(metrics.grossSales)} SAR", sizePx = 20f, bold = false)
        if (metrics.totalDiscounts > 0.001) {
            builder.addTwoColumnRtlRow("الخصومات / Discounts", "-${formatNumber(metrics.totalDiscounts)} SAR", sizePx = 20f, bold = false)
        }
        builder.addTwoColumnRtlRow("ضريبة ١٥٪ / VAT 15%", "${formatNumber(metrics.totalVat)} SAR", sizePx = 20f, bold = false)
        if (metrics.totalRefunds > 0.001) {
            builder.addTwoColumnRtlRow("المرتجعات / Refunds", "-${formatNumber(metrics.totalRefunds)} SAR", sizePx = 20f, bold = false)
        }
        builder.addTwoColumnRtlRow("صافي المبيعات / Net Sales", "${formatNumber(metrics.netSales)} SAR", sizePx = 23f, bold = true)
        builder.addDivider(thicknessPx = 2f)

        builder.addCenteredText("تفصيل المدفوعات • PAYMENT CHANNELS", sizePx = 21f, bold = true, rtl = true)
        builder.addTwoColumnRtlRow("نقدي / Cash Sales", "${formatNumber(metrics.cashSales)} SAR", sizePx = 20f, bold = false)
        builder.addTwoColumnRtlRow("مدى / Mada Sales", "${formatNumber(metrics.madaSales)} SAR", sizePx = 20f, bold = false)
        builder.addTwoColumnRtlRow("فيزا / Visa Sales", "${formatNumber(metrics.visaSales)} SAR", sizePx = 20f, bold = false)
        builder.addTwoColumnRtlRow("التطبيقات / Delivery Apps", "${formatNumber(metrics.onlineSales)} SAR", sizePx = 20f, bold = false)
        if (metrics.dueSales > 0.001) {
            builder.addTwoColumnRtlRow("آجل / Credit Due", "${formatNumber(metrics.dueSales)} SAR", sizePx = 20f, bold = false)
        }
        builder.addDivider(thicknessPx = 2f)

        builder.addCenteredText("مطابقة الصندوق • CASH DRAWER", sizePx = 21f, bold = true, rtl = true)
        builder.addTwoColumnRtlRow("العهدة الافتتاحية / Opening Float", "${formatNumber(metrics.openingFloat)} SAR", sizePx = 20f, bold = false)
        builder.addTwoColumnRtlRow("مبيعات النقد / Cash Sales", "+${formatNumber(metrics.cashSales)} SAR", sizePx = 20f, bold = false)
        if (metrics.cashRefunds > 0.001) {
            builder.addTwoColumnRtlRow("مرتجعات النقد / Cash Refunds", "-${formatNumber(metrics.cashRefunds)} SAR", sizePx = 20f, bold = false)
        }
        builder.addTwoColumnRtlRow("النقد المتوقع / Expected Cash", "${formatNumber(shift.expectedCash)} SAR", sizePx = 21f, bold = false)
        builder.addTwoColumnRtlRow("النقد المعدود / Counted Cash", "${formatNumber(shift.countedCash)} SAR", sizePx = 22f, bold = true)
        builder.addTwoColumnRtlRow("العجز أو الزيادة / Variance", "$variancePrefix${formatNumber(shift.variance)} SAR", sizePx = 23f, bold = true)

        builder.addDivider(thicknessPx = 2f)
        builder.addCenteredText("******", sizePx = 21f, bold = true, rtl = false)
        if (settings.phone.isNotBlank()) {
            builder.addCenteredText(settings.phone, sizePx = 20f, bold = false, rtl = false)
        }
        builder.addSpacer(20)
        return builder.build()
    }

    /**
     * Two-pass 576px Canvas builder: measures all blocks first, allocates exact bitmap height,
     * then draws high-contrast pure black (#000000) ink onto pure white (#FFFFFF) paper.
     */
    private class ReceiptCanvasBuilder(baseTypeface: Typeface?) {
        private val regularTypeface = baseTypeface ?: Typeface.SANS_SERIF
        private val boldTypeface = Typeface.create(regularTypeface, Typeface.BOLD)
        private val ops = mutableListOf<DrawOp>()
        private var totalHeight = 0

        private sealed class DrawOp(val height: Int) {
            class Spacer(h: Int) : DrawOp(h)
            class Divider(val thickness: Float, val dashed: Boolean) : DrawOp(18)
            class SingleLayout(
                val layout: StaticLayout,
                val offsetX: Float,
                val padTop: Int = 3,
                val padBottom: Int = 3
            ) : DrawOp(layout.height + padTop + padBottom)

            class DualLayout(
                val leftLayout: StaticLayout,
                val rightLayout: StaticLayout,
                val rightStartX: Float,
                val padTop: Int = 4,
                val padBottom: Int = 4
            ) : DrawOp(maxOf(leftLayout.height, rightLayout.height) + padTop + padBottom)

            class CenteredImage(
                val bitmap: Bitmap,
                val padTop: Int = 8,
                val padBottom: Int = 8
            ) : DrawOp(bitmap.height + padTop + padBottom)
        }

        private fun makePaint(sizePx: Float, bold: Boolean): TextPaint {
            return TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = sizePx
                typeface = if (bold) boldTypeface else regularTypeface
                isSubpixelText = true
                if (bold && boldTypeface == regularTypeface) {
                    isFakeBoldText = true
                }
            }
        }

        private fun createLayout(
            text: String,
            paint: TextPaint,
            width: Int,
            alignment: Layout.Alignment,
            rtl: Boolean
        ): StaticLayout {
            val safeWidth = width.coerceAtLeast(32)
            return StaticLayout.Builder
                .obtain(text, 0, text.length, paint, safeWidth)
                .setAlignment(alignment)
                .setTextDirection(if (rtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR)
                .setLineSpacing(2f, 1.08f)
                .setIncludePad(true)
                .build()
        }

        fun addSpacer(px: Int) {
            val op = DrawOp.Spacer(px)
            ops.add(op)
            totalHeight += op.height
        }

        fun addDivider(thicknessPx: Float = 2f) {
            val op = DrawOp.Divider(thicknessPx, dashed = false)
            ops.add(op)
            totalHeight += op.height
        }

        fun addDashedDivider() {
            val op = DrawOp.Divider(1.5f, dashed = true)
            ops.add(op)
            totalHeight += op.height
        }

        fun addCenteredText(text: String, sizePx: Float, bold: Boolean, rtl: Boolean) {
            if (text.isBlank()) return
            val layout = createLayout(text, makePaint(sizePx, bold), CONTENT_WIDTH_PX, Layout.Alignment.ALIGN_CENTER, rtl)
            val op = DrawOp.SingleLayout(layout, MARGIN_X.toFloat())
            ops.add(op)
            totalHeight += op.height
        }

        fun addRightAlignedText(text: String, sizePx: Float, bold: Boolean, rtl: Boolean) {
            if (text.isBlank()) return
            val alignment = if (rtl) Layout.Alignment.ALIGN_NORMAL else Layout.Alignment.ALIGN_OPPOSITE
            val layout = createLayout(text, makePaint(sizePx, bold), CONTENT_WIDTH_PX, alignment, rtl)
            val op = DrawOp.SingleLayout(layout, MARGIN_X.toFloat(), padTop = 3, padBottom = 3)
            ops.add(op)
            totalHeight += op.height
        }

        /**
         * Draws a two-column RTL receipt row:
         * - RIGHT column (right-aligned, RTL): Arabic/bilingual label or product
         * - LEFT column (left-aligned, LTR): SAR amount or value
         */
        fun addTwoColumnRtlRow(
            rightText: String,
            leftText: String,
            sizePx: Float,
            bold: Boolean
        ) {
            val gap = 12
            val leftColWidth = 196
            val rightColWidth = CONTENT_WIDTH_PX - leftColWidth - gap
            val paint = makePaint(sizePx, bold)

            val leftLayout = createLayout(leftText, paint, leftColWidth, Layout.Alignment.ALIGN_NORMAL, rtl = false)
            val rightLayout = createLayout(rightText, paint, rightColWidth, Layout.Alignment.ALIGN_NORMAL, rtl = true)
            val rightStartX = (MARGIN_X + leftColWidth + gap).toFloat()
            val op = DrawOp.DualLayout(leftLayout, rightLayout, rightStartX)
            ops.add(op)
            totalHeight += op.height
        }

        fun addItemRow(
            rightPrimary: String,
            rightSecondary: String,
            leftAmount: String
        ) {
            val gap = 12
            val leftColWidth = 152
            val rightColWidth = CONTENT_WIDTH_PX - leftColWidth - gap

            val amountPaint = makePaint(21f, bold = true)
            val namePaint = makePaint(22f, bold = true)
            val leftLayout = createLayout(leftAmount, amountPaint, leftColWidth, Layout.Alignment.ALIGN_NORMAL, rtl = false)
            val rightLayout = createLayout(rightPrimary, namePaint, rightColWidth, Layout.Alignment.ALIGN_NORMAL, rtl = true)
            val rightStartX = (MARGIN_X + leftColWidth + gap).toFloat()
            val mainOp = DrawOp.DualLayout(leftLayout, rightLayout, rightStartX, padTop = 5, padBottom = 2)
            ops.add(mainOp)
            totalHeight += mainOp.height

            if (rightSecondary.isNotBlank()) {
                val subPaint = makePaint(19f, bold = false)
                val subLayout = createLayout(rightSecondary, subPaint, rightColWidth, Layout.Alignment.ALIGN_OPPOSITE, rtl = false)
                val subOp = DrawOp.SingleLayout(subLayout, rightStartX, padTop = 0, padBottom = 5)
                ops.add(subOp)
                totalHeight += subOp.height
            }
        }

        fun addCenteredBitmap(bitmap: Bitmap) {
            val op = DrawOp.CenteredImage(bitmap)
            ops.add(op)
            totalHeight += op.height
        }

        fun build(): Bitmap {
            val safeHeight = totalHeight.coerceAtLeast(64)
            val bitmap = Bitmap.createBitmap(PAPER_WIDTH_PX, safeHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)

            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                style = Paint.Style.STROKE
            }

            var y = 0
            ops.forEach { op ->
                when (op) {
                    is DrawOp.Spacer -> Unit
                    is DrawOp.Divider -> {
                        val lineY = y + (op.height / 2f)
                        linePaint.strokeWidth = op.thickness
                        if (op.dashed) {
                            var cx = MARGIN_X.toFloat()
                            val endX = (PAPER_WIDTH_PX - MARGIN_X).toFloat()
                            while (cx < endX) {
                                canvas.drawLine(cx, lineY, minOf(cx + 8f, endX), lineY, linePaint)
                                cx += 14f
                            }
                        } else {
                            canvas.drawLine(
                                MARGIN_X.toFloat(),
                                lineY,
                                (PAPER_WIDTH_PX - MARGIN_X).toFloat(),
                                lineY,
                                linePaint
                            )
                        }
                    }
                    is DrawOp.SingleLayout -> {
                        canvas.save()
                        canvas.translate(op.offsetX, (y + op.padTop).toFloat())
                        op.layout.draw(canvas)
                        canvas.restore()
                    }
                    is DrawOp.DualLayout -> {
                        canvas.save()
                        canvas.translate(MARGIN_X.toFloat(), (y + op.padTop).toFloat())
                        op.leftLayout.draw(canvas)
                        canvas.restore()

                        canvas.save()
                        canvas.translate(op.rightStartX, (y + op.padTop).toFloat())
                        op.rightLayout.draw(canvas)
                        canvas.restore()
                    }
                    is DrawOp.CenteredImage -> {
                        val left = ((PAPER_WIDTH_PX - op.bitmap.width) / 2f).coerceAtLeast(MARGIN_X.toFloat())
                        canvas.drawBitmap(op.bitmap, left, (y + op.padTop).toFloat(), null)
                    }
                }
                y += op.height
            }

            return bitmap
        }
    }
}
