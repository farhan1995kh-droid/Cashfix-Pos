package com.example.hardware

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.util.Base64
import com.example.i18n.AppLang
import com.example.i18n.formatNumber
import com.example.model.DiscoveredPrinter
import com.example.model.PosOrder
import com.example.model.ReceiptCopyMode
import com.example.model.ShiftSession
import com.example.model.ShiftSummaryMetrics
import com.example.model.StoreSettings
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object ZatcaQrAndEscPos {

    // Exact ESC/POS Drawer Kick sequence: [27, 112, 0, 25, 250]
    val CASH_DRAWER_KICK_BYTES = byteArrayOf(27, 112, 0, 25, 250.toByte())

    /**
     * Generates ZATCA Phase-1 compliant Base64 TLV QR payload:
     * Tag 1: Seller Name
     * Tag 2: VAT Registration Number
     * Tag 3: Timestamp (ISO 8601 UTC)
     * Tag 4: Invoice Total (with VAT)
     * Tag 5: VAT Total
     */
    fun buildZatcaBase64Tlv(
        sellerName: String,
        vatNumber: String,
        timestampMillis: Long,
        invoiceTotal: Double,
        vatTotal: Double
    ): String {
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val ts = isoFormat.format(Date(timestampMillis))
        val out = ByteArrayOutputStream()

        fun writeTag(tag: Int, value: String) {
            val bytes = value.toByteArray(StandardCharsets.UTF_8)
            out.write(tag)
            out.write(bytes.size.coerceAtMost(255))
            out.write(bytes, 0, bytes.size.coerceAtMost(255))
        }

        writeTag(1, sellerName)
        writeTag(2, vatNumber)
        writeTag(3, ts)
        writeTag(4, formatNumber(invoiceTotal))
        writeTag(5, formatNumber(vatTotal))

        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    fun generateQrBitmap(content: String, sizePx: Int = 380): Bitmap? {
        return runCatching {
            val hints = mapOf(
                EncodeHintType.MARGIN to 1,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
            val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
                }
            }
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, width, 0, 0, width, height)
            }
        }.getOrNull()
    }

    /**
     * Builds 80mm (576px) ESC/POS GS v 0 monochrome raster bitmap payload for TCP port 9100.
     * Permanently prevents disconnected/garbled Arabic glyphs on Wi-Fi thermal printers.
     */
    fun buildEscPosReceiptBytes(
        order: PosOrder,
        settings: StoreSettings,
        kickDrawer: Boolean,
        customerMenuUrl: String = "",
        mode: ReceiptCopyMode = ReceiptCopyMode.BOTH,
        typeface: Typeface? = null
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val resolvedMenuUrl = customerMenuUrl.ifBlank {
            val tablePart = if (order.tableOrCarNumber.isNotBlank()) "?table=${order.tableOrCarNumber.trim()}" else ""
            "http://${getLocalIpv4Address()}:${settings.qrServerPort}/$tablePart"
        }
        when (mode) {
            ReceiptCopyMode.CUSTOMER_ONLY -> {
                out.write(buildCustomerBillEscPosBytes(order, settings, kickDrawer, resolvedMenuUrl, typeface))
            }
            ReceiptCopyMode.KITCHEN_ONLY -> {
                out.write(buildRestaurantCopyEscPosBytes(order, settings, typeface))
            }
            ReceiptCopyMode.BOTH -> {
                out.write(buildCustomerBillEscPosBytes(order, settings, kickDrawer, resolvedMenuUrl, typeface))
                out.write(buildRestaurantCopyEscPosBytes(order, settings, typeface))
            }
        }
        return out.toByteArray()
    }

    fun buildCustomerBillEscPosBytes(
        order: PosOrder,
        settings: StoreSettings,
        kickDrawer: Boolean,
        customerMenuUrl: String = "",
        typeface: Typeface? = null
    ): ByteArray {
        val menuUrl = customerMenuUrl.ifBlank { "http://${getLocalIpv4Address()}:${settings.qrServerPort}/" }
        val bitmap = ThermalReceiptRasterizer.renderCustomerBillBitmap(
            order = order,
            settings = settings,
            customerMenuUrl = menuUrl,
            typeface = typeface
        )
        return try {
            ThermalReceiptRasterizer.bitmapToEscPosRasterBytes(
                bitmap = bitmap,
                feedLines = 5,
                cutPaper = true,
                kickDrawer = kickDrawer
            )
        } finally {
            bitmap.recycle()
        }
    }

    fun buildRestaurantCopyEscPosBytes(
        order: PosOrder,
        settings: StoreSettings,
        typeface: Typeface? = null
    ): ByteArray {
        val bitmap = ThermalReceiptRasterizer.renderRestaurantCopyBitmap(
            order = order,
            settings = settings,
            typeface = typeface
        )
        return try {
            ThermalReceiptRasterizer.bitmapToEscPosRasterBytes(
                bitmap = bitmap,
                feedLines = 5,
                cutPaper = true,
                kickDrawer = false
            )
        } finally {
            bitmap.recycle()
        }
    }

    fun buildShiftSummaryEscPosBytes(
        shift: ShiftSession,
        metrics: ShiftSummaryMetrics,
        settings: StoreSettings,
        branchNameEn: String = "",
        typeface: Typeface? = null
    ): ByteArray {
        val effectiveSettings = if (branchNameEn.isNotBlank() && settings.addressEn.isBlank()) {
            settings.copy(addressEn = branchNameEn)
        } else {
            settings
        }
        val bitmap = ThermalReceiptRasterizer.renderShiftSummaryBitmap(
            shift = shift,
            metrics = metrics,
            settings = effectiveSettings,
            typeface = typeface
        )
        return try {
            ThermalReceiptRasterizer.bitmapToEscPosRasterBytes(
                bitmap = bitmap,
                feedLines = 5,
                cutPaper = true,
                kickDrawer = false
            )
        } finally {
            bitmap.recycle()
        }
    }

    suspend fun sendRawEscPosToPrinter(
        ip: String,
        port: Int,
        payload: ByteArray
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip.trim(), port), 2500)
                socket.soTimeout = 4500
                socket.getOutputStream().use { stream ->
                    stream.write(payload)
                    stream.flush()
                }
            }
            "Sent ${payload.size} bytes (80mm Raster) to $ip:$port"
        }
    }

    suspend fun kickCashDrawer(ip: String, port: Int): Result<String> =
        sendRawEscPosToPrinter(ip, port, CASH_DRAWER_KICK_BYTES)

    fun getLocalIpv4Address(): String {
        return runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { !it.isLoopbackAddress && it.hostAddress?.contains(':') == false }
                ?.hostAddress ?: "192.168.1.100"
        }.getOrDefault("192.168.1.100")
    }

    suspend fun scanLanPrinters(port: Int = 9100): List<DiscoveredPrinter> = withContext(Dispatchers.IO) {
        val localIp = getLocalIpv4Address()
        val prefix = localIp.substringBeforeLast('.', "192.168.1")
        val found = coroutineScope {
            (1..254).map { host ->
                async {
                    val targetIp = "$prefix.$host"
                    val ok = runCatching {
                        Socket().use { s ->
                            s.connect(InetSocketAddress(targetIp, port), 160)
                            true
                        }
                    }.getOrDefault(false)
                    if (ok) DiscoveredPrinter(targetIp, port, "80mm ESC/POS Thermal ($targetIp)", true) else null
                }
            }.awaitAll().filterNotNull()
        }
        found
    }

    fun formatReceiptPreviewText(
        order: PosOrder,
        settings: StoreSettings,
        lang: AppLang,
        customerMenuUrl: String = "",
        mode: ReceiptCopyMode = ReceiptCopyMode.BOTH
    ): String {
        val formattedTime = ThermalReceiptRasterizer.formatReceiptTimestamp(order.createdAt)
        val menuUrl = customerMenuUrl.ifBlank { "http://${getLocalIpv4Address()}:${settings.qrServerPort}/" }
        val includeCustomer = mode == ReceiptCopyMode.CUSTOMER_ONLY || mode == ReceiptCopyMode.BOTH
        val includeKitchen = mode == ReceiptCopyMode.KITCHEN_ONLY || mode == ReceiptCopyMode.BOTH
        val netAfterDiscount = (order.subtotal - order.discountAmount).coerceAtLeast(0.0)
        val taxableAmount = if (order.vatInclusive) netAfterDiscount / (1.0 + order.vatRate) else netAfterDiscount
        val preTaxSubtotal = if (order.vatInclusive) order.subtotal / (1.0 + order.vatRate) else order.subtotal

        return buildString {
            if (includeCustomer) {
                appendLine("CUSTOMER BILL")
                appendLine("${settings.shopNameAr} / ${settings.shopNameEn}")
                appendLine("${settings.addressAr} / ${settings.addressEn}")
                appendLine("VAT: ${settings.vatNumber}")
                appendLine("رقم الفاتورة : ${order.orderNumber}")
                appendLine(formattedTime)
                appendLine(ThermalReceiptRasterizer.resolveOrderTypeBilingual(order))
                if (order.customerName.isNotBlank()) appendLine("Customer: ${order.customerName}")
                if (order.tableOrCarNumber.isNotBlank()) appendLine("Table/Car: ${order.tableOrCarNumber}")
                appendLine("--------------------------------")
                order.items.forEach { item ->
                    appendLine("${item.qty}x ${item.bilingualName()}  = ${formatNumber(item.lineTotal)} SAR")
                    if (item.note.isNotBlank()) appendLine("   * ${item.note}")
                }
                appendLine("--------------------------------")
                appendLine("عدد المنتجات: ${order.items.sumOf { it.qty }}")
                appendLine("Subtotal (المجموع الفرعي): ${formatNumber(preTaxSubtotal)} SAR")
                if (order.discountAmount > 0.001) appendLine("Discount: -${formatNumber(order.discountAmount)} SAR")
                appendLine("Taxable Amount (المبلغ الخاضع للضريبة): ${formatNumber(taxableAmount)} SAR")
                appendLine("Tax 15%: ${formatNumber(order.vatAmount)} SAR")
                appendLine("GRAND TOTAL (المجموع): ${formatNumber(order.grandTotal)} SAR")
                if (order.payment.isSplit) {
                    if (order.payment.cashAmount > 0.001) appendLine("Paid Cash: ${formatNumber(order.payment.cashAmount)} SAR")
                    if (order.payment.madaAmount > 0.001) appendLine("Paid Mada: ${formatNumber(order.payment.madaAmount)} SAR")
                    if (order.payment.visaAmount > 0.001) appendLine("Paid Visa: ${formatNumber(order.payment.visaAmount)} SAR")
                    if (order.payment.onlineAmount > 0.001) {
                        val cmp = order.payment.onlineCompanyEn.ifBlank { "App" }
                        appendLine("Paid $cmp: ${formatNumber(order.payment.onlineAmount)} SAR")
                    }
                    if (order.payment.dueAmount > 0.001) appendLine("Paid Due: ${formatNumber(order.payment.dueAmount)} SAR")
                } else {
                    appendLine("طريقة الدفع: ${ThermalReceiptRasterizer.resolvePaymentMethodBilingual(order)}")
                    appendLine("مبلغ: ${formatNumber(order.grandTotal)} SAR")
                }
                if (order.payment.cashTendered > 0.001) {
                    appendLine("Cash Tendered: ${formatNumber(order.payment.cashTendered)} SAR")
                    appendLine("Change Due: ${formatNumber(order.payment.changeDue)} SAR")
                }
                appendLine("--------------------------------")
                appendLine("تمت خدمتك من قبل: ${order.cashierName}")
                appendLine("******")
                if (settings.receiptFooterAr.isNotBlank()) appendLine(settings.receiptFooterAr)
                if (settings.receiptFooterEn.isNotBlank()) appendLine(settings.receiptFooterEn)
                appendLine("Tel: ${settings.phone}")
                appendLine("Order Menu QR URL: $menuUrl")
            }
            if (includeCustomer && includeKitchen) {
                appendLine()
                appendLine("--- CUT HERE ---")
                appendLine()
            }
            if (includeKitchen) {
                appendLine("RESTAURANT / KITCHEN COPY (FOR MAKING ORDER)")
                appendLine("Order #: ${order.orderNumber}")
                appendLine("Date: $formattedTime")
                appendLine("Type: ${ThermalReceiptRasterizer.resolveOrderTypeBilingual(order)}")
                if (order.customerName.isNotBlank()) appendLine("Customer: ${order.customerName}")
                if (order.tableOrCarNumber.isNotBlank()) appendLine("Table/Car: ${order.tableOrCarNumber}")
                if (order.customerPhone.isNotBlank()) appendLine("Phone: ${order.customerPhone}")
                if (order.notes.isNotBlank()) appendLine("Order Note: ${order.notes}")
                appendLine("--------------------------------")
                order.items.forEach { item ->
                    appendLine("[${item.qty}x] ${item.bilingualName()}")
                    if (item.note.isNotBlank()) appendLine("   * ${item.note}")
                }
                appendLine("--------------------------------")
                appendLine("TOTAL ITEMS: ${order.items.sumOf { it.qty }}")
            }
        }
    }

    fun formatShiftSummaryPreviewText(
        shift: ShiftSession,
        metrics: ShiftSummaryMetrics,
        settings: StoreSettings,
        branchNameEn: String = ""
    ): String {
        val closedTime = shift.closedAt ?: System.currentTimeMillis()
        val variancePrefix = if (shift.variance >= 0.0) "+" else ""
        return buildString {
            appendLine("SHIFT CLOSING SUMMARY (Z-REPORT)")
            appendLine("${settings.shopNameAr} / ${settings.shopNameEn}")
            appendLine("${settings.addressAr} / ${settings.addressEn}")
            if (branchNameEn.isNotBlank() && branchNameEn != settings.addressEn) appendLine("Branch: $branchNameEn")
            appendLine("VAT #: ${settings.vatNumber}")
            appendLine("Tel: ${settings.phone}")
            appendLine("--------------------------------")
            appendLine("Cashier: ${shift.cashierName}")
            appendLine("Opened: ${ThermalReceiptRasterizer.formatReceiptTimestamp(shift.openedAt)}")
            appendLine("Closed: ${ThermalReceiptRasterizer.formatReceiptTimestamp(closedTime)}")
            appendLine("--------------------------------")
            appendLine("Orders Count: ${metrics.orderCount}")
            appendLine("Gross Sales: ${formatNumber(metrics.grossSales)} SAR")
            appendLine("Discounts: -${formatNumber(metrics.totalDiscounts)} SAR")
            appendLine("VAT (15%): ${formatNumber(metrics.totalVat)} SAR")
            appendLine("Total Refunds: -${formatNumber(metrics.totalRefunds)} SAR")
            appendLine("NET SHIFT SALES: ${formatNumber(metrics.netSales)} SAR")
            appendLine("--------------------------------")
            appendLine("Cash Sales: ${formatNumber(metrics.cashSales)} SAR")
            appendLine("Mada Sales: ${formatNumber(metrics.madaSales)} SAR")
            appendLine("Visa Sales: ${formatNumber(metrics.visaSales)} SAR")
            appendLine("Delivery Apps: ${formatNumber(metrics.onlineSales)} SAR")
            appendLine("Credit / Due: ${formatNumber(metrics.dueSales)} SAR")
            appendLine("--------------------------------")
            appendLine("Opening Float: ${formatNumber(metrics.openingFloat)} SAR")
            appendLine("Cash Refunds: -${formatNumber(metrics.cashRefunds)} SAR")
            appendLine("Expected Cash: ${formatNumber(shift.expectedCash)} SAR")
            appendLine("Counted Cash: ${formatNumber(shift.countedCash)} SAR")
            appendLine("CASH VARIANCE: $variancePrefix${formatNumber(shift.variance)} SAR")
        }
    }
}
