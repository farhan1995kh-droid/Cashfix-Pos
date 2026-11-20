package com.example

import android.Manifest
import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hardware.QrOrderRuntimeHub
import com.example.i18n.LocalAppLang
import com.example.i18n.layoutDirection
import com.example.model.OrderStatus
import com.example.ui.ActiveDestination
import com.example.ui.PosViewModel
import com.example.ui.components.*
import com.example.ui.theme.CashFixTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CashFixApp()
        }
    }
}

@Composable
fun CashFixApp(vm: PosViewModel = viewModel()) {
    val lang by vm.lang.collectAsStateWithLifecycle()
    val darkTheme by vm.darkTheme.collectAsStateWithLifecycle()
    val settings by vm.storeSettings.collectAsStateWithLifecycle()
    val cashiers by vm.cashiers.collectAsStateWithLifecycle()
    val activeCashier by vm.activeCashier.collectAsStateWithLifecycle()
    val isAdminSessionUnlocked by vm.isAdminSessionUnlocked.collectAsStateWithLifecycle()
    val openShift by vm.openShift.collectAsStateWithLifecycle()
    val orderSources by vm.orderSources.collectAsStateWithLifecycle()
    val selectedSource by vm.selectedOrderSource.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val selectedCategoryId by vm.selectedCategoryId.collectAsStateWithLifecycle()
    val products by vm.products.collectAsStateWithLifecycle()
    val productOffers by vm.productOffers.collectAsStateWithLifecycle()
    val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()
    val cartItems by vm.cartItems.collectAsStateWithLifecycle()
    val cartTotals by vm.cartTotals.collectAsStateWithLifecycle()
    val discountType by vm.discountType.collectAsStateWithLifecycle()
    val discountValue by vm.discountValue.collectAsStateWithLifecycle()
    val customerName by vm.customerName.collectAsStateWithLifecycle()
    val customerPhone by vm.customerPhone.collectAsStateWithLifecycle()
    val tableOrCar by vm.tableOrCar.collectAsStateWithLifecycle()
    val orderNotes by vm.orderNotes.collectAsStateWithLifecycle()
    val orders by vm.orders.collectAsStateWithLifecycle()
    val refunds by vm.refunds.collectAsStateWithLifecycle()
    val shiftHistory by vm.shiftHistory.collectAsStateWithLifecycle()
    val customers by vm.customers.collectAsStateWithLifecycle()
    val destination by vm.destination.collectAsStateWithLifecycle()
    val receiptState by vm.receiptState.collectAsStateWithLifecycle()
    val shiftMemoState by vm.shiftMemoState.collectAsStateWithLifecycle()
    val incomingQrOrders by vm.incomingQrOrders.collectAsStateWithLifecycle()
    val alarmSecondsRemaining by vm.alarmSecondsRemaining.collectAsStateWithLifecycle()
    val isOnline by vm.isOnline.collectAsStateWithLifecycle()
    val pendingSyncCount by vm.pendingSyncCount.collectAsStateWithLifecycle()
    val toastMessage by vm.toastMessage.collectAsStateWithLifecycle()
    val discoveredPrinters by vm.discoveredPrinters.collectAsStateWithLifecycle()
    val isScanningPrinters by vm.isScanningPrinters.collectAsStateWithLifecycle()
    val qrGatewayRunning by vm.qrGatewayRunning.collectAsStateWithLifecycle()
    val backgroundAlertsActive by vm.backgroundAlertsActive.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var showOpeningMotion by rememberSaveable { mutableStateOf(true) }
    var hasNotificationPermission by remember {
        mutableStateOf(QrOrderRuntimeHub.hasNotificationPermission(context))
    }
    var hasPromptedNotificationPermission by rememberSaveable { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted || QrOrderRuntimeHub.hasNotificationPermission(context)
        vm.syncBackgroundAlertService()
    }

    val requestNotificationPermissionIfNeeded: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !QrOrderRuntimeHub.hasNotificationPermission(context)) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            hasNotificationPermission = true
            vm.syncBackgroundAlertService()
        }
    }

    // Always-On Display (Keep Screen Awake) via Android WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
    DisposableEffect(context, settings.alwaysOnDisplay) {
        val window = (context as? Activity)?.window
        if (settings.alwaysOnDisplay) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Sync Foreground Background-Alert Service & request POST_NOTIFICATIONS cleanly once on Android 13+
    LaunchedEffect(settings.backgroundOrderAlertsEnabled, hasNotificationPermission, showOpeningMotion) {
        vm.syncBackgroundAlertService()
        if (!showOpeningMotion &&
            settings.backgroundOrderAlertsEnabled &&
            !hasNotificationPermission &&
            !hasPromptedNotificationPermission &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) {
            hasPromptedNotificationPermission = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val heldOrders = remember(orders) { orders.filter { it.status == OrderStatus.HELD } }

    var showCheckoutDialog by remember { mutableStateOf(false) }
    var showSwitchCashierDialog by remember { mutableStateOf(false) }
    var showLiveShiftDialog by remember { mutableStateOf(false) }
    var showZReportDialog by remember { mutableStateOf(false) }
    var showSetFloatDialog by remember { mutableStateOf(false) }
    var pendingAdminDestination by remember { mutableStateOf<ActiveDestination?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    LaunchedEffect(toastMessage) {
        toastMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
            vm.clearToast()
        }
    }

    CashFixTheme(darkTheme = darkTheme, isArabic = lang.name == "AR") {
        CompositionLocalProvider(
            LocalAppLang provides lang,
            LocalLayoutDirection provides lang.layoutDirection()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (destination == ActiveDestination.CUSTOMER_QR_MODE) {
                    CustomerQrOrderScreen(
                        lang = lang,
                        settings = settings,
                        categories = categories,
                        products = products,
                        productOffers = productOffers,
                        onToggleLang = { vm.toggleLanguage() },
                        onExitToPos = { vm.navigateTo(ActiveDestination.POS_REGISTER) },
                        onSubmitQrOrder = { items, table, name, phone, notes ->
                            vm.submitCustomerQrCart(items, table, name, phone, notes)
                        }
                    )
                } else if (openShift == null && shiftMemoState == null) {
                    DayStartScreen(
                        lang = lang,
                        settings = settings,
                        cashiers = cashiers,
                        onToggleLang = { vm.toggleLanguage() },
                        onOpenCustomerQrMode = { vm.navigateTo(ActiveDestination.CUSTOMER_QR_MODE) },
                        onStartShift = { cashier, pin, openingFloat, branchId ->
                            vm.openShiftWithPin(cashier, pin, openingFloat, branchId)
                        }
                    )
                } else {
                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        drawerContent = {
                            PosNavigationDrawerContent(
                                lang = lang,
                                settings = settings,
                                activeCashier = activeCashier,
                                openShift = openShift,
                                activeDestination = destination,
                                heldCount = heldOrders.size,
                                darkTheme = darkTheme,
                                onNavigate = { vm.navigateTo(it) },
                                onOpenLiveShiftSummary = { showLiveShiftDialog = true },
                                onOpenZReport = { showZReportDialog = true },
                                onEditOpeningFloat = { showSetFloatDialog = true },
                                onOpenManagementLocked = {
                                    if (isAdminSessionUnlocked) {
                                        vm.navigateTo(ActiveDestination.ADMIN_PANEL)
                                    } else {
                                        pendingAdminDestination = ActiveDestination.ADMIN_PANEL
                                    }
                                },
                                onOpenCustomMenuDirect = {
                                    vm.navigateTo(ActiveDestination.ADMIN_PANEL)
                                },
                                onOpenCustomerCrmLocked = {
                                    if (isAdminSessionUnlocked) {
                                        vm.navigateTo(ActiveDestination.CUSTOMER_CRM)
                                    } else {
                                        pendingAdminDestination = ActiveDestination.CUSTOMER_CRM
                                    }
                                },
                                onSwitchCashier = { showSwitchCashierDialog = true },
                                onKickCashDrawer = { vm.kickCashDrawerManual() },
                                onToggleLang = { vm.toggleLanguage() },
                                onToggleTheme = { vm.toggleDarkTheme() },
                                onCloseDrawer = { scope.launch { drawerState.close() } }
                            )
                        }
                    ) {
                        Scaffold(
                            contentWindowInsets = WindowInsets.navigationBars.union(
                                WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)
                            ),
                            snackbarHost = { SnackbarHost(snackbarHostState) },
                            topBar = {
                                PosHeaderBar(
                                    lang = lang,
                                    settings = settings,
                                    activeCashier = activeCashier,
                                    heldCount = heldOrders.size,
                                    isOnline = isOnline,
                                    pendingSyncCount = pendingSyncCount,
                                    alwaysOnDisplay = settings.alwaysOnDisplay,
                                    backgroundAlertsActive = backgroundAlertsActive,
                                    hasNotificationPermission = hasNotificationPermission,
                                    onOpenDrawer = { scope.launch { drawerState.open() } },
                                    onOpenHeldOrders = { vm.navigateTo(ActiveDestination.HELD_ORDERS) },
                                    onSyncNow = { vm.manualCloudSync() },
                                    onToggleAlwaysOnDisplay = { vm.toggleAlwaysOnDisplay() },
                                    onClickBackgroundAlertsStatus = {
                                        if (!hasNotificationPermission) {
                                            requestNotificationPermissionIfNeeded()
                                        } else {
                                            vm.setBackgroundOrderAlertsEnabled(!settings.backgroundOrderAlertsEnabled)
                                        }
                                    },
                                    onToggleLang = { vm.toggleLanguage() },
                                    onOpenCustomerQrMode = { vm.navigateTo(ActiveDestination.CUSTOMER_QR_MODE) },
                                    onSwitchCashierClick = { showSwitchCashierDialog = true }
                                )
                            }
                        ) { innerPadding ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            ) {
                                when (destination) {
                                    ActiveDestination.POS_REGISTER -> {
                                        PosSellingScreen(
                                            lang = lang,
                                            settings = settings,
                                            activeCashier = activeCashier,
                                            orderSources = orderSources,
                                            selectedSource = selectedSource,
                                            categories = categories,
                                            selectedCategoryId = selectedCategoryId,
                                            products = products,
                                            productOffers = productOffers,
                                            searchQuery = searchQuery,
                                            cartItems = cartItems,
                                            cartTotals = cartTotals,
                                            discountType = discountType,
                                            discountValue = discountValue,
                                            customerName = customerName,
                                            customerPhone = customerPhone,
                                            tableOrCar = tableOrCar,
                                            orderNotes = orderNotes,
                                            onSelectSource = { vm.selectOrderSource(it) },
                                            onSelectCategory = { vm.selectCategory(it) },
                                            onSearchChange = { vm.setSearchQuery(it) },
                                            onProductClick = { vm.addProductToCart(it) },
                                            onAddProductWithOffer = { p, opt -> vm.addProductWithOfferToCart(p, opt) },
                                            onQtyChange = { id, d -> vm.updateCartItemQty(id, d) },
                                            onEditCartItem = { id, pr, nt -> vm.updateCartItemPriceAndNote(id, pr, nt) },
                                            onSetDiscount = { t, v -> vm.setDiscount(t, v) },
                                            onSetCustomerTag = { n, ph, tc, nt -> vm.setCustomerTagging(n, ph, tc, nt) },
                                            onClearCart = { vm.clearCart() },
                                            onHoldOrderConfirmed = { name, phone, carOrTable, notes ->
                                                vm.holdCurrentOrder(name, phone, carOrTable, notes)
                                            },
                                            onOpenCheckout = { showCheckoutDialog = true }
                                        )
                                    }

                                    ActiveDestination.HELD_ORDERS -> {
                                        HeldOrdersScreen(
                                            lang = lang,
                                            heldOrders = heldOrders,
                                            onBack = { vm.navigateTo(ActiveDestination.POS_REGISTER) },
                                            onRecall = { vm.recallHeldOrder(it) },
                                            onDiscard = { vm.discardHeldOrder(it) }
                                        )
                                    }

                                    ActiveDestination.SALES_HISTORY -> {
                                        SalesHistoryScreen(
                                            lang = lang,
                                            orders = orders,
                                            refunds = refunds,
                                            shiftHistory = shiftHistory,
                                            canProcessRefunds = activeCashier?.permissions?.canProcessRefunds == true || activeCashier?.isAdminOrManager == true,
                                            onBack = { vm.navigateTo(ActiveDestination.POS_REGISTER) },
                                            onViewReceipt = { vm.openReceipt(it) },
                                            onViewShiftMemo = { vm.openShiftSummaryMemo(it) },
                                            onProcessRefund = { ord, amt, m, r -> vm.processRefund(ord, amt, m, r) }
                                        )
                                    }

                                    ActiveDestination.PRINTER_SETTINGS -> {
                                        PrinterSettingsScreen(
                                            lang = lang,
                                            settings = settings,
                                            discoveredPrinters = discoveredPrinters,
                                            isScanningPrinters = isScanningPrinters,
                                            qrGatewayRunning = qrGatewayRunning,
                                            localIp = vm.localDeviceIp,
                                            onBack = { vm.navigateTo(ActiveDestination.POS_REGISTER) },
                                            onSaveSettings = { vm.saveStoreSettings(it) },
                                            onScanPrinters = { vm.scanWifiPrinters() },
                                            onTestDrawerKick = { vm.kickCashDrawerManual() }
                                        )
                                    }

                                    ActiveDestination.ADMIN_PANEL -> {
                                        AdminPanelScreen(
                                            lang = lang,
                                            isFullAdmin = isAdminSessionUnlocked || (activeCashier?.isAdminOrManager == true),
                                            settings = settings,
                                            categories = categories,
                                            products = products,
                                            productOffers = productOffers,
                                            cashiers = cashiers,
                                            orderSources = orderSources,
                                            localIp = vm.localDeviceIp,
                                            onBack = { vm.navigateTo(ActiveDestination.POS_REGISTER) },
                                            onSaveCategory = { vm.saveCategory(it) },
                                            onRemoveCategory = { vm.removeCategory(it) },
                                            onSaveProduct = { vm.saveProduct(it) },
                                            onImportProductImage = { uri, id -> vm.importProductImage(uri, id) },
                                            onRemoveProduct = { vm.removeProduct(it) },
                                            onSaveOffer = { vm.saveProductOffer(it) },
                                            onRemoveOffer = { vm.removeProductOffer(it) },
                                            onSaveCashier = { vm.saveCashier(it) },
                                            onRemoveCashier = { vm.removeCashier(it) },
                                            onSaveOrderSource = { vm.saveOrderSource(it) },
                                            onSaveSettings = { vm.saveStoreSettings(it) },
                                            backgroundAlertsActive = backgroundAlertsActive,
                                            hasNotificationPermission = hasNotificationPermission,
                                            onToggleAlwaysOn = { vm.toggleAlwaysOnDisplay() },
                                            onToggleBackgroundAlerts = { enabled -> vm.setBackgroundOrderAlertsEnabled(enabled) },
                                            onRequestNotificationPermission = requestNotificationPermissionIfNeeded
                                        )
                                    }

                                    ActiveDestination.CUSTOMER_CRM -> {
                                        CustomerCrmScreen(
                                            lang = lang,
                                            customers = customers,
                                            onBack = { vm.navigateTo(ActiveDestination.POS_REGISTER) },
                                            onSaveCustomer = { vm.saveCustomer(it) },
                                            onDeleteCustomer = { vm.removeCustomer(it) }
                                        )
                                    }

                                    ActiveDestination.CUSTOMER_QR_MODE -> Unit
                                }
                            }
                        }
                    }
                }

                // Flashing Full-Screen Incoming QR Order & 15s Alarm Overlay
                incomingQrOrders.firstOrNull()?.let { qrOrder ->
                    IncomingQrAlarmOverlay(
                        lang = lang,
                        order = qrOrder,
                        alarmSecondsRemaining = alarmSecondsRemaining,
                        onAccept = { vm.acceptIncomingQrOrder(it) },
                        onReject = { vm.rejectIncomingQrOrder(it) }
                    )
                }

                // Modals
                if (showCheckoutDialog) {
                    CheckoutSplitPaymentDialog(
                        lang = lang,
                        grandTotal = cartTotals.grandTotal,
                        pricingMode = selectedSource?.pricingMode ?: com.example.model.PricingMode.OFFLINE,
                        initialCustomerName = customerName,
                        initialCustomerPhone = customerPhone,
                        autoKickDrawer = settings.autoKickDrawer,
                        onDismiss = { showCheckoutDialog = false },
                        onConfirmPayment = { payment, cName, cPhone ->
                            showCheckoutDialog = false
                            vm.completeCheckout(payment, cName, cPhone)
                        }
                    )
                }

                receiptState?.let { st ->
                    ThermalReceiptDialog(
                        lang = lang,
                        order = st.order,
                        settings = settings,
                        localIp = vm.localDeviceIp,
                        initialMode = st.mode,
                        onDismiss = { vm.closeReceipt() },
                        onPrintEscPos = { o, mode, kick -> vm.printReceiptEscPos(o, mode, kick) },
                        onKickDrawer = { vm.kickCashDrawerManual() }
                    )
                }

                if (showSwitchCashierDialog) {
                    SwitchCashierPinDialog(
                        lang = lang,
                        cashiers = cashiers,
                        activeCashierId = activeCashier?.id,
                        onDismiss = { showSwitchCashierDialog = false },
                        onSwitchWithoutPin = { target -> vm.switchCashierWithoutPin(target) },
                        onSwitchWithPin = { target, pin -> vm.switchCashierWithPin(target, pin) }
                    )
                }

                pendingAdminDestination?.let { dest ->
                    AdminPinLockDialog(
                        lang = lang,
                        onDismiss = { pendingAdminDestination = null },
                        onVerifyPin = { pin -> vm.verifyAdminPin(pin) },
                        onUnlocked = {
                            pendingAdminDestination = null
                            vm.unlockAdminAndNavigate(dest)
                        }
                    )
                }

                if (showLiveShiftDialog) {
                    LiveShiftSummaryDialog(
                        lang = lang,
                        metrics = vm.computeCurrentShiftMetrics(),
                        onDismiss = { showLiveShiftDialog = false },
                        onEditOpeningFloat = {
                            showLiveShiftDialog = false
                            showSetFloatDialog = true
                        }
                    )
                }

                if (showSetFloatDialog && openShift != null) {
                    SetOpeningFloatDialog(
                        lang = lang,
                        currentFloat = openShift!!.openingFloat,
                        onDismiss = { showSetFloatDialog = false },
                        onSaveFloat = { amt ->
                            showSetFloatDialog = false
                            vm.updateCurrentShiftOpeningFloat(amt)
                        }
                    )
                }

                if (showZReportDialog && openShift != null) {
                    ZReportCloseDayDialog(
                        lang = lang,
                        settings = settings,
                        shift = openShift!!,
                        metrics = vm.computeCurrentShiftMetrics(),
                        onDismiss = { showZReportDialog = false },
                        onCloseShift = { counted, openingOverride ->
                            showZReportDialog = false
                            vm.closeCurrentShift(counted, openingOverride)
                        }
                    )
                }

                shiftMemoState?.let { memo ->
                    ShiftSummaryMemoDialog(
                        lang = lang,
                        shift = memo.shift,
                        metrics = memo.metrics,
                        settings = settings,
                        branchNameEn = memo.branchNameEn,
                        branchNameAr = memo.branchNameAr,
                        onDismiss = { vm.closeShiftMemo() },
                        onPrintEscPos = { mm -> vm.printShiftMemoEscPos(memo, mm) }
                    )
                }

                AnimatedVisibility(
                    visible = showOpeningMotion,
                    exit = fadeOut(animationSpec = tween(380)) + scaleOut(
                        targetScale = 1.04f,
                        animationSpec = tween(380)
                    )
                ) {
                    AppOpeningMotionScreen(
                        lang = lang,
                        onFinished = { showOpeningMotion = false }
                    )
                }
            }
        }
    }
}
