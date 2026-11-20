package com.example.hardware

import com.example.model.Category
import com.example.model.Product
import com.example.model.ProductOffer
import com.example.model.activeOfferFor
import com.example.model.deduplicatedAvailable
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

@Serializable
data class IncomingQrPayload(
    val tableOrCar: String = "",
    val customerName: String = "",
    val customerPhone: String = "",
    val notes: String = "",
    val items: List<IncomingQrLine> = emptyList()
)

@Serializable
data class IncomingQrLine(
    val productId: String,
    val qty: Int,
    val optionNameEn: String = "",
    val optionNameAr: String = "",
    val extraPrice: Double = 0.0
)

@Serializable
private data class WebCategoryDto(
    val id: String,
    val nameEn: String,
    val nameAr: String,
    val parentCategoryId: String? = null,
    val sortOrder: Int = 0
)

@Serializable
private data class WebOfferOptionDto(
    val id: String,
    val nameEn: String,
    val nameAr: String,
    val extraPrice: Double
)

@Serializable
private data class WebOfferDto(
    val titleEn: String,
    val titleAr: String,
    val options: List<WebOfferOptionDto>
)

@Serializable
private data class WebProductDto(
    val id: String,
    val categoryId: String,
    val nameEn: String,
    val nameAr: String,
    val price: Double,
    val hasImage: Boolean,
    val offer: WebOfferDto? = null
)

/**
 * Embedded LAN HTTP Server for QR Table Self-Ordering (e.g., http://<ip>:8090/?table=T2).
 * Serves a structured CashFix POS web menu matching the main POS dashboard:
 * - White base + Royal Blue secondary theme, bilingual EN/AR with LTR/RTL switching
 * - Deduplicated menu items grouped into filterable category tabs + live search bar
 * - 2-column card grid on tablet/desktop, compact structured cards on mobile
 * - Table badge (e.g., T2), mandatory Customer Name, optional Table/Car & Mobile inputs
 * - Product photos served directly from local app storage (/img/<productId>)
 * - Browser Screen Wake Lock API support so kiosk/table screens stay awake
 * - Smooth sticky cart summary + 15% VAT breakdown + Kitchen submission
 */
class QrSelfOrderGateway {
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private var runningPort: Int = -1
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _incomingPayloads = MutableSharedFlow<IncomingQrPayload>(extraBufferCapacity = 32)
    val incomingPayloads = _incomingPayloads.asSharedFlow()

    @Volatile
    private var latestProducts: List<Product> = emptyList()

    @Volatile
    private var latestCategories: List<Category> = emptyList()

    @Volatile
    private var latestOffers: List<ProductOffer> = emptyList()

    @Volatile
    private var latestShopNameEn: String = "Crispy Nation"

    @Volatile
    private var latestShopNameAr: String = "كريسبي نيشن"

    @Volatile
    private var latestAddressEn: String = "Al Hofuf, Saudi Arabia"

    @Volatile
    private var latestAddressAr: String = "الأحساء، المملكة العربية السعودية"

    val isRunning: Boolean
        get() = serverSocket != null && serverSocket?.isClosed == false && serverJob?.isActive == true

    fun updateCatalogSnapshot(
        shopNameEn: String,
        products: List<Product>,
        shopNameAr: String = shopNameEn,
        categories: List<Category> = emptyList(),
        offers: List<ProductOffer> = emptyList(),
        addressEn: String = "Al Hofuf, Saudi Arabia",
        addressAr: String = "الأحساء، المملكة العربية السعودية"
    ) {
        latestShopNameEn = shopNameEn.ifBlank { "Crispy Nation" }
        latestShopNameAr = shopNameAr.ifBlank { latestShopNameEn }
        latestCategories = categories
        latestProducts = products.deduplicatedAvailable()
        latestOffers = offers
        latestAddressEn = addressEn
        latestAddressAr = addressAr
    }

    @Synchronized
    fun start(port: Int, scope: CoroutineScope): Boolean {
        if (isRunning && runningPort == port) return true
        stop()
        return runCatching {
            val socket = ServerSocket(port)
            serverSocket = socket
            runningPort = port
            serverJob = scope.launch(Dispatchers.IO) {
                while (isActive && !socket.isClosed) {
                    val client = runCatching { socket.accept() }.getOrNull() ?: continue
                    launch { handleClient(client) }
                }
            }
            true
        }.getOrDefault(false)
    }

    @Synchronized
    fun stop() {
        serverJob?.cancel()
        serverJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        runningPort = -1
    }

