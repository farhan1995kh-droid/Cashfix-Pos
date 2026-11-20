package com.example

import com.example.hardware.ThermalReceiptRasterizer
import com.example.hardware.ZatcaQrAndEscPos
import com.example.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CashFixDomainUnitTest {

    private fun countEscPosCuts(bytes: ByteArray): Int {
        var cuts = 0
        for (i in 0 until bytes.size - 2) {
            if (bytes[i] == 0x1D.toByte() && bytes[i + 1] == 0x56.toByte() && bytes[i + 2] == 0x00.toByte()) {
                cuts++
            }
        }
        return cuts
    }

    private fun containsGsV0Raster576Header(bytes: ByteArray): Boolean {
        // GS v 0 m=0 xL=72(0x48) xH=0 (576px / 8 = 72 bytes/row)
        for (i in 0 until bytes.size - 5) {
            if (bytes[i] == 0x1D.toByte() &&
                bytes[i + 1] == 0x76.toByte() &&
                bytes[i + 2] == 0x30.toByte() &&
                bytes[i + 3] == 0x00.toByte() &&
                bytes[i + 4] == 0x48.toByte() &&
                bytes[i + 5] == 0x00.toByte()
            ) {
                return true
            }
        }
        return false
    }

    @Test
    fun singleBranchStoreSettingsDefaultToAlHofufAnd80mm() {
        val defaults = StoreSettings()
        assertEquals("Al Hofuf, Saudi Arabia", defaults.addressEn)
        assertEquals("الأحساء، المملكة العربية السعودية", defaults.addressAr)
        assertEquals(80, defaults.paperWidthMm)
        assertEquals(9100, defaults.printerPort)
        assertEquals(15, defaults.vatNumber.length)
    }

    @Test
    fun splitPaymentBreakdownCalculatesAllocatedTotalAndCashUsage() {
        val payment = PaymentBreakdown(
            primaryMethod = PaymentMethod.SPLIT,
            isSplit = true,
            cashAmount = 15.0,
            madaAmount = 8.0,
            cashTendered = 20.0,
            changeDue = 5.0
        )
        assertEquals(23.0, payment.totalPaidAllocated, 0.001)
        assertTrue(payment.usesCash)
        assertEquals(5.0, payment.changeDue, 0.001)
    }

    @Test
    fun dualPricingSwitchesBetweenOfflineAndOnline() {
        val product = Product(
            id = "p1",
            categoryId = "c1",
            nameEn = "Spanish Latte",
            nameAr = "سبانش لاتيه",
            sku = "CF-102",
            barcode = "6281001001028",
            offlinePrice = 19.0,
            onlinePrice = 23.0,
            stock = 50
        )
        assertEquals(19.0, product.priceFor(PricingMode.OFFLINE), 0.001)
        assertEquals(23.0, product.priceFor(PricingMode.ONLINE), 0.001)
    }

    @Test
    fun escPosDrawerKickMatchesExactPulseBytes() {
        val expected = byteArrayOf(27, 112, 0, 25, 250.toByte())
        assertArrayEquals(expected, ZatcaQrAndEscPos.CASH_DRAWER_KICK_BYTES)
    }

    @Test
    fun zatcaPhase1QrTlvGeneratesNonEmptyBase64AndBitmap() {
        val tlv = ZatcaQrAndEscPos.buildZatcaBase64Tlv(
            sellerName = "CashFix Roastery",
            vatNumber = "310123456700003",
            timestampMillis = 1763337600000L,
            invoiceTotal = 23.00,
            vatTotal = 3.00
        )
        assertTrue(tlv.isNotBlank())
        val bitmap = ZatcaQrAndEscPos.generateQrBitmap(tlv, 128)
        assertNotNull(bitmap)
        assertEquals(128, bitmap!!.width)
    }

    @Test
    fun dualMemoReceiptGeneratesCustomerBillAndRestaurantCopy() {
        val order = PosOrder(
            id = "ord_1",
            orderNumber = "INV-10001",
            branchId = "br_riyadh",
            shiftId = "sh_1",
            cashierId = "csh_1",
            cashierName = "Ahmed",
            sourceId = "src_offline",
            sourceNameEn = "Offline Order",
            sourceNameAr = "طلب محلي",
            pricingMode = PricingMode.OFFLINE,
            items = listOf(
                CartItem(
                    id = "ci_1",
                    productId = "p1",
                    nameEn = "Spanish Latte",
                    nameAr = "سبانش لاتيه",
                    offlinePrice = 19.0,
                    onlinePrice = 23.0,
                    unitPrice = 19.0,
                    qty = 2,
                    note = "Less ice"
                )
            ),
            subtotal = 38.0,
            discountType = DiscountType.PERCENT,
            discountValue = 0.0,
            discountAmount = 0.0,
            vatRate = 0.15,
            vatInclusive = true,
            vatAmount = 4.96,
            grandTotal = 38.0,
            payment = PaymentBreakdown(primaryMethod = PaymentMethod.CASH, cashAmount = 38.0),
            tableOrCarNumber = "T-04"
        )
        val customSettings = StoreSettings(
            shopNameEn = "Al-Noor Café",
            shopNameAr = "مقهى النور",
            addressEn = "King Fahd Rd, Jeddah",
            addressAr = "طريق الملك فهد، جدة",
            phone = "+966 50 999 8888",
            vatNumber = "310999888700003",
            receiptFooterEn = "See you soon!",
            receiptFooterAr = "نراكم قريباً!"
        )
        val menuUrl = "http://192.168.1.50:8090/?table=T-04"
        val bytes = ZatcaQrAndEscPos.buildEscPosReceiptBytes(order, customSettings, kickDrawer = false, customerMenuUrl = menuUrl)
        // Must start with ESC @ init and contain GS v 0 576px raster command (no raw garbled Arabic text)
        assertEquals(0x1B.toByte(), bytes[0])
        assertEquals(0x40.toByte(), bytes[1])
        assertTrue(containsGsV0Raster576Header(bytes))
        assertEquals(2, countEscPosCuts(bytes))

        // Verify 576px rasterized bitmaps render cleanly for both customer bill and kitchen copy
        val customerBitmap = ThermalReceiptRasterizer.renderCustomerBillBitmap(order, customSettings, menuUrl)
        assertEquals(ThermalReceiptRasterizer.PAPER_WIDTH_PX, customerBitmap.width)
        assertTrue(customerBitmap.height > 200)
        customerBitmap.recycle()

        val kitchenBitmap = ThermalReceiptRasterizer.renderRestaurantCopyBitmap(order, customSettings)
        assertEquals(ThermalReceiptRasterizer.PAPER_WIDTH_PX, kitchenBitmap.width)
        assertTrue(kitchenBitmap.height > 150)
        kitchenBitmap.recycle()

        // Verify reference memo preview pulls all editable Store Setup fields and Saudi tax lines
        val arPreview = ZatcaQrAndEscPos.formatReceiptPreviewText(order, customSettings, com.example.i18n.AppLang.AR, menuUrl)
        assertTrue(arPreview.contains("مقهى النور"))
        assertTrue(arPreview.contains("Al-Noor Café"))
        assertTrue(arPreview.contains("طريق الملك فهد، جدة"))
        assertTrue(arPreview.contains("310999888700003"))
        assertTrue(arPreview.contains("رقم الفاتورة"))
        assertTrue(arPreview.contains("INV-10001"))
        assertTrue(arPreview.contains("Spanish Latte / سبانش لاتيه"))
        assertTrue(arPreview.contains("عدد المنتجات"))
        assertTrue(arPreview.contains("المجموع الفرعي"))
        assertTrue(arPreview.contains("المبلغ الخاضع للضريبة"))
        assertTrue(arPreview.contains("Tax 15%"))
        assertTrue(arPreview.contains("38.00"))
        assertTrue(arPreview.contains("طريقة الدفع"))
        assertTrue(arPreview.contains("تمت خدمتك من قبل"))
        assertTrue(arPreview.contains("See you soon!"))
        assertTrue(arPreview.contains("+966 50 999 8888"))
        assertTrue(arPreview.contains(menuUrl))
    }

    @Test
    fun arabicModeShowsArabicAndEnglishNamesTogether() {
        val category = Category("cat_coffee", "Hot & Iced Coffee", "القهوة الساخنة والباردة", 1)
        val product = Product(
            id = "p_1",
            categoryId = "cat_coffee",
            nameEn = "Spanish Latte",
            nameAr = "سبانش لاتيه",
            sku = "CF-101",
            barcode = "123456",
            offlinePrice = 19.0,
            onlinePrice = 23.0,
            stock = 10
        )
        assertEquals("Spanish Latte", product.displayName(com.example.i18n.AppLang.EN))
        assertEquals("سبانش لاتيه / Spanish Latte", product.displayName(com.example.i18n.AppLang.AR))
        assertEquals("القهوة الساخنة والباردة / Hot & Iced Coffee", category.displayName(com.example.i18n.AppLang.AR))
    }

    @Test
    fun categorySubFolderAndProductOfferOptionsWorkCorrectly() {
        val drinks = Category("cat_drinks", "Drinks", "المشروبات", 3, null)
        val softDrinks = Category("cat_soft_drinks", "Soft Drinks", "المشروبات الغازية", 1, parentCategoryId = drinks.id)
        assertNull(drinks.parentCategoryId)
        assertEquals("cat_drinks", softDrinks.parentCategoryId)

        val offer = ProductOffer(
            id = "off_box_drink",
            titleEn = "Free Drink or Juice Upgrade with Box",
            titleAr = "مشروب مجاني أو ترقية عصير مع البوكس",
            targetProductId = "prd_box_1",
            isEnabled = true,
            options = listOf(
                OfferOption("opt_tea", "Tea", "شاي", 0.0),
                OfferOption("opt_cola", "Pepsi / Cola", "بيبسي / كولا", 0.0),
                OfferOption("opt_juice", "Fresh Orange Juice", "عصير برتقال طازج", 2.0)
            )
        )
        val boxBasePrice = 35.0
        val freeOptionTotal = boxBasePrice + offer.options[0].extraPrice
        val juiceUpgradeTotal = boxBasePrice + offer.options[2].extraPrice
        assertEquals(35.0, freeOptionTotal, 0.001)
        assertEquals(37.0, juiceUpgradeTotal, 0.001)
        assertEquals("شاي / Tea", offer.options[0].displayName(com.example.i18n.AppLang.AR))
    }

    @Test
    fun crispyNationCategoryOffersAndPricingWorkCorrectly() {
        val sigZinger = Product("prd_z_sig", "cat_zingers", "Signature Zinger", "زينجر سيجنتشر", "ZN-101", "6281001001011", 15.0, 18.0, 120)
        val nashZinger = Product("prd_z_nash", "cat_zingers", "Nashville Zinger", "زينجر ناشفيل", "ZN-103", "6281001001035", 16.0, 19.0, 100)
        val frenchFries = Product("prd_c_fries", "cat_crunchies", "French Fries", "بطاطس", "CR-301", "6281001003015", 6.0, 8.0, 200)

        val zingerCatOffer = ProductOffer(
            id = "off_zingers_meal",
            titleEn = "Zinger: Make it a Meal / Add Mozzarella",
            titleAr = "زينجر: حولها لوجبة أو أضف موزريلا",
            targetProductId = "cat:cat_zingers",
            isEnabled = true,
            options = listOf(
                OfferOption("opt_mozz", "Add Mozzarella Slice", "إضافة شريحة موزريلا", 4.0),
                OfferOption("opt_meal_pepsi", "Make it a Meal (Fries + Pepsi)", "وجبة (بطاطس + بيبسي)", 7.0),
                OfferOption("opt_meal_mozz", "Make it a Meal + Add Mozzarella", "وجبة + إضافة شريحة موزريلا", 11.0)
            )
        )
        val specificNashOffer = ProductOffer(
            id = "off_nash_special",
            titleEn = "Nashville Special",
            titleAr = "عرض ناشفيل الخاص",
            targetProductId = "prd_z_nash",
            isEnabled = true,
            options = listOf(OfferOption("opt_only", "Sandwich Only", "ساندويتش فقط", 0.0))
        )

        val offers = listOf(zingerCatOffer, specificNashOffer)

        assertTrue(zingerCatOffer.isCategoryTarget)
        assertEquals("cat_zingers", zingerCatOffer.targetCategoryIdOrNull)

        // Category offer matches Signature Zinger
        val matchedSig = offers.activeOfferFor(sigZinger)
        assertEquals("off_zingers_meal", matchedSig?.id)
        assertEquals(19.0, sigZinger.offlinePrice + matchedSig!!.options[0].extraPrice, 0.001)
        assertEquals(22.0, sigZinger.offlinePrice + matchedSig.options[1].extraPrice, 0.001)
        assertEquals(26.0, sigZinger.offlinePrice + matchedSig.options[2].extraPrice, 0.001)

        // Product-specific offer takes precedence over category-wide offer
        val matchedNash = offers.activeOfferFor(nashZinger)
        assertEquals("off_nash_special", matchedNash?.id)

        // Crunchies has no offer attached
        assertNull(offers.activeOfferFor(frenchFries))
    }

    @Test
    fun receiptCopyModeSupportsKitchenOnlyCustomerOnlyAndBoth() {
        val order = PosOrder(
            id = "ord_mode_1",
            orderNumber = "H-2045",
            branchId = "br_riyadh",
            shiftId = "sh_1",
            cashierId = "csh_1",
            cashierName = "Sara",
            sourceId = "src_offline",
            sourceNameEn = "Offline Order",
            sourceNameAr = "طلب محلي",
            pricingMode = PricingMode.OFFLINE,
            items = listOf(
                CartItem(
                    id = "ci_1",
                    productId = "prd_z_sig",
                    nameEn = "Signature Zinger",
                    nameAr = "زينجر سيجنتشر",
                    offlinePrice = 15.0,
                    onlinePrice = 18.0,
                    unitPrice = 15.0,
                    qty = 1
                )
            ),
            subtotal = 15.0,
            discountType = DiscountType.PERCENT,
            discountValue = 0.0,
            discountAmount = 0.0,
            vatRate = 0.15,
            vatInclusive = true,
            vatAmount = 1.96,
            grandTotal = 15.0,
            payment = PaymentBreakdown(primaryMethod = PaymentMethod.CASH, cashAmount = 15.0),
            customerName = "Tariq"
        )
        val settings = StoreSettings()

        val kitchenBytes = ZatcaQrAndEscPos.buildEscPosReceiptBytes(order, settings, kickDrawer = false, mode = ReceiptCopyMode.KITCHEN_ONLY)
        assertEquals(1, countEscPosCuts(kitchenBytes))
        assertTrue(containsGsV0Raster576Header(kitchenBytes))

        val customerBytes = ZatcaQrAndEscPos.buildEscPosReceiptBytes(order, settings, kickDrawer = false, mode = ReceiptCopyMode.CUSTOMER_ONLY)
        assertEquals(1, countEscPosCuts(customerBytes))
        assertTrue(containsGsV0Raster576Header(customerBytes))

        val bothBytes = ZatcaQrAndEscPos.buildEscPosReceiptBytes(order, settings, kickDrawer = false, mode = ReceiptCopyMode.BOTH)
        assertEquals(2, countEscPosCuts(bothBytes))
        assertTrue(bothBytes.size > customerBytes.size)
        assertTrue(bothBytes.size > kitchenBytes.size)

        val productWithPhoto = Product(
            id = "p_img",
            categoryId = "cat_zingers",
            nameEn = "Signature Zinger",
            nameAr = "زينجر سيجنتشر",
            sku = "ZN-101",
            barcode = "123",
            offlinePrice = 15.0,
            onlinePrice = 18.0,
            stock = 10,
            imageUri = "/data/user/0/com.example/files/product_images/zinger.jpg"
        )
        assertTrue(productWithPhoto.imageUri.endsWith("zinger.jpg"))
    }

    @Test
    fun singleAdminLockAndCashierRoleSeparation() {
        val settings = StoreSettings()
        assertEquals("1234", settings.adminPin)

        val cashier = Cashier(
            id = "csh_1",
            nameEn = "Sara Al-Harbi",
            nameAr = "سارة الحربي",
            pin = "",
            role = "cashier",
            branchId = "br_riyadh",
            permissions = CashierPermissions()
        )
        val admin = Cashier(
            id = "csh_admin",
            nameEn = "Sultan Al-Otaibi",
            nameAr = "سلطان العتيبي",
            pin = "1234",
            role = "admin",
            branchId = "br_riyadh",
            permissions = CashierPermissions(
                canAddProducts = true,
                canProcessRefunds = true,
                canAccessManagement = true
            )
        )
        assertFalse(cashier.isAdminOrManager)
        assertTrue(admin.isAdminOrManager)
    }

    @Test
    fun shiftCloseSummaryMemoBuildsEscPosAndPreviewText() {
        val shift = ShiftSession(
            id = "sh_closed_1",
            branchId = "br_riyadh",
            cashierId = "csh_1",
            cashierName = "Sara Al-Harbi",
            openingFloat = 200.0,
            openedAt = 1763330000000L,
            closedAt = 1763358800000L,
            expectedCash = 550.0,
            countedCash = 545.0,
            variance = -5.0,
            isOpen = false
        )
        val metrics = ShiftSummaryMetrics(
            orderCount = 14,
            grossSales = 1250.0,
            totalDiscounts = 50.0,
            totalVat = 156.52,
            cashSales = 370.0,
            madaSales = 500.0,
            visaSales = 180.0,
            onlineSales = 150.0,
            dueSales = 0.0,
            cashRefunds = 20.0,
            totalRefunds = 20.0,
            netSales = 1230.0,
            openingFloat = 200.0,
            expectedCashInDrawer = 550.0
        )
        val settings = StoreSettings(
            shopNameEn = "Crispy Nation",
            shopNameAr = "كريسبي نيشن",
            vatNumber = "310123456700003"
        )

        val bytes = ZatcaQrAndEscPos.buildShiftSummaryEscPosBytes(shift, metrics, settings, "Al Hofuf, Saudi Arabia")
        assertEquals(0x1B.toByte(), bytes[0])
        assertEquals(0x40.toByte(), bytes[1])
        assertTrue(containsGsV0Raster576Header(bytes))
        assertEquals(1, countEscPosCuts(bytes))

        val shiftBitmap = ThermalReceiptRasterizer.renderShiftSummaryBitmap(shift, metrics, settings)
        assertEquals(ThermalReceiptRasterizer.PAPER_WIDTH_PX, shiftBitmap.width)
        assertTrue(shiftBitmap.height > 200)
        shiftBitmap.recycle()

        val preview = ZatcaQrAndEscPos.formatShiftSummaryPreviewText(shift, metrics, settings, "Al Hofuf, Saudi Arabia")
        assertTrue(preview.contains("SHIFT CLOSING SUMMARY"))
        assertTrue(preview.contains("Al Hofuf, Saudi Arabia"))
        assertTrue(preview.contains("Cashier: Sara Al-Harbi"))
        assertTrue(preview.contains("Mada Sales: 500.00 SAR"))
        assertTrue(preview.contains("NET SHIFT SALES: 1230.00 SAR"))
        assertTrue(preview.contains("CASH VARIANCE: -5.00 SAR"))
    }

    @Test
    fun shiftOpeningAndClosingCashReconciliationCalculatesAccurately() {
        val openingCash = 500.0
        val cashSales = 340.0
        val cashRefunds = 40.0
        val expectedClosingCash = openingCash + cashSales - cashRefunds // 800.0
        val actualClosingCash = 815.0
        val variance = actualClosingCash - expectedClosingCash // +15.0

        assertEquals(800.0, expectedClosingCash, 0.001)
        assertEquals(15.0, variance, 0.001)

        val closedShift = ShiftSession(
            id = "sh_recon",
            branchId = "br_riyadh",
            cashierId = "csh_1",
            cashierName = "Sara Al-Harbi",
            openingFloat = openingCash,
            openedAt = 1763330000000L,
            closedAt = 1763358800000L,
            expectedCash = expectedClosingCash,
            countedCash = actualClosingCash,
            variance = variance,
            isOpen = false
        )
        val metrics = ShiftSummaryMetrics(
            orderCount = 6,
            grossSales = 340.0,
            cashSales = cashSales,
            cashRefunds = cashRefunds,
            totalRefunds = cashRefunds,
            netSales = 300.0,
            openingFloat = openingCash,
            expectedCashInDrawer = expectedClosingCash
        )
        val preview = ZatcaQrAndEscPos.formatShiftSummaryPreviewText(closedShift, metrics, StoreSettings())
        assertTrue(preview.contains("Opening Float: 500.00 SAR"))
        assertTrue(preview.contains("Expected Cash: 800.00 SAR"))
        assertTrue(preview.contains("Counted Cash: 815.00 SAR"))
        assertTrue(preview.contains("CASH VARIANCE: +15.00 SAR"))
    }

    @Test
    fun deduplicatedAvailableRemovesDuplicateAndUnavailableItems() {
        val rawProducts = listOf(
            Product("p1", "cat_zingers", "Signature Zinger", "زينجر سيجنتشر", "ZN-1", "111", 15.0, 18.0, 50, true),
            Product("p1", "cat_zingers", "Signature Zinger", "زينجر سيجنتشر", "ZN-1", "111", 15.0, 18.0, 50, true),
            Product("p2", "cat_zingers", " signature  zinger ", "زينجر سيجنتشر", "ZN-2", "112", 15.0, 18.0, 40, true),
            Product("p3", "cat_zingers", "Sold Out Burger", "برجر غير متوفر", "ZN-3", "113", 16.0, 19.0, 0, false),
            Product("p4", "cat_drinks", "Pepsi", "بيبسي", "DR-1", "211", 4.0, 5.0, 100, true)
        )
        val cleaned = rawProducts.deduplicatedAvailable()
        assertEquals(2, cleaned.size)
        assertEquals("p1", cleaned[0].id)
        assertEquals("p4", cleaned[1].id)
    }

    @Test
    fun qrWebMenuHtmlMatchesPosDashboardWithTableBadgeCategoriesSearchAndWakeLock() {
        val gateway = com.example.hardware.QrSelfOrderGateway()
        val categories = listOf(
            Category("cat_zingers", "Burgers & Sandwiches", "برجر وساندويتشات", 1),
            Category("cat_drinks", "Drinks", "المشروبات", 2)
        )
        val products = listOf(
            Product("p1", "cat_zingers", "Signature Zinger", "زينجر سيجنتشر", "ZN-1", "111", 15.0, 18.0, 50, true),
            Product("p1_dup", "cat_zingers", "Signature Zinger", "زينجر سيجنتشر", "ZN-1", "111", 15.0, 18.0, 50, true),
            Product("p2", "cat_drinks", "Pepsi", "بيبسي", "DR-1", "211", 4.0, 5.0, 100, true)
        )
        gateway.updateCatalogSnapshot(
            shopNameEn = "Crispy Nation",
            shopNameAr = "كريسبي نيشن",
            products = products,
            categories = categories,
            addressEn = "Al Hofuf, Saudi Arabia",
            addressAr = "الأحساء، المملكة العربية السعودية"
        )
        val html = gateway.buildCustomerMenuHtml(
            shopNameEn = "Crispy Nation",
            shopNameAr = "كريسبي نيشن",
            addressEn = "Al Hofuf, Saudi Arabia",
            addressAr = "الأحساء، المملكة العربية السعودية",
            tableCode = "T2",
            categories = categories,
            products = products
        )
        assertTrue(html.contains("Table: T2"))
        assertTrue(html.contains("Burgers & Sandwiches"))
        assertTrue(html.contains("navigator.wakeLock.request('screen')"))
        assertTrue(html.contains("grid-template-columns:repeat(2,1fr)"))
        assertTrue(html.contains("searchInput"))
        assertTrue(html.contains("catStrip"))
        // Deduplicated: Signature Zinger appears once in embedded catalog JSON
        assertTrue(html.contains("\"id\":\"p1\""))
        assertFalse(html.contains("\"id\":\"p1_dup\""))
    }

    @Test
    fun alwaysOnAndBackgroundAlertsDefaultEnabledAndChannelsInitialize() {
        val defaults = StoreSettings()
        assertTrue(defaults.alwaysOnDisplay)
        assertTrue(defaults.backgroundOrderAlertsEnabled)

        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        com.example.hardware.QrOrderRuntimeHub.ensureNotificationChannels(context)
        val monitorNotification = com.example.hardware.QrOrderRuntimeHub.buildMonitorNotification(
            context = context,
            port = 8090,
            shopName = "Crispy Nation"
        )
        assertNotNull(monitorNotification)
    }
}
