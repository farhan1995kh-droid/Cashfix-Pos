package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.hardware.ZatcaQrAndEscPos
import com.example.i18n.AppLang
import com.example.i18n.formatNumber
import com.example.i18n.stringsFor
import com.example.model.*
import com.example.ui.theme.StatusDanger
import com.example.ui.theme.StatusSuccess
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

private enum class AdminTab { STORE_VAT, CATALOG, OFFERS, TEAM, QR_TENTS }

@Composable
fun AdminPanelScreen(
    lang: AppLang,
    isFullAdmin: Boolean,
    settings: StoreSettings,
    categories: List<Category>,
    products: List<Product>,
    productOffers: List<ProductOffer>,
    cashiers: List<Cashier>,
    orderSources: List<OrderSource>,
    localIp: String,
    onBack: () -> Unit,
    onSaveCategory: (Category) -> Unit,
    onRemoveCategory: (String) -> Unit,
    onSaveProduct: (Product) -> Unit,
    onImportProductImage: suspend (String, String) -> String = { uri, _ -> uri },
    onRemoveProduct: (String) -> Unit,
    onSaveOffer: (ProductOffer) -> Unit,
    onRemoveOffer: (String) -> Unit,
    onSaveCashier: (Cashier) -> Unit,
    onRemoveCashier: (String) -> Unit,
    onSaveOrderSource: (OrderSource) -> Unit,
    onSaveSettings: (StoreSettings) -> Unit,
    backgroundAlertsActive: Boolean = false,
    hasNotificationPermission: Boolean = true,
    onToggleAlwaysOn: () -> Unit = {},
    onToggleBackgroundAlerts: (Boolean) -> Unit = {},
    onRequestNotificationPermission: () -> Unit = {}
) {
    val s = stringsFor(lang)
    BackHandler(onBack = onBack)
    var activeTab by remember(isFullAdmin) {
        mutableStateOf(if (isFullAdmin) AdminTab.STORE_VAT else AdminTab.CATALOG)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.close)
                }
                Text(
                    text = if (isFullAdmin) s.managementSettings else "${s.managementSettings} • ${s.menuCustomizationTitle}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        val tabs = if (isFullAdmin) {
            listOf(
                AdminTab.STORE_VAT to s.storeVatTab,
                AdminTab.CATALOG to s.catalogTab,
                AdminTab.OFFERS to s.offersTab,
                AdminTab.TEAM to s.teamTab,
                AdminTab.QR_TENTS to s.qrTentsTab
            )
        } else {
            listOf(AdminTab.CATALOG to s.catalogTab)
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(tabs) { (tab, label) ->
                FilterChip(
                    selected = activeTab == tab,
                    onClick = { activeTab = tab },
                    modifier = Modifier.testTag("admin_tab_${tab.name}"),
                    label = { Text(label, fontWeight = if (activeTab == tab) FontWeight.Bold else FontWeight.Medium) }
                )
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            when (activeTab) {
                AdminTab.STORE_VAT -> if (isFullAdmin) StoreVatTab(
                    lang = lang,
                    settings = settings,
                    orderSources = orderSources,
                    backgroundAlertsActive = backgroundAlertsActive,
                    hasNotificationPermission = hasNotificationPermission,
                    onToggleAlwaysOn = onToggleAlwaysOn,
                    onToggleBackgroundAlerts = onToggleBackgroundAlerts,
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onSaveSettings = onSaveSettings,
                    onSaveOrderSource = onSaveOrderSource
                )
                AdminTab.CATALOG -> CatalogTab(lang, categories, products, onSaveCategory, onRemoveCategory, onSaveProduct, onImportProductImage, onRemoveProduct)
                AdminTab.OFFERS -> if (isFullAdmin) OffersTab(lang, categories, products, productOffers, onSaveOffer, onRemoveOffer)
                AdminTab.TEAM -> if (isFullAdmin) TeamTab(lang, cashiers, onSaveCashier, onRemoveCashier)
                AdminTab.QR_TENTS -> if (isFullAdmin) QrTentsTab(lang, settings, localIp)
            }
        }
    }
}