    private suspend fun handleClient(client: Socket) {
        runCatching {
            client.use { s ->
                s.soTimeout = 6000
                val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                val requestLine = reader.readLine() ?: return
                var contentLength = 0
                while (true) {
                    val header = reader.readLine() ?: break
                    if (header.isEmpty()) break
                    if (header.startsWith("Content-Length:", ignoreCase = true)) {
                        contentLength = header.substringAfter(":").trim().toIntOrNull() ?: 0
                    }
                }
                val body = if (contentLength > 0 && contentLength < 512_000) {
                    val buf = CharArray(contentLength)
                    var readTotal = 0
                    while (readTotal < contentLength) {
                        val r = reader.read(buf, readTotal, contentLength - readTotal)
                        if (r <= 0) break
                        readTotal += r
                    }
                    String(buf, 0, readTotal)
                } else ""

                val parts = requestLine.split(" ")
                val method = parts.getOrNull(0) ?: "GET"
                val rawPath = parts.getOrNull(1) ?: "/"
                val out = s.getOutputStream()

                when {
                    method == "OPTIONS" -> {
                        writeResponse(
                            out,
                            status = "204 No Content",
                            contentType = "text/plain",
                            bodyBytes = ByteArray(0),
                            extraHeaders = "Access-Control-Allow-Origin: *\r\nAccess-Control-Allow-Methods: GET, POST, OPTIONS\r\nAccess-Control-Allow-Headers: Content-Type\r\n"
                        )
                    }
                    method == "POST" && rawPath.startsWith("/api/qr-order") -> {
                        val payload = json.decodeFromString<IncomingQrPayload>(body)
                        _incomingPayloads.emit(payload)
                        val respJson = """{"ok":true,"message":"Order received by CashFix POS"}"""
                        writeResponse(
                            out,
                            status = "200 OK",
                            contentType = "application/json; charset=utf-8",
                            bodyBytes = respJson.toByteArray(Charsets.UTF_8),
                            extraHeaders = "Access-Control-Allow-Origin: *\r\n"
                        )
                    }
                    method == "GET" && rawPath.startsWith("/img/") -> {
                        val productId = rawPath.removePrefix("/img/").substringBefore("?").trim()
                        val prod = latestProducts.firstOrNull { it.id == productId }
                        val file = prod?.imageUri?.takeIf { it.isNotBlank() }?.let { File(it) }
                        if (file != null && file.exists() && file.isFile) {
                            val mime = if (file.name.endsWith(".png", ignoreCase = true)) "image/png" else "image/jpeg"
                            writeResponse(
                                out,
                                status = "200 OK",
                                contentType = mime,
                                bodyBytes = file.readBytes(),
                                extraHeaders = "Cache-Control: public, max-age=300\r\n"
                            )
                        } else {
                            writeResponse(out, "404 Not Found", "text/plain", "Not Found".toByteArray())
                        }
                    }
                    method == "GET" && rawPath.startsWith("/api/health") -> {
                        val resp = """{"ok":true,"service":"CashFix POS","items":${latestProducts.size}}"""
                        writeResponse(
                            out,
                            status = "200 OK",
                            contentType = "application/json; charset=utf-8",
                            bodyBytes = resp.toByteArray(Charsets.UTF_8),
                            extraHeaders = "Access-Control-Allow-Origin: *\r\n"
                        )
                    }
                    else -> {
                        val rawTable = if (rawPath.contains("table=")) {
                            rawPath.substringAfter("table=").substringBefore("&")
                        } else ""
                        val decodedTable = runCatching { URLDecoder.decode(rawTable, "UTF-8") }.getOrDefault(rawTable).trim()
                        val html = buildCustomerMenuHtml(
                            shopNameEn = latestShopNameEn,
                            shopNameAr = latestShopNameAr,
                            addressEn = latestAddressEn,
                            addressAr = latestAddressAr,
                            tableCode = decodedTable,
                            categories = latestCategories,
                            products = latestProducts,
                            offers = latestOffers
                        )
                        writeResponse(
                            out,
                            status = "200 OK",
                            contentType = "text/html; charset=utf-8",
                            bodyBytes = html.toByteArray(Charsets.UTF_8)
                        )
                    }
                }
                out.flush()
            }
        }
    }

    private fun writeResponse(
        out: OutputStream,
        status: String,
        contentType: String,
        bodyBytes: ByteArray,
        extraHeaders: String = ""
    ) {
        val header = buildString {
            append("HTTP/1.1 $status\r\n")
            append("Content-Type: $contentType\r\n")
            append("Content-Length: ${bodyBytes.size}\r\n")
            append("Connection: close\r\n")
            if (extraHeaders.isNotEmpty()) append(extraHeaders)
            append("\r\n")
        }
        out.write(header.toByteArray(Charsets.UTF_8))
        if (bodyBytes.isNotEmpty()) {
            out.write(bodyBytes)
        }
    }

    private fun escapeHtml(input: String): String =
        input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")

