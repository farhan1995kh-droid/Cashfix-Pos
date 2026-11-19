package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "branches")
data class BranchEntity(
    @PrimaryKey val id: String,
    val nameEn: String,
    val nameAr: String,
    val address: String,
    val phone: String,
    val isActive: Boolean
)

@Entity(tableName = "cashiers")
data class CashierEntity(
    @PrimaryKey val id: String,
    val nameEn: String,
    val nameAr: String,
    val pin: String,
    val role: String,
    val branchId: String,
    val permissionsJson: String
)

@Entity(tableName = "order_sources")
data class OrderSourceEntity(
    @PrimaryKey val id: String,
    val nameEn: String,
    val nameAr: String,
    val pricingMode: String,
    val iconKey: String,
    val isEnabled: Boolean
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val nameEn: String,
    val nameAr: String,
    val sortOrder: Int,
    val parentCategoryId: String? = null
)

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val id: String,
    val categoryId: String,
    val nameEn: String,
    val nameAr: String,
    val sku: String,
    val barcode: String,
    val offlinePrice: Double,
    val onlinePrice: Double,
    val stock: Int,
    val isAvailable: Boolean,
    val imageUri: String = ""
)

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey val id: String,
    val orderNumber: String,
    val branchId: String,
    val shiftId: String,
    val cashierId: String,
    val cashierName: String,
    val sourceId: String,
    val sourceNameEn: String,
    val sourceNameAr: String,
    val pricingMode: String,
    val itemsJson: String,
    val subtotal: Double,
    val discountType: String,
    val discountValue: Double,
    val discountAmount: Double,
    val vatRate: Double,
    val vatInclusive: Boolean,
    val vatAmount: Double,
    val grandTotal: Double,
    val paymentJson: String,
    val customerName: String,
    val customerPhone: String,
    val tableOrCarNumber: String,
    val notes: String,
    val status: String,
    val refundedAmount: Double,
    val createdAt: Long,
    val synced: Boolean
)

@Entity(tableName = "refunds")
data class RefundEntity(
    @PrimaryKey val id: String,
    val orderId: String,
    val orderNumber: String,
    val branchId: String,
    val shiftId: String,
    val cashierId: String,
    val amount: Double,
    val method: String,
    val reason: String,
    val itemsSummary: String,
    val createdAt: Long
)

@Entity(tableName = "shifts")
data class ShiftEntity(
    @PrimaryKey val id: String,
    val branchId: String,
    val cashierId: String,
    val cashierName: String,
    val openingFloat: Double,
    val openedAt: Long,
    val closedAt: Long?,
    val expectedCash: Double,
    val countedCash: Double,
    val variance: Double,
    val isOpen: Boolean
)

@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String,
    val notes: String,
    val totalSpent: Double,
    val dueBalance: Double,
    val visitsCount: Int,
    val lastVisitAt: Long
)

@Entity(tableName = "store_settings")
data class StoreSettingsEntity(
    @PrimaryKey val id: String,
    val settingsJson: String
)

@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey val id: String,
    val entityType: String,
    val entityId: String,
    val payloadJson: String,
    val createdAt: Long,
    val retryCount: Int,
    val status: String // "PENDING", "SYNCED"
)

@Entity(tableName = "product_offers")
data class ProductOfferEntity(
    @PrimaryKey val id: String,
    val titleEn: String,
    val titleAr: String,
    val targetProductId: String,
    val isEnabled: Boolean,
    val optionsJson: String
)
