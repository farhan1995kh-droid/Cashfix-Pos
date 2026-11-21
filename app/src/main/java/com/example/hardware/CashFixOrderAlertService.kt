package com.example.hardware

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.PosRepository
import com.example.i18n.formatNumber
import com.example.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

/**
 * Shared application-wide runtime hub for the embedded QR Self-Ordering LAN Server,
 * 15-second audible/vibration order alarm, and Android system heads-up notifications.
 *
 * Keeps QR order reception and alerting active seamlessly whether CashFix POS is in the
 * foreground or backgrounded behind another POS app (such as Tabsense).
 */
object QrOrderRuntimeHub {
    const val CHANNEL_BG_MONITOR = "cashfix_bg_monitor"
    const val CHANNEL_INCOMING_ORDER = "cashfix_incoming_qr_app_chime_v3"
    const val FOREGROUND_NOTIFICATION_ID = 4101
    const val INCOMING_ORDER_NOTIFICATION_ID = 4102

    val qrGateway = QrSelfOrderGateway()

    private val hubScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _isGatewayRunning = MutableStateFlow(false)
    val isGatewayRunning: StateFlow<Boolean> = _isGatewayRunning.asStateFlow()

    private val _alarmSecondsRemaining = MutableStateFlow(0)
    val alarmSecondsRemaining: StateFlow<Int> = _alarmSecondsRemaining.asStateFlow()

    private val _lastAlertedOrderId = MutableStateFlow<String?>(null)

    @Volatile
    private var alarmPlayer: AlarmSoundPlayer? = null

    @Volatile
    private var alarmWakeLock: PowerManager.WakeLock? = null

    internal fun setServiceRunning(running: Boolean) {
        _isServiceRunning.value = running
    }

    fun ensureGatewayStarted(port: Int): Boolean {
        val ok = qrGateway.start(port, hubScope)
        _isGatewayRunning.value = ok || qrGateway.isRunning
        return _isGatewayRunning.value
    }

    fun updateCatalogSnapshot(
        settings: StoreSettings,
        categories: List<Category>,
        products: List<Product>,
        offers: List<ProductOffer>
    ) {
        qrGateway.updateCatalogSnapshot(
            shopNameEn = settings.shopNameEn,
            shopNameAr = settings.shopNameAr,
            products = products,
            categories = categories,
            offers = offers,
            addressEn = settings.addressEn,
            addressAr = settings.addressAr
        )
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun ensureNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val bgChannel = NotificationChannel(
            CHANNEL_BG_MONITOR,
            "CashFix Background Order Listener",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps QR self-ordering & incoming order sync active while other POS apps are open."
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
        }

        // Visual heads-up notification channel with NO system alarm/ringtone audio —
        // CashFix POS plays ONLY its own built-in app chime via AlarmSoundPlayer.
        val incomingChannel = NotificationChannel(
            CHANNEL_INCOMING_ORDER,
            "Incoming QR Order Alerts (App Chime)",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Heads-up visual banner for incoming QR orders (uses CashFix POS built-in chime, no system alarm)."
            enableLights(true)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 260, 180, 260)
            setSound(null, null)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        nm.createNotificationChannel(bgChannel)
        nm.createNotificationChannel(incomingChannel)
    }

    /**
     * Triggers the 15-second audible + vibration alarm AND posts a high-priority heads-up
     * system notification so staff hear and see incoming QR orders even if another POS app is active.
     */
    fun triggerIncomingOrderAlert(context: Context, order: PosOrder) {
        if (_lastAlertedOrderId.value == order.id) return
        _lastAlertedOrderId.value = order.id

        val appCtx = context.applicationContext
        ensureNotificationChannels(appCtx)
        postIncomingOrderNotification(appCtx, order)

        // Acquire brief CPU wakelock (max 20s) so Doze/background throttling doesn't silence the 15s alarm
        runCatching {
            val pm = appCtx.getSystemService(Context.POWER_SERVICE) as? PowerManager
            alarmWakeLock?.let { if (it.isHeld) it.release() }
            alarmWakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CashFixPOS:IncomingQrAlarm")?.apply {
                setReferenceCounted(false)
                acquire(20_000L)
            }
        }

