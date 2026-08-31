package com.example.ui.screens.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BusinessProfileEntity
import com.example.ui.theme.*
import com.example.ui.util.CurrencyUtils

@Composable
fun OnboardingFlow(
    profile: BusinessProfileEntity,
    currentStep: Int,
    onStepChange: (Int) -> Unit,
    onFinishOnboarding: (BusinessProfileEntity) -> Unit,
    onPreloadProducts: ((String) -> Unit)? = null,
    onTestPrint: ((String) -> Unit)? = null
) {
    var businessName by remember { mutableStateOf(profile.name.ifBlank { "" }) }
    var businessType by remember { mutableStateOf(profile.businessType.ifBlank { "Retail" }) }
    var phoneNumber by remember { mutableStateOf(profile.phone.ifBlank { "" }) }
    var businessAddress by remember { mutableStateOf(profile.address.ifBlank { "" }) }
    var ownerName by remember { mutableStateOf(profile.activeStaffName.ifBlank { "Manager" }) }
    var selectedShopPreset by remember { mutableStateOf("RETAIL_GROCERY") }
    var shouldPreloadCatalog by remember { mutableStateOf(true) }
    var managementLevel by remember { mutableStateOf(profile.managementLevel) }
    var trackStock by remember { mutableStateOf(profile.trackStock) }
    var creditMode by remember { mutableStateOf("YES") }
    var staffMode by remember { mutableStateOf("ME_STAFF") }
    var adminPin by remember { mutableStateOf("1234") }
    var printerConnected by remember { mutableStateOf(true) }
    var printerPaperWidth by remember { mutableStateOf("58mm") }
    var receiptStyle by remember { mutableStateOf("Modern") }
    var receiptFooter by remember { mutableStateOf(profile.receiptFooter) }
    var showQr by remember { mutableStateOf(true) }

    AnimatedContent(
        targetState = currentStep,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "OnboardingTransition"
    ) { step ->
        when (step) {
            1 -> WelcomeScreen(
                onGetStarted = { onStepChange(2) },
                onSkip = {
                    onStepChange(2)
                }
            )
            2 -> Screen02BusinessInfo(
                businessName = businessName,
                onBusinessNameChange = { businessName = it },
                businessType = businessType,
                onBusinessTypeChange = { businessType = it },
                phoneNumber = phoneNumber,
                onPhoneNumberChange = { phoneNumber = it },
                businessAddress = businessAddress,
                onBusinessAddressChange = { businessAddress = it },
                ownerName = ownerName,
                onOwnerNameChange = { ownerName = it },
                onBack = { onStepChange(1) },
                onContinue = { onStepChange(3) }
            )
            3 -> Screen03ShopTypeAndCatalog(
                selectedPresetKey = selectedShopPreset,
                onSelectPreset = { selectedShopPreset = it },
                shouldPreload = shouldPreloadCatalog,
                onTogglePreload = { shouldPreloadCatalog = it },
                onBack = { onStepChange(2) },
                onContinue = {
                    if (shouldPreloadCatalog && onPreloadProducts != null) {
                        onPreloadProducts(selectedShopPreset)
                    }
                    onStepChange(4)
                }
            )
            4 -> Screen04HowDoYouBill(
                selectedLevel = managementLevel,
                onSelectLevel = { managementLevel = it },
                onBack = { onStepChange(3) },
                onContinue = { onStepChange(5) }
            )
            5 -> Screen05StockTracking(
                trackStock = trackStock,
                onTrackStockChange = { trackStock = it },
                onBack = { onStepChange(4) },
                onContinue = { onStepChange(6) }
            )
            6 -> Screen06CustomersAndCredit(
                creditMode = creditMode,
                onCreditModeChange = { creditMode = it },
                onBack = { onStepChange(5) },
                onContinue = { onStepChange(7) }
            )
            7 -> Screen07StaffAndTeam(
                staffMode = staffMode,
                onStaffModeChange = { staffMode = it },
                adminPin = adminPin,
                onAdminPinChange = { adminPin = it },
                onBack = { onStepChange(6) },
                onContinue = { onStepChange(8) }
            )
            8 -> Screen08PrinterSetup(
                printerConnected = printerConnected,
                onPrinterToggle = { printerConnected = it },
                paperWidth = printerPaperWidth,
                onPaperWidthChange = { printerPaperWidth = it },
                onTestPrint = { width ->
                    if (onTestPrint != null) {
                        onTestPrint(width)
                    }
                },
                onBack = { onStepChange(7) },
                onContinue = { onStepChange(9) }
            )
            9 -> Screen09ReceiptSetup(
                businessName = businessName.ifBlank { "My Business" },
                phoneNumber = phoneNumber,
                address = businessAddress,
                footerMessage = receiptFooter,
                onFooterChange = { receiptFooter = it },
                showQr = showQr,
                onShowQrChange = { showQr = it },
                paperWidth = printerPaperWidth,
                onBack = { onStepChange(8) },
                onContinue = { onStepChange(10) }
            )
            10 -> Screen10Ready(
                businessName = businessName.ifBlank { "My Business" },
                trackStock = trackStock,
                printerConnected = printerConnected,
                creditMode = creditMode,
                onStartSelling = {
                    onFinishOnboarding(
                        profile.copy(
                            name = businessName.ifBlank { "My Business" },
                            businessType = businessType,
                            phone = phoneNumber,
                            address = businessAddress.ifBlank { "Main Street, Colombo" },
                            activeStaffName = ownerName.ifBlank { "Owner" },
                            managementLevel = managementLevel,
                            trackStock = trackStock,
                            creditEnabled = creditMode != "NO",
                            staffEnabled = staffMode != "JUST_ME",
                            printerConnected = printerConnected,
                            printerPaperWidth = printerPaperWidth,
                            receiptFooter = receiptFooter,
                            receiptShowQr = showQr,
                            isConfigured = true
                        )
                    )
                }
            )
            else -> WelcomeScreen(
                onGetStarted = { onStepChange(2) },
                onSkip = {
                    onStepChange(2)
                }
            )
        }
    }
}

