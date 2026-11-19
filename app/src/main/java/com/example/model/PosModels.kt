package com.example.model

import com.example.i18n.AppLang
import kotlinx.serialization.Serializable

@Serializable
enum class PricingMode { OFFLINE, ONLINE }

@Serializable
enum class PaymentMethod { CASH, MADA, VISA, ONLINE, DUE, SPLIT }

@Serializable
enum class RefundMethod { CASH, MADA, VISA, ONLINE }

@Serializable
enum class DiscountType { PERCENT, FIXED }

@Serializable
enum class OrderStatus { COMPLETED, HELD, INCOMING_QR, PARTIALLY_REFUNDED, REFUNDED, REJECTED }

@Serializable
data class CashierPermissions(
    val canTakeOrders: Boolean = true,
    val canAddProducts: Boolean = false,
    val canProcessRefunds: Boolean = false,
    val canHoldOrders: Boolean = true,
    val canViewReports: Boolean = true,
    val canAccessManagement: Boolean = false
)

@Serializable
data class Branch(
    val id: String,
    val nameEn: String,
    val nameAr: String,
    val address: String,
    val phone: String,
    val isActive: Boolean = false
) {
    fun displayName(lang: AppLang): String = if (lang == AppLang.AR) nameAr else nameEn
}

@Serializable
data class Cashier(
    val id: String,
    val nameEn: String,
    val nameAr: String,
    val pin: String,
    val role: String, // "admin" or "cashier"
    val branchId: String,
    val permissions: CashierPermissions
) {
    fun displayName(lang: AppLang): String = if (lang == AppLang.AR) nameAr else nameEn
    val isAdminOrManager: Boolean get() = role == "admin" || permissions.canAccessManagement
}

@Serializable
data class OrderSource(
    val id: String,
    val nameEn: String,
    val nameAr: String,
    val pricingMode: PricingMode,
    val iconKey: String = "store",
    val isEnabled: Boolean = true
) {
    fun displayName(lang: AppLang): String = if (lang == AppLang.AR) nameAr else nameEn
}

@Serializable
data class OnlineDeliveryCompany(
    val id: String,
    val nameEn: String,
    val nameAr: String
) {
    fun displayName(lang: AppLang): String = if (lang == AppLang.AR) nameAr else nameEn
}

val DefaultOnlineDeliveryCompanies = listOf(
    OnlineDeliveryCompany("cmp_hungerstation", "HungerStation", "هنقرستيشن"),
    OnlineDeliveryCompany("cmp_keeta", "Keeta", "كيتا"),
    OnlineDeliveryCompany("cmp_jahez", "Jahez", "جاهز"),
    OnlineDeliveryCompany("cmp_toyou", "ToYou", "تو يو"),
    OnlineDeliveryCompany("cmp_ninja", "Ninja", "نينجا"),
    OnlineDeliveryCompany("cmp_mrsool", "Mrsool", "مرسول"),
    OnlineDeliveryCompany("cmp_chefz", "The Chefz", "ذا شفز"),
    OnlineDeliveryCompany("cmp_lugmety", "Lugmety", "لقمتي")
)

@Serializable
data class Category(
    val id: String,
    val nameEn: String,
    val nameAr: String,
    val sortOrder: Int = 0,
    val parentCategoryId: String? = null
) {
    fun displayName(lang: AppLang): String =
        if (lang == AppLang.AR && nameAr.isNotBlank() && !nameAr.equals(nameEn, ignoreCase = true)) "$nameAr / $nameEn"
        else if (lang == AppLang.AR) nameAr.ifBlank { nameEn } else nameEn
}

@Serializable
data class OfferOption(
    val id: String,
    val nameEn: String,
    val nameAr: String,
    val extraPrice: Double = 0.0
) {
    fun displayName(lang: AppLang): String =
        if (lang == AppLang.AR && nameAr.isNotBlank() && !nameAr.equals(nameEn, ignoreCase = true)) "$nameAr / $nameEn"
        else if (lang == AppLang.AR) nameAr.ifBlank { nameEn } else nameEn

    fun bilingualName(): String =
        if (nameAr.isNotBlank() && !nameAr.equals(nameEn, ignoreCase = true)) "$nameEn / $nameAr" else nameEn
}

