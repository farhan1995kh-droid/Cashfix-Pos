package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.i18n.AppLang
import com.example.i18n.formatNumber
import com.example.i18n.formatSar
import com.example.i18n.stringsFor
import com.example.model.*
import com.example.ui.theme.BluePrimary
import com.example.ui.theme.StatusDanger
import com.example.ui.theme.StatusSuccess
import java.io.File
import java.util.UUID

@Composable
fun CustomerQrOrderScreen(
    lang: AppLang,
    settings: StoreSettings,
    categories: List<Category>,
    products: List<Product>,
    productOffers: List<ProductOffer>,
    onToggleLang: () -> Unit,
    onExitToPos: () -> Unit,
    onSubmitQrOrder: (items: List<CartItem>, tableOrCar: String, customerName: String, customerPhone: String, notes: String) -> Unit
) {
    val s = stringsFor(lang)
    BackHandler(onBack = onExitToPos)

    var selectedTopCatId by remember { mutableStateOf<String?>(null) }
    var selectedSubCatId by remember { mutableStateOf<String?>(null) }
    var customerName by remember { mutableStateOf("") }
    var tableOrCar by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var qrCart by remember { mutableStateOf<List<CartItem>>(emptyList()) }
    var orderSentBanner by remember { mutableStateOf(false) }
    var pendingOfferProduct by remember { mutableStateOf<Pair<Product, ProductOffer>?>(null) }

    val topCategories = remember(categories) {
        categories.filter { it.parentCategoryId == null }.sortedBy { it.sortOrder }
    }
    val subCategoriesForSelected = remember(categories, selectedTopCatId) {
        if (selectedTopCatId == null) emptyList()
        else categories.filter { it.parentCategoryId == selectedTopCatId }.sortedBy { it.sortOrder }
    }
    val activeOfferProductIds = remember(products, productOffers) {
        products.filter { productOffers.activeOfferFor(it) != null }.map { it.id }.toSet()
    }

    // Group visible categories cleanly for Menu Bar + Section Headers
    val visibleSections: List<Pair<Category, List<Product>>> = remember(
        products, categories, selectedTopCatId, selectedSubCatId
    ) {
        val availableProducts = products.filter { it.isAvailable }
        val catsToDisplay = when {
            selectedSubCatId != null -> categories.filter { it.id == selectedSubCatId }
            selectedTopCatId != null -> {
                val parent = categories.filter { it.id == selectedTopCatId }
                val children = categories.filter { it.parentCategoryId == selectedTopCatId }.sortedBy { it.sortOrder }
                parent + children
            }
            else -> {
                topCategories.flatMap { top ->
                    val children = categories.filter { it.parentCategoryId == top.id }.sortedBy { it.sortOrder }
                    listOf(top) + children
                }
            }
        }
        catsToDisplay.mapNotNull { cat ->
            val items = availableProducts.filter { it.categoryId == cat.id }
            if (items.isEmpty()) null else cat to items
        }
    }

    val total = qrCart.sumOf { it.unitPrice * it.qty }
    val canSubmit = qrCart.isNotEmpty() && customerName.isNotBlank()

    fun incrementProduct(product: Product) {
        orderSentBanner = false
        val offer = productOffers.activeOfferFor(product)
        if (offer != null) {
            pendingOfferProduct = product to offer
        } else {
            val ex = qrCart.firstOrNull { it.productId == product.id && it.note.isBlank() }
            qrCart = if (ex != null) {
                qrCart.map { if (it.id == ex.id) it.copy(qty = it.qty + 1) else it }
            } else {
                qrCart + CartItem(
                    id = "qci_${UUID.randomUUID().toString().take(8)}",
                    productId = product.id,
                    nameEn = product.nameEn,
                    nameAr = product.nameAr,
                    offlinePrice = product.offlinePrice,
                    onlinePrice = product.onlinePrice,
                    unitPrice = product.offlinePrice,
                    qty = 1
                )
            }
        }
    }

    fun decrementProduct(product: Product) {
        val ex = qrCart.lastOrNull { it.productId == product.id } ?: return
        qrCart = if (ex.qty <= 1) {
            qrCart.filterNot { it.id == ex.id }
        } else {
            qrCart.map { if (it.id == ex.id) it.copy(qty = it.qty - 1) else it }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        val bannerHeight = if (maxHeight < 650.dp) 96.dp else 132.dp
        Column(modifier = Modifier.fillMaxSize()) {
            // Banner Header
            Box(modifier = Modifier.height(bannerHeight).fillMaxWidth()) {
                Image(
                    painter = painterResource(id = R.drawable.img_qr_menu_banner),
                    contentDescription = s.customerMenuTitle,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(0.25f), Color.Black.copy(0.80f))))
                )
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(settings.shopDisplayName(lang), color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(s.customerMenuTitle, color = Color(0xFF93C5FD), style = MaterialTheme.typography.labelLarge)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilledTonalButton(onClick = onToggleLang) {
                            Text(if (lang == AppLang.EN) "عربي" else "EN")
                        }
                        FilledTonalButton(onClick = onExitToPos, modifier = Modifier.testTag("qr_exit_to_pos_btn")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(s.backToPos)
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (orderSentBanner) {
                    Surface(
                        color = StatusSuccess.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusSuccess)
                            Text(s.qrOrderSentSuccess, fontWeight = FontWeight.Bold, color = StatusSuccess)
                        }
                    }
                }

                // Customer Details Card: Name Mandatory, Table/Car & Mobile Optional
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = customerName,
                                onValueChange = { customerName = it },
                                label = { Text(s.customerNameRequired) },
                                isError = qrCart.isNotEmpty() && customerName.isBlank(),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1.2f)
                                    .testTag("qr_customer_name_input")
                            )
                            OutlinedTextField(
                                value = tableOrCar,
                                onValueChange = { tableOrCar = it },
                                label = { Text("${s.tableOrCar} (${s.optionalLabel})") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = customerPhone,
                                onValueChange = { customerPhone = it },
                                label = { Text("${s.customerPhone} (${s.optionalLabel})") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("qr_customer_phone_input")
                            )
                        }
                        if (qrCart.isNotEmpty() && customerName.isBlank()) {
                            Text(
                                text = if (lang == AppLang.AR) "يرجى إدخال اسمك لإرسال الطلب" else "Please enter Customer Name (*) to send order",
                                style = MaterialTheme.typography.labelSmall,
                                color = StatusDanger,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Organized Menu Bar (Top-Level Category Tabs)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            item {
                                FilterChip(
                                    selected = selectedTopCatId == null,
                                    onClick = {
                                        selectedTopCatId = null
                                        selectedSubCatId = null
                                    },
                                    leadingIcon = { Icon(Icons.Default.RestaurantMenu, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    label = { Text(s.allCategories, fontWeight = FontWeight.Bold) }
                                )
                            }
                            items(topCategories, key = { it.id }) { cat ->
                                FilterChip(
                                    selected = selectedTopCatId == cat.id,
                                    onClick = {
                                        selectedTopCatId = cat.id
                                        selectedSubCatId = null
                                    },
                                    label = { Text(cat.displayName(lang), fontWeight = FontWeight.SemiBold) }
                                )
                            }
                        }
                        if (subCategoriesForSelected.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                item {
                                    AssistChip(
                                        onClick = { selectedSubCatId = null },
                                        label = {
                                            Text(
                                                text = if (lang == AppLang.AR) "كل الأصناف" else "All in Category",
                                                fontWeight = if (selectedSubCatId == null) FontWeight.Bold else FontWeight.Normal,
                                                color = if (selectedSubCatId == null) BluePrimary else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    )
                                }
                                items(subCategoriesForSelected, key = { it.id }) { sub ->
                                    FilterChip(
                                        selected = selectedSubCatId == sub.id,
                                        onClick = { selectedSubCatId = if (selectedSubCatId == sub.id) null else sub.id },
                                        leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(15.dp)) },
                                        label = { Text(sub.displayName(lang), fontWeight = FontWeight.Medium) }
                                    )
                                }
                            }
                        }
                    }
                }

                // Organized Menu Grid Grouped by Category Section Headers
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 165.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    visibleSections.forEach { (category, sectionProducts) ->
                        item(
                            key = "header_${category.id}",
                            span = { GridItemSpan(maxLineSpan) }
                        ) {
                            val parent = categories.firstOrNull { it.id == category.parentCategoryId }
                            val titleText = if (parent != null) {
                                "${parent.displayName(lang)}  ›  ${category.displayName(lang)}"
                            } else {
                                category.displayName(lang)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp, bottom = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .width(4.dp)
                                            .height(18.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(BluePrimary)
                                    )
                                    Text(
                                        text = titleText,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = "${sectionProducts.size} ${s.itemsCountLabel}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        items(sectionProducts, key = { it.id }) { product ->
                            val qtyInCart = qrCart.filter { it.productId == product.id }.sumOf { it.qty }
                            val hasOffer = activeOfferProductIds.contains(product.id)
                            Card(
                                onClick = { incrementProduct(product) },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(
                                    width = if (qtyInCart > 0) 1.5.dp else 1.dp,
                                    color = if (qtyInCart > 0) BluePrimary else MaterialTheme.colorScheme.outline
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("qr_menu_product_${product.id}")
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    if (product.imageUri.isNotBlank()) {
                                        AsyncImage(
                                            model = File(product.imageUri).takeIf { it.exists() } ?: product.imageUri,
                                            contentDescription = product.displayName(lang),
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(102.dp)
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(54.dp)
                                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Fastfood,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                                                modifier = Modifier.size(26.dp)
                                            )
                                        }
                                    }

                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Text(
                                            text = product.displayName(lang),
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.heightIn(min = 38.dp)
                                        )
                                        if (lang == AppLang.EN && product.nameAr.isNotBlank()) {
                                            Text(
                                                text = product.nameAr,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = formatSar(product.offlinePrice, lang),
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                            if (qtyInCart > 0) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Surface(
                                                        onClick = { decrementProduct(product) },
                                                        shape = CircleShape,
                                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                                        modifier = Modifier.size(26.dp)
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Icon(Icons.Default.Remove, contentDescription = "Remove", modifier = Modifier.size(14.dp))
                                                        }
                                                    }
                                                    Text("$qtyInCart", fontWeight = FontWeight.ExtraBold)
                                                    Surface(
                                                        onClick = { incrementProduct(product) },
                                                        shape = CircleShape,
                                                        color = BluePrimary,
                                                        modifier = Modifier.size(26.dp)
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White, modifier = Modifier.size(14.dp))
                                                        }
                                                    }
                                                }
                                            } else if (hasOffer) {
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = StatusSuccess.copy(alpha = 0.14f)
                                                ) {
                                                    Text(
                                                        text = "OFFER",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = StatusSuccess,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Submit Bar
            Surface(tonalElevation = 6.dp, shadowElevation = 8.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${qrCart.sumOf { it.qty }} ${s.itemsCountLabel}", style = MaterialTheme.typography.labelMedium)
                        Text(formatSar(total, lang), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    }
                    Button(
                        enabled = canSubmit,
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("qr_submit_order_btn"),
                        onClick = {
                            onSubmitQrOrder(qrCart, tableOrCar.trim(), customerName.trim(), customerPhone.trim(), notes.trim())
                            qrCart = emptyList()
                            orderSentBanner = true
                        }
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(s.submitQrOrder, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        pendingOfferProduct?.let { (prod, offer) ->
            SelectOfferOptionDialog(
                lang = lang,
                product = prod,
                offer = offer,
                pricingMode = PricingMode.OFFLINE,
                onDismiss = { pendingOfferProduct = null },
                onSelectOption = { opt ->
                    val extra = opt?.extraPrice?.coerceAtLeast(0.0) ?: 0.0
                    val itemNameEn = if (opt != null) "${prod.nameEn} (+ ${opt.nameEn})" else prod.nameEn
                    val itemNameAr = if (opt != null) "${prod.nameAr} (+ ${opt.nameAr})" else prod.nameAr
                    val noteText = when {
                        opt == null -> ""
                        extra <= 0.001 -> "Offer: ${opt.nameEn} (FREE)"
                        else -> "Offer: ${opt.nameEn} (+${formatNumber(extra)} SAR)"
                    }
                    val unitPrice = prod.offlinePrice + extra
                    val ex = qrCart.firstOrNull { it.productId == prod.id && it.nameEn == itemNameEn && it.note == noteText }
                    qrCart = if (ex != null) {
                        qrCart.map { if (it.id == ex.id) it.copy(qty = it.qty + 1) else it }
                    } else {
                        qrCart + CartItem(
                            id = "qci_${UUID.randomUUID().toString().take(8)}",
                            productId = prod.id,
                            nameEn = itemNameEn,
                            nameAr = itemNameAr,
                            offlinePrice = prod.offlinePrice + extra,
                            onlinePrice = prod.onlinePrice + extra,
                            unitPrice = unitPrice,
                            qty = 1,
                            note = noteText
                        )
                    }
                    pendingOfferProduct = null
                }
            )
        }
    }
}

@Composable
fun IncomingQrAlarmOverlay(
    lang: AppLang,
    order: PosOrder,
    alarmSecondsRemaining: Int,
    onAccept: (PosOrder) -> Unit,
    onReject: (PosOrder) -> Unit
) {
    val s = stringsFor(lang)
    val infiniteTransition = rememberInfiniteTransition(label = "qr_alarm_flash")
    val flashBg by infiniteTransition.animateColor(
        initialValue = Color(0xFF991B1B),
        targetValue = Color(0xFFDC2626),
        animationSpec = infiniteRepeatable(
            animation = tween(420, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flash"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(flashBg.copy(alpha = 0.94f))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(50.dp),
                    color = StatusDanger.copy(alpha = 0.16f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = StatusDanger)
                        Text(
                            text = if (alarmSecondsRemaining > 0) "${s.alarmActiveSec}: ${alarmSecondsRemaining}s" else s.incomingQrOrder,
                            color = StatusDanger,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                Text(
                    text = s.incomingQrOrder,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold
                )
                val customerHeader = listOfNotNull(
                    order.orderNumber,
                    order.customerName.takeIf { it.isNotBlank() },
                    order.tableOrCarNumber.takeIf { it.isNotBlank() }?.let { "${s.tableOrCar}: $it" },
                    order.customerPhone.takeIf { it.isNotBlank() }
                ).joinToString(" • ")
                Text(
                    text = customerHeader,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        order.items.forEach { item ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${item.qty}x ${item.displayName(lang)}", fontWeight = FontWeight.Medium)
                                Text(formatSar(item.lineTotal, lang), fontWeight = FontWeight.Bold)
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(s.grandTotal, fontWeight = FontWeight.Bold)
                            Text(formatSar(order.grandTotal, lang), fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { onReject(order) },
                        modifier = Modifier
                            .weight(0.8f)
                            .height(52.dp)
                            .testTag("qr_alarm_reject_btn"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusDanger)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(s.rejectOrder, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { onAccept(order) },
                        modifier = Modifier
                            .weight(1.3f)
                            .height(52.dp)
                            .testTag("qr_alarm_accept_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = StatusSuccess)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(s.acceptOrder, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
