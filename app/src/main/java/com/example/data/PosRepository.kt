package com.example.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import com.example.data.local.*
import com.example.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class PosRepository(private val context: Context) {
    private val dao = PosDatabase.getInstance(context).posDao()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val branches: Flow<List<Branch>> = dao.observeBranches().map { list ->
        list.map { Branch(it.id, it.nameEn, it.nameAr, it.address, it.phone, it.isActive) }
    }

    val cashiers: Flow<List<Cashier>> = dao.observeCashiers().map { list ->
        list.map {
            val perms = runCatching {
                json.decodeFromString<CashierPermissions>(it.permissionsJson)
            }.getOrDefault(CashierPermissions())
            Cashier(it.id, it.nameEn, it.nameAr, it.pin, it.role, it.branchId, perms)
        }
    }

    val orderSources: Flow<List<OrderSource>> = dao.observeOrderSources().map { list ->
        list.map {
            val mode = runCatching { PricingMode.valueOf(it.pricingMode) }.getOrDefault(PricingMode.OFFLINE)
            OrderSource(it.id, it.nameEn, it.nameAr, mode, it.iconKey, it.isEnabled)
        }
    }

    val categories: Flow<List<Category>> = dao.observeCategories().map { list ->
        list.map { Category(it.id, it.nameEn, it.nameAr, it.sortOrder, it.parentCategoryId) }
    }

    val products: Flow<List<Product>> = dao.observeProducts().map { list ->
        list.map {
            Product(
                it.id, it.categoryId, it.nameEn, it.nameAr, it.sku, it.barcode,
                it.offlinePrice, it.onlinePrice, it.stock, it.isAvailable, it.imageUri
            )
        }
    }

    val productOffers: Flow<List<ProductOffer>> = dao.observeOffers().map { list ->
        list.map { entity ->
            val options = runCatching { json.decodeFromString<List<OfferOption>>(entity.optionsJson) }.getOrDefault(emptyList())
            ProductOffer(
                id = entity.id,
                titleEn = entity.titleEn,
                titleAr = entity.titleAr,
                targetProductId = entity.targetProductId,
                isEnabled = entity.isEnabled,
                options = options
            )
        }
    }

    val orders: Flow<List<PosOrder>> = dao.observeOrders().map { list ->
        list.map { entity ->
            val items = runCatching { json.decodeFromString<List<CartItem>>(entity.itemsJson) }.getOrDefault(emptyList())
            val payment = runCatching { json.decodeFromString<PaymentBreakdown>(entity.paymentJson) }.getOrDefault(PaymentBreakdown())
            val pricingMode = runCatching { PricingMode.valueOf(entity.pricingMode) }.getOrDefault(PricingMode.OFFLINE)
            val discountType = runCatching { DiscountType.valueOf(entity.discountType) }.getOrDefault(DiscountType.PERCENT)
            val status = runCatching { OrderStatus.valueOf(entity.status) }.getOrDefault(OrderStatus.COMPLETED)
            PosOrder(
                id = entity.id,
                orderNumber = entity.orderNumber,
                branchId = entity.branchId,
                shiftId = entity.shiftId,
                cashierId = entity.cashierId,
                cashierName = entity.cashierName,
                sourceId = entity.sourceId,
                sourceNameEn = entity.sourceNameEn,
                sourceNameAr = entity.sourceNameAr,
                pricingMode = pricingMode,
                items = items,
                subtotal = entity.subtotal,
                discountType = discountType,
                discountValue = entity.discountValue,
                discountAmount = entity.discountAmount,
                vatRate = entity.vatRate,
                vatInclusive = entity.vatInclusive,
                vatAmount = entity.vatAmount,
                grandTotal = entity.grandTotal,
                payment = payment,
                customerName = entity.customerName,
                customerPhone = entity.customerPhone,
                tableOrCarNumber = entity.tableOrCarNumber,
                notes = entity.notes,
                status = status,
                refundedAmount = entity.refundedAmount,
                createdAt = entity.createdAt,
                synced = entity.synced
            )
        }
    }

    val refunds: Flow<List<RefundRecord>> = dao.observeRefunds().map { list ->
        list.map {
            val method = runCatching { RefundMethod.valueOf(it.method) }.getOrDefault(RefundMethod.CASH)
            RefundRecord(
                it.id, it.orderId, it.orderNumber, it.branchId, it.shiftId,
                it.cashierId, it.amount, method, it.reason, it.itemsSummary, it.createdAt
            )
        }
    }

    val openShift: Flow<ShiftSession?> = dao.observeOpenShift().map { entity ->
        entity?.let {
            ShiftSession(
                it.id, it.branchId, it.cashierId, it.cashierName, it.openingFloat,
                it.openedAt, it.closedAt, it.expectedCash, it.countedCash, it.variance, it.isOpen
            )
        }
    }

    val shiftHistory: Flow<List<ShiftSession>> = dao.observeShiftHistory().map { list ->
        list.map {
            ShiftSession(
                it.id, it.branchId, it.cashierId, it.cashierName, it.openingFloat,
                it.openedAt, it.closedAt, it.expectedCash, it.countedCash, it.variance, it.isOpen
            )
        }
    }

    val customers: Flow<List<Customer>> = dao.observeCustomers().map { list ->
        list.map {
            Customer(it.id, it.name, it.phone, it.notes, it.totalSpent, it.dueBalance, it.visitsCount, it.lastVisitAt)
        }
    }

    val storeSettings: Flow<StoreSettings> = dao.observeStoreSettings().map { entity ->
        entity?.let {
            runCatching { json.decodeFromString<StoreSettings>(it.settingsJson) }.getOrNull()
        } ?: StoreSettings()
    }

    val pendingSyncCount: Flow<Int> = dao.observePendingSync().map { it.size }

    fun observeNetworkOnline(): Flow<Boolean> = callbackFlow {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        fun checkNow(): Boolean {
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
        trySend(checkNow())

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(true) }
            override fun onLost(network: Network) { trySend(checkNow()) }
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                trySend(networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            }
        }
        val req = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
        runCatching { cm.registerNetworkCallback(req, callback) }
        awaitClose { runCatching { cm.unregisterNetworkCallback(callback) } }
    }.distinctUntilChanged()

    suspend fun seedInitialDataIfNeeded() = withContext(Dispatchers.IO) {
        val twoSources = listOf(
            OrderSourceEntity("src_offline", "Offline Order", "طلب محلي", PricingMode.OFFLINE.name, "store", true),
            OrderSourceEntity("src_online", "Online Order", "طلب أونلاين", PricingMode.ONLINE.name, "delivery", true)
        )
        val crispyCategories = listOf(
            CategoryEntity("cat_zingers", "Zingers", "زينجر", 1, null),
            CategoryEntity("cat_twisters", "Twisters", "تويستر", 2, null),
            CategoryEntity("cat_crunchies", "Crunchies", "قرمشة", 3, null),
            CategoryEntity("cat_drinks", "Drinks", "مشروبات", 4, null),
            CategoryEntity("cat_soft_drinks", "Soft Drinks", "مشروب غازي", 1, "cat_drinks"),
            CategoryEntity("cat_sauces", "Sauces", "صوصات", 5, null)
        )
        val crispyProducts = listOf(
            // Zingers
            ProductEntity("prd_z_sig", "cat_zingers", "Signature Zinger", "زينجر سيجنتشر", "ZN-101", "6281001001011", 15.0, 18.0, 120, true),
            ProductEntity("prd_z_orig", "cat_zingers", "Original Zinger", "زينجر أوريجينال", "ZN-102", "6281001001028", 15.0, 18.0, 120, true),
            ProductEntity("prd_z_nash", "cat_zingers", "Nashville Zinger", "زينجر ناشفيل", "ZN-103", "6281001001035", 16.0, 19.0, 100, true),
            // Twisters
            ProductEntity("prd_t_orig", "cat_twisters", "Original Twister", "تويستر أوريجينال", "TW-201", "6281001002018", 12.0, 15.0, 120, true),
            ProductEntity("prd_t_nash", "cat_twisters", "Nashville Twister", "تويستر ناشفيل", "TW-202", "6281001002025", 15.0, 18.0, 100, true),
            ProductEntity("prd_t_sig", "cat_twisters", "Signature Twister", "تويستر سيجنتشر", "TW-203", "6281001002032", 12.0, 15.0, 120, true),
            // Crunchies
            ProductEntity("prd_c_fries", "cat_crunchies", "French Fries", "بطاطس", "CR-301", "6281001003015", 6.0, 8.0, 200, true),
            ProductEntity("prd_c_spicy", "cat_crunchies", "Spicy Fries", "بطاطس مبهر", "CR-302", "6281001003022", 8.0, 10.0, 150, true),
            ProductEntity("prd_c_mozz", "cat_crunchies", "Mozzarella Sticks", "أصابع موزاريلا", "CR-303", "6281001003039", 12.0, 15.0, 80, true),
            ProductEntity("prd_c_onion", "cat_crunchies", "Onion Rings", "حلقات بصل", "CR-304", "6281001003046", 7.0, 9.0, 100, true),
            // Drinks (Water directly inside Drinks + Soft Drinks sub-folder)
            ProductEntity("prd_d_water", "cat_drinks", "Water", "ماء", "DR-401", "6281001004012", 1.0, 2.0, 300, true),
            // Soft Drinks (Sub-folder inside Drinks)
            ProductEntity("prd_sd_pepsi", "cat_soft_drinks", "Pepsi", "بيبسي", "SD-411", "6281001004111", 3.0, 4.0, 200, true),
            ProductEntity("prd_sd_pepsi_diet", "cat_soft_drinks", "Pepsi Diet", "بيبسي دايت", "SD-412", "6281001004128", 3.0, 4.0, 150, true),
            ProductEntity("prd_sd_dew", "cat_soft_drinks", "Mountain Dew", "ماونتن ديو", "SD-413", "6281001004135", 3.0, 4.0, 150, true),
            ProductEntity("prd_sd_7up", "cat_soft_drinks", "7Up Zero", "سفن أب زيرو", "SD-414", "6281001004142", 3.0, 4.0, 150, true),
            ProductEntity("prd_sd_mirinda", "cat_soft_drinks", "Mirinda Citrus", "ميرندا حمضيات", "SD-415", "6281001004159", 3.0, 4.0, 150, true),
            // Sauces
            ProductEntity("prd_s_sig", "cat_sauces", "Signature Sauce", "صوص سيجنتشر", "SC-501", "6281001005019", 2.0, 3.0, 250, true),
            ProductEntity("prd_s_cheese", "cat_sauces", "Cheese Sauce", "صوص الجبن", "SC-502", "6281001005026", 2.0, 3.0, 250, true),
            ProductEntity("prd_s_nash", "cat_sauces", "Nashville Seasoning", "بهارات ناشفيل", "SC-503", "6281001005033", 2.0, 3.0, 200, true)
        )
        val sandwichMealOptions = listOf(
            OfferOption("opt_only", "Sandwich Only", "ساندويتش فقط", 0.0),
            OfferOption("opt_mozz", "Add Mozzarella Slice", "إضافة شريحة موزريلا", 4.0),
            OfferOption("opt_meal_pepsi", "Make it a Meal (Fries + Pepsi)", "وجبة (بطاطس + بيبسي)", 7.0),
            OfferOption("opt_meal_diet", "Make it a Meal (Fries + Pepsi Diet)", "وجبة (بطاطس + بيبسي دايت)", 7.0),
            OfferOption("opt_meal_dew", "Make it a Meal (Fries + Mountain Dew)", "وجبة (بطاطس + ماونتن ديو)", 7.0),
            OfferOption("opt_meal_7up", "Make it a Meal (Fries + 7Up Zero)", "وجبة (بطاطس + سفن أب زيرو)", 7.0),
            OfferOption("opt_meal_mirinda", "Make it a Meal (Fries + Mirinda)", "وجبة (بطاطس + ميرندا)", 7.0),
            OfferOption("opt_meal_mozz", "Make it a Meal + Add Mozzarella", "وجبة + إضافة شريحة موزريلا", 11.0)
        )
        val crispyOffers = listOf(
            ProductOfferEntity(
                id = "off_zingers_meal",
                titleEn = "Zinger: Make it a Meal / Add Mozzarella",
                titleAr = "زينجر: حولها لوجبة أو أضف موزريلا",
                targetProductId = "cat:cat_zingers",
                isEnabled = true,
                optionsJson = json.encodeToString(sandwichMealOptions)
            ),
            ProductOfferEntity(
                id = "off_twisters_meal",
                titleEn = "Twister: Make it a Meal / Add Mozzarella",
                titleAr = "تويستر: حولها لوجبة أو أضف موزريلا",
                targetProductId = "cat:cat_twisters",
                isEnabled = true,
                optionsJson = json.encodeToString(sandwichMealOptions)
            )
        )

        val singleBranchId = "br_hofuf"

        if (dao.branchCount() > 0) {
            val existingIds = dao.getOrderSourceIds().toSet()
            if (existingIds != setOf("src_offline", "src_online")) {
                dao.clearOrderSources()
                dao.insertOrderSources(twoSources)
            }
            // Migrate old café demo catalog cleanly to Crispy Nation organized menu
            if (dao.categoryExists("cat_zingers") == 0) {
                dao.clearCategories()
                dao.clearProducts()
                dao.clearOffers()
                dao.insertCategories(crispyCategories)
                dao.insertProducts(crispyProducts)
                dao.insertOffers(crispyOffers)
            }

            // Migrate existing settings to Al Hofuf defaults if still using legacy Riyadh defaults & enforce 80mm
            val currentSettingsEntity = dao.getStoreSettingsOnce()
            val loadedSettings = currentSettingsEntity?.let {
                runCatching { json.decodeFromString<StoreSettings>(it.settingsJson) }.getOrNull()
            } ?: StoreSettings()
            var migratedSettings = loadedSettings.copy(paperWidthMm = 80)
            if (migratedSettings.shopNameEn.contains("CashFix", ignoreCase = true)) {
                migratedSettings = migratedSettings.copy(shopNameEn = "Crispy Nation", shopNameAr = "كريسبي نيشن")
            }
            if (migratedSettings.addressEn.contains("Olaya", ignoreCase = true) || migratedSettings.addressEn.contains("Riyadh", ignoreCase = true)) {
                migratedSettings = migratedSettings.copy(
                    addressEn = "Al Hofuf, Saudi Arabia",
                    addressAr = "الأحساء، المملكة العربية السعودية"
                )
            }
            if (migratedSettings != loadedSettings) {
                dao.upsertStoreSettings(StoreSettingsEntity("default", json.encodeToString(migratedSettings)))
            }

            // Consolidate to a single branch aligned with Store Setup
            dao.upsertBranch(
                BranchEntity(
                    id = singleBranchId,
                    nameEn = migratedSettings.addressEn.ifBlank { "Al Hofuf, Saudi Arabia" },
                    nameAr = migratedSettings.addressAr.ifBlank { "الأحساء، المملكة العربية السعودية" },
                    address = migratedSettings.addressEn.ifBlank { "Al Hofuf, Saudi Arabia" },
                    phone = migratedSettings.phone,
                    isActive = true
                )
            )
            dao.keepOnlyBranch(singleBranchId)
            dao.reassignAllCashiersToBranch(singleBranchId)
            return@withContext
        }

        val defaultSettings = StoreSettings()
        dao.insertBranches(
            listOf(
                BranchEntity(
                    id = singleBranchId,
                    nameEn = defaultSettings.addressEn,
                    nameAr = defaultSettings.addressAr,
                    address = defaultSettings.addressEn,
                    phone = defaultSettings.phone,
                    isActive = true
                )
            )
        )

        val adminPerms = CashierPermissions(true, true, true, true, true, true)
        val seniorPerms = CashierPermissions(true, false, true, true, true, false)
        val basicPerms = CashierPermissions(true, false, false, true, true, false)

        dao.insertCashiers(
            listOf(
                CashierEntity("csh_admin", "Tariq Al-Otaibi (Admin)", "طارق العتيبي (مدير)", "1234", "admin", singleBranchId, json.encodeToString(adminPerms)),
                CashierEntity("csh_sara", "Sara Al-Harbi", "سارة الحربي", "1111", "cashier", singleBranchId, json.encodeToString(basicPerms)),
                CashierEntity("csh_fahad", "Fahad Al-Qahtani", "فهد القحطاني", "2222", "cashier", singleBranchId, json.encodeToString(seniorPerms))
            )
        )

        dao.insertOrderSources(twoSources)
        dao.insertCategories(crispyCategories)
        dao.insertProducts(crispyProducts)
        dao.insertOffers(crispyOffers)

        dao.upsertCustomer(CustomerEntity("cust_1", "Abdullah Al-Mansour", "0551122334", "VIP Regular - Signature Zinger", 420.0, 0.0, 12, System.currentTimeMillis() - 86400000L))
        dao.upsertCustomer(CustomerEntity("cust_2", "Noura Al-Dosari", "0509988776", "Corporate Account", 890.0, 45.0, 18, System.currentTimeMillis() - 43200000L))
        dao.upsertCustomer(CustomerEntity("cust_3", "Khalid Al-Ghamdi", "0543344556", "", 195.0, 0.0, 5, System.currentTimeMillis() - 172800000L))

        dao.upsertStoreSettings(StoreSettingsEntity("default", json.encodeToString(defaultSettings)))
    }

    suspend fun openShift(branchId: String, cashier: Cashier, openingFloat: Double): ShiftSession = withContext(Dispatchers.IO) {
        val shift = ShiftSession(
            id = "shf_${UUID.randomUUID().toString().take(8)}",
            branchId = branchId,
            cashierId = cashier.id,
            cashierName = cashier.nameEn,
            openingFloat = openingFloat,
            openedAt = System.currentTimeMillis(),
            isOpen = true
        )
        dao.upsertShift(
            ShiftEntity(
                shift.id, shift.branchId, shift.cashierId, shift.cashierName,
                shift.openingFloat, shift.openedAt, null, 0.0, 0.0, 0.0, true
            )
        )
        dao.setActiveBranch(branchId)
        enqueueSyncItem("SHIFT_OPEN", shift.id, json.encodeToString(shift))
        shift
    }

    suspend fun updateShiftOpeningFloat(shift: ShiftSession, openingFloat: Double) = withContext(Dispatchers.IO) {
        val safeFloat = openingFloat.coerceAtLeast(0.0)
        val updated = shift.copy(openingFloat = safeFloat)
        dao.upsertShift(
            ShiftEntity(
                updated.id, updated.branchId, updated.cashierId, updated.cashierName,
                updated.openingFloat, updated.openedAt, updated.closedAt,
                updated.expectedCash, updated.countedCash, updated.variance, updated.isOpen
            )
        )
        enqueueSyncItem("SHIFT_UPDATE_FLOAT", updated.id, json.encodeToString(updated))
    }

    suspend fun closeShift(shift: ShiftSession, expectedCash: Double, countedCash: Double) = withContext(Dispatchers.IO) {
        val variance = countedCash - expectedCash
        val closed = shift.copy(
            closedAt = System.currentTimeMillis(),
            expectedCash = expectedCash,
            countedCash = countedCash,
            variance = variance,
            isOpen = false
        )
        dao.upsertShift(
            ShiftEntity(
                closed.id, closed.branchId, closed.cashierId, closed.cashierName,
                closed.openingFloat, closed.openedAt, closed.closedAt,
                closed.expectedCash, closed.countedCash, closed.variance, false
            )
        )
        enqueueSyncItem("SHIFT_CLOSE", closed.id, json.encodeToString(closed))
    }

    suspend fun saveOrder(order: PosOrder, decrementInventory: Boolean) = withContext(Dispatchers.IO) {
        dao.upsertOrder(
            OrderEntity(
                id = order.id,
                orderNumber = order.orderNumber,
                branchId = order.branchId,
                shiftId = order.shiftId,
                cashierId = order.cashierId,
                cashierName = order.cashierName,
                sourceId = order.sourceId,
                sourceNameEn = order.sourceNameEn,
                sourceNameAr = order.sourceNameAr,
                pricingMode = order.pricingMode.name,
                itemsJson = json.encodeToString(order.items),
                subtotal = order.subtotal,
                discountType = order.discountType.name,
                discountValue = order.discountValue,
                discountAmount = order.discountAmount,
                vatRate = order.vatRate,
                vatInclusive = order.vatInclusive,
                vatAmount = order.vatAmount,
                grandTotal = order.grandTotal,
                paymentJson = json.encodeToString(order.payment),
                customerName = order.customerName,
                customerPhone = order.customerPhone,
                tableOrCarNumber = order.tableOrCarNumber,
                notes = order.notes,
                status = order.status.name,
                refundedAmount = order.refundedAmount,
                createdAt = order.createdAt,
                synced = order.synced
            )
        )

        if (decrementInventory && order.status == OrderStatus.COMPLETED) {
            order.items.filter { !it.isCustom }.forEach { item ->
                dao.decrementStock(item.productId, item.qty)
            }
            if (order.customerPhone.isNotBlank() || order.customerName.isNotBlank()) {
                val existing = if (order.customerPhone.isNotBlank()) dao.findCustomerByPhone(order.customerPhone) else null
                val updated = if (existing != null) {
                    existing.copy(
                        name = order.customerName.ifBlank { existing.name },
                        totalSpent = existing.totalSpent + order.grandTotal,
                        dueBalance = existing.dueBalance + order.payment.dueAmount,
                        visitsCount = existing.visitsCount + 1,
                        lastVisitAt = System.currentTimeMillis()
                    )
                } else {
                    CustomerEntity(
                        id = "cust_${UUID.randomUUID().toString().take(8)}",
                        name = order.customerName.ifBlank { "Walk-in Customer" },
                        phone = order.customerPhone,
                        notes = "",
                        totalSpent = order.grandTotal,
                        dueBalance = order.payment.dueAmount,
                        visitsCount = 1,
                        lastVisitAt = System.currentTimeMillis()
                    )
                }
                dao.upsertCustomer(updated)
            }
        }
        enqueueSyncItem("ORDER_${order.status.name}", order.id, json.encodeToString(order))
    }

    suspend fun deleteOrder(orderId: String) = withContext(Dispatchers.IO) {
        dao.deleteOrder(orderId)
    }

    suspend fun processRefund(
        order: PosOrder,
        shiftId: String,
        cashierId: String,
        amount: Double,
        method: RefundMethod,
        reason: String,
        itemsSummary: String
    ) = withContext(Dispatchers.IO) {
        val refund = RefundRecord(
            id = "ref_${UUID.randomUUID().toString().take(8)}",
            orderId = order.id,
            orderNumber = order.orderNumber,
            branchId = order.branchId,
            shiftId = shiftId,
            cashierId = cashierId,
            amount = amount,
            method = method,
            reason = reason,
            itemsSummary = itemsSummary,
            createdAt = System.currentTimeMillis()
        )
        dao.insertRefund(
            RefundEntity(
                refund.id, refund.orderId, refund.orderNumber, refund.branchId, refund.shiftId,
                refund.cashierId, refund.amount, refund.method.name, refund.reason, refund.itemsSummary, refund.createdAt
            )
        )
        val newRefunded = (order.refundedAmount + amount).coerceAtMost(order.grandTotal)
        val newStatus = if (newRefunded >= order.grandTotal - 0.01) OrderStatus.REFUNDED else OrderStatus.PARTIALLY_REFUNDED
        saveOrder(order.copy(refundedAmount = newRefunded, status = newStatus), decrementInventory = false)
        enqueueSyncItem("REFUND", refund.id, json.encodeToString(refund))
    }

    // Admin CRUD
    suspend fun upsertBranch(branch: Branch) = withContext(Dispatchers.IO) {
        dao.upsertBranch(BranchEntity(branch.id, branch.nameEn, branch.nameAr, branch.address, branch.phone, branch.isActive))
        if (branch.isActive) dao.setActiveBranch(branch.id)
    }

    suspend fun setActiveBranch(branchId: String) = withContext(Dispatchers.IO) {
        dao.setActiveBranch(branchId)
    }

    suspend fun deleteBranch(branchId: String) = withContext(Dispatchers.IO) {
        dao.deleteBranch(branchId)
    }

    suspend fun upsertCategory(cat: Category) = withContext(Dispatchers.IO) {
        dao.upsertCategory(CategoryEntity(cat.id, cat.nameEn, cat.nameAr, cat.sortOrder, cat.parentCategoryId))
    }

    suspend fun deleteCategory(id: String) = withContext(Dispatchers.IO) {
        dao.deleteCategory(id)
    }

    suspend fun upsertOffer(offer: ProductOffer) = withContext(Dispatchers.IO) {
        dao.upsertOffer(
            ProductOfferEntity(
                id = offer.id,
                titleEn = offer.titleEn,
                titleAr = offer.titleAr,
                targetProductId = offer.targetProductId,
                isEnabled = offer.isEnabled,
                optionsJson = json.encodeToString(offer.options)
            )
        )
    }

    suspend fun deleteOffer(id: String) = withContext(Dispatchers.IO) {
        dao.deleteOffer(id)
    }

    suspend fun upsertProduct(p: Product) = withContext(Dispatchers.IO) {
        dao.upsertProduct(
            ProductEntity(
                p.id, p.categoryId, p.nameEn, p.nameAr, p.sku, p.barcode,
                p.offlinePrice, p.onlinePrice, p.stock, p.isAvailable, p.imageUri
            )
        )
    }

    suspend fun importProductImage(uriString: String, productId: String): String = withContext(Dispatchers.IO) {
        if (uriString.isBlank()) return@withContext ""
        val existingFile = File(uriString)
        if (existingFile.exists() && existingFile.isFile) return@withContext existingFile.absolutePath

        runCatching {
            val uri = Uri.parse(uriString)
            val dir = File(context.filesDir, "product_images").apply { if (!exists()) mkdirs() }
            val safeId = productId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val dest = File(dir, "${safeId}_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            if (dest.exists() && dest.length() > 0L) dest.absolutePath else ""
        }.getOrDefault("")
    }

    suspend fun deleteProduct(id: String) = withContext(Dispatchers.IO) {
        dao.deleteProduct(id)
    }

    suspend fun upsertCashier(c: Cashier) = withContext(Dispatchers.IO) {
        dao.upsertCashier(
            CashierEntity(
                c.id, c.nameEn, c.nameAr, c.pin, c.role, c.branchId,
                json.encodeToString(c.permissions)
            )
        )
    }

    suspend fun deleteCashier(id: String) = withContext(Dispatchers.IO) {
        dao.deleteCashier(id)
    }

    suspend fun upsertOrderSource(s: OrderSource) = withContext(Dispatchers.IO) {
        dao.upsertOrderSource(
            OrderSourceEntity(s.id, s.nameEn, s.nameAr, s.pricingMode.name, s.iconKey, s.isEnabled)
        )
    }

    suspend fun deleteOrderSource(id: String) = withContext(Dispatchers.IO) {
        dao.deleteOrderSource(id)
    }

    suspend fun upsertCustomer(c: Customer) = withContext(Dispatchers.IO) {
        dao.upsertCustomer(
            CustomerEntity(c.id, c.name, c.phone, c.notes, c.totalSpent, c.dueBalance, c.visitsCount, c.lastVisitAt)
        )
    }

    suspend fun deleteCustomer(id: String) = withContext(Dispatchers.IO) {
        dao.deleteCustomer(id)
    }

    suspend fun saveStoreSettings(settings: StoreSettings) = withContext(Dispatchers.IO) {
        val normalized = settings.copy(paperWidthMm = 80)
        dao.upsertStoreSettings(StoreSettingsEntity("default", json.encodeToString(normalized)))
        // Sync single branch entry directly from Store Setup
        dao.upsertBranch(
            BranchEntity(
                id = "br_hofuf",
                nameEn = normalized.addressEn.ifBlank { "Al Hofuf, Saudi Arabia" },
                nameAr = normalized.addressAr.ifBlank { "الأحساء، المملكة العربية السعودية" },
                address = normalized.addressEn.ifBlank { "Al Hofuf, Saudi Arabia" },
                phone = normalized.phone,
                isActive = true
            )
        )
        dao.keepOnlyBranch("br_hofuf")
    }

    private suspend fun enqueueSyncItem(entityType: String, entityId: String, payloadJson: String) {
        dao.enqueueSync(
            SyncQueueEntity(
                id = "syn_${UUID.randomUUID().toString().take(8)}",
                entityType = entityType,
                entityId = entityId,
                payloadJson = payloadJson,
                createdAt = System.currentTimeMillis(),
                retryCount = 0,
                status = "PENDING"
            )
        )
    }

    suspend fun runCloudSync(): Boolean = withContext(Dispatchers.IO) {
        delay(350)
        dao.markSyncQueueCompleted()
        dao.markAllOrdersSynced()
        true
    }
}