    /**
     * Generates the structured, responsive CashFix POS Customer QR Menu web page
     * matching the main POS dashboard (white base, royal blue secondary, EN/AR LTR/RTL,
     * category tabs, search bar, 2-column desktop/tablet cards, deduplicated items, Wake Lock).
     */
    fun buildCustomerMenuHtml(
        shopNameEn: String,
        shopNameAr: String,
        addressEn: String = "Al Hofuf, Saudi Arabia",
        addressAr: String = "الأحساء، المملكة العربية السعودية",
        tableCode: String = "",
        categories: List<Category>,
        products: List<Product>,
        offers: List<ProductOffer> = emptyList()
    ): String {
        val cleanProducts = products.deduplicatedAvailable()
        val sortedCats = categories.sortedBy { it.sortOrder }.ifEmpty {
            listOf(Category("cat_all", "Menu", "القائمة", 1))
        }
        val catDtos = sortedCats.map {
            WebCategoryDto(it.id, it.nameEn, it.nameAr, it.parentCategoryId, it.sortOrder)
        }
        val prodDtos = cleanProducts.map { p ->
            val matchedOffer = offers.activeOfferFor(p)
            val offerDto = matchedOffer?.let { off ->
                WebOfferDto(
                    titleEn = off.titleEn,
                    titleAr = off.titleAr,
                    options = off.options.map { o -> WebOfferOptionDto(o.id, o.nameEn, o.nameAr, o.extraPrice) }
                )
            }
            val hasImg = p.imageUri.isNotBlank() && File(p.imageUri).exists()
            WebProductDto(
                id = p.id,
                categoryId = p.categoryId,
                nameEn = p.nameEn,
                nameAr = p.nameAr,
                price = p.offlinePrice,
                hasImage = hasImg,
                offer = offerDto
            )
        }
        val catsJson = json.encodeToString(catDtos).replace("</", "<\\/")
        val prodsJson = json.encodeToString(prodDtos).replace("</", "<\\/")
        val safeShopEn = escapeHtml(shopNameEn)
        val safeShopAr = escapeHtml(shopNameAr)
        val safeAddrEn = escapeHtml(addressEn)
        val safeAddrAr = escapeHtml(addressAr)
        val safeTable = escapeHtml(tableCode)

        return """<!DOCTYPE html>
<html lang="en" dir="ltr">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
<title>$safeShopEn • QR Self-Order Menu</title>
<style>
:root{
  --bg:#F4F7FC; --surface:#FFFFFF; --surface-soft:#F8FAFC;
  --primary:#155EEF; --primary-bright:#2563EB; --primary-soft:#EFF6FF; --primary-border:#BFDBFE;
  --ink:#0F172A; --muted:#64748B; --border:#E2E8F0;
  --success:#16A34A; --success-soft:#DCFCE7; --danger:#DC2626;
}
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,'Noto Sans Arabic',Tahoma,sans-serif;background:var(--bg);color:var(--ink);padding-bottom:104px;-webkit-font-smoothing:antialiased}
.topbar{position:sticky;top:0;z-index:30;background:var(--surface);border-bottom:1px solid var(--border);box-shadow:0 2px 10px rgba(15,23,42,.04)}
.topbar-inner{max-width:980px;margin:0 auto;padding:12px 16px;display:flex;align-items:center;justify-content:space-between;gap:10px;flex-wrap:wrap}
.brand-row{display:flex;align-items:center;gap:10px;min-width:0}
.brand-logo{width:40px;height:40px;border-radius:12px;background:linear-gradient(135deg,#155EEF,#3B82F6);color:#fff;font-weight:900;font-size:18px;display:flex;align-items:center;justify-content:center;flex-shrink:0}
.brand-title{font-size:17px;font-weight:800;line-height:1.2}
.brand-sub{font-size:12px;color:var(--primary);font-weight:600}
.top-actions{display:flex;align-items:center;gap:8px;flex-wrap:wrap}
.table-badge{background:var(--primary);color:#fff;font-weight:800;font-size:12px;padding:6px 12px;border-radius:999px;display:inline-flex;align-items:center;gap:5px}
.pill-btn{background:var(--surface);border:1px solid var(--border);color:var(--ink);font-weight:700;font-size:12px;padding:7px 12px;border-radius:10px;cursor:pointer;display:inline-flex;align-items:center;gap:5px}
.pill-btn.active{background:var(--primary-soft);border-color:var(--primary-border);color:var(--primary)}
.container{max-width:980px;margin:0 auto;padding:14px 16px;display:flex;flex-direction:column;gap:12px}
.card{background:var(--surface);border:1px solid var(--border);border-radius:16px;padding:14px;box-shadow:0 1px 4px rgba(15,23,42,.03)}
.customer-grid{display:grid;grid-template-columns:1fr;gap:8px}
@media(min-width:640px){.customer-grid{grid-template-columns:1.4fr 1fr 1fr}}
.field label{display:block;font-size:11px;font-weight:700;color:var(--muted);margin-bottom:4px}
.field input{width:100%;padding:10px 12px;border:1px solid var(--border);border-radius:10px;font-size:14px;background:var(--surface-soft);color:var(--ink);outline:none}
.field input:focus{border-color:var(--primary);background:#fff;box-shadow:0 0 0 3px rgba(21,94,239,.12)}
.field input.error{border-color:var(--danger);background:#FEF2F2}
.search-wrap{position:relative}
.search-wrap input{width:100%;padding:11px 14px 11px 38px;border:1px solid var(--border);border-radius:12px;font-size:14px;background:var(--surface);outline:none}
[dir="rtl"] .search-wrap input{padding:11px 38px 11px 14px}
.search-icon{position:absolute;top:50%;transform:translateY(-50%);left:12px;color:var(--muted);font-size:15px;pointer-events:none}
[dir="rtl"] .search-icon{left:auto;right:12px}
.cat-strip{display:flex;gap:8px;overflow-x:auto;padding-bottom:4px;scrollbar-width:none}
.cat-strip::-webkit-scrollbar{display:none}
.cat-pill{flex:0 0 auto;border:1px solid var(--border);background:var(--surface);color:var(--ink);padding:8px 13px;border-radius:12px;font-size:13px;font-weight:700;cursor:pointer;display:inline-flex;align-items:center;gap:6px;transition:all .15s}
.cat-pill .cnt{background:var(--surface-soft);color:var(--muted);font-size:11px;padding:2px 7px;border-radius:999px;font-weight:800}
.cat-pill.active{background:var(--primary);border-color:var(--primary);color:#fff}
.cat-pill.active .cnt{background:rgba(255,255,255,.22);color:#fff}
.section{display:flex;flex-direction:column;gap:10px}
.section-head{display:flex;align-items:center;justify-content:space-between;padding:4px 2px}
.section-title-wrap{display:flex;align-items:center;gap:8px}
.section-bar{width:4px;height:20px;border-radius:4px;background:var(--primary)}
.section-title{font-size:16px;font-weight:800}
.section-sub{font-size:12px;color:var(--muted);font-weight:600}
.section-badge{background:var(--primary-soft);color:var(--primary);font-size:11px;font-weight:800;padding:4px 10px;border-radius:999px}
.grid{display:grid;grid-template-columns:1fr;gap:10px}
@media(min-width:640px){.grid{grid-template-columns:repeat(2,1fr);gap:12px}}
.prod-card{background:var(--surface);border:1.5px solid var(--border);border-radius:16px;overflow:hidden;display:flex;flex-direction:row;align-items:stretch;transition:border-color .15s,box-shadow .15s}
.prod-card.in-cart{border-color:var(--primary);box-shadow:0 4px 14px rgba(21,94,239,.10)}
.prod-thumb{width:92px;min-height:92px;background:var(--primary-soft);display:flex;align-items:center;justify-content:center;flex-shrink:0;overflow:hidden}
.prod-thumb img{width:100%;height:100%;object-fit:cover;display:block}
.prod-thumb-icon{font-size:28px;color:var(--primary)}
.prod-body{flex:1;padding:11px 12px;display:flex;flex-direction:column;justify-content:space-between;gap:8px;min-width:0}
.prod-name-en{font-size:14px;font-weight:800;line-height:1.25;color:var(--ink)}
.prod-name-ar{font-size:13px;color:var(--muted);font-weight:600;margin-top:2px}
.prod-foot{display:flex;align-items:center;justify-content:space-between;gap:8px;flex-wrap:wrap}
.price-tag{font-size:15px;font-weight:900;color:var(--primary)}
.offer-chip{font-size:10px;font-weight:800;background:var(--success-soft);color:var(--success);padding:2px 7px;border-radius:999px;margin-inline-start:6px}
.add-btn{background:var(--primary);color:#fff;border:none;border-radius:10px;padding:7px 14px;font-size:13px;font-weight:800;cursor:pointer;min-height:36px}
.add-btn:active{transform:scale(.97)}
.stepper{display:inline-flex;align-items:center;gap:8px;background:var(--primary-soft);border:1px solid var(--primary-border);border-radius:10px;padding:3px 6px}
.step-btn{width:28px;height:28px;border:none;border-radius:8px;background:#fff;color:var(--primary);font-size:16px;font-weight:900;cursor:pointer;display:flex;align-items:center;justify-content:center}
.step-btn.plus{background:var(--primary);color:#fff}
.step-qty{font-size:14px;font-weight:900;min-width:18px;text-align:center}
.bottom-bar{position:fixed;left:0;right:0;bottom:0;z-index:40;background:var(--surface);border-top:1px solid var(--border);box-shadow:0 -4px 18px rgba(15,23,42,.08);padding:10px 16px}
.bottom-inner{max-width:980px;margin:0 auto;display:flex;align-items:center;justify-content:space-between;gap:10px}
.cart-summary-btn{background:var(--primary-soft);border:1px solid var(--primary-border);border-radius:12px;padding:8px 12px;cursor:pointer;text-align:start}
.cart-meta{font-size:11px;font-weight:700;color:var(--muted)}
.cart-total{font-size:17px;font-weight:900;color:var(--primary)}
.submit-btn{flex:1;max-width:340px;background:var(--primary);color:#fff;border:none;border-radius:12px;padding:13px 16px;font-size:15px;font-weight:800;cursor:pointer;min-height:48px}
.submit-btn:disabled{background:#94A3B8;cursor:not-allowed}
.toast{position:fixed;top:74px;left:50%;transform:translateX(-50%);z-index:60;background:#16A34A;color:#fff;padding:11px 18px;border-radius:12px;font-weight:800;font-size:14px;box-shadow:0 8px 24px rgba(0,0,0,.18);display:none}
.modal-backdrop{position:fixed;inset:0;z-index:50;background:rgba(15,23,42,.52);display:none;align-items:flex-end;justify-content:center;padding:12px}
.modal-backdrop.open{display:flex}
@media(min-width:640px){.modal-backdrop{align-items:center}}
.modal-sheet{width:100%;max-width:480px;background:var(--surface);border-radius:20px;padding:18px;max-height:82vh;overflow-y:auto;display:flex;flex-direction:column;gap:10px}
.opt-btn{width:100%;text-align:start;background:var(--surface-soft);border:1px solid var(--border);border-radius:12px;padding:12px;cursor:pointer;display:flex;justify-content:space-between;align-items:center;font-weight:700}
.opt-btn:hover{border-color:var(--primary);background:var(--primary-soft)}
.empty-state{text-align:center;padding:34px 16px;color:var(--muted);font-weight:600}
</style>
</head>
<body>
<div class="toast" id="toast"></div>
<header class="topbar">
  <div class="topbar-inner">
    <div class="brand-row">
      <div class="brand-logo">CF</div>
      <div>
        <div class="brand-title" id="shopTitle">$safeShopEn</div>
        <div class="brand-sub" id="shopAddr">$safeAddrEn</div>
      </div>
    </div>
    <div class="top-actions">
      <span class="table-badge" id="tableBadge" style="${if (safeTable.isBlank()) "display:none" else ""}">Table: $safeTable</span>
      <button class="pill-btn" id="wakeBtn" type="button" onclick="toggleWakeLock()">Screen Awake</button>
      <button class="pill-btn active" id="langBtn" type="button" onclick="toggleLang()">العربية</button>
    </div>
  </div>
</header>

<main class="container">
  <section class="card">
    <div class="customer-grid">
      <div class="field">
        <label id="lblName">Customer Name * (Required)</label>
        <input id="custName" type="text" placeholder="Enter your name" oninput="updateSubmitState()">
      </div>
      <div class="field">
        <label id="lblTable">Table / Car No. (Optional)</label>
        <input id="tableOrCar" type="text" value="$safeTable" placeholder="e.g. T2" oninput="syncTableBadge()">
      </div>
      <div class="field">
        <label id="lblPhone">Mobile Number (Optional)</label>
        <input id="custPhone" type="tel" placeholder="05xxxxxxxx">
      </div>
    </div>
  </section>

  <div class="search-wrap">
    <span class="search-icon">🔍</span>
    <input id="searchInput" type="search" placeholder="Search burgers, meals, sides, drinks..." oninput="renderMenu()">
  </div>

  <nav class="cat-strip" id="catStrip"></nav>

  <div id="menuSections"></div>
</main>

<footer class="bottom-bar">
  <div class="bottom-inner">
    <button class="cart-summary-btn" type="button" onclick="openCartModal()">
      <div class="cart-meta" id="cartMeta">0 Items • Incl. 15% VAT</div>
      <div class="cart-total" id="cartTotal">0.00 SAR</div>
    </button>
    <button class="submit-btn" id="submitBtn" type="button" disabled onclick="submitOrder()">Send Order to Kitchen</button>
  </div>
</footer>

<div class="modal-backdrop" id="offerModal" onclick="if(event.target===this)closeOfferModal()">
  <div class="modal-sheet">
    <h3 id="offerTitle"></h3>
    <div id="offerOptions" style="display:flex;flex-direction:column;gap:8px"></div>
    <button class="pill-btn" type="button" onclick="closeOfferModal()" style="justify-content:center">Close</button>
  </div>
</div>

<div class="modal-backdrop" id="cartModal" onclick="if(event.target===this)closeCartModal()">
  <div class="modal-sheet">
    <h3 id="cartModalTitle">Your Order</h3>
    <div id="cartModalLines" style="display:flex;flex-direction:column;gap:8px"></div>
    <button class="pill-btn" type="button" onclick="closeCartModal()" style="justify-content:center">Close</button>
  </div>
</div>

<script>
const SHOP = { en: ${json.encodeToString(shopNameEn)}, ar: ${json.encodeToString(shopNameAr)}, addrEn: ${json.encodeToString(addressEn)}, addrAr: ${json.encodeToString(addressAr)} };
const CATEGORIES = $catsJson;
const PRODUCTS = $prodsJson;

let lang = 'en';
let selectedCat = 'ALL';
let cart = [];
let wakeLockSentinel = null;

async function acquireWakeLock() {
  try {
    if ('wakeLock' in navigator && !wakeLockSentinel) {
      wakeLockSentinel = await navigator.wakeLock.request('screen');
      wakeLockSentinel.addEventListener('release', () => { wakeLockSentinel = null; updateWakeBtn(); });
      updateWakeBtn();
    }
  } catch (e) {}
}
async function toggleWakeLock() {
  if (wakeLockSentinel) {
    try { await wakeLockSentinel.release(); } catch(e) {}
    wakeLockSentinel = null;
    updateWakeBtn();
  } else {
    await acquireWakeLock();
  }
}
function updateWakeBtn() {
  const b = document.getElementById('wakeBtn');
  if (!b) return;
  const active = !!wakeLockSentinel;
  b.classList.toggle('active', active);
  b.textContent = lang === 'ar' ? (active ? 'الشاشة نشطة' : 'تفعيل الشاشة') : (active ? 'Awake ON' : 'Keep Awake');
}
document.addEventListener('visibilitychange', () => {
  if (document.visibilityState === 'visible') acquireWakeLock();
});
acquireWakeLock();

function t(en, ar) { return lang === 'ar' ? ar : en; }
function fmtSar(val) { return Number(val).toFixed(2) + (lang === 'ar' ? ' ر.س' : ' SAR'); }

function syncTableBadge() {
  const v = document.getElementById('tableOrCar').value.trim();
  const badge = document.getElementById('tableBadge');
  if (v) {
    badge.style.display = 'inline-flex';
    badge.textContent = (lang === 'ar' ? 'طاولة: ' : 'Table: ') + v;
  } else {
    badge.style.display = 'none';
  }
}

function toggleLang() {
  lang = lang === 'en' ? 'ar' : 'en';
  document.documentElement.lang = lang;
  document.documentElement.dir = lang === 'ar' ? 'rtl' : 'ltr';
  document.getElementById('shopTitle').textContent = lang === 'ar' ? SHOP.ar : SHOP.en;
  document.getElementById('shopAddr').textContent = lang === 'ar' ? SHOP.addrAr : SHOP.addrEn;
  document.getElementById('langBtn').textContent = lang === 'en' ? 'العربية' : 'EN';
  document.getElementById('lblName').textContent = t('Customer Name * (Required)', 'اسم العميل * (مطلوب)');
  document.getElementById('custName').placeholder = t('Enter your name', 'أدخل اسمك');
  document.getElementById('lblTable').textContent = t('Table / Car No. (Optional)', 'رقم الطاولة / السيارة (اختياري)');
  document.getElementById('lblPhone').textContent = t('Mobile Number (Optional)', 'رقم الجوال (اختياري)');
  document.getElementById('searchInput').placeholder = t('Search burgers, meals, sides, drinks...', 'ابحث في القائمة بالعربية أو الإنجليزية...');
  document.getElementById('submitBtn').textContent = t('Send Order to Kitchen', 'إرسال الطلب للمطبخ');
  document.getElementById('cartModalTitle').textContent = t('Your Order', 'ملخص طلبك');
  syncTableBadge();
  updateWakeBtn();
  renderCategories();
  renderMenu();
  renderCartBar();
}

function topCategories() {
  return CATEGORIES.filter(c => !c.parentCategoryId);
}
function categoryFamilyIds(catId) {
  const children = CATEGORIES.filter(c => c.parentCategoryId === catId).map(c => c.id);
  return [catId, ...children];
}

function renderCategories() {
  const strip = document.getElementById('catStrip');
  const tops = topCategories();
  let html = '<button class="cat-pill ' + (selectedCat === 'ALL' ? 'active' : '') + '" onclick="selectCat(\'ALL\')">'
    + t('All Menu', 'كل القائمة') + '<span class="cnt">' + PRODUCTS.length + '</span></button>';
  for (const c of tops) {
    const ids = categoryFamilyIds(c.id);
    const count = PRODUCTS.filter(p => ids.includes(p.categoryId)).length;
    if (count === 0) continue;
    html += '<button class="cat-pill ' + (selectedCat === c.id ? 'active' : '') + '" onclick="selectCat(\'' + c.id + '\')">'
      + (lang === 'ar' ? c.nameAr : c.nameEn) + '<span class="cnt">' + count + '</span></button>';
  }
  strip.innerHTML = html;
}

function selectCat(id) {
  selectedCat = id;
  renderCategories();
  renderMenu();
}

function productQty(prodId) {
  return cart.filter(i => i.productId === prodId).reduce((s, i) => s + i.qty, 0);
}

function handleAddClick(prodId) {
  const p = PRODUCTS.find(x => x.id === prodId);
  if (!p) return;
  if (p.offer && p.offer.options && p.offer.options.length) {
    openOfferModal(p);
  } else {
    addCartItem(p, null);
  }
}

function addCartItem(p, opt) {
  const extra = opt ? Math.max(0, opt.extraPrice || 0) : 0;
  const key = p.id + '|' + (opt ? opt.id : 'base');
  const existing = cart.find(x => x.key === key);
  if (existing) {
    existing.qty++;
  } else {
    cart.push({
      key,
      productId: p.id,
      nameEn: opt ? (p.nameEn + ' (+ ' + opt.nameEn + ')') : p.nameEn,
      nameAr: opt ? (p.nameAr + ' (+ ' + opt.nameAr + ')') : p.nameAr,
      unitPrice: p.price + extra,
      extraPrice: extra,
      optionNameEn: opt ? opt.nameEn : '',
      optionNameAr: opt ? opt.nameAr : '',
      qty: 1
    });
  }
  renderMenu();
  renderCartBar();
}

function decrementProduct(prodId) {
  for (let i = cart.length - 1; i >= 0; i--) {
    if (cart[i].productId === prodId) {
      cart[i].qty--;
      if (cart[i].qty <= 0) cart.splice(i, 1);
      break;
    }
  }
  renderMenu();
  renderCartBar();
}

function openOfferModal(p) {
  document.getElementById('offerTitle').textContent = (lang === 'ar' ? p.nameAr : p.nameEn) + ' — ' + (lang === 'ar' ? p.offer.titleAr : p.offer.titleEn);
  const wrap = document.getElementById('offerOptions');
  let html = '<button class="opt-btn" onclick="pickOffer(\'' + p.id + '\', null)"><span>'
    + t('Standard Item Only', 'المنتج الأساسي فقط') + '</span><span>' + fmtSar(p.price) + '</span></button>';
  for (const o of p.offer.options) {
    const label = lang === 'ar' ? o.nameAr : o.nameEn;
    const extraTxt = o.extraPrice > 0.001 ? ('+' + fmtSar(o.extraPrice)) : t('FREE', 'مجاني');
    html += '<button class="opt-btn" onclick="pickOffer(\'' + p.id + '\', \'' + o.id + '\')"><span>' + label + '</span><span style="color:var(--primary)">' + extraTxt + '</span></button>';
  }
  wrap.innerHTML = html;
  document.getElementById('offerModal').classList.add('open');
}
function pickOffer(prodId, optId) {
  const p = PRODUCTS.find(x => x.id === prodId);
  const opt = optId && p && p.offer ? p.offer.options.find(o => o.id === optId) : null;
  if (p) addCartItem(p, opt);
  closeOfferModal();
}
function closeOfferModal() { document.getElementById('offerModal').classList.remove('open'); }

function renderMenu() {
  const q = document.getElementById('searchInput').value.trim().toLowerCase();
  const container = document.getElementById('menuSections');
  const catsToShow = selectedCat === 'ALL'
    ? CATEGORIES
    : CATEGORIES.filter(c => c.id === selectedCat || c.parentCategoryId === selectedCat);

  let html = '';
  let totalMatches = 0;

  for (const c of catsToShow) {
    const list = PRODUCTS.filter(p => {
      if (p.categoryId !== c.id) return false;
      if (!q) return true;
      return p.nameEn.toLowerCase().includes(q) || p.nameAr.toLowerCase().includes(q);
    });
    if (!list.length) continue;
    totalMatches += list.length;

    const parent = c.parentCategoryId ? CATEGORIES.find(x => x.id === c.parentCategoryId) : null;
    const secTitle = parent
      ? ((lang === 'ar' ? parent.nameAr : parent.nameEn) + ' › ' + (lang === 'ar' ? c.nameAr : c.nameEn))
      : (lang === 'ar' ? c.nameAr : c.nameEn);
    const secSub = lang === 'ar' ? c.nameEn : c.nameAr;

    html += '<section class="section"><div class="section-head"><div class="section-title-wrap"><span class="section-bar"></span><div><div class="section-title">'
      + secTitle + '</div><div class="section-sub">' + secSub + '</div></div></div><span class="section-badge">'
      + list.length + ' ' + t('items', 'صنف') + '</span></div><div class="grid">';

    for (const p of list) {
      const qty = productQty(p.id);
      const thumbHtml = p.hasImage
        ? '<img src="/img/' + p.id + '" alt="" loading="lazy">'
        : '<span class="prod-thumb-icon">🍔</span>';
      const ctrlHtml = qty > 0
        ? '<div class="stepper"><button class="step-btn" type="button" onclick="decrementProduct(\'' + p.id + '\')">−</button><span class="step-qty">' + qty + '</span><button class="step-btn plus" type="button" onclick="handleAddClick(\'' + p.id + '\')">+</button></div>'
        : '<button class="add-btn" type="button" onclick="handleAddClick(\'' + p.id + '\')">' + t('+ Add', '+ أضف') + '</button>';
      const offerHtml = p.offer ? '<span class="offer-chip">' + t('OFFER', 'عرض') + '</span>' : '';

      html += '<article class="prod-card ' + (qty > 0 ? 'in-cart' : '') + '">'
        + '<div class="prod-thumb">' + thumbHtml + '</div>'
        + '<div class="prod-body">'
        + '<div><div class="prod-name-en">' + (lang === 'ar' ? p.nameAr : p.nameEn) + offerHtml + '</div>'
        + '<div class="prod-name-ar">' + (lang === 'ar' ? p.nameEn : p.nameAr) + '</div></div>'
        + '<div class="prod-foot"><span class="price-tag">' + fmtSar(p.price) + '</span>' + ctrlHtml + '</div>'
        + '</div></article>';
    }
    html += '</div></section>';
  }

  if (!totalMatches) {
    html = '<div class="card empty-state">' + t('No menu items match your search.', 'لا توجد أصناف مطابقة للبحث.') + '</div>';
  }
  container.innerHTML = html;
}

function renderCartBar() {
  const count = cart.reduce((s, i) => s + i.qty, 0);
  const total = cart.reduce((s, i) => s + i.unitPrice * i.qty, 0);
  document.getElementById('cartMeta').textContent = count + ' ' + t('Items • Incl. 15% VAT', 'أصناف • شامل ١٥٪ ضريبة');
  document.getElementById('cartTotal').textContent = fmtSar(total);
  updateSubmitState();
}

function updateSubmitState() {
  const nameInput = document.getElementById('custName');
  const hasName = nameInput.value.trim().length > 0;
  nameInput.classList.toggle('error', cart.length > 0 && !hasName);
  document.getElementById('submitBtn').disabled = !(cart.length > 0 && hasName);
}

function openCartModal() {
  const lines = document.getElementById('cartModalLines');
  if (!cart.length) {
    lines.innerHTML = '<div class="empty-state">' + t('Your cart is empty.', 'السلة فارغة.') + '</div>';
  } else {
    const total = cart.reduce((s, i) => s + i.unitPrice * i.qty, 0);
    const vat = total - (total / 1.15);
    lines.innerHTML = cart.map(i =>
      '<div style="display:flex;justify-content:space-between;padding:8px 0;border-bottom:1px solid var(--border)">'
      + '<span><b>' + i.qty + '×</b> ' + (lang === 'ar' ? i.nameAr : i.nameEn) + '</span>'
      + '<span style="font-weight:800;color:var(--primary)">' + fmtSar(i.unitPrice * i.qty) + '</span></div>'
    ).join('') + '<div style="display:flex;justify-content:space-between;padding-top:8px;font-size:13px;color:var(--muted)"><span>'
      + t('VAT (15% Included)', 'ضريبة القيمة المضافة ١٥٪') + '</span><span>' + fmtSar(vat) + '</span></div>'
      + '<div style="display:flex;justify-content:space-between;font-size:16px;font-weight:900"><span>'
      + t('Total', 'الإجمالي') + '</span><span style="color:var(--primary)">' + fmtSar(total) + '</span></div>';
  }
  document.getElementById('cartModal').classList.add('open');
}
function closeCartModal() { document.getElementById('cartModal').classList.remove('open'); }

function showToast(msg) {
  const el = document.getElementById('toast');
  el.textContent = msg;
  el.style.display = 'block';
  setTimeout(() => { el.style.display = 'none'; }, 3400);
}

async function submitOrder() {
  const name = document.getElementById('custName').value.trim();
  if (!name) { updateSubmitState(); return; }
  if (!cart.length) return;
  const btn = document.getElementById('submitBtn');
  btn.disabled = true;
  try {
    const res = await fetch('/api/qr-order', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        tableOrCar: document.getElementById('tableOrCar').value.trim(),
        customerName: name,
        customerPhone: document.getElementById('custPhone').value.trim(),
        items: cart.map(i => ({
          productId: i.productId,
          qty: i.qty,
          optionNameEn: i.optionNameEn,
          optionNameAr: i.optionNameAr,
          extraPrice: i.extraPrice
        }))
      })
    });
    if (res.ok) {
      cart = [];
      renderMenu();
      renderCartBar();
      showToast(t('Order sent to Kitchen! Thank you.', 'تم إرسال طلبك للمطبخ بنجاح!'));
    } else {
      showToast(t('Could not send order. Please retry.', 'تعذر إرسال الطلب، حاول مجدداً.'));
    }
  } catch (e) {
    showToast(t('Connection issue. Please notify cashier.', 'مشكلة في الاتصال، يرجى إبلاغ الكاشير.'));
  } finally {
    updateSubmitState();
  }
}

renderCategories();
renderMenu();
renderCartBar();
syncTableBadge();
</script>
</body>
</html>"""
    }
}
