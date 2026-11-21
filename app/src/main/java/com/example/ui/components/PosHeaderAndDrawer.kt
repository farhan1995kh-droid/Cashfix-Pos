package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingUp
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.i18n.AppLang
import com.example.i18n.formatNumber
import com.example.i18n.stringsFor
import com.example.model.Branch
import com.example.model.Cashier
import com.example.model.ShiftSession
import com.example.model.StoreSettings
import com.example.ui.ActiveDestination
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosHeaderBar(
    lang: AppLang,
    settings: StoreSettings,
    activeCashier: Cashier?,
    heldCount: Int,
    isOnline: Boolean,
    pendingSyncCount: Int,
    onOpenDrawer: () -> Unit,
    onOpenHeldOrders: () -> Unit,
    onSyncNow: () -> Unit,
    onToggleLang: () -> Unit,
    onOpenCustomerQrMode: () -> Unit,
    onSwitchCashierClick: () -> Unit
) {
    val s = stringsFor(lang)

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.windowInsetsPadding(
                WindowInsets.statusBars.union(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
            )
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val isWideHeader = maxWidth >= 760.dp

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: Menu Box + Brand Logo + Shop Name + POS badge + Branch location
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Surface(
                            onClick = onOpenDrawer,
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .size(42.dp)
                                .testTag("header_drawer_btn")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Menu",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Spacer(Modifier.width(10.dp))

                        Surface(
                            shape = RoundedCornerShape(11.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.cashfix_brand_logo),
                                contentDescription = s.appName,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(11.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }

                        Spacer(Modifier.width(10.dp))

                        Column(modifier = Modifier.widthIn(max = 260.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = settings.shopDisplayName(lang),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = "POS",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 1.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    text = settings.addressDisplay(lang),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    // Right: Pill Action Buttons matching screenshot style
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        // Hold Orders Pill
                        Surface(
                            onClick = onOpenHeldOrders,
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .height(40.dp)
                                .testTag("header_held_orders_btn")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = s.heldOrders,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (isWideHeader) {
                                    Text(
                                        text = if (lang == AppLang.AR) "المعلقة" else "Hold Orders",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Surface(
                                    shape = CircleShape,
                                    color = if (heldCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = "$heldCount",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (heldCount > 0) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        // Sync / Status Pill
                        Surface(
                            onClick = onSyncNow,
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .height(40.dp)
                                .testTag("header_sync_btn")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (isOnline) StatusLiveGreen else StatusWarning)
                                )
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = s.syncNow,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                                if (isWideHeader) {
                                    Text(
                                        text = if (pendingSyncCount > 0) "${s.syncNow} ($pendingSyncCount)" else s.syncNow,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        // Language Pill
                        Surface(
                            onClick = onToggleLang,
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .height(40.dp)
                                .testTag("header_lang_btn")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = if (lang == AppLang.EN) "العربية" else "EN",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // QR Customer Menu Pill
                        Surface(
                            onClick = onOpenCustomerQrMode,
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .height(40.dp)
                                .testTag("header_qr_mode_btn")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCode2,
                                    contentDescription = s.customerQrPreview,
                                    modifier = Modifier.size(17.dp),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                                if (isWideHeader) {
                                    Text(
                                        text = "QR",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        // Active Cashier Profile Card Pill
                        Surface(
                            onClick = onSwitchCashierClick,
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .height(42.dp)
                                .testTag("header_switch_cashier_btn")
                        ) {
                            val cashierDisplay = activeCashier?.displayName(lang) ?: "Cashier"
                            val initial = cashierDisplay.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "C"
                            val roleLabel = if (activeCashier?.isAdminOrManager == true) {
                                if (lang == AppLang.AR) "المدير" else "Manager"
                            } else {
                                if (lang == AppLang.AR) "كاشير" else "Cashier"
                            }
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(AvatarAmber),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = initial,
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                                if (isWideHeader) {
                                    Column {
                                        Text(
                                            text = cashierDisplay.substringBefore(" ("),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = roleLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                } else {
                                    Text(
                                        text = cashierDisplay.substringBefore(" "),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
fun PosNavigationDrawerContent(
    lang: AppLang,
    settings: StoreSettings,
    activeCashier: Cashier?,
    openShift: ShiftSession?,
    activeDestination: ActiveDestination,
    heldCount: Int,
    darkTheme: Boolean,
    onNavigate: (ActiveDestination) -> Unit,
    onOpenLiveShiftSummary: () -> Unit,
    onOpenZReport: () -> Unit,
    onEditOpeningFloat: () -> Unit = {},
    onOpenManagementLocked: () -> Unit,
    onOpenCustomMenuDirect: () -> Unit,
    onOpenCustomerCrmLocked: () -> Unit,
    onSwitchCashier: () -> Unit,
    onKickCashDrawer: () -> Unit,
    onToggleLang: () -> Unit,
    onToggleTheme: () -> Unit,
    onCloseDrawer: () -> Unit
) {
    val s = stringsFor(lang)
    val canAccessAdmin = activeCashier?.isAdminOrManager == true
    val canCustomizeMenu = canAccessAdmin || activeCashier?.permissions?.canAddProducts == true
    val shiftStartFormatted = remember(openShift?.openedAt) {
        openShift?.openedAt?.let {
            SimpleDateFormat("hh:mm a", Locale.US).format(Date(it))
        } ?: "--:--"
    }

    ModalDrawerSheet(
        modifier = Modifier.width(316.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        windowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Indigo-Blue Gradient Header (matches screenshots 2-4)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            listOf(IndigoHeaderStart, IndigoHeaderEnd)
                        )
                    )
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 18.dp, vertical = 18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White,
                            shadowElevation = 2.dp,
                            modifier = Modifier.size(46.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.cashfix_brand_logo),
                                contentDescription = s.appName,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Column {
                            Text(
                                text = settings.shopDisplayName(lang),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${s.appName} POS",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFDBEAFE)
                            )
                        }
                    }

                    Surface(
                        onClick = onCloseDrawer,
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.18f),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = s.close,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Cashier Profile & Active Shift Float / Close Day Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val cashierName = activeCashier?.displayName(lang) ?: "Cashier"
                val initial = cashierName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "C"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(AvatarAmber),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = initial,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        Column {
                            Text(
                                text = cashierName,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (canAccessAdmin) {
                                    if (lang == AppLang.AR) "مدير المتجر" else "STORE ADMIN"
                                } else {
                                    if (lang == AppLang.AR) "كاشير" else "CASHIER"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Surface(
                        onClick = { onCloseDrawer(); onSwitchCashier() },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = s.switchCashier,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = s.switchLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = if (lang == AppLang.AR) "بداية الوردية: $shiftStartFormatted" else "SHIFT STARTED: $shiftStartFormatted",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                        Surface(
                            onClick = { onCloseDrawer(); onEditOpeningFloat() },
                            shape = RoundedCornerShape(10.dp),
                            color = StatusSuccess.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, StatusSuccess.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("drawer_edit_float_btn")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (lang == AppLang.AR) {
                                        "العهدة: ${formatNumber(openShift?.openingFloat ?: 0.0)} ر.س"
                                    } else {
                                        "Start Cash: SAR ${formatNumber(openShift?.openingFloat ?: 0.0)}"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = StatusSuccess,
                                    fontFamily = FontFamily.Monospace
                                )
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = s.setOpeningCashTitle,
                                    modifier = Modifier.size(16.dp),
                                    tint = StatusSuccess
                                )
                            }
                        }
                    }

                    Button(
                        onClick = { onCloseDrawer(); onOpenZReport() },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StatusDanger,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("drawer_close_day_btn")
                    ) {
                        Text(
                            text = if (lang == AppLang.AR) "تقرير Z / الوردية" else "Z-Report / Reset Shift",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            // Scrollable Main Menu Items — every folder/menu card uses uniform 56.dp height & identical sizing
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (lang == AppLang.AR) "القائمة الرئيسية" else "MAIN MENU OPTIONS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.7.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                DrawerNavItem(
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    iconTint = MaterialTheme.colorScheme.primary,
                    label = s.liveShiftSummary,
                    badgeText = "LIVE",
                    badgeBgColor = StatusLiveGreen,
                    badgeTextColor = Color.White,
                    showBadgeLockIcon = false,
                    selected = false,
                    testTagName = "drawer_live_shift_btn",
                    onClick = { onCloseDrawer(); onOpenLiveShiftSummary() }
                )

                DrawerNavItem(
                    icon = Icons.Default.ShoppingBag,
                    iconTint = MaterialTheme.colorScheme.primary,
                    label = if (lang == AppLang.AR) "نقطة البيع (الكاشير)" else "Cashier POS",
                    selected = activeDestination == ActiveDestination.POS_REGISTER,
                    testTagName = "drawer_pos_register_btn",
                    onClick = { onNavigate(ActiveDestination.POS_REGISTER); onCloseDrawer() }
                )

                DrawerNavItem(
                    icon = Icons.Default.Schedule,
                    iconTint = MaterialTheme.colorScheme.primary,
                    label = if (lang == AppLang.AR) s.heldOrders else "Advance / Held Orders",
                    badgeText = "$heldCount",
                    badgeBgColor = if (heldCount > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    badgeTextColor = if (heldCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    showBadgeLockIcon = false,
                    selected = activeDestination == ActiveDestination.HELD_ORDERS,
                    testTagName = "drawer_held_orders_btn",
                    onClick = { onNavigate(ActiveDestination.HELD_ORDERS); onCloseDrawer() }
                )

                if (activeCashier?.permissions?.canViewReports != false) {
                    DrawerNavItem(
                        icon = Icons.Default.ReceiptLong,
                        iconTint = MaterialTheme.colorScheme.primary,
                        label = s.salesHistory,
                        selected = activeDestination == ActiveDestination.SALES_HISTORY,
                        testTagName = "drawer_sales_history_btn",
                        onClick = { onNavigate(ActiveDestination.SALES_HISTORY); onCloseDrawer() }
                    )
                }

                // Custom Menu unlocked directly if cashier was granted canAddProducts permission
                if (!canAccessAdmin && canCustomizeMenu) {
                    DrawerNavItem(
                        icon = Icons.Default.RestaurantMenu,
                        iconTint = MaterialTheme.colorScheme.primary,
                        label = s.menuCustomizationTitle,
                        selected = activeDestination == ActiveDestination.ADMIN_PANEL,
                        testTagName = "drawer_custom_menu_btn",
                        onClick = { onCloseDrawer(); onOpenCustomMenuDirect() }
                    )
                }

                // Administration & CRM always visible and protected by the single Admin Lock PIN
                DrawerNavItem(
                    icon = Icons.Default.VerifiedUser,
                    iconTint = Color(0xFF4F46E5),
                    label = if (lang == AppLang.AR) "إعدادات الإدارة" else "Admin Settings",
                    badgeText = "ADMIN PIN",
                    selected = activeDestination == ActiveDestination.ADMIN_PANEL && canAccessAdmin,
                    testTagName = "drawer_admin_settings_btn",
                    onClick = { onCloseDrawer(); onOpenManagementLocked() }
                )

                DrawerNavItem(
                    icon = Icons.Default.QrCode2,
                    iconTint = MaterialTheme.colorScheme.primary,
                    label = s.customerQrPreview,
                    selected = activeDestination == ActiveDestination.CUSTOMER_QR_MODE,
                    testTagName = "drawer_customer_qr_btn",
                    onClick = { onNavigate(ActiveDestination.CUSTOMER_QR_MODE); onCloseDrawer() }
                )

                DrawerNavItem(
                    icon = Icons.Default.PersonAddAlt1,
                    iconTint = StatusSuccess,
                    label = s.customerDirectoryCrm,
                    badgeText = "ADMIN PIN",
                    selected = activeDestination == ActiveDestination.CUSTOMER_CRM,
                    testTagName = "drawer_customer_crm_btn",
                    onClick = { onCloseDrawer(); onOpenCustomerCrmLocked() }
                )

                Spacer(Modifier.height(4.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(2.dp))

                // Hardware & Drawer Section — uses exact same uniform DrawerNavItem sizing
                Text(
                    text = if (lang == AppLang.AR) "الطابعة ودرج النقدية" else "HARDWARE & DRAWER",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.7.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                DrawerNavItem(
                    icon = Icons.Default.LockOpen,
                    iconTint = MaterialTheme.colorScheme.primary,
                    label = if (lang == AppLang.AR) "فتح درج النقدية" else "Open Cash Drawer",
                    badgeText = "PULSE",
                    badgeBgColor = MaterialTheme.colorScheme.surfaceVariant,
                    badgeTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    showBadgeLockIcon = false,
                    selected = false,
                    testTagName = "drawer_kick_cash_drawer_btn",
                    onClick = { onCloseDrawer(); onKickCashDrawer() }
                )

                DrawerNavItem(
                    icon = Icons.Default.Print,
                    iconTint = MaterialTheme.colorScheme.primary,
                    label = s.printerSettingsTitle,
                    badgeText = "80mm",
                    badgeBgColor = StatusSuccess.copy(alpha = 0.14f),
                    badgeTextColor = StatusSuccess,
                    showBadgeLockIcon = false,
                    selected = activeDestination == ActiveDestination.PRINTER_SETTINGS,
                    testTagName = "drawer_printer_settings_btn",
                    onClick = { onNavigate(ActiveDestination.PRINTER_SETTINGS); onCloseDrawer() }
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            // Drawer Footer (Language Toggle + Theme + Version)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onToggleLang() }
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (lang == AppLang.EN) "العربية" else "English",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = onToggleTheme,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = if (darkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Theme",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = "CashFix v2.0",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Uniform fixed-height (56.dp) sidebar menu / folder row ensuring every item in the
 * navigation drawer shares identical dimensions, padding, icon alignment, and typography.
 */
@Composable
private fun DrawerNavItem(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    badgeText: String? = null,
    badgeBgColor: Color = GoldLightContainer,
    badgeTextColor: Color = GoldTertiary,
    showBadgeLockIcon: Boolean = true,
    selected: Boolean,
    testTagName: String? = null,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
        shadowElevation = if (selected) 2.dp else 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .let { if (testTagName != null) it.testTag(testTagName) else it }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(
                        if (selected) Color.White.copy(alpha = 0.20f)
                        else iconTint.copy(alpha = 0.12f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) Color.White else iconTint,
                    modifier = Modifier.size(19.dp)
                )
            }

            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            if (badgeText != null) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (selected) Color.White.copy(alpha = 0.22f) else badgeBgColor
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        if (showBadgeLockIcon) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = if (selected) Color.White else badgeTextColor,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (selected) Color.White else badgeTextColor,
                            maxLines = 1
                        )
                    }
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