// ----------------------------------------------------
// Screen 01: Welcome
// ----------------------------------------------------
@Composable
fun WelcomeScreen(
    onGetStarted: () -> Unit,
    onSkip: () -> Unit
) {
    Scaffold(
        containerColor = LightBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                // Logo Emblem
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(BrandTealPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PointOfSale,
                        contentDescription = "App Logo",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Sell. Print.\nDone.",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp,
                        lineHeight = 40.sp
                    ),
                    textAlign = TextAlign.Center,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Simple billing for your business — even when you're offline.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Floating POS preview illustration
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = LightSurface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "NEW SALE",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = BrandTealPrimary
                            )
                            Badge(containerColor = StatusGreenBg) {
                                Text("OFFLINE READY", color = StatusGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Coca Cola 500ml", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                            Text("Rs. 240", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Prima Bread", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                            Text("Rs. 180", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Fresh Milk 1L", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                            Text("Rs. 320", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = LightBorder)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("TOTAL", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "Rs. 740.00",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = BrandTealPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Three core benefits
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BenefitRow(icon = Icons.Default.ReceiptLong, title = "Bills", desc = "Create invoices in seconds")
                    BenefitRow(icon = Icons.Default.Print, title = "Print", desc = "Connect portable Bluetooth printers")
                    BenefitRow(icon = Icons.Default.CloudOff, title = "Offline", desc = "Keep selling with zero internet")
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = onGetStarted,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("get_started_button")
                ) {
                    Text(
                        "Get Started",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(
                    onClick = onSkip,
                    modifier = Modifier.testTag("already_setup_button")
                ) {
                    Text("Already set up? Enter App", color = TextSecondary)
                }
            }
        }
    }
}

@Composable
fun BenefitRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, desc: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(BrandMintSurface),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = BrandTealPrimary, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(text = title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
            Text(text = desc, fontSize = 12.sp, color = TextSecondary)
        }
    }
}

