package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PosDao {
    // Branches
    @Query("SELECT * FROM branches ORDER BY isActive DESC, nameEn ASC")
    fun observeBranches(): Flow<List<BranchEntity>>

    @Query("SELECT COUNT(*) FROM branches")
    suspend fun branchCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBranch(branch: BranchEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBranches(branches: List<BranchEntity>)

    @Query("UPDATE branches SET isActive = (id = :branchId)")
    suspend fun setActiveBranch(branchId: String)

    @Query("DELETE FROM branches WHERE id = :branchId")
    suspend fun deleteBranch(branchId: String)

    @Query("DELETE FROM branches WHERE id != :keepId")
    suspend fun keepOnlyBranch(keepId: String)

    // Cashiers
    @Query("SELECT * FROM cashiers ORDER BY role ASC, nameEn ASC")
    fun observeCashiers(): Flow<List<CashierEntity>>

    @Query("UPDATE cashiers SET branchId = :branchId")
    suspend fun reassignAllCashiersToBranch(branchId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCashier(cashier: CashierEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCashiers(cashiers: List<CashierEntity>)

    @Query("DELETE FROM cashiers WHERE id = :cashierId")
    suspend fun deleteCashier(cashierId: String)

    // Order Sources
    @Query("SELECT * FROM order_sources ORDER BY pricingMode ASC, id ASC")
    fun observeOrderSources(): Flow<List<OrderSourceEntity>>

    @Query("SELECT id FROM order_sources")
    suspend fun getOrderSourceIds(): List<String>

    @Query("DELETE FROM order_sources")
    suspend fun clearOrderSources()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOrderSource(source: OrderSourceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrderSources(sources: List<OrderSourceEntity>)

    @Query("DELETE FROM order_sources WHERE id = :id")
    suspend fun deleteOrderSource(id: String)

    // Categories
    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, nameEn ASC")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategory(category: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Query("SELECT COUNT(*) FROM categories WHERE id = :id")
    suspend fun categoryExists(id: String): Int

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategory(id: String)

    @Query("DELETE FROM categories")
    suspend fun clearCategories()

    // Products
    @Query("SELECT * FROM products ORDER BY nameEn ASC")
    fun observeProducts(): Flow<List<ProductEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProduct(product: ProductEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<ProductEntity>)

    @Query("UPDATE products SET stock = MAX(0, stock - :qty) WHERE id = :productId")
    suspend fun decrementStock(productId: String, qty: Int)

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteProduct(id: String)

    @Query("DELETE FROM products")
    suspend fun clearProducts()

    // Orders
    @Query("SELECT * FROM orders ORDER BY createdAt DESC")
    fun observeOrders(): Flow<List<OrderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOrder(order: OrderEntity)

    @Query("DELETE FROM orders WHERE id = :id")
    suspend fun deleteOrder(id: String)

    @Query("UPDATE orders SET synced = 1 WHERE synced = 0")
    suspend fun markAllOrdersSynced()

    // Refunds
    @Query("SELECT * FROM refunds ORDER BY createdAt DESC")
    fun observeRefunds(): Flow<List<RefundEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRefund(refund: RefundEntity)

    // Shifts
    @Query("SELECT * FROM shifts WHERE isOpen = 1 ORDER BY openedAt DESC LIMIT 1")
    fun observeOpenShift(): Flow<ShiftEntity?>

    @Query("SELECT * FROM shifts ORDER BY openedAt DESC LIMIT 30")
    fun observeShiftHistory(): Flow<List<ShiftEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertShift(shift: ShiftEntity)

    // Customers
    @Query("SELECT * FROM customers ORDER BY lastVisitAt DESC")
    fun observeCustomers(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE phone = :phone LIMIT 1")
    suspend fun findCustomerByPhone(phone: String): CustomerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCustomer(customer: CustomerEntity)

    @Query("DELETE FROM customers WHERE id = :id")
    suspend fun deleteCustomer(id: String)

    // Settings
    @Query("SELECT * FROM store_settings WHERE id = 'default' LIMIT 1")
    fun observeStoreSettings(): Flow<StoreSettingsEntity?>

    @Query("SELECT * FROM store_settings WHERE id = 'default' LIMIT 1")
    suspend fun getStoreSettingsOnce(): StoreSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStoreSettings(settings: StoreSettingsEntity)

    // Sync Queue
    @Query("SELECT * FROM sync_queue WHERE status = 'PENDING' ORDER BY createdAt ASC")
    fun observePendingSync(): Flow<List<SyncQueueEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueueSync(item: SyncQueueEntity)

    @Query("UPDATE sync_queue SET status = 'SYNCED' WHERE status = 'PENDING'")
    suspend fun markSyncQueueCompleted()

    // Product Combo Offers
    @Query("SELECT * FROM product_offers ORDER BY titleEn ASC")
    fun observeOffers(): Flow<List<ProductOfferEntity>>

    @Query("SELECT COUNT(*) FROM product_offers")
    suspend fun offerCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOffer(offer: ProductOfferEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOffers(offers: List<ProductOfferEntity>)

    @Query("DELETE FROM product_offers WHERE id = :id")
    suspend fun deleteOffer(id: String)

    @Query("DELETE FROM product_offers")
    suspend fun clearOffers()
}