@Serializable
data class ProductOffer(
    val id: String,
    val titleEn: String,
    val titleAr: String,
    val targetProductId: String, // productId OR "cat:<categoryId>"
    val isEnabled: Boolean = true,
    val options: List<OfferOption> = emptyList()
) {
    fun displayTitle(lang: AppLang): String =
        if (lang == AppLang.AR && titleAr.isNotBlank() && !titleAr.equals(titleEn, ignoreCase = true)) "$titleAr / $titleEn"
        else if (lang == AppLang.AR) titleAr.ifBlank { titleEn } else titleEn

    val isCategoryTarget: Boolean get() = targetProductId.startsWith("cat:")
    val targetCategoryIdOrNull: String? get() = if (isCategoryTarget) targetProductId.removePrefix("cat:") else null
}

fun List<ProductOffer>.activeOfferFor(product: Product): ProductOffer? =
    firstOrNull { it.isEnabled && it.options.isNotEmpty() && it.targetProductId == product.id }
        ?: firstOrNull { it.isEnabled && it.options.isNotEmpty() && it.targetProductId == "cat:${product.categoryId}" }

@Serializable
enum class ReceiptCopyMode { KITCHEN_ONLY, CUSTOMER_ONLY, BOTH }

@Serializable
data class Product(
    val id: String,
    val categoryId: String,
    val nameEn: String,
    val nameAr: String,
    val sku: String,
    val barcode: String,
    val offlinePrice: Double,
    val onlinePrice: Double,
    val stock: Int,
    val isAvailable: Boolean = true,
    val imageUri: String = ""
) {
    fun displayName(lang: AppLang): String =
        if (lang == AppLang.AR && nameAr.isNotBlank() && !nameAr.equals(nameEn, ignoreCase = true)) "$nameAr / $nameEn"
        else if (lang == AppLang.AR) nameAr.ifBlank { nameEn } else nameEn

    fun bilingualName(): String =
        if (nameAr.isNotBlank() && !nameAr.equals(nameEn, ignoreCase = true)) "$nameEn / $nameAr" else nameEn

    fun priceFor(mode: PricingMode): Double = if (mode == PricingMode.ONLINE) onlinePrice else offlinePrice
}

@Serializable
data class CartItem(
    val id: String,
    val productId: String,
    val nameEn: String,
    val nameAr: String,
    val offlinePrice: Double,
    val onlinePrice: Double,
    val unitPrice: Double,
    val qty: Int,
    val note: String = "",
    val isCustom: Boolean = false
) {
    fun displayName(lang: AppLang): String =
        if (lang == AppLang.AR && nameAr.isNotBlank() && !nameAr.equals(nameEn, ignoreCase = true)) "$nameAr / $nameEn"
        else if (lang == AppLang.AR) nameAr.ifBlank { nameEn } else nameEn

    fun bilingualName(): String =
        if (nameAr.isNotBlank() && !nameAr.equals(nameEn, ignoreCase = true)) "$nameEn / $nameAr" else nameEn

    val lineTotal: Double get() = unitPrice * qty
}

@Serializable
data class PaymentBreakdown(
    val primaryMethod: PaymentMethod = PaymentMethod.CASH,
    val isSplit: Boolean = false,
    val cashAmount: Double = 0.0,
    val madaAmount: Double = 0.0,
    val visaAmount: Double = 0.0,
    val onlineAmount: Double = 0.0,
    val dueAmount: Double = 0.0,
    val cashTendered: Double = 0.0,
    val changeDue: Double = 0.0,
    val onlineCompanyEn: String = "",
    val onlineCompanyAr: String = ""
) {
    val totalPaidAllocated: Double get() = cashAmount + madaAmount + visaAmount + onlineAmount + dueAmount
    val usesCash: Boolean get() = cashAmount > 0.001
}

@Serializable
data class RefundRecord(
    val id: String,
    val orderId: String,
    val orderNumber: String,
    val branchId: String,
    val shiftId: String,
    val cashierId: String,
    val amount: Double,
    val method: RefundMethod,
    val reason: String,
    val itemsSummary: String,
    val createdAt: Long
)