// ----------------------------------------------------
// Screen 02: Business Info
// ----------------------------------------------------
@Composable
fun Screen02BusinessInfo(
    businessName: String,
    onBusinessNameChange: (String) -> Unit,
    businessType: String,
    onBusinessTypeChange: (String) -> Unit,
    phoneNumber: String,
    onPhoneNumberChange: (String) -> Unit,
    businessAddress: String,
    onBusinessAddressChange: (String) -> Unit,
    ownerName: String,
    onOwnerNameChange: (String) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    var hasAttemptedContinue by remember { mutableStateOf(false) }
    val isNameValid = businessName.trim().length >= 2
    val isPhoneValid = phoneNumber.trim().filter { it.isDigit() }.length >= 7
    val isAddressValid = businessAddress.trim().length >= 3
    val isOwnerValid = ownerName.trim().isNotBlank()
    val isFormValid = isNameValid && isPhoneValid && isAddressValid && isOwnerValid

    Scaffold(
        topBar = {
            TopAppBarOnboarding(step = 2, totalSteps = 10, onBack = onBack)
        },
        containerColor = LightBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Let's set up your business",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                Text(
                    text = "Please enter your business details to customize invoices, receipts, and reports.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
                )

                Text("BUSINESS NAME *", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = businessName,
                    onValueChange = onBusinessNameChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("business_name_input"),
                    shape = RoundedCornerShape(14.dp),
                    textStyle = TextStyle(color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = BrandTealPrimary,
                        unfocusedBorderColor = LightBorder,
                        focusedLabelColor = BrandTealPrimary,
                        unfocusedLabelColor = TextSecondary,
                        cursorColor = BrandTealPrimary
                    ),
                    placeholder = { Text("e.g. ABC Super Stores / City Cafe", color = TextMuted) },
                    isError = hasAttemptedContinue && !isNameValid,
                    supportingText = {
                        if (hasAttemptedContinue && !isNameValid) {
                            Text("Please enter a business name (min 2 letters)", color = StatusRed)
                        }
                    },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text("BUSINESS TYPE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TextSecondary)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SelectionCard(
                        modifier = Modifier.weight(1f),
                        title = "Retail",
                        subtitle = "Products & stock",
                        icon = Icons.Default.ShoppingBag,
                        isSelected = businessType == "Retail",
                        onClick = { onBusinessTypeChange("Retail") }
                    )
                    SelectionCard(
                        modifier = Modifier.weight(1f),
                        title = "Service",
                        subtitle = "Services & hourly",
                        icon = Icons.Default.Build,
                        isSelected = businessType == "Service",
                        onClick = { onBusinessTypeChange("Service") }
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                SelectionCard(
                    modifier = Modifier.fillMaxWidth(),
                    title = "Both (Products + Services)",
                    subtitle = "Grocery, repair shop, salon with retail items",
                    icon = Icons.Default.Storefront,
                    isSelected = businessType == "Both",
                    onClick = { onBusinessTypeChange("Both") }
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text("CONTACT PHONE NUMBER *", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = onPhoneNumberChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("contact_phone_input"),
                    shape = RoundedCornerShape(14.dp),
                    textStyle = TextStyle(color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = BrandTealPrimary,
                        unfocusedBorderColor = LightBorder,
                        focusedLabelColor = BrandTealPrimary,
                        unfocusedLabelColor = TextSecondary,
                        cursorColor = BrandTealPrimary
                    ),
                    placeholder = { Text("077 123 4567", color = TextMuted) },
                    leadingIcon = {
                        Text("🇱🇰 +94", fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp), color = TextPrimary)
                    },
                    isError = hasAttemptedContinue && !isPhoneValid,
                    supportingText = {
                        if (hasAttemptedContinue && !isPhoneValid) {
                            Text("Valid phone number required (min 7 digits)", color = StatusRed)
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text("BUSINESS ADDRESS / LOCATION *", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = businessAddress,
                    onValueChange = onBusinessAddressChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("business_address_input"),
                    shape = RoundedCornerShape(14.dp),
                    textStyle = TextStyle(color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = BrandTealPrimary,
                        unfocusedBorderColor = LightBorder,
                        focusedLabelColor = BrandTealPrimary,
                        unfocusedLabelColor = TextSecondary,
                        cursorColor = BrandTealPrimary
                    ),
                    placeholder = { Text("e.g. 120 Galle Road, Colombo 03", color = TextMuted) },
                    leadingIcon = {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = BrandTealPrimary)
                    },
                    isError = hasAttemptedContinue && !isAddressValid,
                    supportingText = {
                        if (hasAttemptedContinue && !isAddressValid) {
                            Text("Business address is required for receipts", color = StatusRed)
                        }
                    },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text("OWNER / CASHIER NAME *", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = ownerName,
                    onValueChange = onOwnerNameChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("owner_name_input"),
                    shape = RoundedCornerShape(14.dp),
                    textStyle = TextStyle(color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = BrandTealPrimary,
                        unfocusedBorderColor = LightBorder,
                        focusedLabelColor = BrandTealPrimary,
                        unfocusedLabelColor = TextSecondary,
                        cursorColor = BrandTealPrimary
                    ),
                    placeholder = { Text("e.g. Kasun / Manager", color = TextMuted) },
                    leadingIcon = {
                        Icon(Icons.Default.Person, contentDescription = null, tint = BrandTealPrimary)
                    },
                    isError = hasAttemptedContinue && !isOwnerValid,
                    supportingText = {
                        if (hasAttemptedContinue && !isOwnerValid) {
                            Text("Owner or Cashier name is required", color = StatusRed)
                        }
                    },
                    singleLine = true
                )
            }

            Button(
                onClick = {
                    hasAttemptedContinue = true
                    if (isFormValid) {
                        onContinue()
                    }
                },
                enabled = isFormValid,
                colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
                    .height(54.dp)
                    .testTag("continue_step2_button")
            ) {
                Text("Continue", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }
        }
    }
}

// ----------------------------------------------------
// Screen 03: Shop Type & Preload Products
// ----------------------------------------------------
@Composable
fun Screen03ShopTypeAndCatalog(
    selectedPresetKey: String,
    onSelectPreset: (String) -> Unit,
    shouldPreload: Boolean,
    onTogglePreload: (Boolean) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    val presets = listOf(
        Triple("RETAIL_GROCERY", "Grocery & Supermarket", "Rice, sugar, tea, milk, biscuits, soaps, snacks"),
        Triple("FOOD_CAFE", "Restaurant, Cafe & Bakery", "Kottu, fried rice, milk tea, rolls, burgers, coffee"),
        Triple("PHARMACY", "Pharmacy & Healthcare", "Paracetamol, multivitamins, masks, bandages, sanitizer"),
        Triple("CLOTHING", "Clothing & Fashion Boutique", "T-shirts, denim jeans, dresses, shirts, accessories"),
        Triple("ELECTRONICS", "Electronics & Hardware", "USB cables, wall chargers, ear buds, LED bulbs, adapters"),
        Triple("GENERAL", "General Retail & Stationery", "Ballpoint pens, notebooks, tissues, water bottles")
    )

    Scaffold(
        topBar = { TopAppBarOnboarding(step = 3, totalSteps = 10, onBack = onBack) },
        containerColor = LightBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "What type of shop do you run?",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                Text(
                    text = "Select your shop category to quickly setup your product catalog.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                // Preload question card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (shouldPreload) BrandMintSurface else LightSurfaceVariant
                    ),
                    border = CardDefaults.outlinedCardBorder(),
                    modifier = Modifier.fillMaxWidth().clickable { onTogglePreload(!shouldPreload) }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = shouldPreload,
                            onCheckedChange = { onTogglePreload(it) },
                            colors = CheckboxDefaults.colors(checkedColor = BrandTealPrimary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                "Add common starter items for my shop",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = TextPrimary
                            )
                            Text(
                                "Preloads popular items with selling prices so you can start billing immediately.",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("CHOOSE YOUR SHOP CATEGORY", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TextSecondary)
                Spacer(modifier = Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    presets.forEach { (key, title, sampleItems) ->
                        val isSelected = selectedPresetKey == key
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) LightSurface else LightSurface
                            ),
                            border = if (isSelected) {
                                androidx.compose.foundation.BorderStroke(2.dp, BrandTealPrimary)
                            } else {
                                CardDefaults.outlinedCardBorder()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectPreset(key) }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = if (isSelected) BrandTealPrimary else TextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = sampleItems,
                                        fontSize = 12.sp,
                                        color = TextSecondary
                                    )
                                }
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { onSelectPreset(key) },
                                    colors = RadioButtonDefaults.colors(selectedColor = BrandTealPrimary)
                                )
                            }
                        }
                    }
                }
            }

            Button(
                onClick = onContinue,
                colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
                    .height(54.dp)
                    .testTag("continue_step3_button")
            ) {
                Text("Continue", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }
        }
    }
}

