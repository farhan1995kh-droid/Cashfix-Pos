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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerQrOrderScreen(
    lang: AppLang,
    settings: StoreSettings,
    categories: List<Category>,
    products: List<Product>,
    productOffers: List<ProductOffer>,
    initialTableCode: String = "T2",
    onToggleLang: () -> Unit,
    onExitToPos: () -> Unit,
    onSubmitQrOrder: (items: List<CartItem>, tableOrCar: String, customerName: String, customerPhone: String, notes: String) -> Unit
) {
    val s = stringsFor(lang)
    BackHandler(onBack = onExitToPos)

    var selectedTopCatId by remember { mutableStateOf<String?>(null) }
    var selectedSubCatId by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var customerName by remember { mutableStateOf("") }
    var tableOrCar by remember(initialTableCode) { mutableStateOf(initialTableCode) }
    var customerPhone by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var qrCart by remember { mutableStateOf<List<CartItem>>(emptyList()) }
    var orderSentBanner by remember { mutableStateOf(false) }
    var pendingOfferProduct by remember { mutableStateOf<Pair<Product, ProductOffer>?>(null) }
    var showCartSummarySheet by remember { mutableStateOf(false) }

    // Deduplicate available products so identical items never repeat
    val availableProducts = remember(products) { products.deduplicatedAvailable() }

    val topCategories = remember(categories) {
        categories.filter { it.parentCategoryId == null }.sortedBy { it.sortOrder }
    }
    val subCategoriesForSelected = remember(categories, selectedTopCatId) {
        if (selectedTopCatId == null) emptyList()
        else categories.filter { it.parentCategoryId == selectedTopCatId }.sortedBy { it.sortOrder }
    }
    val activeOfferProductIds = remember(availableProducts, productOffers) {
        availableProducts.filter { productOffers.activeOfferFor(it) != null }.map { it.id }.toSet()
    }

    // Count items per top category (including its sub-categories)
    val topCategoryCounts = remember(availableProducts, categories, topCategories) {
        topCategories.associate { top ->
            val familyIds = setOf(top.id) + categories.filter { it.parentCategoryId == top.id }.map { it.id }
            top.id to availableProducts.count { it.categoryId in familyIds }
        }
    }

    // Group visible categories cleanly matching the main POS dashboard
    val visibleSections: List<Pair<Category, List<Product>>> = remember(
        availableProducts, categories, selectedTopCatId, selectedSubCatId, searchQuery
    ) {
        val q = searchQuery.trim().lowercase()
        val catsToDisplay = when {
            q.isNotEmpty() -> topCategories.flatMap { top ->
                val children = categories.filter { it.parentCategoryId == top.id }.sortedBy { it.sortOrder }
                listOf(top) + children
            }
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
            val items = availableProducts.filter { p ->
                p.categoryId == cat.id && (
                    q.isEmpty() ||
                        p.nameEn.lowercase().contains(q) ||
                        p.nameAr.contains(q)
                )
            }
            if (items.isEmpty()) null else cat to items
        }
    }

    val total = qrCart.sumOf { it.unitPrice * it.qty }
    val vatIncludedAmount = if (settings.vatInclusive) total - (total / (1.0 + settings.vatRate)) else total * settings.vatRate
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
        val isWide = maxWidth >= 640.dp
        val gridColumns = if (isWide) GridCells.Fixed(2) else GridCells.Fixed(1)

        Column(modifier = Modifier.fillMaxSize()) {
            // Top Brand Bar matching Main POS Dashboard (White base + Royal Blue accents + Table Badge)
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.cashfix_brand_logo),
                                    contentDescription = settings.shopDisplayName(lang),
                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = settings.shopDisplayName(lang),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (tableOrCar.isNotBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(50),
                                            color = BluePrimary,
                                            modifier = Modifier.testTag("qr_table_badge")
                                        ) {
                                            Text(
                                                text = if (lang == AppLang.AR) "طاولة: ${tableOrCar.trim()}" else "Table: ${tableOrCar.trim()}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "${settings.addressDisplay(lang)} • ${s.customerMenuTitle}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = onToggleLang,
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (lang == AppLang.EN) "العربية" else "EN", fontWeight = FontWeight.Bold)
                            }
                            FilledTonalButton(
                                onClick = onExitToPos,
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("qr_exit_to_pos_btn")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(s.backToPos, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
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
                        color = StatusSuccess.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, StatusSuccess.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusSuccess)
                            Text(s.qrOrderSentSuccess, fontWeight = FontWeight.Bold, color = StatusSuccess)
                        }
                    }
                }

                // Customer Details Card: Name Mandatory, Table Badge / Car & Mobile Optional
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
                        if (isWide) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = customerName,
                                    onValueChange = { customerName = it },
                                    label = { Text(s.customerNameRequired) },
                                    isError = qrCart.isNotEmpty() && customerName.isBlank(),
                                    singleLine = true,
                                    modifier = Modifier.weight(1.3f).testTag("qr_customer_name_input")
                                )
                                OutlinedTextField(
                                    value = tableOrCar,
                                    onValueChange = { tableOrCar = it },
                                    label = { Text("${s.tableOrCar} (${s.optionalLabel})") },
                                    singleLine = true,
                                    modifier = Modifier.weight(0.9f)
                                )
                                OutlinedTextField(
                                    value = customerPhone,
                                    onValueChange = { customerPhone = it },
                                    label = { Text("${s.customerPhone} (${s.optionalLabel})") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f).testTag("qr_customer_phone_input")
                                )
                            }
                        } else {
                            OutlinedTextField(
                                value = customerName,
                                onValueChange = { customerName = it },
                                label = { Text(s.customerNameRequired) },
                                isError = qrCart.isNotEmpty() && customerName.isBlank(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("qr_customer_name_input")
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                    modifier = Modifier.weight(1f).testTag("qr_customer_phone_input")
                                )
                            }
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

                // Search Bar + Filterable Category Tabs (Matching Main POS Dashboard)
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = if (lang == AppLang.AR) "ابحث في القائمة (برجر، وجبات، مشروبات...)" else "Search burgers, meals, sides, drinks...",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = s.clear)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("qr_menu_search_input")
                )

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
                                    label = { Text("${s.allCategories} (${availableProducts.size})", fontWeight = FontWeight.Bold) }
                                )
                            }
                            items(topCategories, key = { it.id }) { cat ->
                                val count = topCategoryCounts[cat.id] ?: 0
                                if (count > 0) {
                                    FilterChip(
                                        selected = selectedTopCatId == cat.id,
                                        onClick = {
                                            selectedTopCatId = cat.id
                                            selectedSubCatId = null
                                        },
                                        label = { Text("${cat.displayName(lang)} ($count)", fontWeight = FontWeight.SemiBold) }
                                    )
                                }
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

                // Organized Menu Grid Grouped by Category Section Headers (2-col on tablet/desktop, structured horizontal cards on mobile)
                LazyVerticalGrid(
                    columns = gridColumns,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (visibleSections.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.SearchOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
                                    Text(
                                        text = if (lang == AppLang.AR) "لا توجد أصناف مطابقة للبحث" else "No menu items match your search",
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

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
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .width(4.dp)
                                            .height(20.dp)
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
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
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
                                elevation = CardDefaults.cardElevation(defaultElevation = if (qtyInCart > 0) 3.dp else 1.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("qr_menu_product_${product.id}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(IntrinsicSize.Min),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (product.imageUri.isNotBlank()) {
                                        AsyncImage(
                                            model = File(product.imageUri).takeIf { it.exists() } ?: product.imageUri,
                                            contentDescription = product.displayName(lang),
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .width(96.dp)
                                                .fillMaxHeight()
                                                .heightIn(min = 96.dp)
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .width(84.dp)
                                                .fillMaxHeight()
                                                .heightIn(min = 92.dp)
                                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Fastfood,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }

                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = product.displayName(lang),
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
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
                                            }
                                            if (hasOffer) {
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = StatusSuccess.copy(alpha = 0.14f)
                                                ) {
                                                    Text(
                                                        text = if (lang == AppLang.AR) "عرض" else "OFFER",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = StatusSuccess,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = formatSar(product.offlinePrice, lang),
                                                color = MaterialTheme.colorScheme.primary,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                            if (qtyInCart > 0) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Surface(
                                                        onClick = { decrementProduct(product) },
                                                        shape = CircleShape,
                                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                                        modifier = Modifier.size(30.dp)
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Icon(Icons.Default.Remove, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                                                        }
                                                    }
                                                    Text("$qtyInCart", fontWeight = FontWeight.ExtraBold)
                                                    Surface(
                                                        onClick = { incrementProduct(product) },
                                                        shape = CircleShape,
                                                        color = BluePrimary,
                                                        modifier = Modifier.size(30.dp)
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White, modifier = Modifier.size(16.dp))
                                                        }
                                                    }
                                                }
                                            } else {
                                                FilledTonalButton(
                                                    onClick = { incrementProduct(product) },
                                                    shape = RoundedCornerShape(10.dp),
                                                    colors = ButtonDefaults.filledTonalButtonColors(
                                                        containerColor = BluePrimary,
                                                        contentColor = Color.White
                                                    ),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                    modifier = Modifier.height(34.dp)
                                                ) {
                                                    Text(
                                                        text = if (lang == AppLang.AR) "+ أضف" else "+ Add",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Bold
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

            // Smooth Cart Summary & Checkout Bar
            Surface(
                tonalElevation = 4.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = { if (qrCart.isNotEmpty()) showCartSummarySheet = true },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                            Text(
                                text = "${qrCart.sumOf { it.qty }} ${s.itemsCountLabel} • ${if (lang == AppLang.AR) "شامل ١٥٪ ضريبة" else "Incl. 15% VAT"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatSar(total, lang),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Button(
                        enabled = canSubmit,
                        shape = RoundedCornerShape(12.dp),
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

        if (showCartSummarySheet) {
            ModalBottomSheet(onDismissRequest = { showCartSummarySheet = false }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (lang == AppLang.AR) "ملخص طلبك" else "Order Cart Summary",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    qrCart.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${item.qty}x ${item.displayName(lang)}", fontWeight = FontWeight.SemiBold)
                                if (item.note.isNotBlank()) {
                                    Text(item.note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Text(formatSar(item.lineTotal, lang), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    HorizontalDivider()
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(s.vat15, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatSar(vatIncludedAmount, lang), fontWeight = FontWeight.SemiBold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(s.grandTotal, fontWeight = FontWeight.ExtraBold)
                        Text(formatSar(total, lang), fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(12.dp))
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
