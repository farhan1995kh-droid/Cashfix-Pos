package com.example.ui

import android.app.Application
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.PosRepository
import com.example.hardware.*
import com.example.i18n.AppLang
import com.example.i18n.formatNumber
import com.example.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.abs

enum class ActiveDestination {
    POS_REGISTER,
    HELD_ORDERS,
    SALES_HISTORY,
    CUSTOMER_QR_MODE,
    PRINTER_SETTINGS,
    ADMIN_PANEL,
    CUSTOMER_CRM
}

data class CartTotals(
    val subtotal: Double = 0.0,
    val discountAmount: Double = 0.0,
    val vatAmount: Double = 0.0,
    val grandTotal: Double = 0.0
)

data class ReceiptDialogState(
    val order: PosOrder,
    val mode: ReceiptCopyMode = ReceiptCopyMode.BOTH
)

data class ShiftMemoState(
    val shift: ShiftSession,
    val metrics: ShiftSummaryMetrics,
    val branchNameEn: String,
    val branchNameAr: String
)

class PosViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val repo = PosRepository(application)
    private val receiptTypeface: Typeface? = runCatching {
        ResourcesCompat.getFont(application, R.font.cairo)
    }.getOrNull()

    val branches: StateFlow<List<Branch>> = repo.branches.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val cashiers: StateFlow<List<Cashier>> = repo.cashiers.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val orderSources: StateFlow<List<OrderSource>> = repo.orderSources.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val categories: StateFlow<List<Category>> = repo.categories.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val products: StateFlow<List<Product>> = repo.products.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val productOffers: StateFlow<List<ProductOffer>> = repo.productOffers.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val orders: StateFlow<List<PosOrder>> = repo.orders.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val refunds: StateFlow<List<RefundRecord>> = repo.refunds.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val openShift: StateFlow<ShiftSession?> = repo.openShift.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val shiftHistory: StateFlow<List<ShiftSession>> = repo.shiftHistory.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val customers: StateFlow<List<Customer>> = repo.customers.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val storeSettings: StateFlow<StoreSettings> = repo.storeSettings.stateIn(viewModelScope, SharingStarted.Eagerly, StoreSettings())
    val pendingSyncCount: StateFlow<Int> = repo.pendingSyncCount.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val isOnline: StateFlow<Boolean> = repo.observeNetworkOnline().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _lang = MutableStateFlow(AppLang.EN)
    val lang: StateFlow<AppLang> = _lang.asStateFlow()

    private val _darkTheme = MutableStateFlow(false)
    val darkTheme: StateFlow<Boolean> = _darkTheme.asStateFlow()

    private val _activeCashierId = MutableStateFlow<String?>(null)
    val activeCashier: StateFlow<Cashier?> = combine(cashiers, _activeCashierId, openShift) { list, id, shift ->
        val resolvedId = id ?: shift?.cashierId
        list.firstOrNull { it.id == resolvedId }
            ?: list.firstOrNull { !it.isAdminOrManager }
            ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _isAdminSessionUnlocked = MutableStateFlow(false)
    val isAdminSessionUnlocked: StateFlow<Boolean> = _isAdminSessionUnlocked.asStateFlow()

    val activeBranch: StateFlow<Branch?> = combine(branches, storeSettings) { list, settings ->
        list.firstOrNull { it.id == "br_hofuf" }
            ?: list.firstOrNull { it.isActive }
            ?: list.firstOrNull()
            ?: Branch(
                id = "br_hofuf",
                nameEn = settings.addressEn,
                nameAr = settings.addressAr,
                address = settings.addressEn,
                phone = settings.phone,
                isActive = true
            )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _destination = MutableStateFlow(ActiveDestination.POS_REGISTER)
    val destination: StateFlow<ActiveDestination> = _destination.asStateFlow()

    // POS Selling State
    private val _selectedCategoryId = MutableStateFlow<String?>(null)
    val selectedCategoryId: StateFlow<String?> = _selectedCategoryId.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedSourceId = MutableStateFlow("src_offline")
    val selectedOrderSource: StateFlow<OrderSource?> = combine(orderSources, _selectedSourceId) { list, id ->
        list.firstOrNull { it.id == id } ?: list.firstOrNull { it.isEnabled }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _cartItems = MutableStateFlow<List<CartItem>>(emptyList())
    val cartItems: StateFlow<List<CartItem>> = _cartItems.asStateFlow()

    private val _discountType = MutableStateFlow(DiscountType.PERCENT)
    val discountType: StateFlow<DiscountType> = _discountType.asStateFlow()

    private val _discountValue = MutableStateFlow(0.0)
    val discountValue: StateFlow<Double> = _discountValue.asStateFlow()

    private val _customerName = MutableStateFlow("")
    val customerName: StateFlow<String> = _customerName.asStateFlow()

    private val _customerPhone = MutableStateFlow("")
    val customerPhone: StateFlow<String> = _customerPhone.asStateFlow()

    private val _tableOrCar = MutableStateFlow("")
    val tableOrCar: StateFlow<String> = _tableOrCar.asStateFlow()

    private val _orderNotes = MutableStateFlow("")
    val orderNotes: StateFlow<String> = _orderNotes.asStateFlow()

    private val _editingHeldOrderId = MutableStateFlow<String?>(null)

    val cartTotals: StateFlow<CartTotals> = combine(
        _cartItems, _discountType, _discountValue, storeSettings
    ) { items, dType, dVal, settings ->
        val sub = items.sumOf { it.unitPrice * it.qty }
        val disc = when (dType) {
            DiscountType.PERCENT -> sub * (dVal.coerceIn(0.0, 100.0) / 100.0)
            DiscountType.FIXED -> dVal.coerceIn(0.0, sub)
        }
        val net = (sub - disc).coerceAtLeast(0.0)
        val vat = if (settings.vatInclusive) {
            net - (net / (1.0 + settings.vatRate))
        } else {
            net * settings.vatRate
        }
        val grand = if (settings.vatInclusive) net else net + vat
        CartTotals(sub, disc, vat, grand)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CartTotals())

    // Modals & Alerts
    private val _receiptState = MutableStateFlow<ReceiptDialogState?>(null)
    val receiptState: StateFlow<ReceiptDialogState?> = _receiptState.asStateFlow()

    private val _shiftMemoState = MutableStateFlow<ShiftMemoState?>(null)
    val shiftMemoState: StateFlow<ShiftMemoState?> = _shiftMemoState.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    // Incoming QR Order & 15-Second Alarm (shared with CashFixOrderAlertService for multi-app background alerts)
    val incomingQrOrders: StateFlow<List<PosOrder>> = orders.map { list ->
        list.filter { it.status == OrderStatus.INCOMING_QR }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val alarmSecondsRemaining: StateFlow<Int> = QrOrderRuntimeHub.alarmSecondsRemaining
    val backgroundAlertsActive: StateFlow<Boolean> = QrOrderRuntimeHub.isServiceRunning
    val qrGatewayRunning: StateFlow<Boolean> = QrOrderRuntimeHub.isGatewayRunning

    // Printer Scanner
    private val _discoveredPrinters = MutableStateFlow<List<DiscoveredPrinter>>(emptyList())
    val discoveredPrinters: StateFlow<List<DiscoveredPrinter>> = _discoveredPrinters.asStateFlow()

    private val _isScanningPrinters = MutableStateFlow(false)
    val isScanningPrinters: StateFlow<Boolean> = _isScanningPrinters.asStateFlow()

    val localDeviceIp: String get() = ZatcaQrAndEscPos.getLocalIpv4Address()

    init {
        QrOrderRuntimeHub.ensureNotificationChannels(appContext)
        viewModelScope.launch {
            repo.seedInitialDataIfNeeded()
        }
        // Keep default cashier selected without silently auto-opening a 0.00 shift;
        // shifts start when cashier enters Starting Cash (no PIN lock for cashiers).
        viewModelScope.launch {
            combine(cashiers, openShift) { cList, shift -> cList to shift }
                .collect { (cList, shift) ->
                    if (shift != null) {
                        if (_activeCashierId.value == null) {
                            _activeCashierId.value = shift.cashierId
                        }
                    } else if (cList.isNotEmpty() && _activeCashierId.value == null) {
                        val defaultCashier = cList.firstOrNull { !it.isAdminOrManager } ?: cList.first()
                        _activeCashierId.value = defaultCashier.id
                    }
                }
        }
        viewModelScope.launch {
            combine(storeSettings, categories, products, productOffers) { settings, cats, prods, offers ->
                listOf(settings, cats, prods, offers)
            }.collect { tuple ->
                val settings = tuple[0] as StoreSettings
                @Suppress("UNCHECKED_CAST")
                val cats = tuple[1] as List<Category>
                @Suppress("UNCHECKED_CAST")
                val prods = tuple[2] as List<Product>
                @Suppress("UNCHECKED_CAST")
                val offers = tuple[3] as List<ProductOffer>
                QrOrderRuntimeHub.updateCatalogSnapshot(settings, cats, prods, offers)
                QrOrderRuntimeHub.ensureGatewayStarted(settings.qrServerPort)
            }
        }
        viewModelScope.launch {
            QrOrderRuntimeHub.qrGateway.incomingPayloads.collect { payload ->
                // When Foreground Service is running, the service persists incoming payloads directly
                // so orders are processed even when minimized; avoid double-saving here.
                if (!QrOrderRuntimeHub.isServiceRunning.value) {
                    submitIncomingQrPayload(payload)
                }
            }
        }
        viewModelScope.launch {
            incomingQrOrders.collect { list ->
                val latest = list.firstOrNull()
                if (latest != null) {
                    QrOrderRuntimeHub.triggerIncomingOrderAlert(appContext, latest)
                } else {
                    QrOrderRuntimeHub.stopAlarmAndClearNotification(appContext)
                }
            }
        }
        // Auto sync when network returns and pending items exist
        viewModelScope.launch {
            combine(isOnline, pendingSyncCount) { online, count -> online to count }
                .collect { (online, count) ->
                    if (online && count > 0) {
                        repo.runCloudSync()
                    }
                }
        }
    }

    override fun onCleared() {
        // Keep QrOrderRuntimeHub & CashFixOrderAlertService alive when app moves to background
        super.onCleared()
    }

    fun syncBackgroundAlertService() {
        val settings = storeSettings.value
        if (settings.backgroundOrderAlertsEnabled) {
            CashFixOrderAlertService.start(appContext, settings.qrServerPort, settings.shopNameEn)
        } else {
            CashFixOrderAlertService.stop(appContext)
        }
    }

    fun toggleAlwaysOnDisplay() {
        val current = storeSettings.value
        val next = !current.alwaysOnDisplay
        viewModelScope.launch {
            repo.saveStoreSettings(current.copy(alwaysOnDisplay = next))
            showToast(
                if (_lang.value == AppLang.AR) {
                    if (next) "تم تفعيل وضع بقاء الشاشة مضاءة" else "تم إيقاف وضع بقاء الشاشة مضاءة"
                } else {
                    if (next) "Always-On Display enabled — Screen stays awake" else "Always-On Display disabled"
                }
            )
        }
    }

    fun setBackgroundOrderAlertsEnabled(enabled: Boolean) {
        val current = storeSettings.value
        viewModelScope.launch {
            repo.saveStoreSettings(current.copy(backgroundOrderAlertsEnabled = enabled))
            if (enabled) {
                CashFixOrderAlertService.start(appContext, current.qrServerPort, current.shopNameEn)
                showToast(
                    if (_lang.value == AppLang.AR) "تم تفعيل تنبيهات الطلبات في الخلفية"
                    else "Background QR Order Alerts active — safe to switch apps"
                )
            } else {
                CashFixOrderAlertService.stop(appContext)
                showToast(
                    if (_lang.value == AppLang.AR) "تم إيقاف تنبيهات الخلفية"
                    else "Background QR Order Alerts paused"
                )
            }
        }
    }

    fun toggleLanguage() {
        _lang.value = if (_lang.value == AppLang.EN) AppLang.AR else AppLang.EN
    }

    fun setLanguage(lang: AppLang) {
        _lang.value = lang
    }

    fun toggleDarkTheme() {
        _darkTheme.value = !_darkTheme.value
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun showToast(msg: String) {
        _toastMessage.value = msg
    }

    fun navigateTo(dest: ActiveDestination) {
        if (dest != ActiveDestination.ADMIN_PANEL && dest != ActiveDestination.CUSTOMER_CRM) {
            _isAdminSessionUnlocked.value = false
        }
        _destination.value = dest
    }

    fun unlockAdminAndNavigate(dest: ActiveDestination) {
        _isAdminSessionUnlocked.value = true
        _destination.value = dest
    }

    // Shift & Auth (No cashier / day opening-closing lock; single Admin PIN protects Administration)
    fun openShiftWithPin(cashier: Cashier, pinInput: String, openingFloat: Double, branchId: String): Boolean {
        if (cashier.isAdminOrManager) {
            if (!verifyAdminPin(pinInput)) return false
            _isAdminSessionUnlocked.value = true
        } else {
            _isAdminSessionUnlocked.value = false
        }
        val targetBranchId = activeBranch.value?.id ?: branchId.ifBlank { "br_hofuf" }
        val safeFloat = openingFloat.coerceAtLeast(0.0)
        viewModelScope.launch {
            repo.openShift(targetBranchId, cashier, safeFloat)
            _activeCashierId.value = cashier.id
            _destination.value = ActiveDestination.POS_REGISTER
            showToast(
                if (_lang.value == AppLang.AR) "بدأت الوردية بعهدة ${formatNumber(safeFloat)} ر.س"
                else "Shift started with ${formatNumber(safeFloat)} SAR"
            )
        }
        return true
    }

    fun switchCashierWithoutPin(targetCashier: Cashier) {
        if (targetCashier.isAdminOrManager) return
        _activeCashierId.value = targetCashier.id
        _isAdminSessionUnlocked.value = false
        if (_destination.value == ActiveDestination.CUSTOMER_CRM) {
            _destination.value = ActiveDestination.POS_REGISTER
        }
        if (!targetCashier.permissions.canAddProducts && _destination.value == ActiveDestination.ADMIN_PANEL) {
            _destination.value = ActiveDestination.POS_REGISTER
        }
        showToast("Switched to ${targetCashier.displayName(_lang.value)}")
    }

    fun switchCashierWithPin(targetCashier: Cashier, pinInput: String): Boolean {
        if (!targetCashier.isAdminOrManager) {
            switchCashierWithoutPin(targetCashier)
            return true
        }
        if (!verifyAdminPin(pinInput)) return false
        _activeCashierId.value = targetCashier.id
        _isAdminSessionUnlocked.value = true
        showToast("Switched to ${targetCashier.displayName(_lang.value)}")
        return true
    }

    fun verifyAdminPin(pinInput: String): Boolean {
        return pinInput == storeSettings.value.adminPin
    }

    fun switchActiveBranchAsAdmin(branchId: String) {
        if (activeCashier.value?.isAdminOrManager != true && !_isAdminSessionUnlocked.value) return
        viewModelScope.launch {
            repo.setActiveBranch(branchId)
        }
    }

    // Catalog & Dual Pricing
    fun selectCategory(categoryId: String?) {
        _selectedCategoryId.value = categoryId
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        // Barcode instant match
        val trimmed = query.trim()
        if (trimmed.length >= 6) {
            val match = products.value.firstOrNull { it.barcode.equals(trimmed, ignoreCase = true) || it.sku.equals(trimmed, ignoreCase = true) }
            if (match != null) {
                addProductToCart(match)
                _searchQuery.value = ""
                showToast("Scanned: ${match.displayName(_lang.value)}")
            }
        }
    }

    fun selectOrderSource(source: OrderSource) {
        _selectedSourceId.value = source.id
        val mode = source.pricingMode
        // Automatically recalculate cart prices based on Offline vs Online Delivery App pricing
        _cartItems.update { items ->
            items.map { item ->
                if (item.isCustom) item
                else {
                    val updatedUnit = if (mode == PricingMode.ONLINE) item.onlinePrice else item.offlinePrice
                    item.copy(unitPrice = updatedUnit)
                }
            }
        }
    }

    fun addProductToCart(product: Product, qty: Int = 1, note: String = "") {
        if (!product.isAvailable) {
            showToast("Item unavailable")
            return
        }
        val safeQty = qty.coerceAtLeast(1)
        val cleanNote = note.trim()
        val mode = selectedOrderSource.value?.pricingMode ?: PricingMode.OFFLINE
        val unitPrice = product.priceFor(mode)
        _cartItems.update { list ->
            val existing = if (cleanNote.isBlank()) {
                list.firstOrNull { !it.isCustom && it.productId == product.id && it.note.isBlank() }
            } else null
            if (existing != null) {
                list.map { if (it.id == existing.id) it.copy(qty = it.qty + safeQty) else it }
            } else {
                list + CartItem(
                    id = "ci_${UUID.randomUUID().toString().take(8)}",
                    productId = product.id,
                    nameEn = product.nameEn,
                    nameAr = product.nameAr,
                    offlinePrice = product.offlinePrice,
                    onlinePrice = product.onlinePrice,
                    unitPrice = unitPrice,
                    qty = safeQty,
                    note = cleanNote
                )
            }
        }
    }

    fun addProductWithOfferToCart(product: Product, selectedOption: OfferOption?) {
        if (selectedOption == null) {
            addProductToCart(product)
            return
        }
        if (!product.isAvailable) {
            showToast("Item unavailable")
            return
        }
        val extra = selectedOption.extraPrice.coerceAtLeast(0.0)
        val offPrice = product.offlinePrice + extra
        val onPrice = product.onlinePrice + extra
        val mode = selectedOrderSource.value?.pricingMode ?: PricingMode.OFFLINE
        val unitPrice = if (mode == PricingMode.ONLINE) onPrice else offPrice
        val comboNameEn = "${product.nameEn} (+ ${selectedOption.nameEn})"
        val comboNameAr = "${product.nameAr} (+ ${selectedOption.nameAr})"
        val offerNote = if (extra <= 0.001) {
            "Offer: ${selectedOption.nameEn} (FREE)"
        } else {
            "Offer: ${selectedOption.nameEn} (+${formatNumber(extra)} SAR)"
        }
        _cartItems.update { list ->
            val existing = list.firstOrNull {
                !it.isCustom && it.productId == product.id && it.nameEn == comboNameEn && it.note == offerNote
            }
            if (existing != null) {
                list.map { if (it.id == existing.id) it.copy(qty = it.qty + 1) else it }
            } else {
                list + CartItem(
                    id = "ci_${UUID.randomUUID().toString().take(8)}",
                    productId = product.id,
                    nameEn = comboNameEn,
                    nameAr = comboNameAr,
                    offlinePrice = offPrice,
                    onlinePrice = onPrice,
                    unitPrice = unitPrice,
                    qty = 1,
                    note = offerNote
                )
            }
        }
    }

    fun addCustomItemToCart(name: String, price: Double, note: String) {
        if (name.isBlank() || price < 0.0) return
        _cartItems.update { list ->
            list + CartItem(
                id = "ci_${UUID.randomUUID().toString().take(8)}",
                productId = "custom_${UUID.randomUUID().toString().take(6)}",
                nameEn = name.trim(),
                nameAr = name.trim(),
                offlinePrice = price,
                onlinePrice = price,
                unitPrice = price,
                qty = 1,
                note = note.trim(),
                isCustom = true
            )
        }
    }

    fun updateCartItemQty(itemId: String, delta: Int) {
        _cartItems.update { list ->
            list.mapNotNull { item ->
                if (item.id != itemId) item
                else {
                    val newQty = item.qty + delta
                    if (newQty <= 0) null else item.copy(qty = newQty)
                }
            }
        }
    }

    fun updateCartItemPriceAndNote(itemId: String, newPrice: Double, newNote: String) {
        _cartItems.update { list ->
            list.map {
                if (it.id == itemId) it.copy(unitPrice = newPrice.coerceAtLeast(0.0), note = newNote.trim()) else it
            }
        }
    }

    fun setDiscount(type: DiscountType, value: Double) {
        _discountType.value = type
        _discountValue.value = value.coerceAtLeast(0.0)
    }

    fun setCustomerTagging(name: String, phone: String, tableOrCar: String, notes: String) {
        _customerName.value = name
        _customerPhone.value = phone
        _tableOrCar.value = tableOrCar
        _orderNotes.value = notes
    }

    fun clearCart() {
        _cartItems.value = emptyList()
        _discountValue.value = 0.0
        _customerName.value = ""
        _customerPhone.value = ""
        _tableOrCar.value = ""
        _orderNotes.value = ""
        _editingHeldOrderId.value = null
    }

    fun holdCurrentOrder(
        customerNameOverride: String = "",
        phoneOverride: String = "",
        tableOrCarOverride: String = "",
        notesOverride: String = ""
    ) {
        val items = _cartItems.value
        val shift = openShift.value ?: return
        val cashier = activeCashier.value ?: return
        val branch = activeBranch.value ?: return
        val source = selectedOrderSource.value ?: return
        if (items.isEmpty()) return

        val finalName = customerNameOverride.trim().ifBlank { _customerName.value.trim() }
        if (finalName.isBlank()) {
            showToast(if (_lang.value == AppLang.AR) "يرجى إدخال اسم العميل لتعليق الطلب" else "Customer name is required to hold order")
            return
        }
        val finalPhone = phoneOverride.trim().ifBlank { _customerPhone.value.trim() }
        val finalTableOrCar = tableOrCarOverride.trim().ifBlank { _tableOrCar.value.trim() }
        val finalNotes = notesOverride.trim().ifBlank { _orderNotes.value.trim() }

        val totals = cartTotals.value
        val settings = storeSettings.value
        val orderId = _editingHeldOrderId.value ?: "ord_${UUID.randomUUID().toString().take(8)}"
        val orderNo = "H-${(1000..9999).random()}"

        val heldOrder = PosOrder(
            id = orderId,
            orderNumber = orderNo,
            branchId = branch.id,
            shiftId = shift.id,
            cashierId = cashier.id,
            cashierName = cashier.displayName(AppLang.EN),
            sourceId = source.id,
            sourceNameEn = source.nameEn,
            sourceNameAr = source.nameAr,
            pricingMode = source.pricingMode,
            items = items,
            subtotal = totals.subtotal,
            discountType = _discountType.value,
            discountValue = _discountValue.value,
            discountAmount = totals.discountAmount,
            vatRate = settings.vatRate,
            vatInclusive = settings.vatInclusive,
            vatAmount = totals.vatAmount,
            grandTotal = totals.grandTotal,
            payment = PaymentBreakdown(),
            customerName = finalName,
            customerPhone = finalPhone,
            tableOrCarNumber = finalTableOrCar,
            notes = finalNotes,
            status = OrderStatus.HELD
        )
        viewModelScope.launch {
            repo.saveOrder(heldOrder, decrementInventory = false)
            clearCart()
            _receiptState.value = ReceiptDialogState(heldOrder, ReceiptCopyMode.KITCHEN_ONLY)
            val menuUrl = buildCustomerMenuUrl(heldOrder.tableOrCarNumber)
            if (settings.printerIp.isNotBlank()) {
                val bytes = ZatcaQrAndEscPos.buildEscPosReceiptBytes(
                    heldOrder, settings, kickDrawer = false, customerMenuUrl = menuUrl, mode = ReceiptCopyMode.KITCHEN_ONLY, typeface = receiptTypeface
                )
                val printRes = ZatcaQrAndEscPos.sendRawEscPosToPrinter(settings.printerIp, settings.printerPort, bytes)
                if (printRes.isSuccess) {
                    showToast("Order held ($orderNo) — Kitchen copy printed")
                } else {
                    showToast("Order held: $orderNo")
                }
            } else {
                showToast("Order held ($orderNo) — Kitchen memo ready")
            }
        }
    }

    fun recallHeldOrder(order: PosOrder) {
        _cartItems.value = order.items
        _discountType.value = order.discountType
        _discountValue.value = order.discountValue
        _customerName.value = order.customerName
        _customerPhone.value = order.customerPhone
        _tableOrCar.value = order.tableOrCarNumber
        _orderNotes.value = order.notes
        _selectedSourceId.value = order.sourceId
        _editingHeldOrderId.value = order.id
        _destination.value = ActiveDestination.POS_REGISTER
    }

    fun discardHeldOrder(orderId: String) {
        viewModelScope.launch {
            repo.deleteOrder(orderId)
        }
    }

    fun completeCheckout(payment: PaymentBreakdown, customerNameOverride: String, customerPhoneOverride: String) {
        val items = _cartItems.value
        val shift = openShift.value ?: return
        val cashier = activeCashier.value ?: return
        val branch = activeBranch.value ?: return
        val source = selectedOrderSource.value ?: return
        if (items.isEmpty()) return

        val totals = cartTotals.value
        val settings = storeSettings.value
        val wasHeldOrQr = _editingHeldOrderId.value != null
        val receiptMode = if (wasHeldOrQr) ReceiptCopyMode.CUSTOMER_ONLY else ReceiptCopyMode.BOTH
        val orderId = _editingHeldOrderId.value ?: "ord_${UUID.randomUUID().toString().take(8)}"
        val orderNo = "INV-${(10000..99999).random()}"

        val finalName = customerNameOverride.ifBlank { _customerName.value }
        val finalPhone = customerPhoneOverride.ifBlank { _customerPhone.value }

        val resolvedSourceNameEn = if (payment.onlineCompanyEn.isNotBlank()) {
            "${source.nameEn} • ${payment.onlineCompanyEn}"
        } else source.nameEn
        val resolvedSourceNameAr = if (payment.onlineCompanyAr.isNotBlank()) {
            "${source.nameAr} • ${payment.onlineCompanyAr}"
        } else source.nameAr

        val completed = PosOrder(
            id = orderId,
            orderNumber = orderNo,
            branchId = branch.id,
            shiftId = shift.id,
            cashierId = cashier.id,
            cashierName = cashier.displayName(AppLang.EN),
            sourceId = source.id,
            sourceNameEn = resolvedSourceNameEn,
            sourceNameAr = resolvedSourceNameAr,
            pricingMode = source.pricingMode,
            items = items,
            subtotal = totals.subtotal,
            discountType = _discountType.value,
            discountValue = _discountValue.value,
            discountAmount = totals.discountAmount,
            vatRate = settings.vatRate,
            vatInclusive = settings.vatInclusive,
            vatAmount = totals.vatAmount,
            grandTotal = totals.grandTotal,
            payment = payment,
            customerName = finalName,
            customerPhone = finalPhone,
            tableOrCarNumber = _tableOrCar.value,
            notes = _orderNotes.value,
            status = OrderStatus.COMPLETED
        )

        viewModelScope.launch {
            repo.saveOrder(completed, decrementInventory = true)
            clearCart()
            _receiptState.value = ReceiptDialogState(completed, receiptMode)
            val menuUrl = buildCustomerMenuUrl(completed.tableOrCarNumber)
            val shouldKick = payment.usesCash && settings.autoKickDrawer
            if (settings.printerIp.isNotBlank()) {
                val bytes = ZatcaQrAndEscPos.buildEscPosReceiptBytes(completed, settings, shouldKick, menuUrl, receiptMode, receiptTypeface)
                val printRes = ZatcaQrAndEscPos.sendRawEscPosToPrinter(settings.printerIp, settings.printerPort, bytes)
                if (printRes.isSuccess) {
                    val label = if (receiptMode == ReceiptCopyMode.CUSTOMER_ONLY) "Customer Bill" else "2 copies (Customer + Kitchen)"
                    showToast(if (shouldKick) "Printed $label & opened cash drawer" else "Printed $label")
                } else if (shouldKick) {
                    val kickRes = ZatcaQrAndEscPos.kickCashDrawer(settings.printerIp, settings.printerPort)
                    if (kickRes.isSuccess) showToast("Cash Drawer Opened")
                }
            }
        }
    }

    fun buildCustomerMenuUrl(tableCode: String = ""): String {
        val port = storeSettings.value.qrServerPort
        val cleanTable = tableCode.trim()
        return if (cleanTable.isNotBlank()) {
            "http://$localDeviceIp:$port/?table=$cleanTable"
        } else {
            "http://$localDeviceIp:$port/"
        }
    }

    fun openReceipt(order: PosOrder, mode: ReceiptCopyMode = ReceiptCopyMode.BOTH) {
        _receiptState.value = ReceiptDialogState(order, mode)
    }

    fun closeReceipt() {
        _receiptState.value = null
    }

    fun printReceiptEscPos(order: PosOrder, mode: ReceiptCopyMode = ReceiptCopyMode.BOTH, kickDrawer: Boolean) {
        val settings = storeSettings.value
        val menuUrl = buildCustomerMenuUrl(order.tableOrCarNumber)
        viewModelScope.launch {
            val bytes = ZatcaQrAndEscPos.buildEscPosReceiptBytes(order, settings, kickDrawer, menuUrl, mode, receiptTypeface)
            val result = ZatcaQrAndEscPos.sendRawEscPosToPrinter(settings.printerIp, settings.printerPort, bytes)
            val modeLabel = when (mode) {
                ReceiptCopyMode.CUSTOMER_ONLY -> "Customer Bill"
                ReceiptCopyMode.KITCHEN_ONLY -> "Kitchen Copy"
                ReceiptCopyMode.BOTH -> "2 copies (Customer + Restaurant)"
            }
            showToast(result.fold(
                onSuccess = { "Printed $modeLabel to ${settings.printerIp}:${settings.printerPort}" },
                onFailure = { "Printer offline (${settings.printerIp}): ${it.message}" }
            ))
        }
    }

    fun kickCashDrawerManual() {
        val settings = storeSettings.value
        viewModelScope.launch {
            val res = ZatcaQrAndEscPos.kickCashDrawer(settings.printerIp, settings.printerPort)
            showToast(res.fold(
                onSuccess = { "Drawer pulse [27,112,0,25,250] sent!" },
                onFailure = { "Drawer gateway (${settings.printerIp}): ${it.message}" }
            ))
        }
    }

    fun scanWifiPrinters() {
        val port = storeSettings.value.printerPort
        viewModelScope.launch {
            _isScanningPrinters.value = true
            val found = ZatcaQrAndEscPos.scanLanPrinters(port)
            _discoveredPrinters.value = found
            _isScanningPrinters.value = false
            showToast(if (found.isEmpty()) "No TCP:$port thermal printers detected on LAN" else "Found ${found.size} printer(s)")
        }
    }

    // Refunds & Z-Report
    fun processRefund(order: PosOrder, amount: Double, method: RefundMethod, reason: String) {
        val shift = openShift.value ?: return
        val cashier = activeCashier.value ?: return
        val maxRefundable = (order.grandTotal - order.refundedAmount).coerceAtLeast(0.0)
        val safeAmount = amount.coerceIn(0.01, maxRefundable)
        viewModelScope.launch {
            repo.processRefund(
                order = order,
                shiftId = shift.id,
                cashierId = cashier.id,
                amount = safeAmount,
                method = method,
                reason = reason.ifBlank { "Customer Refund" },
                itemsSummary = order.items.joinToString { "${it.qty}x ${it.nameEn}" }
            )
            showToast("Refunded ${formatNumber(safeAmount)} SAR via ${method.name}")
        }
    }

    fun computeCurrentShiftMetrics(): ShiftSummaryMetrics {
        val shift = openShift.value ?: return ShiftSummaryMetrics()
        return computeMetricsForShift(shift)
    }

    fun computeMetricsForShift(shift: ShiftSession): ShiftSummaryMetrics {
        val shiftOrders = orders.value.filter {
            it.shiftId == shift.id && it.status in listOf(OrderStatus.COMPLETED, OrderStatus.PARTIALLY_REFUNDED, OrderStatus.REFUNDED)
        }
        val shiftRefunds = refunds.value.filter { it.shiftId == shift.id }

        val cashSales = shiftOrders.sumOf { it.payment.cashAmount }
        val madaSales = shiftOrders.sumOf { it.payment.madaAmount }
        val visaSales = shiftOrders.sumOf { it.payment.visaAmount }
        val onlineSales = shiftOrders.sumOf { it.payment.onlineAmount }
        val dueSales = shiftOrders.sumOf { it.payment.dueAmount }

        val cashRefunds = shiftRefunds.filter { it.method == RefundMethod.CASH }.sumOf { it.amount }
        val madaRefunds = shiftRefunds.filter { it.method == RefundMethod.MADA }.sumOf { it.amount }
        val visaRefunds = shiftRefunds.filter { it.method == RefundMethod.VISA }.sumOf { it.amount }
        val onlineRefunds = shiftRefunds.filter { it.method == RefundMethod.ONLINE }.sumOf { it.amount }
        val totalRefunds = shiftRefunds.sumOf { it.amount }

        val grossSales = shiftOrders.sumOf { it.grandTotal }
        val totalDiscounts = shiftOrders.sumOf { it.discountAmount }
        val totalVat = shiftOrders.sumOf { it.vatAmount }
        val expectedCash = shift.openingFloat + cashSales - cashRefunds

        return ShiftSummaryMetrics(
            orderCount = shiftOrders.size,
            grossSales = grossSales,
            totalDiscounts = totalDiscounts,
            totalVat = totalVat,
            cashSales = cashSales,
            madaSales = madaSales,
            visaSales = visaSales,
            onlineSales = onlineSales,
            dueSales = dueSales,
            cashRefunds = cashRefunds,
            madaRefunds = madaRefunds,
            visaRefunds = visaRefunds,
            onlineRefunds = onlineRefunds,
            totalRefunds = totalRefunds,
            netSales = grossSales - totalRefunds,
            openingFloat = shift.openingFloat,
            expectedCashInDrawer = expectedCash
        )
    }

    fun updateCurrentShiftOpeningFloat(openingFloat: Double) {
        val shift = openShift.value ?: return
        val safeFloat = openingFloat.coerceAtLeast(0.0)
        viewModelScope.launch {
            repo.updateShiftOpeningFloat(shift, safeFloat)
            showToast(
                if (_lang.value == AppLang.AR) "تم تحديث عهدة البداية: ${formatNumber(safeFloat)} ر.س"
                else "Starting cash updated: ${formatNumber(safeFloat)} SAR"
            )
        }
    }

    fun openShiftSummaryMemo(shift: ShiftSession) {
        val branch = branches.value.firstOrNull { it.id == shift.branchId }
        val metrics = computeMetricsForShift(shift)
        _shiftMemoState.value = ShiftMemoState(
            shift = shift,
            metrics = metrics,
            branchNameEn = branch?.nameEn ?: "",
            branchNameAr = branch?.nameAr ?: ""
        )
    }

    fun closeShiftMemo() {
        _shiftMemoState.value = null
    }

    fun printShiftMemoEscPos(state: ShiftMemoState, paperWidthMm: Int = 80) {
        val settings = storeSettings.value.copy(paperWidthMm = 80)
        viewModelScope.launch {
            val bytes = ZatcaQrAndEscPos.buildShiftSummaryEscPosBytes(
                shift = state.shift,
                metrics = state.metrics,
                settings = settings,
                branchNameEn = state.branchNameEn,
                typeface = receiptTypeface
            )
            val result = ZatcaQrAndEscPos.sendRawEscPosToPrinter(settings.printerIp, settings.printerPort, bytes)
            showToast(result.fold(
                onSuccess = { "Shift Summary Memo (80mm Raster) printed to ${settings.printerIp}:${settings.printerPort}" },
                onFailure = { "Printer offline (${settings.printerIp}): ${it.message}" }
            ))
        }
    }

    fun closeCurrentShift(
        countedCash: Double,
        openingCashOverride: Double? = null
    ) {
        val shift = openShift.value ?: return
        val branch = activeBranch.value
        val settings = storeSettings.value
        val baseMetrics = computeCurrentShiftMetrics()
        val resolvedOpeningFloat = (openingCashOverride ?: shift.openingFloat).coerceAtLeast(0.0)
        val resolvedExpectedCash = resolvedOpeningFloat + baseMetrics.cashSales - baseMetrics.cashRefunds
        val resolvedMetrics = baseMetrics.copy(
            openingFloat = resolvedOpeningFloat,
            expectedCashInDrawer = resolvedExpectedCash
        )
        val safeCountedCash = countedCash.coerceAtLeast(0.0)
        val closedAt = System.currentTimeMillis()
        val variance = safeCountedCash - resolvedExpectedCash
        val shiftToClose = shift.copy(openingFloat = resolvedOpeningFloat)
        val closedShiftSnapshot = shiftToClose.copy(
            closedAt = closedAt,
            expectedCash = resolvedExpectedCash,
            countedCash = safeCountedCash,
            variance = variance,
            isOpen = false
        )
        val memoState = ShiftMemoState(
            shift = closedShiftSnapshot,
            metrics = resolvedMetrics,
            branchNameEn = branch?.nameEn ?: "",
            branchNameAr = branch?.nameAr ?: ""
        )
        _shiftMemoState.value = memoState
        viewModelScope.launch {
            repo.closeShift(shiftToClose, resolvedExpectedCash, safeCountedCash)
            clearCart()
            _destination.value = ActiveDestination.POS_REGISTER
            if (settings.printerIp.isNotBlank()) {
                val bytes = ZatcaQrAndEscPos.buildShiftSummaryEscPosBytes(
                    shift = closedShiftSnapshot,
                    metrics = resolvedMetrics,
                    settings = settings,
                    branchNameEn = memoState.branchNameEn,
                    typeface = receiptTypeface
                )
                ZatcaQrAndEscPos.sendRawEscPosToPrinter(settings.printerIp, settings.printerPort, bytes)
            }
            showToast(if (_lang.value == AppLang.AR) "تم إغلاق الوردية وإصدار ملخص Z" else "Shift closed — Z-Report summary memo ready")
        }
    }

    // Customer QR Self-Order Submission (from in-app Customer mode OR LAN HTTP server)
    fun submitCustomerQrCart(
        items: List<CartItem>,
        tableOrCar: String,
        customerName: String,
        customerPhone: String = "",
        notes: String = ""
    ) {
        val cleanName = customerName.trim()
        if (items.isEmpty() || cleanName.isBlank()) {
            if (cleanName.isBlank()) {
                showToast(if (_lang.value == AppLang.AR) "يرجى إدخال اسم العميل" else "Customer name is required")
            }
            return
        }
        val settings = storeSettings.value
        val branchId = activeBranch.value?.id ?: "br_hofuf"
        val shiftId = openShift.value?.id ?: "qr_pending_shift"
        val sub = items.sumOf { it.unitPrice * it.qty }
        val vat = if (settings.vatInclusive) sub - (sub / (1.0 + settings.vatRate)) else sub * settings.vatRate
        val grand = if (settings.vatInclusive) sub else sub + vat

        val qrOrder = PosOrder(
            id = "qr_${UUID.randomUUID().toString().take(8)}",
            orderNumber = "QR-${(100..999).random()}",
            branchId = branchId,
            shiftId = shiftId,
            cashierId = activeCashier.value?.id ?: "qr_self",
            cashierName = "QR Self-Order",
            sourceId = "src_qr",
            sourceNameEn = "QR Table Menu",
            sourceNameAr = "منيو QR",
            pricingMode = PricingMode.OFFLINE,
            items = items,
            subtotal = sub,
            discountType = DiscountType.PERCENT,
            discountValue = 0.0,
            discountAmount = 0.0,
            vatRate = settings.vatRate,
            vatInclusive = settings.vatInclusive,
            vatAmount = vat,
            grandTotal = grand,
            payment = PaymentBreakdown(primaryMethod = PaymentMethod.CASH, cashAmount = grand),
            customerName = cleanName,
            customerPhone = customerPhone.trim(),
            tableOrCarNumber = tableOrCar.trim(),
            notes = notes.trim(),
            status = OrderStatus.INCOMING_QR
        )
        viewModelScope.launch {
            repo.saveOrder(qrOrder, decrementInventory = false)
        }
    }

    private fun submitIncomingQrPayload(payload: IncomingQrPayload) {
        val catalog = products.value
        val cartLines = payload.items.mapNotNull { line ->
            val p = catalog.firstOrNull { it.id == line.productId } ?: return@mapNotNull null
            val extra = line.extraPrice.coerceAtLeast(0.0)
            val nameEn = if (line.optionNameEn.isNotBlank()) "${p.nameEn} (+ ${line.optionNameEn})" else p.nameEn
            val nameAr = if (line.optionNameAr.isNotBlank()) "${p.nameAr} (+ ${line.optionNameAr})" else p.nameAr
            val noteText = when {
                line.optionNameEn.isBlank() -> ""
                extra <= 0.001 -> "Offer: ${line.optionNameEn} (FREE)"
                else -> "Offer: ${line.optionNameEn} (+${formatNumber(extra)} SAR)"
            }
            CartItem(
                id = "ci_${UUID.randomUUID().toString().take(8)}",
                productId = p.id,
                nameEn = nameEn,
                nameAr = nameAr,
                offlinePrice = p.offlinePrice + extra,
                onlinePrice = p.onlinePrice + extra,
                unitPrice = p.offlinePrice + extra,
                qty = line.qty.coerceAtLeast(1),
                note = noteText
            )
        }
        if (payload.customerName.isBlank()) return
        submitCustomerQrCart(cartLines, payload.tableOrCar, payload.customerName, payload.customerPhone, payload.notes)
    }

    fun acceptIncomingQrOrder(order: PosOrder) {
        val shiftId = openShift.value?.id ?: order.shiftId
        val cashier = activeCashier.value
        val settings = storeSettings.value
        QrOrderRuntimeHub.stopAlarmAndClearNotification(appContext)
        viewModelScope.launch {
            val accepted = order.copy(
                shiftId = shiftId,
                cashierId = cashier?.id ?: order.cashierId,
                cashierName = cashier?.displayName(AppLang.EN) ?: order.cashierName,
                status = OrderStatus.HELD
            )
            repo.saveOrder(accepted, decrementInventory = false)
            _receiptState.value = ReceiptDialogState(accepted, ReceiptCopyMode.KITCHEN_ONLY)
            val menuUrl = buildCustomerMenuUrl(accepted.tableOrCarNumber)
            if (settings.printerIp.isNotBlank()) {
                val bytes = ZatcaQrAndEscPos.buildEscPosReceiptBytes(
                    accepted, settings, kickDrawer = false, customerMenuUrl = menuUrl, mode = ReceiptCopyMode.KITCHEN_ONLY, typeface = receiptTypeface
                )
                ZatcaQrAndEscPos.sendRawEscPosToPrinter(settings.printerIp, settings.printerPort, bytes)
            }
            showToast("QR Order ${order.orderNumber} accepted — Kitchen memo printed")
        }
    }

    fun rejectIncomingQrOrder(order: PosOrder) {
        QrOrderRuntimeHub.stopAlarmAndClearNotification(appContext)
        viewModelScope.launch {
            repo.saveOrder(order.copy(status = OrderStatus.REJECTED), decrementInventory = false)
            showToast("QR Order ${order.orderNumber} rejected")
        }
    }

    fun manualCloudSync() {
        viewModelScope.launch {
            if (!isOnline.value) {
                showToast("Device is Offline — Changes queued safely in SQLite")
            } else {
                repo.runCloudSync()
                showToast("All offline records synchronized")
            }
        }
    }

    // Admin CRUD Passthroughs
    fun saveBranch(branch: Branch) = viewModelScope.launch { repo.upsertBranch(branch) }
    fun setActiveBranch(branchId: String) = viewModelScope.launch { repo.setActiveBranch(branchId) }
    fun removeBranch(branchId: String) = viewModelScope.launch { repo.deleteBranch(branchId) }

    fun saveCategory(cat: Category) = viewModelScope.launch { repo.upsertCategory(cat) }
    fun removeCategory(id: String) = viewModelScope.launch { repo.deleteCategory(id) }

    fun saveProduct(p: Product) = viewModelScope.launch { repo.upsertProduct(p) }
    suspend fun importProductImage(uriString: String, productId: String): String = repo.importProductImage(uriString, productId)
    fun removeProduct(id: String) = viewModelScope.launch { repo.deleteProduct(id) }

    fun saveProductOffer(offer: ProductOffer) = viewModelScope.launch {
        repo.upsertOffer(offer)
        showToast("Offer saved: ${offer.titleEn}")
    }
    fun removeProductOffer(id: String) = viewModelScope.launch { repo.deleteOffer(id) }

    fun saveCashier(c: Cashier) = viewModelScope.launch { repo.upsertCashier(c) }
    fun removeCashier(id: String) = viewModelScope.launch { repo.deleteCashier(id) }

    fun saveOrderSource(s: OrderSource) = viewModelScope.launch { repo.upsertOrderSource(s) }
    fun removeOrderSource(id: String) = viewModelScope.launch { repo.deleteOrderSource(id) }

    fun saveCustomer(c: Customer) = viewModelScope.launch { repo.upsertCustomer(c) }
    fun removeCustomer(id: String) = viewModelScope.launch { repo.deleteCustomer(id) }

    fun saveStoreSettings(s: StoreSettings) = viewModelScope.launch {
        repo.saveStoreSettings(s)
        if (s.backgroundOrderAlertsEnabled) {
            CashFixOrderAlertService.start(appContext, s.qrServerPort, s.shopNameEn)
        } else {
            CashFixOrderAlertService.stop(appContext)
        }
        showToast("Shop & Store settings saved")
    }
}