// ----------------------------------------------------
// Screen 04: How Do You Want to Bill?
// ----------------------------------------------------
@Composable
fun Screen04HowDoYouBill(
    selectedLevel: String,
    onSelectLevel: (String) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    Scaffold(
        topBar = { TopAppBarOnboarding(step = 4, totalSteps = 10, onBack = onBack) },
        containerColor = LightBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "How much do you want to manage?",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                Text(
                    text = "You can always change this later in settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
                )

                ManagementOptionCard(
                    title = "JUST BILL",
                    subtitle = "Create bills quickly and keep selling.",
                    benefits = listOf("Fast billing", "Thermal receipts", "Sales history"),
                    isSelected = selectedLevel == "JUST_BILL",
                    onClick = { onSelectLevel("JUST_BILL") }
                )

                Spacer(modifier = Modifier.height(12.dp))

                ManagementOptionCard(
                    title = "BILL + STOCK",
                    subtitle = "Know what you have in your shop.",
                    benefits = listOf("Everything above", "Stock tracking", "Low-stock alerts"),
                    isSelected = selectedLevel == "BILL_STOCK",
                    onClick = { onSelectLevel("BILL_STOCK") }
                )

                Spacer(modifier = Modifier.height(12.dp))

                ManagementOptionCard(
                    title = "MANAGE BUSINESS",
                    subtitle = "Everything in one place.",
                    benefits = listOf("Stock & purchases", "Customers & Sri Lankan credit book", "Staff shifts & profit reports"),
                    isSelected = selectedLevel == "MANAGE_BUSINESS",
                    onClick = { onSelectLevel("MANAGE_BUSINESS") }
                )
            }

            Button(
                onClick = onContinue,
                colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
                    .height(54.dp)
            ) {
                Text("Continue", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }
        }
    }
}

