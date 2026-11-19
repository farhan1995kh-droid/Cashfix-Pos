package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.i18n.AppLang
import com.example.i18n.formatNumber
import com.example.i18n.formatSar
import com.example.i18n.stringsFor
import com.example.model.*
import java.io.File
import com.example.ui.CartTotals
import com.example.ui.theme.BluePrimary
import com.example.ui.theme.BluePrimaryBright
import com.example.ui.theme.IndigoHeaderStart
import com.example.ui.theme.OnlineOrange
import com.example.ui.theme.StatusDanger
import com.example.ui.theme.StatusSuccess

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosSellingScreen(
    lang: AppLang,
    settings: StoreSettings,
    activeCashier: Cashier?,
    orderSources: List<OrderSource>,
    selectedSource: OrderSource?,
    categories: List<Category>,
    selectedCategoryId: String?,
    products: List<Product>,
    productOffers: List<ProductOffer>,
    searchQuery: String,
    cartItems: List<CartItem>,
    cartTotals: CartTotals,
    discountType: DiscountType,
    discountValue: Double,
    customerName: String,
    customerPhone: String,
    tableOrCar: String,
    orderNotes: String,
    onSelectSource: (OrderSource) -> Unit,
    onSelectCategory: (String?) -> Unit,
    onSearchChange: (String) -> Unit,
    onProductClick: (Product) -> Unit,
    onAddProductWithOffer: (Product, OfferOption?) -> Unit,
    onQtyChange: (itemId: String, delta: Int) -> Unit,
    onEditCartItem: (itemId: String, newPrice: Double, newNote: String) -> Unit,
    onSetDiscount: (DiscountType, Double) -> Unit,
    onSetCustomerTag: (name: String, phone: String, tableOrCar: String, notes: String) -> Unit,
    onClearCart: () -> Unit,
    onHoldOrderConfirmed: (name: String, phone: String, tableOrCar: String, notes: String) -> Unit,
    onOpenCheckout: () -> Unit
) {
    val s = stringsFor(lang)
    val pricingMode = selectedSource?.pricingMode ?: PricingMode.OFFLINE
    val activeOfferProductIds = remember(products, productOffers) {
        products.filter { productOffers.activeOfferFor(it) != null }.map { it.id }.toSet()
    }
    val categoryProductCounts = remember(products) {
        products.groupingBy { it.categoryId }.eachCount()
    }
    val filteredProducts = remember(products, selectedCategoryId, searchQuery) {
        products.filter { p ->
            val q = searchQuery.trim().lowercase()
            val catOk = if (q.isNotEmpty()) true else (selectedCategoryId == null || p.categoryId == selectedCategoryId)
            val qOk = q.isEmpty() ||
                p.nameEn.lowercase().contains(q) ||
                p.nameAr.contains(q) ||
                p.sku.lowercase().contains(q) ||
                p.barcode.contains(q)
            catOk && qOk
        }
    }

    var pendingOfferChoice by remember { mutableStateOf<Pair<Product, ProductOffer>?>(null) }
    var editingCartItem by remember { mutableStateOf<CartItem?>(null) }
    var showDiscountDialog by remember { mutableStateOf(false) }
    var showCustomerTagDialog by remember { mutableStateOf(false) }
    var showHoldOrderDialog by remember { mutableStateOf(false) }
    var showMobileCartSheet by remember { mutableStateOf(false) }
    val canCustomizeMenu = activeCashier?.isAdminOrManager == true || activeCashier?.permissions?.canAddProducts == true

    val handleProductTap: (Product) -> Unit = { product ->
        val offer = productOffers.activeOfferFor(product)
        if (offer != null) {
            pendingOfferChoice = product to offer
        } else {
            onProductClick(product)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isTabletWide = maxWidth >= 720.dp
        val isLargeTabletLandscape = maxWidth >= 1000.dp

        if (isTabletWide) {
            Row(modifier = Modifier.fillMaxSize()) {
                CatalogPane(
                    modifier = Modifier
                        .weight(if (isLargeTabletLandscape) 1.85f else 1.5f)
                        .fillMaxHeight(),
                    lang = lang,
                    pricingMode = pricingMode,
                    orderSources = orderSources.filter { it.isEnabled },
                    selectedSource = selectedSource,
                    categories = categories,
                    allProducts = products,
                    categoryProductCounts = categoryProductCounts,
                    selectedCategoryId = selectedCategoryId,
                    products = filteredProducts,
                    activeOfferProductIds = activeOfferProductIds,
                    searchQuery = searchQuery,
                    onSelectSource = onSelectSource,
                    onSelectCategory = onSelectCategory,
                    onSearchChange = onSearchChange,
                    onProductClick = handleProductTap
                )
                VerticalDivider(color = MaterialTheme.colorScheme.outline)
                CartPane(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    lang = lang,
                    settings = settings,
                    activeCashier = activeCashier,
                    cartItems = cartItems,
                    cartTotals = cartTotals,
                    discountType = discountType,
                    discountValue = discountValue,
                    customerName = customerName,
                    tableOrCar = tableOrCar,
                    onQtyChange = onQtyChange,
                    onEditItem = { editingCartItem = it },
                    onOpenDiscount = { showDiscountDialog = true },
                    onOpenCustomerTag = { showCustomerTagDialog = true },
                    onClearCart = onClearCart,
                    onHoldOrder = { showHoldOrderDialog = true },
                    onCheckout = onOpenCheckout
                )
            }
        } else {
            // Compact Phone Layout: Full Catalog + Sticky Bottom Cart Bar
            Column(modifier = Modifier.fillMaxSize()) {
                CatalogPane(
                    modifier = Modifier.weight(1f),
                    lang = lang,
                    pricingMode = pricingMode,
                    orderSources = orderSources.filter { it.isEnabled },
                    selectedSource = selectedSource,
                    categories = categories,
                    allProducts = products,
                    categoryProductCounts = categoryProductCounts,
                    selectedCategoryId = selectedCategoryId,
                    products = filteredProducts,
                    activeOfferProductIds = activeOfferProductIds,
                    searchQuery = searchQuery,
                    onSelectSource = onSelectSource,
                    onSelectCategory = onSelectCategory,
                    onSearchChange = onSearchChange,
                    onProductClick = handleProductTap
                )

                // Mobile Sticky Cart Footer Bar
                Surface(
                    tonalElevation = 2.dp,
                    shadowElevation = 6.dp,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { showMobileCartSheet = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("mobile_view_cart_btn"),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Icon(Icons.Default.ShoppingCart, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            val count = cartItems.sumOf { it.qty }
                            Text(
                                text = "($count) ${formatSar(cartTotals.grandTotal, lang)}",
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                        Button(
                            onClick = onOpenCheckout,
                            enabled = cartItems.isNotEmpty(),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("mobile_checkout_btn"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.CreditCard, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(s.checkoutPay, maxLines = 1, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (showMobileCartSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showMobileCartSheet = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ) {
                    CartPane(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 420.dp, max = 640.dp),
                        lang = lang,
                        settings = settings,
                        activeCashier = activeCashier,
                        cartItems = cartItems,
                        cartTotals = cartTotals,
                        discountType = discountType,
                        discountValue = discountValue,
                        customerName = customerName,
                        tableOrCar = tableOrCar,
                        onQtyChange = onQtyChange,
                        onEditItem = { editingCartItem = it },
                        onOpenDiscount = { showDiscountDialog = true },
                        onOpenCustomerTag = { showCustomerTagDialog = true },
                        onClearCart = onClearCart,
                        onHoldOrder = {
                            showMobileCartSheet = false
                            showHoldOrderDialog = true
                        },
                        onCheckout = {
                            showMobileCartSheet = false
                            onOpenCheckout()
                        }
                    )
                }
            }
        }
    }

    pendingOfferChoice?.let { (prod, offer) ->
        SelectOfferOptionDialog(
            lang = lang,
            product = prod,
            offer = offer,
            pricingMode = pricingMode,
            onDismiss = { pendingOfferChoice = null },
            onSelectOption = { opt ->
                onAddProductWithOffer(prod, opt)
                pendingOfferChoice = null
            }
        )
    }

    editingCartItem?.let { item ->
        EditCartItemDialog(
            lang = lang,
            item = item,
            canCustomizePrice = canCustomizeMenu,
            onDismiss = { editingCartItem = null },
            onSave = { newPrice, newNote ->
                onEditCartItem(item.id, newPrice, newNote)
                editingCartItem = null
            }
        )
    }

    if (showDiscountDialog) {
        DiscountDialog(
            lang = lang,
            currentType = discountType,
            currentValue = discountValue,
            onDismiss = { showDiscountDialog = false },
            onApply = { t, v ->
                onSetDiscount(t, v)
                showDiscountDialog = false
            }
        )
    }

    if (showCustomerTagDialog) {
        CustomerTagDialog(
            lang = lang,
            name = customerName,
            phone = customerPhone,
            tableOrCar = tableOrCar,
            notes = orderNotes,
            onDismiss = { showCustomerTagDialog = false },
            onSave = { n, p, tc, nt ->
                onSetCustomerTag(n, p, tc, nt)
                showCustomerTagDialog = false
            }
        )
    }

    if (showHoldOrderDialog) {
        HoldOrderDialog(
            lang = lang,
            initialName = customerName,
            initialPhone = customerPhone,
            initialTableOrCar = tableOrCar,
            initialNotes = orderNotes,
            onDismiss = { showHoldOrderDialog = false },
            onConfirmHold = { n, p, tc, nt ->
                showHoldOrderDialog = false
                onHoldOrderConfirmed(n, p, tc, nt)
            }
        )
    }
}

private fun categoryIconFor(category: Category): ImageVector {
    val key = category.id.lowercase() + " " + category.nameEn.lowercase()
    return when {
        "zinger" in key || "burger" in key -> Icons.Default.LunchDining
        "twister" in key || "wrap" in key -> Icons.Default.BakeryDining
        "crunch" in key || "fries" in key -> Icons.Default.Fastfood
        "soft" in key -> Icons.Default.LocalCafe
        "drink" in key -> Icons.Default.LocalDrink
        "sauce" in key -> Icons.Default.Restaurant
        else -> Icons.Default.FolderOpen
    }
}

@Composable
private fun CatalogPane(
    modifier: Modifier,
    lang: AppLang,
    pricingMode: PricingMode,
    orderSources: List<OrderSource>,
    selectedSource: OrderSource?,
    categories: List<Category>,
    allProducts: List<Product>,
    categoryProductCounts: Map<String, Int>,
    selectedCategoryId: String?,
    products: List<Product>,
    activeOfferProductIds: Set<String>,
    searchQuery: String,
    onSelectSource: (OrderSource) -> Unit,
    onSelectCategory: (String?) -> Unit,
    onSearchChange: (String) -> Unit,
    onProductClick: (Product) -> Unit
) {
    val s = stringsFor(lang)
    val isShowingFolders = selectedCategoryId == null && searchQuery.isBlank()
    val topLevelCategories = remember(categories) {
        categories.filter { it.parentCategoryId == null }
    }
    val subFolders = remember(categories, selectedCategoryId) {
        if (selectedCategoryId == null) emptyList()
        else categories.filter { it.parentCategoryId == selectedCategoryId }
    }
    val openedCategory = remember(categories, selectedCategoryId) {
        categories.firstOrNull { it.id == selectedCategoryId }
    }

    fun productsInBranch(catId: String): List<Product> {
        val direct = allProducts.filter { it.categoryId == catId }
        val children = categories.filter { it.parentCategoryId == catId }
        return direct + children.flatMap { productsInBranch(it.id) }
    }

    fun totalItemsInFolder(catId: String): Int {
        val direct = categoryProductCounts[catId] ?: 0
        val children = categories.filter { it.parentCategoryId == catId }
        return direct + children.sumOf { totalItemsInFolder(it.id) }
    }

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Row 1: Search Bar + Dual Order Source Pill Buttons (side-by-side on wide screens)
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val isWideSearchRow = maxWidth >= 600.dp
            if (isWideSearchRow) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SearchPillField(
                        modifier = Modifier.weight(1f),
                        searchQuery = searchQuery,
                        placeholder = s.searchPlaceholder,
                        clearLabel = s.clear,
                        onSearchChange = onSearchChange
                    )
                    OrderSourcePillsRow(
                        lang = lang,
                        orderSources = orderSources,
                        selectedSource = selectedSource,
                        onSelectSource = onSelectSource
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SearchPillField(
                        modifier = Modifier.fillMaxWidth(),
                        searchQuery = searchQuery,
                        placeholder = s.searchPlaceholder,
                        clearLabel = s.clear,
                        onSearchChange = onSearchChange
                    )
                    OrderSourcePillsRow(
                        modifier = Modifier.fillMaxWidth(),
                        lang = lang,
                        orderSources = orderSources,
                        selectedSource = selectedSource,
                        onSelectSource = onSelectSource,
                        expandButtons = true
                    )
                }
            }
        }

        // Row 2: Horizontal Category Folders Bar (matches screenshot pills)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item(key = "chip_all_folders") {
                val isRoot = selectedCategoryId == null && searchQuery.isBlank()
                Surface(
                    onClick = {
                        onSearchChange("")
                        onSelectCategory(null)
                    },
                    shape = RoundedCornerShape(24.dp),
                    color = if (isRoot) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    border = if (isRoot) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.testTag("back_to_folders_btn")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (isRoot) Color.White else MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = s.categoryFoldersTitle,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isRoot) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = CircleShape,
                            color = if (isRoot) Color.White.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "${topLevelCategories.size}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isRoot) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            items(topLevelCategories, key = { "top_chip_${it.id}" }) { cat ->
                val isSelectedBranch = (selectedCategoryId == cat.id || openedCategory?.parentCategoryId == cat.id) && searchQuery.isBlank()
                val count = totalItemsInFolder(cat.id)
                Surface(
                    onClick = {
                        onSearchChange("")
                        onSelectCategory(cat.id)
                    },
                    shape = RoundedCornerShape(24.dp),
                    color = if (isSelectedBranch) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    border = if (isSelectedBranch) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (isSelectedBranch) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = cat.displayName(lang),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelectedBranch) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (isSelectedBranch) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = CircleShape,
                            color = if (isSelectedBranch) Color.White.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "$count",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelectedBranch) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        if (isShowingFolders) {
            // Section Title matching Screenshot 1
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = if (lang == AppLang.AR) "مجلدات أقسام القائمة" else "Menu Category Folders",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = if (lang == AppLang.AR) "اضغط على أي مجلد لتصفح واختيار المنتجات بداخله" else "Tap any category folder to browse and select products inside",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.20f))
                ) {
                    Text(
                        text = if (lang == AppLang.AR) "${topLevelCategories.size} مجلدات" else "${topLevelCategories.size} Folders",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // Category Folder Cards with Signature Top Blue Strip + Preview Items + Open Folder ->
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 215.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("category_folders_grid")
            ) {
                items(topLevelCategories, key = { it.id }) { cat ->
                    val folderProducts = productsInBranch(cat.id)
                    val count = folderProducts.size
                    val previewItems = folderProducts.take(3)
                    val extraCount = (count - 3).coerceAtLeast(0)
                    val childSubFolders = categories.filter { it.parentCategoryId == cat.id }

                    Card(
                        onClick = { onSelectCategory(cat.id) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("category_folder_${cat.id}"),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Top royal blue accent strip (signature look from Screenshot 1)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(BluePrimaryBright, IndigoHeaderStart)
                                        )
                                    )
                            )

                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Icon(
                                            imageVector = categoryIconFor(cat),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .padding(10.dp)
                                                .size(22.dp)
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            text = "$count ${if (lang == AppLang.AR) s.itemsCountLabel else "Items"}",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = if (lang == AppLang.AR) cat.nameAr else cat.nameEn,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (lang == AppLang.AR) cat.nameEn else cat.nameAr,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    previewItems.forEach { prod ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(5.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF60A5FA))
                                            )
                                            Text(
                                                text = prod.displayName(lang),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    if (extraCount > 0) {
                                        Text(
                                            text = if (lang == AppLang.AR) "+$extraCount أصناف إضافية في المجلد" else "+$extraCount more items in folder",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1
                                        )
                                    } else if (childSubFolders.isNotEmpty()) {
                                        Text(
                                            text = "+ ${childSubFolders.joinToString { it.displayName(lang) }}",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = if (lang == AppLang.AR) "فتح المجلد" else "Open Folder",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Opened Folder (or Sub-Folder) / Search Results Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            val parentId = if (searchQuery.isNotBlank()) null else openedCategory?.parentCategoryId
                            onSelectCategory(parentId)
                            onSearchChange("")
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = s.backToFolders,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        val backParent = categories.firstOrNull { it.id == openedCategory?.parentCategoryId }
                        Text(
                            text = backParent?.displayName(lang) ?: s.backToFolders,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (openedCategory != null && searchQuery.isBlank()) {
                        val parentCat = categories.firstOrNull { it.id == openedCategory.parentCategoryId }
                        val breadcrumb = if (parentCat != null) {
                            "${parentCat.displayName(lang)}  ›  ${openedCategory.displayName(lang)}"
                        } else {
                            openedCategory.displayName(lang)
                        }
                        Text(
                            text = breadcrumb,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "${products.size} ${s.itemsCountLabel}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Sub-Folders + Product Cards Grid
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 168.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (searchQuery.isBlank()) {
                    items(subFolders, key = { "sub_${it.id}" }) { sub ->
                        val subCount = totalItemsInFolder(sub.id)
                        Card(
                            onClick = { onSelectCategory(sub.id) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sub_folder_${sub.id}"),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .background(Brush.horizontalGradient(listOf(BluePrimary, IndigoHeaderStart)))
                                )
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Icon(
                                                imageVector = categoryIconFor(sub),
                                                contentDescription = s.subFoldersLabel,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier
                                                    .padding(8.dp)
                                                    .size(22.dp)
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(20.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            Text(
                                                text = "$subCount ${s.itemsCountLabel}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = sub.displayName(lang),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.heightIn(min = 38.dp)
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = s.subFoldersLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                items(products, key = { it.id }) { product ->
                    val activePrice = product.priceFor(pricingMode)
                    val hasOffer = activeOfferProductIds.contains(product.id)
                    Card(
                        onClick = { onProductClick(product) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("product_card_${product.id}"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        border = BorderStroke(
                            width = if (hasOffer) 1.5.dp else 1.dp,
                            color = if (hasOffer) StatusSuccess.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outline
                        )
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .background(if (hasOffer) StatusSuccess else BluePrimary)
                            )
                            if (product.imageUri.isNotBlank()) {
                                AsyncImage(
                                    model = File(product.imageUri).takeIf { it.exists() } ?: product.imageUri,
                                    contentDescription = product.displayName(lang),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(84.dp)
                                )
                            }
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (hasOffer) StatusSuccess.copy(alpha = 0.14f) else MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = if (hasOffer) "OFFER" else product.sku,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (hasOffer) StatusSuccess else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(
                                        text = "${product.stock}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (product.stock < 10) StatusDanger else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Text(
                                    text = product.displayName(lang),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.heightIn(min = 40.dp)
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
                                        text = formatSar(activePrice, lang),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (pricingMode == PricingMode.ONLINE) OnlineOrange else MaterialTheme.colorScheme.primary
                                    )
                                    Surface(
                                        shape = CircleShape,
                                        color = if (hasOffer) StatusSuccess.copy(alpha = 0.14f) else MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Icon(
                                            imageVector = if (hasOffer) Icons.Default.LocalOffer else Icons.Default.Add,
                                            contentDescription = s.addToCart,
                                            modifier = Modifier
                                                .padding(6.dp)
                                                .size(16.dp),
                                            tint = if (hasOffer) StatusSuccess else MaterialTheme.colorScheme.primary
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

@Composable
private fun SearchPillField(
    modifier: Modifier,
    searchQuery: String,
    placeholder: String,
    clearLabel: String,
    onSearchChange: (String) -> Unit
) {
    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchChange,
        placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingIcon = {
            if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { onSearchChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = clearLabel)
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(24.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedBorderColor = MaterialTheme.colorScheme.primary
        ),
        modifier = modifier.testTag("pos_search_input")
    )
}

@Composable
private fun OrderSourcePillsRow(
    modifier: Modifier = Modifier,
    lang: AppLang,
    orderSources: List<OrderSource>,
    selectedSource: OrderSource?,
    onSelectSource: (OrderSource) -> Unit,
    expandButtons: Boolean = false
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        orderSources.take(2).forEach { src ->
            val selected = src.id == selectedSource?.id
            Surface(
                onClick = { onSelectSource(src) },
                shape = RoundedCornerShape(24.dp),
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = (if (expandButtons) Modifier.weight(1f) else Modifier)
                    .height(44.dp)
                    .testTag("source_chip_${src.id}")
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = src.displayName(lang),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun CartPane(
    modifier: Modifier,
    lang: AppLang,
    settings: StoreSettings,
    activeCashier: Cashier?,
    cartItems: List<CartItem>,
    cartTotals: CartTotals,
    discountType: DiscountType,
    discountValue: Double,
    customerName: String,
    tableOrCar: String,
    onQtyChange: (String, Int) -> Unit,
    onEditItem: (CartItem) -> Unit,
    onOpenDiscount: () -> Unit,
    onOpenCustomerTag: () -> Unit,
    onClearCart: () -> Unit,
    onHoldOrder: () -> Unit,
    onCheckout: () -> Unit
) {
    val s = stringsFor(lang)
    val totalItemCount = cartItems.sumOf { it.qty }
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // CURRENT ORDER Header Row (matches Screenshot 1 right sidebar)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (lang == AppLang.AR) "الطلب الحالي" else "CURRENT ORDER",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "$totalItemCount ${if (lang == AppLang.AR) s.itemsCountLabel else "Items"}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onOpenCustomerTag,
                    modifier = Modifier.testTag("cart_customer_tag_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.PersonPin,
                        contentDescription = s.customerTag,
                        tint = if (customerName.isNotBlank() || tableOrCar.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (cartItems.isNotEmpty()) {
                    IconButton(onClick = onClearCart) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = s.clear, tint = StatusDanger)
                    }
                }
            }
        }

        if (customerName.isNotBlank() || tableOrCar.isNotBlank()) {
            Surface(
                onClick = onOpenCustomerTag,
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.PersonPin, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = listOf(customerName, tableOrCar).filter { it.isNotBlank() }.joinToString(" • "),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Cart Items List or Screenshot-Matched Empty State
        if (cartItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(20.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestaurantMenu,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(14.dp)
                                .size(28.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        )
                    }
                    Text(
                        text = if (lang == AppLang.AR) "السلة فارغة. اضغط على أي منتج للإضافة." else "Cart is empty. Tap products to add.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(cartItems, key = { it.id }) { item ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.background,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEditItem(item) }
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.displayName(lang), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    text = "${formatNumber(item.unitPrice)} × ${item.qty} = ${formatSar(item.lineTotal, lang)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (item.note.isNotBlank()) {
                                    Text(
                                        text = "${s.itemNote}: ${item.note}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { onEditItem(item) },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("cart_item_note_btn_${item.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.EditNote,
                                        contentDescription = s.itemNote,
                                        tint = if (item.note.isNotBlank()) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { onQtyChange(item.id, -1) },
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Decrease")
                                }
                                Text(
                                    text = "${item.qty}",
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                                IconButton(
                                    onClick = { onQtyChange(item.id, 1) },
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(Icons.Default.AddCircleOutline, contentDescription = "Increase", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Totals Summary Box (matches Screenshot 1 bottom section)
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SummaryLine(s.subtotal, formatSar(cartTotals.subtotal, lang))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onOpenDiscount() }
                    .padding(vertical = 2.dp)
                    .testTag("cart_discount_btn"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalOffer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (discountValue > 0.0) {
                            val badge = if (discountType == DiscountType.PERCENT) "${discountValue.toInt()}%" else formatNumber(discountValue)
                            "${s.discount} ($badge)"
                        } else {
                            if (lang == AppLang.AR) "إضافة خصم" else "Add Discount"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = if (cartTotals.discountAmount > 0.001) "-${formatNumber(cartTotals.discountAmount)}" else "0.00",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = StatusDanger
                )
            }
            SummaryLine(
                label = if (settings.vatInclusive) {
                    if (lang == AppLang.AR) "الضريبة (١٥٪ شاملة)" else "VAT (15% Included in Price)"
                } else {
                    if (lang == AppLang.AR) "الضريبة (١٥٪)" else "VAT (15%)"
                },
                value = formatSar(cartTotals.vatAmount, lang)
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (lang == AppLang.AR) "الإجمالي النهائي" else "Total Amount",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = formatSar(cartTotals.grandTotal, lang),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Bottom Hold + Pay & Settle Buttons (matches Screenshot 1)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (activeCashier?.permissions?.canHoldOrders != false) {
                OutlinedButton(
                    onClick = onHoldOrder,
                    enabled = cartItems.isNotEmpty(),
                    modifier = Modifier
                        .weight(0.78f)
                        .height(56.dp)
                        .testTag("hold_order_btn"),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(
                            text = if (lang == AppLang.AR) "تعليق" else "Hold Order",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }
            Button(
                onClick = onCheckout,
                enabled = cartItems.isNotEmpty() && (activeCashier?.permissions?.canTakeOrders != false),
                modifier = Modifier
                    .weight(1.35f)
                    .height(56.dp)
                    .testTag("checkout_pay_btn"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.40f),
                    disabledContentColor = Color.White.copy(alpha = 0.90f)
                )
            ) {
                Icon(Icons.Default.CreditCard, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (lang == AppLang.AR) s.checkoutPay else "Pay & Settle",
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditCartItemDialog(
    lang: AppLang,
    item: CartItem,
    canCustomizePrice: Boolean,
    onDismiss: () -> Unit,
    onSave: (Double, String) -> Unit
) {
    val s = stringsFor(lang)
    var price by remember { mutableStateOf(formatNumber(item.unitPrice)) }
    var note by remember { mutableStateOf(item.note) }
    val quickNotes = remember {
        listOf(
            "Extra Spicy / حار زيادة",
            "No Pickles / بدون مخلل",
            "No Cheese / بدون جبن",
            "No Sauce / بدون صوص",
            "Extra Sauce / صوص إضافي",
            "Less Ice / ثلج أقل"
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.displayName(lang), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = price,
                    onValueChange = { if (canCustomizePrice && it.all { c -> c.isDigit() || c == '.' }) price = it },
                    enabled = canCustomizePrice,
                    label = { Text(s.itemPrice) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(s.itemNote) },
                    placeholder = { Text("e.g. Extra spicy, no pickles, no sauce...") },
                    leadingIcon = { Icon(Icons.Default.EditNote, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cart_edit_note_input")
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    quickNotes.forEach { preset ->
                        val shortLabel = preset.substringBefore(" / ")
                        val isSelected = note.contains(shortLabel, ignoreCase = true)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                note = if (isSelected) {
                                    note.split(",").map { it.trim() }.filter { !it.equals(shortLabel, ignoreCase = true) }.joinToString(", ")
                                } else {
                                    if (note.isBlank()) shortLabel else "${note.trim()}, $shortLabel"
                                }
                            },
                            label = { Text(preset, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(if (canCustomizePrice) price.toDoubleOrNull() ?: item.unitPrice else item.unitPrice, note) }) { Text(s.confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } }
    )
}

@Composable
private fun DiscountDialog(
    lang: AppLang,
    currentType: DiscountType,
    currentValue: Double,
    onDismiss: () -> Unit,
    onApply: (DiscountType, Double) -> Unit
) {
    val s = stringsFor(lang)
    var type by remember { mutableStateOf(currentType) }
    var value by remember { mutableStateOf(if (currentValue > 0) formatNumber(currentValue) else "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.discount, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = type == DiscountType.PERCENT,
                        onClick = { type = DiscountType.PERCENT },
                        label = { Text("Percentage (%)") }
                    )
                    FilterChip(
                        selected = type == DiscountType.FIXED,
                        onClick = { type = DiscountType.FIXED },
                        label = { Text("Fixed (${s.sar})") }
                    )
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) value = it },
                    label = { Text(if (type == DiscountType.PERCENT) "0 - 100 %" else s.sar) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = { onApply(type, value.toDoubleOrNull() ?: 0.0) }) { Text(s.confirm) }
        },
        dismissButton = {
            TextButton(onClick = { onApply(DiscountType.PERCENT, 0.0) }) { Text(s.clear) }
        }
    )
}

@Composable
private fun CustomerTagDialog(
    lang: AppLang,
    name: String,
    phone: String,
    tableOrCar: String,
    notes: String,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit
) {
    val s = stringsFor(lang)
    var n by remember { mutableStateOf(name) }
    var p by remember { mutableStateOf(phone) }
    var tc by remember { mutableStateOf(tableOrCar) }
    var nt by remember { mutableStateOf(notes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.customerTag, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = n, onValueChange = { n = it }, label = { Text(s.customerName) }, singleLine = true)
                OutlinedTextField(
                    value = p,
                    onValueChange = { p = it },
                    label = { Text(s.customerPhone) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true
                )
                OutlinedTextField(value = tc, onValueChange = { tc = it }, label = { Text(s.tableOrCar) }, singleLine = true)
                OutlinedTextField(value = nt, onValueChange = { nt = it }, label = { Text(s.orderNotes) })
            }
        },
        confirmButton = { Button(onClick = { onSave(n, p, tc, nt) }) { Text(s.confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } }
    )
}

@Composable
private fun HoldOrderDialog(
    lang: AppLang,
    initialName: String,
    initialPhone: String,
    initialTableOrCar: String,
    initialNotes: String,
    onDismiss: () -> Unit,
    onConfirmHold: (String, String, String, String) -> Unit
) {
    val s = stringsFor(lang)
    var name by remember { mutableStateOf(initialName) }
    var phone by remember { mutableStateOf(initialPhone) }
    var tableOrCar by remember { mutableStateOf(initialTableOrCar) }
    var notes by remember { mutableStateOf(initialNotes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.holdOrderDialogTitle, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(s.customerNameRequired) },
                    isError = name.isBlank(),
                    supportingText = if (name.isBlank()) {
                        {
                            Text(
                                text = if (lang == AppLang.AR) "إلزامي لتعليق الطلب" else "Required before holding order",
                                color = StatusDanger
                            )
                        }
                    } else null,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("hold_customer_name_input")
                )
                OutlinedTextField(
                    value = tableOrCar,
                    onValueChange = { tableOrCar = it },
                    label = { Text("${s.tableOrCar} (${s.optionalLabel})") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("${s.customerPhone} (${s.optionalLabel})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("${s.orderNotes} (${s.optionalLabel})") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank(),
                onClick = { onConfirmHold(name.trim(), phone.trim(), tableOrCar.trim(), notes.trim()) },
                modifier = Modifier.testTag("confirm_hold_order_btn")
            ) {
                Text(s.confirmHoldOrder, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } }
    )
}

@Composable
fun SelectOfferOptionDialog(
    lang: AppLang,
    product: Product,
    offer: ProductOffer,
    pricingMode: PricingMode,
    onDismiss: () -> Unit,
    onSelectOption: (OfferOption?) -> Unit
) {
    val s = stringsFor(lang)
    val basePrice = product.priceFor(pricingMode)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.LocalOffer, contentDescription = null, tint = StatusSuccess)
                    Text(offer.displayTitle(lang), fontWeight = FontWeight.Bold)
                }
                Text(
                    text = "${product.displayName(lang)} • ${formatSar(basePrice, lang)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(s.chooseOfferTitle, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                offer.options.forEach { opt ->
                    val isFree = opt.extraPrice <= 0.001
                    val totalPrice = basePrice + opt.extraPrice
                    Card(
                        onClick = { onSelectOption(opt) },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isFree) StatusSuccess.copy(alpha = 0.08f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isFree) StatusSuccess.copy(alpha = 0.55f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("offer_choice_${opt.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(opt.displayName(lang), fontWeight = FontWeight.Bold)
                                Text(
                                    text = formatSar(totalPrice, lang),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isFree) StatusSuccess else MaterialTheme.colorScheme.primary
                            ) {
                                Text(
                                    text = if (isFree) s.freeLabel else "+${formatSar(opt.extraPrice, lang)}",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelectOption(null) }) {
                Text(s.noOfferBaseOnly)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(s.cancel)
            }
        }
    )
}
