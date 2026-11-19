package com.example.hardware

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.model.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class CashFixBackupSnapshot(
    val exportedAt: Long,
    val settings: StoreSettings,
    val branches: List<Branch>,
    val categories: List<Category>,
    val products: List<Product>,
    val customers: List<Customer>,
    val orders: List<PosOrder>
)

object StandaloneExporter {
    private val json = Json { prettyPrint = true; encodeDefaults = true }

    fun buildStandaloneOfflineHtml(
        settings: StoreSettings,
        categories: List<Category>,
        products: List<Product>
    ): String {
        val productCards = products.joinToString("\n") { p ->
            """{"id":"${p.id}","cat":"${p.categoryId}","nameEn":"${escapeJs(p.nameEn)}","nameAr":"${escapeJs(p.nameAr)}","off":${p.offlinePrice},"on":${p.onlinePrice}}"""
        }
        return """<!DOCTYPE html>
<html lang="en" dir="ltr">
<head>
<meta charset="UTF-8" />
<meta name="viewport" content="width=device-width,initial-scale=1.0" />
<title>${settings.shopNameEn} — CashFix Offline POS</title>
<style>
  body { font-family: system-ui, -apple-system, sans-serif; margin: 0; background: #f8fafc; color: #0f172a; }
  header { background: #1D4ED8; color: #fff; padding: 14px 18px; display: flex; justify-content: space-between; align-items: center; }
  .wrap { display: grid; grid-template-columns: 1.5fr 1fr; gap: 16px; padding: 16px; max-width: 1200px; margin: auto; }
  @media (max-width: 768px) { .wrap { grid-template-columns: 1fr; } }
  .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(160px, 1fr)); gap: 10px; }
  .card { background: #fff; border: 1px solid #cbd5e1; border-radius: 12px; padding: 12px; cursor: pointer; }
  .card:hover { border-color: #1D4ED8; }
  .cart { background: #fff; border: 1px solid #cbd5e1; border-radius: 14px; padding: 14px; }
  .btn { background: #1D4ED8; color: #fff; border: none; border-radius: 10px; padding: 10px 14px; font-weight: 600; cursor: pointer; width: 100%; }
</style>
</head>
<body>
<header>
  <div><b>${settings.shopNameEn}</b> | ${settings.shopNameAr}<br/><small>VAT #: ${settings.vatNumber}</small></div>
  <button onclick="toggleLang()" style="background:#fff;color:#1D4ED8;border:none;border-radius:8px;padding:6px 12px;font-weight:bold">EN / عربي</button>
</header>
<div class="wrap">
  <div>
    <div style="margin-bottom:10px">
      <button onclick="mode='off';render()" style="padding:6px 12px">In-Store</button>
      <button onclick="mode='on';render()" style="padding:6px 12px">Delivery Apps</button>
    </div>
    <div id="products" class="grid"></div>
  </div>
  <div class="cart">
    <h3 id="cartTitle">Current Order</h3>
    <div id="cartItems"></div>
    <hr/>
    <div id="totals"></div>
    <button class="btn" onclick="alert('Offline Standalone Receipt Ready!')">Checkout (15% VAT)</button>
  </div>
</div>
<script>
const PRODUCTS = [$productCards];
let lang = 'en', mode = 'off', cart = [];
function toggleLang(){ lang = lang==='en'?'ar':'en'; document.documentElement.dir = lang==='ar'?'rtl':'ltr'; render(); }
function add(id){ const p = PRODUCTS.find(x=>x.id===id); const ex = cart.find(x=>x.id===id); if(ex) ex.qty++; else cart.push({p,qty:1}); render(); }
function render(){
  document.getElementById('products').innerHTML = PRODUCTS.map(p =>
    '<div class="card" onclick="add(\''+p.id+'\')"><b>'+(lang==='ar'?p.nameAr:p.nameEn)+'</b><br/><span>'+(mode==='on'?p.on:p.off).toFixed(2)+' SAR</span></div>'
  ).join('');
  let sub = 0;
  document.getElementById('cartItems').innerHTML = cart.map(c => {
    const price = mode==='on'?c.p.on:c.p.off; sub += price*c.qty;
    return '<div>'+c.qty+'x '+(lang==='ar'?c.p.nameAr:c.p.nameEn)+' — '+(price*c.qty).toFixed(2)+'</div>';
  }).join('') || 'Empty';
  const vat = ${if (settings.vatInclusive) "sub - (sub / 1.15)" else "sub * 0.15"};
  const total = ${if (settings.vatInclusive) "sub" else "sub + vat"};
  document.getElementById('totals').innerHTML = 'VAT 15%: '+vat.toFixed(2)+' SAR<br/><b>Total: '+total.toFixed(2)+' SAR</b>';
}
render();
</script>
</body>
</html>"""
    }

    fun buildJsonBackup(
        settings: StoreSettings,
        branches: List<Branch>,
        categories: List<Category>,
        products: List<Product>,
        customers: List<Customer>,
        orders: List<PosOrder>
    ): String {
        val snapshot = CashFixBackupSnapshot(
            exportedAt = System.currentTimeMillis(),
            settings = settings,
            branches = branches,
            categories = categories,
            products = products,
            customers = customers,
            orders = orders
        )
        return json.encodeToString(snapshot)
    }

    fun copyToClipboard(context: Context, label: String, text: String) {
        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cb.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    fun shareTextFile(context: Context, fileName: String, content: String, mimeType: String) {
        val file = File(context.cacheDir, fileName)
        file.writeText(content, Charsets.UTF_8)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_SUBJECT, fileName)
            putExtra(Intent.EXTRA_TEXT, content)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, fileName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun escapeJs(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")
}