// ----------------------------------------------------
// Screen 05: Stock Tracking
// ----------------------------------------------------
@Composable
fun Screen05StockTracking(
    trackStock: Boolean,
    onTrackStockChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    Scaffold(
        topBar = { TopAppBarOnboarding(step = 5, totalSteps = 10, onBack = onBack) },
        containerColor = LightBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Do you keep track of your stock?",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                Text(
                    text = "Don't worry — barcodes are optional, and you can change this anytime.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
                )

                ManagementOptionCard(
                    title = "YES, TRACK STOCK",
                    subtitle = "Know what's in your shop at any time.",
                    benefits = listOf("Low-stock alerts", "Stock history audit trail", "Purchase tracking"),
                    isSelected = trackStock,
                    onClick = { onTrackStockChange(true) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                ManagementOptionCard(
                    title = "NO, JUST BILL",
                    subtitle = "Keep things simple. Sell without tracking counts.",
                    benefits = listOf("No inventory hassle", "Quick items & custom pricing anytime"),
                    isSelected = !trackStock,
                    onClick = { onTrackStockChange(false) }
                )
            }

            Button(
                onClick = onContinue,
                colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
                    .height(54.dp)
            ) {
                Text("Continue", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }
        }
    }
}

// ----------------------------------------------------
// Screen 06: Customers & Credit
// ----------------------------------------------------
@Composable
fun Screen06CustomersAndCredit(
    creditMode: String,
    onCreditModeChange: (String) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    Scaffold(
        topBar = { TopAppBarOnboarding(step = 6, totalSteps = 10, onBack = onBack) },
        containerColor = LightBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Do customers sometimes pay later?",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                Text(
                    text = "Keep your credit book (kasippu / tab) right inside the app.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
                )

                ManagementOptionCard(
                    title = "YES, I USE CREDIT",
                    subtitle = "Keep track of who owes you and when they pay.",
                    benefits = listOf("Customer balances & running ledger", "Payment history & partial settlements", "1-Tap WhatsApp reminders"),
                    isSelected = creditMode == "YES",
                    onClick = { onCreditModeChange("YES") }
                )

                Spacer(modifier = Modifier.height(12.dp))

                ManagementOptionCard(
                    title = "SOMETIMES",
                    subtitle = "Only for special regular customers.",
                    benefits = listOf("Available at checkout when needed", "Hidden from main screen to keep it clean"),
                    isSelected = creditMode == "SOMETIMES",
                    onClick = { onCreditModeChange("SOMETIMES") }
                )

                Spacer(modifier = Modifier.height(12.dp))

                ManagementOptionCard(
                    title = "NO, I DON'T",
                    subtitle = "Customers pay at the time of sale.",
                    benefits = listOf("Cash & Card checkout only"),
                    isSelected = creditMode == "NO",
                    onClick = { onCreditModeChange("NO") }
                )
            }

            Button(
                onClick = onContinue,
                colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
                    .height(54.dp)
            ) {
                Text("Continue", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }
        }
    }
}