@Composable
internal fun CatalogTab(
    lang: AppLang,
    categories: List<Category>,
    products: List<Product>,
    onSaveCategory: (Category) -> Unit,
    onRemoveCategory: (String) -> Unit,
    onSaveProduct: (Product) -> Unit,
    onImportProductImage: suspend (String, String) -> String = { uri, _ -> uri },
    onRemoveProduct: (String) -> Unit
) {
    val s = stringsFor(lang)
    var editingProduct by remember { mutableStateOf<Product?>(null) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                editingProduct = Product(
                    id = "prd_${UUID.randomUUID().toString().take(6)}",
                    categoryId = categories.firstOrNull()?.id ?: "cat_zingers",
                    nameEn = "", nameAr = "", sku = "SKU-${(100..999).random()}",
                    barcode = "628100${(100000..999999).random()}",
                    offlinePrice = 15.0, onlinePrice = 18.0, stock = 50, isAvailable = true
                )
            }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(s.addProduct)
            }
            OutlinedButton(onClick = {
                editingCategory = Category(
                    id = "cat_${UUID.randomUUID().toString().take(6)}",
                    nameEn = "",
                    nameAr = "",
                    sortOrder = categories.size + 1,
                    parentCategoryId = null
                )
            }) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(s.addCategory)
            }
        }

        // Category Folders & Sub-Folders strip
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(categories, key = { it.id }) { cat ->
                val parent = categories.firstOrNull { it.id == cat.parentCategoryId }
                val label = if (parent != null) "${parent.nameEn} → ${cat.displayName(lang)}" else cat.displayName(lang)
                AssistChip(
                    onClick = { editingCategory = cat },
                    leadingIcon = {
                        Icon(
                            imageVector = if (parent != null) Icons.Default.Folder else Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text(label) }
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 360.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(products, key = { it.id }) { p ->
                Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (p.imageUri.isNotBlank()) {
                            AsyncImage(
                                model = File(p.imageUri).takeIf { it.exists() } ?: p.imageUri,
                                contentDescription = p.displayName(lang),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                        } else {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Fastfood,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${p.nameEn} / ${p.nameAr}", fontWeight = FontWeight.Bold)
                            Text(
                                text = "Off: ${formatNumber(p.offlinePrice)} SAR | App: ${formatNumber(p.onlinePrice)} SAR | Stock: ${p.stock}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = p.isAvailable,
                                onCheckedChange = { onSaveProduct(p.copy(isAvailable = it)) }
                            )
                            IconButton(onClick = { editingProduct = p }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit")
                            }
                            IconButton(onClick = { onRemoveProduct(p.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = StatusDanger)
                            }
                        }
                    }
                }
            }
        }
    }

    editingProduct?.let { p ->
        val scope = rememberCoroutineScope()
        var nameEn by remember(p) { mutableStateOf(p.nameEn) }
        var nameAr by remember(p) { mutableStateOf(p.nameAr) }
        var catId by remember(p) { mutableStateOf(p.categoryId) }
        var sku by remember(p) { mutableStateOf(p.sku) }
        var barcode by remember(p) { mutableStateOf(p.barcode) }
        var offPrice by remember(p) { mutableStateOf(formatNumber(p.offlinePrice)) }
        var onPrice by remember(p) { mutableStateOf(formatNumber(p.onlinePrice)) }
        var stock by remember(p) { mutableStateOf(p.stock.toString()) }
        var imageUri by remember(p) { mutableStateOf(p.imageUri) }

        val photoPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            if (uri != null) {
                scope.launch {
                    val savedPath = onImportProductImage(uri.toString(), p.id)
                    if (savedPath.isNotBlank()) imageUri = savedPath
                }
            }
        }

        AlertDialog(
            onDismissRequest = { editingProduct = null },
            title = { Text(if (p.nameEn.isBlank()) s.addProduct else p.displayName(lang)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Product Photo Upload Card (Zero-Permission Android Photo Picker)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (imageUri.isNotBlank()) {
                                AsyncImage(
                                    model = File(imageUri).takeIf { it.exists() } ?: imageUri,
                                    contentDescription = s.productPhotoLabel,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                )
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(64.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(s.productPhotoLabel, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilledTonalButton(
                                        onClick = {
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        modifier = Modifier.testTag("pick_product_photo_btn"),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(if (imageUri.isBlank()) s.addPhotoBtn else s.changePhotoBtn, style = MaterialTheme.typography.labelSmall)
                                    }
                                    if (imageUri.isNotBlank()) {
                                        TextButton(
                                            onClick = { imageUri = "" },
                                            colors = ButtonDefaults.textButtonColors(contentColor = StatusDanger),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(s.removePhotoBtn, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    OutlinedTextField(value = nameEn, onValueChange = { nameEn = it }, label = { Text("Name (EN)") }, singleLine = true)
                    OutlinedTextField(value = nameAr, onValueChange = { nameAr = it }, label = { Text("الاسم (AR)") }, singleLine = true)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(categories) { c ->
                            FilterChip(selected = catId == c.id, onClick = { catId = c.id }, label = { Text(c.displayName(lang)) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = offPrice, onValueChange = { offPrice = it }, label = { Text(s.offlinePriceLabel) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = onPrice, onValueChange = { onPrice = it }, label = { Text(s.onlinePriceLabel) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f), singleLine = true)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = sku, onValueChange = { sku = it }, label = { Text("SKU") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = stock, onValueChange = { stock = it.filter(Char::isDigit) }, label = { Text(s.stockLabel) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), singleLine = true)
                    }
                    OutlinedTextField(value = barcode, onValueChange = { barcode = it }, label = { Text(s.barcodeLabel) }, singleLine = true)
                }
            },
            confirmButton = {
                Button(
                    enabled = nameEn.isNotBlank(),
                    onClick = {
                        onSaveProduct(
                            p.copy(
                                categoryId = catId,
                                nameEn = nameEn,
                                nameAr = nameAr.ifBlank { nameEn },
                                sku = sku,
                                barcode = barcode,
                                offlinePrice = offPrice.toDoubleOrNull() ?: p.offlinePrice,
                                onlinePrice = onPrice.toDoubleOrNull() ?: p.onlinePrice,
                                stock = stock.toIntOrNull() ?: p.stock,
                                imageUri = imageUri
                            )
                        )
                        editingProduct = null
                    }
                ) { Text(s.confirm) }
            },
            dismissButton = { TextButton(onClick = { editingProduct = null }) { Text(s.cancel) } }
        )
    }

    editingCategory?.let { cat ->
        var cEn by remember(cat) { mutableStateOf(cat.nameEn) }
        var cAr by remember(cat) { mutableStateOf(cat.nameAr) }
        var parentId by remember(cat) { mutableStateOf(cat.parentCategoryId) }
        val topLevelCandidates = categories.filter { it.parentCategoryId == null && it.id != cat.id }

        AlertDialog(
            onDismissRequest = { editingCategory = null },
            title = { Text(if (cat.nameEn.isBlank()) s.addCategory else s.editCategory) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(value = cEn, onValueChange = { cEn = it }, label = { Text("Folder / Category (EN)") }, singleLine = true)
                    OutlinedTextField(value = cAr, onValueChange = { cAr = it }, label = { Text("المجلد / التصنيف (AR)") }, singleLine = true)
                    Text(s.parentFolderLabel, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            FilterChip(
                                selected = parentId == null,
                                onClick = { parentId = null },
                                label = { Text(s.topLevelFolder) }
                            )
                        }
                        items(topLevelCandidates, key = { it.id }) { parent ->
                            FilterChip(
                                selected = parentId == parent.id,
                                onClick = { parentId = parent.id },
                                label = { Text(parent.displayName(lang)) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = cEn.isNotBlank(),
                    onClick = {
                        onSaveCategory(
                            cat.copy(
                                nameEn = cEn.trim(),
                                nameAr = cAr.ifBlank { cEn }.trim(),
                                parentCategoryId = parentId
                            )
                        )
                        editingCategory = null
                    }
                ) { Text(s.confirm) }
            },
            dismissButton = { TextButton(onClick = { editingCategory = null }) { Text(s.cancel) } }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OffersTab(
    lang: AppLang,
    categories: List<Category>,
    products: List<Product>,
    offers: List<ProductOffer>,
    onSaveOffer: (ProductOffer) -> Unit,
    onRemoveOffer: (String) -> Unit
) {
    val s = stringsFor(lang)
    var editingOffer by remember { mutableStateOf<ProductOffer?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = {
                val defaultCat = categories.firstOrNull()
                val defaultProduct = products.firstOrNull()
                editingOffer = ProductOffer(
                    id = "off_${UUID.randomUUID().toString().take(6)}",
                    titleEn = "",
                    titleAr = "",
                    targetProductId = defaultCat?.let { "cat:${it.id}" } ?: defaultProduct?.id.orEmpty(),
                    isEnabled = true,
                    options = emptyList()
                )
            },
            modifier = Modifier.testTag("admin_add_offer_btn")
        ) {
            Icon(Icons.Default.LocalOffer, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(s.addOffer)
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 360.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(offers, key = { it.id }) { offer ->
                val targetLabel = if (offer.isCategoryTarget) {
                    val cat = categories.firstOrNull { it.id == offer.targetCategoryIdOrNull }
                    val prefix = if (lang == AppLang.AR) "القسم" else "Category Folder"
                    "$prefix: ${cat?.displayName(lang) ?: offer.targetCategoryIdOrNull.orEmpty()}"
                } else {
                    val targetProd = products.firstOrNull { it.id == offer.targetProductId }
                    "${s.offerForProduct}: ${targetProd?.displayName(lang) ?: offer.targetProductId}"
                }
                Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(offer.displayTitle(lang), fontWeight = FontWeight.Bold)
                                Text(
                                    text = targetLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = offer.isEnabled,
                                    onCheckedChange = { onSaveOffer(offer.copy(isEnabled = it)) }
                                )
                                IconButton(onClick = { editingOffer = offer }) {
                                    Icon(Icons.Default.Edit, contentDescription = s.editOffer)
                                }
                                IconButton(onClick = { onRemoveOffer(offer.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = StatusDanger)
                                }
                            }
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            offer.options.forEach { opt ->
                                val isFree = opt.extraPrice <= 0.001
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isFree) StatusSuccess.copy(alpha = 0.14f) else MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = if (isFree) {
                                            "${opt.displayName(lang)} • ${s.freeLabel}"
                                        } else {
                                            "${opt.displayName(lang)} • +${formatNumber(opt.extraPrice)} SAR"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isFree) StatusSuccess else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editingOffer?.let { off ->
        var titleEn by remember(off) { mutableStateOf(off.titleEn) }
        var titleAr by remember(off) { mutableStateOf(off.titleAr) }
        var isCategoryTarget by remember(off) { mutableStateOf(off.isCategoryTarget) }
        var targetId by remember(off) { mutableStateOf(off.targetProductId) }
        var options by remember(off) { mutableStateOf(off.options) }
        var optNameEn by remember(off) { mutableStateOf("") }
        var optNameAr by remember(off) { mutableStateOf("") }
        var optExtraPrice by remember(off) { mutableStateOf("0") }

        AlertDialog(
            onDismissRequest = { editingOffer = null },
            title = { Text(if (off.titleEn.isBlank()) s.addOffer else s.editOffer, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = titleEn,
                        onValueChange = { titleEn = it },
                        label = { Text("Offer Title (EN) e.g. Make it a Meal (+7 SAR)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("offer_title_en_input")
                    )
                    OutlinedTextField(
                        value = titleAr,
                        onValueChange = { titleAr = it },
                        label = { Text("عنوان العرض (AR)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = if (lang == AppLang.AR) "تطبيق العرض على:" else "Apply Offer To:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = isCategoryTarget,
                            onClick = {
                                isCategoryTarget = true
                                if (!targetId.startsWith("cat:")) {
                                    targetId = categories.firstOrNull()?.let { "cat:${it.id}" } ?: ""
                                }
                            },
                            label = { Text(if (lang == AppLang.AR) "مجلد / قسم كامل" else "Category Folder") }
                        )
                        FilterChip(
                            selected = !isCategoryTarget,
                            onClick = {
                                isCategoryTarget = false
                                if (targetId.startsWith("cat:") || targetId.isBlank()) {
                                    targetId = products.firstOrNull()?.id ?: ""
                                }
                            },
                            label = { Text(if (lang == AppLang.AR) "منتج محدد" else "Specific Product") }
                        )
                    }
                    if (isCategoryTarget) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(categories, key = { it.id }) { cat ->
                                val catTarget = "cat:${cat.id}"
                                FilterChip(
                                    selected = targetId == catTarget,
                                    onClick = { targetId = catTarget },
                                    label = { Text(cat.displayName(lang)) }
                                )
                            }
                        }
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(products, key = { it.id }) { prod ->
                                FilterChip(
                                    selected = targetId == prod.id,
                                    onClick = { targetId = prod.id },
                                    label = { Text("${prod.nameEn} (${formatNumber(prod.offlinePrice)} SAR)") }
                                )
                            }
                        }
                    }

                    HorizontalDivider()
                    Text(s.offerOptionsLabel, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

                    // Existing options in this offer
                    options.forEach { opt ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${opt.nameEn} / ${opt.nameAr}", fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = if (opt.extraPrice <= 0.001) s.freeLabel else "+${formatNumber(opt.extraPrice)} SAR",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (opt.extraPrice <= 0.001) StatusSuccess else MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            IconButton(onClick = { options = options.filterNot { it.id == opt.id } }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove", tint = StatusDanger)
                            }
                        }
                    }

                    // Quick-fill from existing drinks/sides/menu products
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(
                            products
                                .filter { it.id != targetId && "cat:${it.categoryId}" != targetId }
                                .sortedBy { if (it.categoryId in setOf("cat_drinks", "cat_soft_drinks", "cat_sauces", "cat_crunchies")) 0 else 1 }
                                .take(12),
                            key = { it.id }
                        ) { p ->
                            AssistChip(
                                onClick = {
                                    optNameEn = p.nameEn
                                    optNameAr = p.nameAr
                                },
                                label = { Text("+ ${p.nameEn}") }
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = optNameEn,
                            onValueChange = { optNameEn = it },
                            label = { Text("Choice (EN)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("offer_option_name_input")
                        )
                        OutlinedTextField(
                            value = optNameAr,
                            onValueChange = { optNameAr = it },
                            label = { Text("الخيار (AR)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = optExtraPrice,
                            onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) optExtraPrice = it },
                            label = { Text(s.extraPriceLabel) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("offer_option_extra_price_input")
                        )
                        FilledTonalButton(
                            enabled = optNameEn.isNotBlank(),
                            onClick = {
                                val extra = optExtraPrice.toDoubleOrNull() ?: 0.0
                                options = options + OfferOption(
                                    id = "opt_${UUID.randomUUID().toString().take(6)}",
                                    nameEn = optNameEn.trim(),
                                    nameAr = optNameAr.ifBlank { optNameEn }.trim(),
                                    extraPrice = extra.coerceAtLeast(0.0)
                                )
                                optNameEn = ""
                                optNameAr = ""
                                optExtraPrice = "0"
                            },
                            modifier = Modifier.testTag("add_offer_option_btn")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(s.addOfferOption)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = titleEn.isNotBlank() && targetId.isNotBlank() && options.isNotEmpty(),
                    onClick = {
                        onSaveOffer(
                            off.copy(
                                titleEn = titleEn.trim(),
                                titleAr = titleAr.ifBlank { titleEn }.trim(),
                                targetProductId = targetId,
                                options = options
                            )
                        )
                        editingOffer = null
                    },
                    modifier = Modifier.testTag("save_offer_btn")
                ) { Text(s.confirm) }
            },
            dismissButton = { TextButton(onClick = { editingOffer = null }) { Text(s.cancel) } }
        )
    }
}

@Composable
private fun TeamTab(
    lang: AppLang,
    cashiers: List<Cashier>,
    onSaveCashier: (Cashier) -> Unit,
    onRemoveCashier: (String) -> Unit
) {
    val s = stringsFor(lang)
    var editingCashier by remember { mutableStateOf<Cashier?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = {
            editingCashier = Cashier(
                id = "csh_${UUID.randomUUID().toString().take(6)}",
                nameEn = "", nameAr = "", pin = "3333", role = "cashier",
                branchId = "br_hofuf",
                permissions = CashierPermissions()
            )
        }) {
            Icon(Icons.Default.PersonAdd, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(s.addCashier)
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 360.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(cashiers, key = { it.id }) { c ->
                Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${c.nameEn} / ${c.nameAr}", fontWeight = FontWeight.Bold)
                            val roleDetail = if (c.isAdminOrManager) {
                                "Role: ${c.role} • Admin Lock"
                            } else {
                                "Role: ${c.role} • No Lock"
                            }
                            Text(roleDetail, style = MaterialTheme.typography.bodySmall)
                        }
                        Row {
                            IconButton(onClick = { editingCashier = c }) { Icon(Icons.Default.Edit, contentDescription = "Edit") }
                            if (cashiers.size > 1 && c.role != "admin") {
                                IconButton(onClick = { onRemoveCashier(c.id) }) { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = StatusDanger) }
                            }
                        }
                    }
                }
            }
        }
    }

    editingCashier?.let { c ->
        var nEn by remember(c) { mutableStateOf(c.nameEn) }
        var nAr by remember(c) { mutableStateOf(c.nameAr) }
        var pin by remember(c) { mutableStateOf(c.pin) }
        var role by remember(c) { mutableStateOf(c.role) }
        var perms by remember(c) { mutableStateOf(c.permissions) }

        AlertDialog(
            onDismissRequest = { editingCashier = null },
            title = { Text(if (c.nameEn.isBlank()) s.addCashier else c.displayName(lang)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(value = nEn, onValueChange = { nEn = it }, label = { Text("Name (EN)") }, singleLine = true)
                    OutlinedTextField(value = nAr, onValueChange = { nAr = it }, label = { Text("الاسم (AR)") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = role == "cashier", onClick = { role = "cashier" }, label = { Text("Cashier (No Lock)") })
                        FilterChip(selected = role == "admin", onClick = { role = "admin" }, label = { Text("Admin (PIN Locked)") })
                    }
                    HorizontalDivider()
                    PermissionToggleRow("Take Orders", perms.canTakeOrders) { perms = perms.copy(canTakeOrders = it) }
                    PermissionToggleRow("Customize Menu (Add/Edit Products)", perms.canAddProducts) { perms = perms.copy(canAddProducts = it) }
                    PermissionToggleRow("Process Refunds", perms.canProcessRefunds) { perms = perms.copy(canProcessRefunds = it) }
                    PermissionToggleRow("Hold Orders", perms.canHoldOrders) { perms = perms.copy(canHoldOrders = it) }
                    PermissionToggleRow("View Reports", perms.canViewReports) { perms = perms.copy(canViewReports = it) }
                    PermissionToggleRow("Access Management", perms.canAccessManagement) { perms = perms.copy(canAccessManagement = it) }
                }
            },
            confirmButton = {
                Button(
                    enabled = nEn.isNotBlank(),
                    onClick = {
                        onSaveCashier(c.copy(nameEn = nEn, nameAr = nAr.ifBlank { nEn }, pin = pin.ifBlank { "1111" }, role = role, branchId = "br_hofuf", permissions = perms))
                        editingCashier = null
                    }
                ) { Text(s.confirm) }
            },
            dismissButton = { TextButton(onClick = { editingCashier = null }) { Text(s.cancel) } }
        )
    }
}

@Composable
private fun PermissionToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun StoreVatTab(
    lang: AppLang,
    settings: StoreSettings,
    orderSources: List<OrderSource>,
    backgroundAlertsActive: Boolean,
    hasNotificationPermission: Boolean,
    onToggleAlwaysOn: () -> Unit,
    onToggleBackgroundAlerts: (Boolean) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onSaveSettings: (StoreSettings) -> Unit,
    onSaveOrderSource: (OrderSource) -> Unit
) {
    val s = stringsFor(lang)
    var shopEn by remember(settings) { mutableStateOf(settings.shopNameEn) }
    var shopAr by remember(settings) { mutableStateOf(settings.shopNameAr) }
    var addressEn by remember(settings) { mutableStateOf(settings.addressEn) }
    var addressAr by remember(settings) { mutableStateOf(settings.addressAr) }
    var phone by remember(settings) { mutableStateOf(settings.phone) }
    var vatNum by remember(settings) { mutableStateOf(settings.vatNumber) }
    var footerEn by remember(settings) { mutableStateOf(settings.receiptFooterEn) }
    var footerAr by remember(settings) { mutableStateOf(settings.receiptFooterAr) }
    var vatInclusive by remember(settings) { mutableStateOf(settings.vatInclusive) }
    var adminPin by remember(settings) { mutableStateOf(settings.adminPin) }
    var autoKick by remember(settings) { mutableStateOf(settings.autoKickDrawer) }
    var printerIp by remember(settings) { mutableStateOf(settings.printerIp) }
    var printerPort by remember(settings) { mutableStateOf(settings.printerPort.toString()) }
    var alwaysOn by remember(settings) { mutableStateOf(settings.alwaysOnDisplay) }
    var bgAlerts by remember(settings) { mutableStateOf(settings.backgroundOrderAlertsEnabled) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Card 1: Single-Branch Bilingual Store Identity & Al Hofuf Details
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Storefront, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = if (lang == AppLang.AR) "بيانات المتجر والفاتورة (فرع واحد)" else "Single-Branch Store & Receipt Setup",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = StatusSuccess.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = if (lang == AppLang.AR) "تحديث فوري للفاتورة" else "Live Receipt Sync",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = StatusSuccess,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = shopEn,
                        onValueChange = { shopEn = it },
                        label = { Text("Shop / Brand Name (English)") },
                        modifier = Modifier.weight(1f).testTag("store_name_en_input"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = shopAr,
                        onValueChange = { shopAr = it },
                        label = { Text("اسم المتجر / العلامة (عربي)") },
                        modifier = Modifier.weight(1f).testTag("store_name_ar_input"),
                        singleLine = true
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = addressEn,
                        onValueChange = { addressEn = it },
                        label = { Text("Shop Address (English)") },
                        placeholder = { Text("Al Hofuf, Saudi Arabia") },
                        modifier = Modifier.weight(1f).testTag("store_location_en_input"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = addressAr,
                        onValueChange = { addressAr = it },
                        label = { Text("عنوان المتجر (عربي)") },
                        placeholder = { Text("الأحساء، المملكة العربية السعودية") },
                        modifier = Modifier.weight(1f).testTag("store_location_ar_input"),
                        singleLine = true
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = vatNum,
                        onValueChange = { input ->
                            val digits = input.filter(Char::isDigit).take(15)
                            vatNum = digits
                        },
                        label = { Text(if (lang == AppLang.AR) "الرقم الضريبي (15 رقم)" else "15-Digit VAT Registration No.") },
                        supportingText = {
                            Text("${vatNum.length}/15 ${if (lang == AppLang.AR) "رقم" else "digits"}")
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f).testTag("store_vat_input"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text(if (lang == AppLang.AR) "رقم الهاتف للتواصل" else "Contact Phone Number") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.weight(1f).testTag("store_phone_input"),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = footerEn,
                    onValueChange = { footerEn = it },
                    label = { Text("Receipt Footer Thank-You Message (English)") },
                    modifier = Modifier.fillMaxWidth().testTag("store_footer_en_input")
                )
                OutlinedTextField(
                    value = footerAr,
                    onValueChange = { footerAr = it },
                    label = { Text("رسالة الشكر أسفل الفاتورة (عربي)") },
                    modifier = Modifier.fillMaxWidth().testTag("store_footer_ar_input")
                )
            }
        }

        // Card 2: 80mm Wi-Fi Thermal Printer (TCP Port 9100), VAT & Security
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Print, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = if (lang == AppLang.AR) "طابعة حرارية 80mm والضريبة والأمان" else "80mm Wi-Fi Printer, VAT & Admin Lock",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "80mm • 576px RTL Raster",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = printerIp,
                        onValueChange = { printerIp = it },
                        label = { Text(if (lang == AppLang.AR) "عنوان IP للطابعة" else "Wi-Fi Printer IP") },
                        singleLine = true,
                        modifier = Modifier.weight(2f).testTag("store_printer_ip_input")
                    )
                    OutlinedTextField(
                        value = printerPort,
                        onValueChange = { printerPort = it.filter(Char::isDigit).take(5) },
                        label = { Text("TCP Port") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("store_printer_port_input")
                    )
                }

                OutlinedTextField(
                    value = adminPin,
                    onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) adminPin = it },
                    label = { Text(if (lang == AppLang.AR) "رمز قفل الإدارة (4 أرقام)" else "Admin Lock PIN (4 digits)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (vatInclusive) s.vatInclusiveNote else s.vatExclusiveNote, modifier = Modifier.weight(1f))
                    Switch(checked = vatInclusive, onCheckedChange = { vatInclusive = it })
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (lang == AppLang.AR) "فتح درج النقدية تلقائياً عند الدفع النقدي" else "Auto-Kick Cash Drawer on Cash Payments",
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = autoKick, onCheckedChange = { autoKick = it })
                }

                Button(
                    onClick = {
                        onSaveSettings(
                            settings.copy(
                                shopNameEn = shopEn.trim().ifBlank { "Crispy Nation" },
                                shopNameAr = shopAr.trim().ifBlank { shopEn.trim().ifBlank { "كريسبي نيشن" } },
                                addressEn = addressEn.trim().ifBlank { "Al Hofuf, Saudi Arabia" },
                                addressAr = addressAr.trim().ifBlank { "الأحساء، المملكة العربية السعودية" },
                                phone = phone.trim(),
                                vatNumber = vatNum.trim(),
                                receiptFooterEn = footerEn.trim(),
                                receiptFooterAr = footerAr.trim().ifBlank { footerEn.trim() },
                                vatInclusive = vatInclusive,
                                adminPin = adminPin.ifBlank { "1234" },
                                autoKickDrawer = autoKick,
                                printerIp = printerIp.trim().ifBlank { settings.printerIp },
                                printerPort = printerPort.toIntOrNull() ?: 9100,
                                paperWidthMm = 80,
                                alwaysOnDisplay = alwaysOn,
                                backgroundOrderAlertsEnabled = bgAlerts
                            )
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("save_store_settings_btn")
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (lang == AppLang.AR) "حفظ إعدادات المتجر والفاتورة 80mm" else "Save Store Setup & 80mm Receipt Settings",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Card 2B: Always-On Display & Background Multi-App QR Order Alerts
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = if (lang == AppLang.AR) "بقاء الشاشة مضاءة وتنبيهات الطلبات بالخلفية" else "Always-On Display & Multi-App Alerts",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    val alertStatusHealthy = bgAlerts && backgroundAlertsActive && hasNotificationPermission
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (alertStatusHealthy) StatusSuccess.copy(alpha = 0.14f) else MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = when {
                                alertStatusHealthy -> if (lang == AppLang.AR) "نشط بالخلفية" else "BG Active"
                                !hasNotificationPermission -> if (lang == AppLang.AR) "الإذن مطلوب" else "Permission Needed"
                                else -> if (lang == AppLang.AR) "متوقف" else "Paused"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (alertStatusHealthy) StatusSuccess else StatusDanger,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Always-On Display Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (lang == AppLang.AR) "وضع الشاشة المضاءة دائماً (Always-On Display)" else "Always-On Display (Keep Screen Awake)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (lang == AppLang.AR) {
                                "يمنع إطفاء شاشة نقطة البيع أثناء الوردية النشطة"
                            } else {
                                "Prevents accidental screen sleep while CashFix POS is open during active shifts."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = alwaysOn,
                        onCheckedChange = {
                            alwaysOn = it
                            onToggleAlwaysOn()
                        },
                        modifier = Modifier.testTag("settings_always_on_switch")
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

                // Background Multi-App Order Alerts Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (lang == AppLang.AR) "تنبيه وجرس 15 ثانية بالخلفية (تعدد التطبيقات)" else "Background QR Order Alerts & 15s Alarm",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (lang == AppLang.AR) {
                                "يعمل حتى عند فتح تطبيق كاشير آخر (مثل Tabsense) مع إشعار علوي وصوت واهتزاز"
                            } else {
                                "Keeps QR polling & 15s sound/vibration alarm active even when another POS app (e.g., Tabsense) is open."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = bgAlerts,
                        onCheckedChange = { enabled ->
                            bgAlerts = enabled
                            onToggleBackgroundAlerts(enabled)
                            if (enabled && !hasNotificationPermission) {
                                onRequestNotificationPermission()
                            }
                        },
                        modifier = Modifier.testTag("settings_bg_alerts_switch")
                    )
                }

                if (!hasNotificationPermission) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                        border = BorderStroke(1.dp, StatusDanger.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (lang == AppLang.AR) {
                                    "امنح إذن الإشعارات لاستلام تنبيهات طلبات QR عند التصغير."
                                } else {
                                    "Grant notification permission to receive heads-up QR alerts when backgrounded."
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            FilledTonalButton(
                                onClick = onRequestNotificationPermission,
                                modifier = Modifier.testTag("grant_notification_permission_btn")
                            ) {
                                Text(if (lang == AppLang.AR) "منح الإذن" else "Allow Alerts")
                            }
                        }
                    }
                }
            }
        }

        // Card 3: Order Sources
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (lang == AppLang.AR) "مصادر الطلبات (محلي وتطبيقات التوصيل)" else "Order Sources (Offline vs Online Delivery Apps)",
                    fontWeight = FontWeight.Bold
                )
                orderSources.forEach { src ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("${src.displayName(lang)} (${src.pricingMode.name})")
                        Switch(checked = src.isEnabled, onCheckedChange = { onSaveOrderSource(src.copy(isEnabled = it)) })
                    }
                }
            }
        }
    }
}

@Composable
private fun QrTentsTab(
    lang: AppLang,
    settings: StoreSettings,
    localIp: String
) {
    val s = stringsFor(lang)
    var tableCode by remember { mutableStateOf("T1") }
    val qrUrl = "http://$localIp:${settings.qrServerPort}/?table=${tableCode.trim()}"
    val bitmap = remember(qrUrl) { ZatcaQrAndEscPos.generateQrBitmap(qrUrl, 360) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = tableCode,
            onValueChange = { tableCode = it },
            label = { Text(s.tableNumber) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(settings.shopDisplayName(lang), color = Color.Black, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Scan to Order • امسح للطلب", color = Color(0xFF1D4ED8), fontWeight = FontWeight.Bold)
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Table Tent QR",
                        modifier = Modifier.size(210.dp).background(Color.White)
                    )
                }
                Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFF1D4ED8)) {
                    Text(
                        text = "${s.tableNumber}: $tableCode",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
                Text(qrUrl, color = Color.Gray, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
