package com.example.hardware

import com.example.model.CartItem
import com.example.model.Product
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket

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
    val qty: Int
)

/**
 * Lightweight embedded LAN HTTP Server for QR Table Self-Ordering.
 * Allows customer phones on the same Wi-Fi to scan a Table Tent QR code
 * and submit orders directly into the CashFix POS incoming order alarm flow.
 */
class QrSelfOrderGateway {
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val json = Json { ignoreUnknownKeys = true }

    private val _incomingPayloads = MutableSharedFlow<IncomingQrPayload>(extraBufferCapacity = 16)
    val incomingPayloads = _incomingPayloads.asSharedFlow()

    @Volatile
    private var latestProducts: List<Product> = emptyList()

    @Volatile
    private var latestShopName: String = "CashFix Café"

    fun updateCatalogSnapshot(shopName: String, products: List<Product>) {
        latestShopName = shopName
        latestProducts = products
    }

    fun start(port: Int, scope: CoroutineScope): Boolean {
        stop()
        return runCatching {
            val socket = ServerSocket(port)
            serverSocket = socket
            serverJob = scope.launch(Dispatchers.IO) {
                while (isActive && !socket.isClosed) {
                    val client = runCatching { socket.accept() }.getOrNull() ?: continue
                    launch { handleClient(client) }
                }
            }
            true
        }.getOrDefault(false)
    }

    fun stop() {
        serverJob?.cancel()
        serverJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    private suspend fun handleClient(client: Socket) {
        runCatching {
            client.use { s ->
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
                val body = if (contentLength > 0) {
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
                val path = parts.getOrNull(1) ?: "/"

                val writer = OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8)
                if (method == "POST" && path.startsWith("/api/qr-order")) {
                    val payload = json.decodeFromString<IncomingQrPayload>(body)
                    _incomingPayloads.emit(payload)
                    val respJson = """{"ok":true,"message":"Order received by CashFix POS"}"""
                    writer.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nAccess-Control-Allow-Origin: *\r\nContent-Length: ${respJson.toByteArray().size}\r\n\r\n$respJson")
                } else {
                    val tableParam = path.substringAfter("table=", "").substringBefore("&")
                    val html = buildQuickCustomerHtml(latestShopName, tableParam, latestProducts)
                    val htmlBytes = html.toByteArray(Charsets.UTF_8)
                    writer.write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${htmlBytes.size}\r\n\r\n$html")
                }
                writer.flush()
            }
        }
    }

    private fun buildQuickCustomerHtml(shopName: String, table: String, products: List<Product>): String {
        val itemsHtml = products.filter { it.isAvailable }.joinToString("") { p ->
            """<div style="border:1px solid #cbd5e1;border-radius:12px;padding:12px;margin:8px 0;display:flex;justify-content:space-between;align-items:center;background:#fff">
                <div><b>${p.nameEn}</b><br/><span style="color:#475569;font-size:13px">${p.nameAr}</span><br/><small style="color:#155EEF;font-weight:bold">${p.offlinePrice} SAR</small></div>
                <button onclick="add('${p.id}','${p.nameEn}',${p.offlinePrice})" style="background:#155EEF;color:#fff;border:none;border-radius:8px;padding:8px 14px;font-weight:bold">+ Add</button>
            </div>"""
        }
        return """<!DOCTYPE html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
        <title>$shopName - QR Menu</title></head><body style="font-family:sans-serif;background:#f4f7fc;padding:16px;max-width:540px;margin:auto">
        <h2 style="margin-bottom:4px">$shopName</h2>
        <div style="background:#fff;border:1px solid #cbd5e1;border-radius:12px;padding:12px;margin:10px 0">
          <input id="custName" placeholder="Customer Name * (Required)" style="width:100%;box-sizing:border-box;padding:10px;border:1px solid #94a3b8;border-radius:8px;margin-bottom:8px"/>
          <div style="display:flex;gap:8px">
            <input id="tableOrCar" value="$table" placeholder="Table / Car (Optional)" style="flex:1;padding:10px;border:1px solid #cbd5e1;border-radius:8px"/>
            <input id="custPhone" placeholder="Mobile (Optional)" style="flex:1;padding:10px;border:1px solid #cbd5e1;border-radius:8px"/>
          </div>
        </div>
        $itemsHtml
        <div id="cart" style="margin-top:16px;padding:12px;background:#eff6ff;border-radius:12px"></div>
        <button onclick="submitOrder()" style="width:100%;margin-top:12px;padding:14px;background:#155EEF;color:#fff;border:none;border-radius:12px;font-size:16px;font-weight:bold">Send Order to Kitchen</button>
        <script>
        const cart={};
        function add(id,name,price){cart[id]=cart[id]||{productId:id,name,price,qty:0};cart[id].qty++;render();}
        function render(){const items=Object.values(cart);document.getElementById('cart').innerHTML=items.map(i=>i.qty+'x '+i.name+' ('+(i.qty*i.price).toFixed(2)+' SAR)').join('<br>')||'Cart empty';}
        async function submitOrder(){const name=document.getElementById('custName').value.trim();if(!name)return alert('Customer Name is required');const items=Object.values(cart).map(i=>({productId:i.productId,qty:i.qty}));if(!items.length)return alert('Add items first');
        await fetch('/api/qr-order',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({tableOrCar:document.getElementById('tableOrCar').value.trim(),customerName:name,customerPhone:document.getElementById('custPhone').value.trim(),items})});
        alert('Order Sent to Kitchen!');Object.keys(cart).forEach(k=>delete cart[k]);render();}
        </script></body></html>"""
    }
}