        val player = alarmPlayer ?: AlarmSoundPlayer(appCtx).also { alarmPlayer = it }
        player.start15SecondAlarm(
            scope = hubScope,
            onTickRemainingSeconds = { sec -> _alarmSecondsRemaining.value = sec },
            onCompleted = {
                _alarmSecondsRemaining.value = 0
                releaseWakeLock()
            }
        )
    }

    fun stopAlarmAndClearNotification(context: Context) {
        _lastAlertedOrderId.value = null
        _alarmSecondsRemaining.value = 0
        alarmPlayer?.stop()
        releaseWakeLock()
        runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(INCOMING_ORDER_NOTIFICATION_ID)
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            alarmWakeLock?.let { if (it.isHeld) it.release() }
            alarmWakeLock = null
        }
    }

    fun buildMonitorNotification(context: Context, port: Int, shopName: String): Notification {
        ensureNotificationChannels(context)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            context,
            100,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_BG_MONITOR)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("$shopName • CashFix POS Alerts Active")
            .setContentText("Listening for QR orders in background (Port $port)")
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openPending)
            .build()
    }

    fun postIncomingOrderNotification(context: Context, order: PosOrder) {
        if (!hasNotificationPermission(context)) return
        ensureNotificationChannels(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("incoming_order_id", order.id)
        }
        val openPending = PendingIntent.getActivity(
            context,
            201,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val silenceIntent = Intent(context, CashFixOrderAlertService::class.java).apply {
            action = CashFixOrderAlertService.ACTION_STOP_ALARM
        }
        val silencePending = PendingIntent.getService(
            context,
            202,
            silenceIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val tableTag = order.tableOrCarNumber.takeIf { it.isNotBlank() }?.let { "Table $it • " } ?: ""
        val itemCount = order.items.sumOf { it.qty }
        val itemsSummary = order.items.take(3).joinToString(", ") { "${it.qty}x ${it.nameEn}" }
        val title = "New QR Order ${order.orderNumber} • ${formatNumber(order.grandTotal)} SAR"
        val text = "$tableTag${order.customerName} ($itemCount items): $itemsSummary"

        val notification = NotificationCompat.Builder(context, CHANNEL_INCOMING_ORDER)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$text\nTap to open CashFix POS and Accept/Print Kitchen Memo."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setSilent(true)
            .setContentIntent(openPending)
            .addAction(0, "Open & Accept", openPending)
            .addAction(0, "Silence Chime", silencePending)
            .build()

        runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.notify(INCOMING_ORDER_NOTIFICATION_ID, notification)
        }
    }
}

/**
 * Play-Policy-compliant Foreground Service (foregroundServiceType="dataSync") that keeps
 * CashFix POS's QR order listener, background sync polling, and 15-second incoming order
 * alarm running smoothly even when another POS app (such as Tabsense) is open on screen.
 */
