package com.example.ui.screens.more

import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.example.data.model.Permission
import com.example.data.model.BusinessProfileEntity
import com.example.ui.theme.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.example.ui.util.ReceiptDesign
import com.example.ui.util.ReceiptItemData
import com.example.ui.util.CurrencyUtils
import com.example.ui.viewmodel.PosViewModel

/** A ready-to-fill CSV so the owner can prepare a catalogue on a computer. */
private val PRODUCT_CSV_TEMPLATE = """
    name,sellingPrice,costPrice,barcode,sku,category,subCategory,unit,currentStock,lowStockThreshold,tracked,favourite,variants
    Coconut Oil 1L,1900,1600,4791000000001,COCO-1L,Grocery,Oil,Litre,12,4,true,false,
    Biriyani Regular,1200,800,,BIR-REG,Food,Rice dishes,Plate,0,0,false,false,"Regular|1200;Full|1800"
""".trimIndent()

enum class SettingsSection(val title: String, val icon: @Composable () -> Unit) {
    ALL("All", { Icon(Icons.Default.Tune, null, Modifier.size(16.dp)) }),
    BUSINESS("Business", { Icon(Icons.Default.Store, null, Modifier.size(16.dp)) }),
    RECEIPTS("Receipts & Print", { Icon(Icons.Default.Print, null, Modifier.size(16.dp)) }),
    OPERATIONS("Operations & Rules", { Icon(Icons.Default.Rule, null, Modifier.size(16.dp)) }),
    DATA("Data & System", { Icon(Icons.Default.Storage, null, Modifier.size(16.dp)) })
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SettingsConfigurationScreen(
    viewModel: PosViewModel,
    onBack: () -> Unit
) {
    val screenPermissions by viewModel.permissions.collectAsState()
    if (screenPermissions.cannot(Permission.MANAGE_SETTINGS)) {
        com.example.ui.components.LockedScreenNotice(
            message = screenPermissions.denialMessage(Permission.MANAGE_SETTINGS),
            onBack = onBack
        )
        return
    }

    val profile by viewModel.profile.collectAsState()
    val staffList by viewModel.staffList.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedSection by remember { mutableStateOf(SettingsSection.ALL) }

    // Form state initialized from profile
    var name by remember(profile) { mutableStateOf(profile?.name.orEmpty()) }
    var businessType by remember(profile) { mutableStateOf(profile?.businessType ?: "Retail") }
    var phone by remember(profile) { mutableStateOf(profile?.phone ?: "+94 77 123 4567") }
    var address by remember(profile) { mutableStateOf(profile?.address ?: "123 Galle Road, Colombo") }
    var currencySymbol by remember(profile) { mutableStateOf(profile?.currencySymbol ?: "Rs.") }
    var taxRate by remember { mutableStateOf("0.0") }
    var taxInclusive by remember { mutableStateOf(true) }

    // Receipts & Printer
    var receiptFooter by remember(profile) { mutableStateOf(profile?.receiptFooter ?: "Thank you for shopping with us! Please come again.") }
    var receiptShowQr by remember(profile) { mutableStateOf(profile?.receiptShowQr ?: true) }
    // Bill design: what actually gets printed on the customer's receipt.
    var receiptHeaderName by remember(profile) { mutableStateOf(profile?.receiptHeaderName.orEmpty()) }
    var receiptHeaderNote by remember(profile) { mutableStateOf(profile?.receiptHeaderNote.orEmpty()) }
    var receiptShowAddress by remember(profile) { mutableStateOf(profile?.receiptShowAddress ?: true) }
    var receiptShowPhone by remember(profile) { mutableStateOf(profile?.receiptShowPhone ?: true) }
    var receiptShowDateTime by remember(profile) { mutableStateOf(profile?.receiptShowDateTime ?: true) }
    var receiptShowCashier by remember(profile) { mutableStateOf(profile?.receiptShowCashier ?: true) }
    var receiptShowItemCount by remember(profile) { mutableStateOf(profile?.receiptShowItemCount ?: true) }
    var receiptReturnNote by remember(profile) { mutableStateOf(profile?.receiptReturnNote.orEmpty()) }
    var printerName by remember(profile) { mutableStateOf(profile?.printerName.orEmpty()) }

    // Real catalogue rows drive the receipt preview.
    val allProducts by viewModel.products.collectAsState()
    val previewItems = remember(allProducts) { allProducts.take(2) }
    val previewTotal = remember(previewItems) { previewItems.sumOf { it.sellingPrice } }
    var printerWidth by remember(profile) { mutableStateOf(profile?.printerPaperWidth ?: "58mm") }
    var autoPrint by remember(profile) { mutableStateOf(profile?.autoPrint ?: false) }
    var receiptStyle by remember(profile) { mutableStateOf(profile?.receiptStyle ?: "Modern") }

    // Operations
    var trackStock by remember(profile) { mutableStateOf(profile?.trackStock ?: true) }
    var creditEnabled by remember(profile) { mutableStateOf(profile?.creditEnabled ?: true) }
    var cashDrawerEnabled by remember(profile) { mutableStateOf(profile?.cashDrawerEnabled ?: false) }
    var staffEnabled by remember(profile) { mutableStateOf(profile?.staffEnabled ?: true) }
    var defaultLowStockThreshold by remember { mutableStateOf("5") }
    var allowNegativeStock by remember { mutableStateOf(false) }
    var requireManagerPinForRefund by remember { mutableStateOf(true) }
    var maxCashierDiscount by remember { mutableStateOf("10") }
    var defaultOpeningCash by remember { mutableStateOf("10000") }
    var defaultCreditLimit by remember { mutableStateOf("25000") }
    var whatsappTemplate by remember {
        mutableStateOf("Hi {customer_name}, your outstanding balance at {business_name} is {amount}. Thank you.")
    }

    // Dialog states
    var showTestPrintDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var showBackupSuccessDialog by remember { mutableStateOf(false) }
    var showFlushConfirmDialog by remember { mutableStateOf(false) }
    var showProviderCloudDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val csv = viewModel.exportProductsCsv()
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(csv.toByteArray(Charsets.UTF_8))
                    }
                }.onFailure { viewModel.showMessage("Could not write the CSV file") }
            }
        }
    }
    val templateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(PRODUCT_CSV_TEMPLATE.toByteArray(Charsets.UTF_8))
                }
            }.onFailure { viewModel.showMessage("Could not write the CSV template") }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val content = runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                }.getOrDefault("")
                if (content.isBlank()) {
                    viewModel.showMessage("That file was empty")
                } else {
                    viewModel.importProductsFromCsv(content)
                }
            }
        }
    }

    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
            .only(WindowInsetsSides.Top),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Settings & Configuration", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
                        Text("Personalize store rules & hardware", fontSize = 12.sp, color = TextSecondary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            val current = profile ?: BusinessProfileEntity()
                            viewModel.saveBusinessProfile(
                                current.copy(
                                    name = name.trim(),
                                    businessType = businessType,
                                    phone = phone.trim(),
                                    address = address.trim(),
                                    currencySymbol = currencySymbol,
                                    receiptFooter = receiptFooter.trim(),
                                    receiptShowQr = receiptShowQr,
                                    receiptHeaderName = receiptHeaderName.trim(),
                                    receiptHeaderNote = receiptHeaderNote.trim(),
                                    receiptShowAddress = receiptShowAddress,
                                    receiptShowPhone = receiptShowPhone,
                                    receiptShowDateTime = receiptShowDateTime,
                                    receiptShowCashier = receiptShowCashier,
                                    receiptShowItemCount = receiptShowItemCount,
                                    receiptReturnNote = receiptReturnNote.trim(),
                                    printerName = printerName,
                                    printerPaperWidth = printerWidth,
                                    autoPrint = autoPrint,
                                    receiptStyle = receiptStyle,
                                    trackStock = trackStock,
                                    creditEnabled = creditEnabled,
                                    cashDrawerEnabled = cashDrawerEnabled,
                                    staffEnabled = staffEnabled
                                )
                            )
                            onBack()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 8.dp).testTag("save_settings_top")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LightSurface)
            )
        },
        containerColor = LightBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 48.dp)
        ) {
            // Search field
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search settings & rules...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = LightSurface,
                        unfocusedContainerColor = LightSurface,
                        focusedBorderColor = BrandTealPrimary,
                        unfocusedBorderColor = LightBorder
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("settings_search_field"),
                    singleLine = true
                )
            }

            // Section Filter Chips
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(SettingsSection.values()) { section ->
                        FilterChip(
                            selected = selectedSection == section,
                            onClick = { selectedSection = section },
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    section.icon()
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(section.title, fontSize = 12.sp, fontWeight = if (selectedSection == section) FontWeight.Bold else FontWeight.Normal)
                                }
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BrandTealPrimary,
                                selectedLabelColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }

            // SECTION 1: BUSINESS PROFILE
            if (shouldShowSection(SettingsSection.BUSINESS, selectedSection, searchQuery, "Business profile name phone address tax type")) {
                item {
                    SettingsCard(
                        title = "1. BUSINESS PROFILE",
                        subtitle = "Identity, category & contact info shown on bills"
                    ) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Business Name *") },
                            leadingIcon = { Icon(Icons.Default.Storefront, null, tint = BrandTealPrimary) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Business Type / Model", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            listOf("Retail", "Service", "Both", "Food", "Repairs").forEach { type ->
                                FilterChip(
                                    selected = businessType == type,
                                    onClick = { businessType = type },
                                    label = { Text(type, fontSize = 11.sp) },
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("Phone Number (Sri Lanka default)") },
                            leadingIcon = { Icon(Icons.Default.Phone, null, tint = BrandTealPrimary) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = address,
                            onValueChange = { address = it },
                            label = { Text("Shop Address") },
                            leadingIcon = { Icon(Icons.Default.LocationOn, null, tint = BrandTealPrimary) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = currencySymbol,
                                onValueChange = { currencySymbol = it },
                                label = { Text("Currency Symbol") },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = taxRate,
                                onValueChange = { taxRate = it },
                                label = { Text("Tax Rate (%)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Prices Include Tax", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimary)
                                Text("No additional tax added at checkout", fontSize = 11.sp, color = TextSecondary)
                            }
                            Switch(checked = taxInclusive, onCheckedChange = { taxInclusive = it })
                        }
                    }
                }
            }

            // SECTION 2: INVOICES, RECEIPTS & PRINTER
            if (shouldShowSection(SettingsSection.RECEIPTS, selectedSection, searchQuery, "Receipts printer thermal 58mm 80mm bluetooth qr")) {
                item {
                    SettingsCard(
                        title = "2. RECEIPTS & THERMAL PRINTER",
                        subtitle = "Layout, thermal printer connection & customer slip"
                    ) {
                        Text(
                            "This is what your customer takes home. Change the wording " +
                                "here and the preview at the bottom updates straight away.",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = receiptHeaderName,
                            onValueChange = { receiptHeaderName = it },
                            label = { Text("Name on the bill") },
                            placeholder = { Text(name.ifBlank { "Your shop name" }) },
                            supportingText = {
                                Text("Leave blank to use your shop name.", fontSize = 10.sp)
                            },
                            leadingIcon = { Icon(Icons.Default.Storefront, null, tint = BrandTealPrimary) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = receiptHeaderNote,
                            onValueChange = { receiptHeaderNote = it },
                            label = { Text("Line under the name (optional)") },
                            placeholder = { Text("Wholesale & Retail") },
                            supportingText = {
                                Text("Good for a slogan, VAT number or branch.", fontSize = 10.sp)
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            "SHOW ON THE BILL",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        ReceiptToggle(
                            "Date and time",
                            "When the sale happened. Useful for returns.",
                            receiptShowDateTime
                        ) { receiptShowDateTime = it }
                        ReceiptToggle(
                            "Shop address",
                            null,
                            receiptShowAddress
                        ) { receiptShowAddress = it }
                        ReceiptToggle(
                            "Phone number",
                            null,
                            receiptShowPhone
                        ) { receiptShowPhone = it }
                        ReceiptToggle(
                            "Who served them",
                            "Shows the staff member's name on the bill.",
                            receiptShowCashier
                        ) { receiptShowCashier = it }
                        ReceiptToggle(
                            "Item and quantity count",
                            "A line showing how many items and how many units.",
                            receiptShowItemCount
                        ) { receiptShowItemCount = it }

                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = receiptReturnNote,
                            onValueChange = { receiptReturnNote = it },
                            label = { Text("Returns / warranty note (optional)") },
                            placeholder = { Text("Keep this bill for exchanges within 7 days") },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = receiptFooter,
                            onValueChange = { receiptFooter = it },
                            label = { Text("Thank you message") },
                            leadingIcon = { Icon(Icons.Default.FavoriteBorder, null, tint = BrandTealPrimary) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            "HOW THE BILL WILL PRINT",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        // Real sample bill built by the same code that drives the
                        // printer, so nothing here can drift from what comes out.
                        val previewText = remember(
                            name, phone, address, receiptHeaderName, receiptHeaderNote,
                            receiptShowAddress, receiptShowPhone, receiptShowDateTime,
                            receiptShowCashier, receiptShowItemCount, receiptReturnNote,
                            receiptFooter, printerWidth
                        ) {
                            CurrencyUtils.buildReceiptText(
                                businessName = name,
                                businessPhone = phone,
                                businessAddress = address,
                                invoiceNumber = "INV-000123",
                                timestamp = System.currentTimeMillis(),
                                cashierName = "Nimal",
                                customerName = "Walk-in",
                                items = listOf(
                                    ReceiptItemData("Sample item one", 2.0, 250.0, 500.0),
                                    ReceiptItemData("Sample item two", 1.0, 180.0, 180.0)
                                ),
                                subtotal = 680.0,
                                discount = 30.0,
                                total = 650.0,
                                paymentMethod = "CASH",
                                cashReceived = 1000.0,
                                change = 350.0,
                                footerMessage = receiptFooter,
                                paperWidth = printerWidth,
                                design = ReceiptDesign(
                                    headerName = receiptHeaderName,
                                    headerNote = receiptHeaderNote,
                                    showAddress = receiptShowAddress,
                                    showPhone = receiptShowPhone,
                                    showDateTime = receiptShowDateTime,
                                    showCashier = receiptShowCashier,
                                    showItemCount = receiptShowItemCount,
                                    returnNote = receiptReturnNote
                                )
                            )
                        }

                        Surface(
                            color = ReceiptPaper,
                            shape = RoundedCornerShape(4.dp),
                            border = CardDefaults.outlinedCardBorder(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = previewText,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                lineHeight = 12.sp,
                                color = ReceiptText,
                                softWrap = false,
                                modifier = Modifier
                                    .horizontalScroll(rememberScrollState())
                                    .padding(10.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Thermal Printer Hardware", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                        Spacer(modifier = Modifier.height(6.dp))

                        Card(
                            colors = CardDefaults.cardColors(containerColor = BrandMintSurface),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.Print, contentDescription = null, tint = BrandTealPrimary, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(printerName, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                                        Text("Bluetooth Thermal Paper: $printerWidth • Ready", fontSize = 11.sp, color = StatusGreen)
                                    }
                                }
                                Button(
                                    onClick = { showTestPrintDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("test_print_btn")
                                ) {
                                    Text("Test Print", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Thermal Paper Width", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(
                                    selected = printerWidth == "58mm",
                                    onClick = { printerWidth = "58mm" },
                                    label = { Text("58 mm (Standard)", fontSize = 11.sp) },
                                    shape = RoundedCornerShape(8.dp)
                                )
                                FilterChip(
                                    selected = printerWidth == "80mm",
                                    onClick = { printerWidth = "80mm" },
                                    label = { Text("80 mm", fontSize = 11.sp) },
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Auto-Print on Checkout", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimary)
                                Text("Send slip to printer immediately when paid", fontSize = 11.sp, color = TextSecondary)
                            }
                            Switch(checked = autoPrint, onCheckedChange = { autoPrint = it })
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = LightBorder)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Digital Receipt QR Code", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimary)
                                Text("Print verifiable digital QR slip for customers", fontSize = 11.sp, color = TextSecondary)
                            }
                            Switch(checked = receiptShowQr, onCheckedChange = { receiptShowQr = it })
                        }
                    }
                }
            }

            // SECTION 3: OPERATIONS & BUSINESS RULES
            if (shouldShowSection(SettingsSection.OPERATIONS, selectedSection, searchQuery, "Stock inventory credit staff manager pin rules discount")) {
                item {
                    SettingsCard(
                        title = "3. OPERATIONS & STORE RULES",
                        subtitle = "Inventory limits, customer credit book & cashier controls"
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Stock & Inventory Tracking", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimary)
                                Text("Automatically decrease stock on completed sales", fontSize = 11.sp, color = TextSecondary)
                            }
                            Switch(checked = trackStock, onCheckedChange = { trackStock = it })
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Customer Credit / Pay Later", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimary)
                                Text("Enable traditional Sri Lankan credit book workflow", fontSize = 11.sp, color = TextSecondary)
                            }
                            Switch(checked = creditEnabled, onCheckedChange = { creditEnabled = it })
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Cash drawer count", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimary)
                                Text(
                                    "Count your float at the start of the day and again when " +
                                        "you close, so the app can tell you if cash is missing. " +
                                        "Leave this off if you just keep money in a box.",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(checked = cashDrawerEnabled, onCheckedChange = { cashDrawerEnabled = it })
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Require Manager PIN for Refunds", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimary)
                                Text("Prevent unauthorized invoice cancellations/voids", fontSize = 11.sp, color = TextSecondary)
                            }
                            Switch(checked = requireManagerPinForRefund, onCheckedChange = { requireManagerPinForRefund = it })
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = maxCashierDiscount,
                                onValueChange = { maxCashierDiscount = it },
                                label = { Text("Max Cashier Discount %") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = defaultOpeningCash,
                                onValueChange = { defaultOpeningCash = it },
                                label = { Text("Default Opening Cash") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = whatsappTemplate,
                            onValueChange = { whatsappTemplate = it },
                            label = { Text("WhatsApp Credit Reminder Template") },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3
                        )
                    }
                }
            }

            // SECTION 4: DATA, OFFLINE & MAINTENANCE
            if (shouldShowSection(SettingsSection.DATA, selectedSection, searchQuery, "Data backup export offline reset demo wizard csv import flush erase clear")) {
                item {
                    SettingsCard(
                        title = "4. DATA & SYSTEM DURABILITY",
                        subtitle = "Offline database sync, export backup & setup wizard"
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = LightSurfaceVariant),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    // Provider only: long-press the offline status
                                    // block to open the hidden provider controls.
                                    onClick = {},
                                    onLongClick = { showProviderCloudDialog = true }
                                )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier.size(10.dp).clip(CircleShape).background(StatusGreen)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("100% Offline-First Architecture Active", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextPrimary)
                                    Text("All sales, customers and stock are stored safely on device.", fontSize = 11.sp, color = TextSecondary)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.backupNow()
                                    showBackupSuccessDialog = true
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).testTag("backup_data_btn")
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Export Backup", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }

                            OutlinedButton(
                                onClick = {
                                    viewModel.setOnboardingStep(1)
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).testTag("rerun_wizard_btn")
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Setup Wizard", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = {
                                    templateLauncher.launch("kadepos-products-template.csv")
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).testTag("download_products_template")
                            ) {
                                Icon(Icons.Default.Description, contentDescription = null, Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Template", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                            OutlinedButton(
                                onClick = {
                                    exportLauncher.launch("kadepos-products.csv")
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).testTag("export_products_csv")
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Products CSV", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                            OutlinedButton(
                                onClick = { importLauncher.launch("text/csv") },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).testTag("import_products_csv")
                            ) {
                                Icon(Icons.Default.Upload, contentDescription = null, Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Import CSV", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "CSV columns: name, sellingPrice, costPrice, barcode, sku, category, subCategory, unit, currentStock, lowStockThreshold, tracked, favourite, variants. Tap Template for a ready-to-fill file, export your current list, or import one from your computer.",
                            fontSize = 10.sp,
                            color = TextSecondary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = { showFlushConfirmDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusRed),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("flush_data_btn")
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Data flush — remove all current data", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = { showResetConfirmDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusAmber),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("reset_demo_btn")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Run the Setup Wizard again", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Bottom Save Button
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        val current = profile ?: BusinessProfileEntity()
                        viewModel.saveBusinessProfile(
                            current.copy(
                                name = name.trim(),
                                businessType = businessType,
                                phone = phone.trim(),
                                address = address.trim(),
                                currencySymbol = currencySymbol,
                                receiptFooter = receiptFooter.trim(),
                                receiptShowQr = receiptShowQr,
                                receiptHeaderName = receiptHeaderName.trim(),
                                receiptHeaderNote = receiptHeaderNote.trim(),
                                receiptShowAddress = receiptShowAddress,
                                receiptShowPhone = receiptShowPhone,
                                receiptShowDateTime = receiptShowDateTime,
                                receiptShowCashier = receiptShowCashier,
                                receiptShowItemCount = receiptShowItemCount,
                                receiptReturnNote = receiptReturnNote.trim(),
                                printerName = printerName,
                                printerPaperWidth = printerWidth,
                                autoPrint = autoPrint,
                                receiptStyle = receiptStyle,
                                trackStock = trackStock,
                                creditEnabled = creditEnabled,
                                cashDrawerEnabled = cashDrawerEnabled,
                                staffEnabled = staffEnabled
                            )
                        )
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("save_settings_bottom_btn")
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("SAVE ALL SETTINGS", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }

    // --- Test Print Dialog ---
    if (showTestPrintDialog) {
        Dialog(onDismissRequest = { showTestPrintDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = LightSurface),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(StatusGreenBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusGreen, modifier = Modifier.size(30.dp))
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Thermal Print Slip Preview", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                    Text("Output to $printerName ($printerWidth)", fontSize = 12.sp, color = TextSecondary)

                    Spacer(modifier = Modifier.height(16.dp))

                    // Slip Paper Container
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ReceiptPaper),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, LightBorder, RoundedCornerShape(8.dp))
                            .padding(4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(name.uppercase(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ReceiptText)
                            Text(address, fontSize = 10.sp, color = ReceiptText)
                            Text(phone, fontSize = 10.sp, color = ReceiptText)
                            Text("--------------------------------", fontSize = 10.sp, color = ReceiptDashed, fontFamily = FontFamily.Monospace)
                            // Preview uses this shop's real items, so what you
                            // see is what the printer will actually produce.
                            previewItems.forEach { previewItem ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(previewItem.name, fontSize = 11.sp, color = ReceiptText)
                                    Text(
                                        CurrencyUtils.formatLkr(previewItem.sellingPrice),
                                        fontSize = 11.sp,
                                        color = ReceiptText,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            if (previewItems.isEmpty()) {
                                Text("Add items to see them here", fontSize = 11.sp, color = ReceiptDashed)
                            }
                            Text("--------------------------------", fontSize = 10.sp, color = ReceiptDashed, fontFamily = FontFamily.Monospace)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("TOTAL PAID (CASH)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ReceiptText)
                                Text(CurrencyUtils.formatLkr(previewTotal), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BrandTealPrimary)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(receiptFooter, fontSize = 9.sp, color = ReceiptText)
                            if (receiptShowQr) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("[QR CODE VERIFIED]", fontSize = 8.sp, color = ReceiptDashed, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))
                    Button(
                        onClick = { showTestPrintDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Looks good", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // --- Export Backup Success Dialog ---
    if (showBackupSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showBackupSuccessDialog = false },
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusGreen) },
            title = { Text("Backup Created", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Store database export complete.\n\nFile: ${name.lowercase().replace(" ", "_")}_backup_${System.currentTimeMillis()}.json\n\nContains all sales, customers, stock levels, suppliers, and cashier logs safely."
                )
            },
            confirmButton = {
                Button(
                    onClick = { showBackupSuccessDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary)
                ) {
                    Text("OK")
                }
            }
        )
    }

    // --- Setup Wizard Confirm Dialog ---
    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            icon = { Icon(Icons.Default.Refresh, contentDescription = null, tint = StatusAmber) },
            title = { Text("Run the setup wizard again?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This opens the setup screens. Nothing is deleted just by running it — use “Data flush” below if you also want to remove all current data."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirmDialog = false
                        viewModel.setOnboardingStep(1)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary)
                ) {
                    Text("Open wizard", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // --- Data flush Confirm Dialog ---
    if (showFlushConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showFlushConfirmDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = StatusRed) },
            title = { Text("Erase all app data?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This permanently removes every product, sale, customer, supplier, credit entry, stock movement, expense, team member and audit log from this phone. The app is then prepared for setup again.\n\nThere is no undo."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showFlushConfirmDialog = false
                        viewModel.clearAllBusinessData()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusRed)
                ) {
                    Text("Yes, erase everything", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFlushConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // --- Provider-only cloud controls (hidden in normal navigation) ---
    if (showProviderCloudDialog) {
        ProviderCloudScreen(
            viewModel = viewModel,
            onDismiss = { showProviderCloudDialog = false }
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = LightSurface),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BrandTealPrimary)
            Text(subtitle, fontSize = 11.sp, color = TextSecondary)
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}

private fun shouldShowSection(
    section: SettingsSection,
    selectedSection: SettingsSection,
    query: String,
    keywords: String
): Boolean {
    if (selectedSection != SettingsSection.ALL && selectedSection != section) return false
    if (query.isBlank()) return true
    return keywords.contains(query, ignoreCase = true) || section.title.contains(query, ignoreCase = true)
}

/** One on/off line in the bill designer. */
@Composable
private fun ReceiptToggle(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
            if (subtitle != null) {
                Text(subtitle, fontSize = 10.sp, color = TextSecondary)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