// ----------------------------------------------------
// Screen 07: Staff & Team
// ----------------------------------------------------
@Composable
fun Screen07StaffAndTeam(
    staffMode: String,
    onStaffModeChange: (String) -> Unit,
    adminPin: String,
    onAdminPinChange: (String) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    val isPinValid = staffMode == "JUST_ME" || adminPin.trim().length >= 4

    Scaffold(
        topBar = { TopAppBarOnboarding(step = 7, totalSteps = 10, onBack = onBack) },
        containerColor = LightBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Will anyone else use this app?",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                Text(
                    text = "You can add or remove cashiers anytime.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
                )

                ManagementOptionCard(
                    title = "JUST ME",
                    subtitle = "I'm the only one using the system.",
                    benefits = listOf("No login PIN required", "Direct instant billing"),
                    isSelected = staffMode == "JUST_ME",
                    onClick = { onStaffModeChange("JUST_ME") }
                )

                Spacer(modifier = Modifier.height(12.dp))

                ManagementOptionCard(
                    title = "ME + STAFF",
                    subtitle = "A few people help me run the shop.",
                    benefits = listOf("Fast 4-digit PIN switch", "Cashier sales tracking", "Safe permissions"),
                    isSelected = staffMode == "ME_STAFF",
                    onClick = { onStaffModeChange("ME_STAFF") }
                )

                Spacer(modifier = Modifier.height(12.dp))

                ManagementOptionCard(
                    title = "I HAVE A TEAM",
                    subtitle = "Multiple shifts and managers.",
                    benefits = listOf("Shift cash handovers", "Audit logs & manager approvals"),
                    isSelected = staffMode == "TEAM",
                    onClick = { onStaffModeChange("TEAM") }
                )

                if (staffMode != "JUST_ME") {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "SET ADMIN / OWNER MASTER PIN (4 DIGITS) *",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = adminPin,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) onAdminPinChange(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_pin_input"),
                        shape = RoundedCornerShape(14.dp),
                        textStyle = TextStyle(color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = BrandTealPrimary,
                            unfocusedBorderColor = LightBorder,
                            focusedLabelColor = BrandTealPrimary,
                            unfocusedLabelColor = TextSecondary,
                            cursorColor = BrandTealPrimary
                        ),
                        placeholder = { Text("e.g. 1234", color = TextMuted) },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = BrandTealPrimary)
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        supportingText = {
                            if (!isPinValid) {
                                Text("Please enter at least 4 digits", color = StatusRed)
                            }
                        }
                    )
                }
            }

            Button(
                onClick = onContinue,
                enabled = isPinValid,
                colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
                    .height(54.dp)
                    .testTag("continue_step7_button")
            ) {
                Text("Continue", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }
        }
    }
}