class CashFixOrderAlertService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repo: PosRepository
    private var collectorsStarted = false

    override fun onCreate() {
        super.onCreate()
        repo = PosRepository(applicationContext)
        QrOrderRuntimeHub.ensureNotificationChannels(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_ALARM -> {
                QrOrderRuntimeHub.stopAlarmAndClearNotification(applicationContext)
                return START_STICKY
            }
            ACTION_STOP_SERVICE -> {
                stopForegroundServiceCleanly()
                return START_NOT_STICKY
            }
        }

        val port = intent?.getIntExtra(EXTRA_PORT, 8090) ?: 8090
        val shopName = intent?.getStringExtra(EXTRA_SHOP_NAME)?.takeIf { it.isNotBlank() } ?: "Crispy Nation"

        val monitorNotification = QrOrderRuntimeHub.buildMonitorNotification(applicationContext, port, shopName)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    QrOrderRuntimeHub.FOREGROUND_NOTIFICATION_ID,
                    monitorNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(QrOrderRuntimeHub.FOREGROUND_NOTIFICATION_ID, monitorNotification)
            }
        }

        QrOrderRuntimeHub.setServiceRunning(true)
        QrOrderRuntimeHub.ensureGatewayStarted(port)

        if (!collectorsStarted) {
            collectorsStarted = true
            startBackgroundCollectors()
        }

        return START_STICKY
    }

    private fun startBackgroundCollectors() {
        // 1. Keep embedded QR web catalog synced with Room DB even when MainActivity is paused
        serviceScope.launch {
            combine(
                repo.storeSettings,
                repo.categories,
                repo.products,
                repo.productOffers
            ) { settings, cats, prods, offers ->
                Quadruple(settings, cats, prods, offers)
            }.collect { (settings, cats, prods, offers) ->
                QrOrderRuntimeHub.updateCatalogSnapshot(settings, cats, prods, offers)
                QrOrderRuntimeHub.ensureGatewayStarted(settings.qrServerPort)
            }
        }

        // 2. Receive incoming QR orders from LAN web clients even while another app (e.g. Tabsense) is open
        serviceScope.launch {
            QrOrderRuntimeHub.qrGateway.incomingPayloads.collect { payload ->
                persistIncomingPayloadAndAlert(payload)
            }
        }

        // 3. Watch Room DB for any INCOMING_QR order (from local QR mode or background sync) & fire alert
        serviceScope.launch {
            repo.orders.collect { list ->
                val latestIncoming = list.firstOrNull { it.status == OrderStatus.INCOMING_QR }
                if (latestIncoming != null) {
                    QrOrderRuntimeHub.triggerIncomingOrderAlert(applicationContext, latestIncoming)
                } else {
                    QrOrderRuntimeHub.stopAlarmAndClearNotification(applicationContext)
                }
            }
        }

        // 4. Lightweight 10-second background sync & gateway health check loop
        serviceScope.launch {
            while (isActive) {
                delay(10_000L)
                runCatching {
                    val settings = repo.storeSettings.first()
                    if (!QrOrderRuntimeHub.qrGateway.isRunning) {
                        QrOrderRuntimeHub.ensureGatewayStarted(settings.qrServerPort)
                    }
                    val pendingCount = repo.pendingSyncCount.first()
                    if (pendingCount > 0) {
                        repo.runCloudSync()
                    }
                }
            }
        }
    }

    private suspend fun persistIncomingPayloadAndAlert(payload: IncomingQrPayload) {
        val cleanName = payload.customerName.trim()
        if (cleanName.isBlank() || payload.items.isEmpty()) return

        val catalog = repo.products.first()
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
        if (cartLines.isEmpty()) return

        val settings = repo.storeSettings.first()
        val openShift = repo.openShift.first()
        val sub = cartLines.sumOf { it.unitPrice * it.qty }
        val vat = if (settings.vatInclusive) sub - (sub / (1.0 + settings.vatRate)) else sub * settings.vatRate
        val grand = if (settings.vatInclusive) sub else sub + vat

        val qrOrder = PosOrder(
            id = "qr_${UUID.randomUUID().toString().take(8)}",
            orderNumber = "QR-${(100..999).random()}",
            branchId = "br_hofuf",
            shiftId = openShift?.id ?: "qr_pending_shift",
            cashierId = openShift?.cashierId ?: "qr_self",
            cashierName = "QR Self-Order",
            sourceId = "src_qr",
            sourceNameEn = "QR Table Menu",
            sourceNameAr = "منيو QR",
            pricingMode = PricingMode.OFFLINE,
            items = cartLines,
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
            customerPhone = payload.customerPhone.trim(),
            tableOrCarNumber = payload.tableOrCar.trim(),
            notes = payload.notes.trim(),
            status = OrderStatus.INCOMING_QR
        )
        repo.saveOrder(qrOrder, decrementInventory = false)
        QrOrderRuntimeHub.triggerIncomingOrderAlert(applicationContext, qrOrder)
    }

    private fun stopForegroundServiceCleanly() {
        QrOrderRuntimeHub.setServiceRunning(false)
        runCatching { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    override fun onDestroy() {
        QrOrderRuntimeHub.setServiceRunning(false)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    companion object {
        const val ACTION_START = "com.example.cashfix.ACTION_START_BG_ALERTS"
        const val ACTION_STOP_SERVICE = "com.example.cashfix.ACTION_STOP_BG_ALERTS"
        const val ACTION_STOP_ALARM = "com.example.cashfix.ACTION_STOP_ALARM"
        const val EXTRA_PORT = "extra_port"
        const val EXTRA_SHOP_NAME = "extra_shop_name"

        fun start(context: Context, port: Int = 8090, shopName: String = "Crispy Nation") {
            val intent = Intent(context, CashFixOrderAlertService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PORT, port)
                putExtra(EXTRA_SHOP_NAME, shopName)
            }
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, CashFixOrderAlertService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            runCatching {
                context.startService(intent)
            }
        }
    }
}