@Serializable
data class PosOrder(
    val id: String,
    val orderNumber: String,
    val branchId: String,
    val shiftId: String,
    val cashierId: String,
    val cashierName: String,
    val sourceId: String,
    val sourceNameEn: String,
    val sourceNameAr: String,
    val pricingMode: PricingMode,
    val items: List<CartItem>,
    val subtotal: Double,
    val discountType: DiscountType,
    val discountValue: Double,
    val discountAmount: Double,
    val vatRate: Double,
    val vatInclusive: Boolean,
    val vatAmount: Double,
    val grandTotal: Double,
    val payment: PaymentBreakdown,
    val customerName: String = "",
    val customerPhone: String = "",
    val tableOrCarNumber: String = "",
    val notes: String = "",
    val status: OrderStatus = OrderStatus.COMPLETED,
    val refundedAmount: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis(),
    val synced: Boolean = false
) {
    fun sourceDisplayName(lang: AppLang): String = if (lang == AppLang.AR) sourceNameAr else sourceNameEn
}

@Serializable
data class ShiftSession(
    val id: String,
    val branchId: String,
    val cashierId: String,
    val cashierName: String,
    val openingFloat: Double,
    val openedAt: Long,
    val closedAt: Long? = null,
    val expectedCash: Double = 0.0,
    val countedCash: Double = 0.0,
    val variance: Double = 0.0,
    val isOpen: Boolean = true
)

@Serializable
data class Customer(
    val id: String,
    val name: String,
    val phone: String,
    val notes: String = "",
    val totalSpent: Double = 0.0,
    val dueBalance: Double = 0.0,
    val visitsCount: Int = 0,
    val lastVisitAt: Long = System.currentTimeMillis()
)

@Serializable
data class StoreSettings(
    val id: String = "default",
    val shopNameEn: String = "Crispy Nation",
    val shopNameAr: String = "كريسبي نيشن",
    val vatNumber: String = "310123456700003",
    val vatRate: Double = 0.15,
    val vatInclusive: Boolean = true,
    val phone: String = "+966 55 123 4567",
    val addressEn: String = "Al Hofuf, Saudi Arabia",
    val addressAr: String = "الأحساء، المملكة العربية السعودية",
    val receiptFooterEn: String = "Thank you for visiting! Prices include 15% VAT",
    val receiptFooterAr: String = "شكراً لزيارتكم! الأسعار شاملة ضريبة القيمة المضافة ١٥٪",
    val paperWidthMm: Int = 80,
    val printerIp: String = "192.168.1.200",
    val printerPort: Int = 9100,
    val adminPin: String = "1234",
    val autoKickDrawer: Boolean = true,
    val qrServerPort: Int = 8090
) {
    fun shopDisplayName(lang: AppLang): String = if (lang == AppLang.AR) shopNameAr else shopNameEn
    fun addressDisplay(lang: AppLang): String = if (lang == AppLang.AR) addressAr else addressEn
    fun footerDisplay(lang: AppLang): String = if (lang == AppLang.AR) receiptFooterAr else receiptFooterEn
}

data class ShiftSummaryMetrics(
    val orderCount: Int = 0,
    val grossSales: Double = 0.0,
    val totalDiscounts: Double = 0.0,
    val totalVat: Double = 0.0,
    val cashSales: Double = 0.0,
    val madaSales: Double = 0.0,
    val visaSales: Double = 0.0,
    val onlineSales: Double = 0.0,
    val dueSales: Double = 0.0,
    val cashRefunds: Double = 0.0,
    val madaRefunds: Double = 0.0,
    val visaRefunds: Double = 0.0,
    val onlineRefunds: Double = 0.0,
    val totalRefunds: Double = 0.0,
    val netSales: Double = 0.0,
    val openingFloat: Double = 0.0,
    val expectedCashInDrawer: Double = 0.0
)

data class DiscoveredPrinter(
    val ip: String,
    val port: Int,
    val label: String,
    val reachable: Boolean
)