// ----------------------------------------------------
// Screen 08: Printer Setup
// ----------------------------------------------------
@Composable
fun Screen08PrinterSetup(
    printerConnected: Boolean,
    onPrinterToggle: (Boolean) -> Unit,
    paperWidth: String,
    onPaperWidthChange: (String) -> Unit,
    onTestPrint: ((String) -> Unit)? = null,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    var showTestPrintDone by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBarOnboarding(step = 8, totalSteps = 10, onBack = onBack) },
        containerColor = LightBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Let's connect your printer",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                Text(
                    text = "Print receipts directly from your phone or tablet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
                )

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = LightSurface),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Print, contentDescription = null, tint = BrandTealPrimary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Thermal POS Printer", fontWeight = FontWeight.Bold, color = TextPrimary)
                                    Text("Bluetooth 58mm / 80mm ESC/POS", fontSize = 12.sp, color = StatusGreen)
                                }
                            }
                            Switch(
                                checked = printerConnected,
                                onCheckedChange = onPrinterToggle
                            )
                        }

                        if (printerConnected) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = LightBorder)
                            Text("RECEIPT PAPER SIZE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TextSecondary)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                FilterChip(
                                    selected = paperWidth == "58mm",
                                    onClick = { onPaperWidthChange("58mm") },
                                    label = { Text("58mm (Portable)") }
                                )
                                FilterChip(
                                    selected = paperWidth == "80mm",
                                    onClick = { onPaperWidthChange("80mm") },
                                    label = { Text("80mm (Desktop)") }
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = {
                                    showTestPrintDone = true
                                    onTestPrint?.invoke(paperWidth)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Test Print Sample")
                            }

                            if (showTestPrintDone) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "✓ Test receipt sent to printer successfully!",
                                    color = StatusGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            Column {
                Button(
                    onClick = onContinue,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                ) {
                    Text("Continue", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                }

                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Skip printer for now", color = TextSecondary)
                }
            }
        }
    }
}

// ----------------------------------------------------
// Screen 09: Receipt Setup
// ----------------------------------------------------
@Composable
fun Screen09ReceiptSetup(
    businessName: String,
    phoneNumber: String,
    address: String,
    footerMessage: String,
    onFooterChange: (String) -> Unit,
    showQr: Boolean,
    onShowQrChange: (Boolean) -> Unit,
    paperWidth: String,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    Scaffold(
        topBar = { TopAppBarOnboarding(step = 9, totalSteps = 10, onBack = onBack) },
        containerColor = LightBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Make your receipt yours.",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                Text(
                    text = "Live preview of what prints on the physical receipt.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                // Live receipt preview card
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = ReceiptPaper),
                    border = CardDefaults.outlinedCardBorder(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(businessName.uppercase(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ReceiptText)
                        if (address.isNotBlank()) {
                            Text(address, fontSize = 11.sp, color = ReceiptText)
                        }
                        Text("Tel: $phoneNumber", fontSize = 11.sp, color = ReceiptText)

                        Text("--------------------------------", fontFamily = FontFamily.Monospace, color = ReceiptDashed, modifier = Modifier.padding(vertical = 4.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Coca Cola 500ml", fontSize = 12.sp, color = ReceiptText)
                            Text("240.00", fontSize = 12.sp, color = ReceiptText)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Prima Bread", fontSize = 12.sp, color = ReceiptText)
                            Text("180.00", fontSize = 12.sp, color = ReceiptText)
                        }

                        Text("--------------------------------", fontFamily = FontFamily.Monospace, color = ReceiptDashed, modifier = Modifier.padding(vertical = 4.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("TOTAL", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ReceiptText)
                            Text("Rs. 420.00", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ReceiptText)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("PAID (CASH)", fontSize = 11.sp, color = ReceiptText)
                            Text("500.00", fontSize = 11.sp, color = ReceiptText)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("CHANGE", fontSize = 11.sp, color = ReceiptText)
                            Text("80.00", fontSize = 11.sp, color = ReceiptText)
                        }

                        Text("--------------------------------", fontFamily = FontFamily.Monospace, color = ReceiptDashed, modifier = Modifier.padding(vertical = 4.dp))
                        Text(footerMessage, fontSize = 11.sp, color = ReceiptText, textAlign = TextAlign.Center)

                        if (showQr) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Icon(Icons.Default.QrCode2, contentDescription = null, tint = ReceiptText, modifier = Modifier.size(32.dp))
                            Text("Scan for digital bill", fontSize = 9.sp, color = ReceiptText)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("RECEIPT FOOTER MESSAGE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = footerMessage,
                    onValueChange = onFooterChange,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = BrandTealPrimary,
                        unfocusedBorderColor = LightBorder,
                        focusedLabelColor = BrandTealPrimary,
                        unfocusedLabelColor = TextSecondary,
                        cursorColor = BrandTealPrimary
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Digital receipt QR code", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Switch(checked = showQr, onCheckedChange = onShowQrChange)
                }
            }

            Button(
                onClick = onContinue,
                colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp)
                    .height(54.dp)
            ) {
                Text("Continue", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }
        }
    }
}

// ----------------------------------------------------
// Screen 10: You're Ready
// ----------------------------------------------------
@Composable
fun Screen10Ready(
    businessName: String,
    trackStock: Boolean,
    printerConnected: Boolean,
    creditMode: String,
    onStartSelling: () -> Unit
) {
    Scaffold(
        containerColor = LightBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(StatusGreenBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Success",
                        tint = StatusGreen,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "You're ready.",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = TextPrimary
                )
                Text(
                    text = "$businessName is set up.",
                    style = MaterialTheme.typography.titleMedium,
                    color = BrandTealPrimary,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(28.dp))

                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = LightSurface),
                    border = CardDefaults.outlinedCardBorder(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        SummaryRow(label = "BUSINESS", value = businessName, isOk = true)
                        SummaryRow(label = "BILLING", value = "Fast billing enabled", isOk = true)
                        SummaryRow(label = "STOCK", value = if (trackStock) "Stock tracking enabled" else "Simple billing mode", isOk = true)
                        SummaryRow(label = "CREDIT BOOK", value = if (creditMode != "NO") "Customer credit enabled" else "Cash/Card mode", isOk = true)
                        SummaryRow(label = "PRINTER", value = if (printerConnected) "MTP-58 ready" else "Digital receipts mode", isOk = true)
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Everything is ready.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onStartSelling,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("start_selling_button")
                ) {
                    Text(
                        "START SELLING",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                }
            }
        }
    }
}

@Composable
fun SummaryRow(label: String, value: String, isOk: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        }
        Icon(
            imageVector = if (isOk) Icons.Default.CheckCircle else Icons.Default.Cancel,
            contentDescription = null,
            tint = if (isOk) StatusGreen else StatusAmber,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun TopAppBarOnboarding(step: Int, totalSteps: Int, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(totalSteps) { idx ->
                Box(
                    modifier = Modifier
                        .size(if (idx + 1 == step) 10.dp else 6.dp)
                        .clip(CircleShape)
                        .background(if (idx + 1 <= step) BrandTealPrimary else LightBorder)
                )
            }
        }

        Text(
            "$step of $totalSteps",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary
        )
    }
}

@Composable
fun SelectionCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) BrandTealPrimary else LightBorder,
                shape = RoundedCornerShape(16.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) BrandMintSurface else LightSurface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) BrandTealPrimary else TextSecondary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
            Text(subtitle, fontSize = 12.sp, color = TextSecondary)
        }
    }
}

@Composable
fun MultiSelectCard(
    modifier: Modifier = Modifier,
    title: String,
    desc: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) BrandTealPrimary else LightBorder,
                shape = RoundedCornerShape(16.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) BrandMintSurface else LightSurface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) BrandTealPrimary else TextSecondary,
                    modifier = Modifier.size(24.dp)
                )
                if (isSelected) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = BrandTealPrimary, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
            Text(desc, fontSize = 12.sp, color = TextSecondary)
        }
    }
}

@Composable
fun ManagementOptionCard(
    title: String,
    subtitle: String,
    benefits: List<String>,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) BrandTealPrimary else LightBorder,
                shape = RoundedCornerShape(16.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) BrandMintSurface else LightSurface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = if (isSelected) BrandTealPrimary else TextPrimary)
                RadioButton(selected = isSelected, onClick = onClick)
            }
            Text(subtitle, fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 8.dp))

            benefits.forEach { benefit ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = if (isSelected) BrandTealPrimary else TextSecondary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(benefit, fontSize = 12.sp, color = TextPrimary)
                }
            }
        }
    }
}
