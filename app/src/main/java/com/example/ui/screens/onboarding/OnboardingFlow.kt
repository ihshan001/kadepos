package com.example.ui.screens.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BusinessProfileEntity
import com.example.data.model.ProductCatalogPresets
import com.example.ui.components.ChoiceCard
import com.example.ui.components.HintCard
import com.example.ui.components.HintTone
import com.example.ui.components.PrimaryActionButton
import com.example.ui.theme.*

/**
 * The setup wizard.
 *
 * Two rules run through the whole flow:
 *  1. Nothing is assumed. Every answer comes from the shopkeeper.
 *  2. The Continue button stays disabled, with the reason shown underneath,
 *     until the current step is genuinely complete.
 */

private const val TOTAL_STEPS = 7

/** Everything the wizard collects, kept in one place so steps stay simple. */
data class SetupDraft(
    val businessName: String = "",
    val ownerName: String = "",
    val phone: String = "",
    val address: String = "",
    val shopTypeKey: String = "",
    val loadStarterItems: Boolean = true,
    val trackStock: Boolean? = null,
    val creditEnabled: Boolean? = null,
    val cashDrawerEnabled: Boolean? = null,
    val hasStaff: Boolean? = null,
    val ownerPin: String = "",
    val ownerPinConfirm: String = "",
    val usesPrinter: Boolean? = null,
    val paperWidth: String = "58mm",
    val language: String = "English",
    val receiptFooter: String = "Thank you! Please come again."
)

@Composable
fun OnboardingFlow(
    profile: BusinessProfileEntity,
    currentStep: Int,
    onStepChange: (Int) -> Unit,
    onFinish: (BusinessProfileEntity, SetupDraft) -> Unit,
    onOpenPrinterSetup: () -> Unit = {}
) {
    var draft by rememberSaveable(stateSaver = SetupDraftSaver) {
        mutableStateOf(
            SetupDraft(
                businessName = profile.name,
                ownerName = profile.activeStaffName,
                phone = profile.phone,
                address = profile.address,
                shopTypeKey = profile.shopTypeKey
            )
        )
    }

    AnimatedContent(
        targetState = currentStep,
        transitionSpec = {
            (fadeIn(tween(180)) togetherWith fadeOut(tween(140)))
        },
        label = "setup"
    ) { step ->
        when (step) {
            1 -> WelcomeStep(onStart = { onStepChange(2) })

            2 -> ShopDetailsStep(
                draft = draft,
                onChange = { draft = it },
                onBack = { onStepChange(1) },
                onNext = { onStepChange(3) }
            )

            3 -> ShopTypeStep(
                draft = draft,
                onChange = { draft = it },
                onBack = { onStepChange(2) },
                onNext = { onStepChange(4) }
            )

            4 -> HowYouWorkStep(
                draft = draft,
                onChange = { draft = it },
                onBack = { onStepChange(3) },
                onNext = { onStepChange(5) }
            )

            5 -> TeamStep(
                draft = draft,
                onChange = { draft = it },
                onBack = { onStepChange(4) },
                onNext = { onStepChange(6) }
            )

            6 -> PrinterStep(
                draft = draft,
                onChange = { draft = it },
                onBack = { onStepChange(5) },
                onNext = { onStepChange(7) },
                onOpenPrinterSetup = onOpenPrinterSetup
            )

            7 -> ReadyStep(
                draft = draft,
                onBack = { onStepChange(6) },
                onFinish = {
                    onFinish(
                        profile.copy(
                            name = draft.businessName.trim(),
                            phone = draft.phone.trim(),
                            address = draft.address.trim(),
                            shopTypeKey = draft.shopTypeKey,
                            trackStock = draft.trackStock == true,
                            creditEnabled = draft.creditEnabled == true,
                            cashDrawerEnabled = draft.cashDrawerEnabled == true,
                            staffEnabled = draft.hasStaff == true,
                            managementLevel = when {
                                draft.trackStock == true && draft.hasStaff == true -> "MANAGE_BUSINESS"
                                draft.trackStock == true -> "BILL_STOCK"
                                else -> "JUST_BILL"
                            },
                            printerPaperWidth = draft.paperWidth,
                            receiptFooter = draft.receiptFooter,
                            language = draft.language,
                            autoPrint = draft.usesPrinter == true
                        ),
                        draft
                    )
                }
            )

            else -> WelcomeStep(onStart = { onStepChange(2) })
        }
    }
}

// ---------------------------------------------------------------------------
// Step 1 — Welcome
// ---------------------------------------------------------------------------

@Composable
private fun WelcomeStep(onStart: () -> Unit) {
    Scaffold(containerColor = LightBackground) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(BrandTealPrimary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.ReceiptLong,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(46.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "KadePOS",
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary
            )
            Text(
                "Open. Sell. Done.",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = BrandTealPrimary,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                "Make a bill, take the money, print the receipt. Works without internet.",
                fontSize = 15.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                WelcomePoint(Icons.Default.Bolt, "Fast billing", "A normal sale takes a few taps")
                WelcomePoint(Icons.Default.Print, "Prints receipts", "Bluetooth or Wi-Fi thermal printers")
                WelcomePoint(Icons.Default.CloudOff, "Works without internet", "Selling is saved on this phone first")
                WelcomePoint(Icons.Default.CloudUpload, "Optional cloud backup", "Connect a Google account later for a safe copy")
                WelcomePoint(Icons.Default.TrendingUp, "Grows with you", "Turn on stock, credit and staff when ready")
            }

            Spacer(modifier = Modifier.height(28.dp))

            HintCard(
                text = "Setup takes about two minutes. You can change any answer later in Settings.",
                tone = HintTone.BRAND
            )

            Spacer(modifier = Modifier.height(24.dp))

            PrimaryActionButton(
                text = "Set up my shop",
                onClick = onStart,
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                modifier = Modifier.testTag("start_setup_button")
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun WelcomePoint(icon: ImageVector, title: String, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(BrandMintSurface),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = BrandTealPrimary, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(detail, fontSize = 13.sp, color = TextSecondary)
        }
    }
}

// ---------------------------------------------------------------------------
// Step 2 — Shop details (all fields required)
// ---------------------------------------------------------------------------

@Composable
private fun ShopDetailsStep(
    draft: SetupDraft,
    onChange: (SetupDraft) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    var touched by rememberSaveable { mutableStateOf(false) }

    val nameError = validateShopName(draft.businessName)
    val ownerError = validateOwnerName(draft.ownerName)
    val phoneError = validatePhone(draft.phone)
    val addressError = validateAddress(draft.address)
    val allValid = listOf(nameError, ownerError, phoneError, addressError).all { it == null }

    StepScaffold(
        step = 2,
        title = "Your shop details",
        subtitle = "These appear on every receipt you print.",
        onBack = onBack,
        canContinue = allValid,
        blockedReason = "Fill in all four boxes to continue",
        onContinue = { touched = true; if (allValid) onNext() },
        onBlockedAttempt = { touched = true },
        testTag = "continue_details"
    ) {
        SetupField(
            label = "Shop name",
            value = draft.businessName,
            onValueChange = { onChange(draft.copy(businessName = it)) },
            placeholder = "e.g. Sunrise Grocery",
            icon = Icons.Default.Storefront,
            error = if (touched) nameError else null,
            testTag = "shop_name_input"
        )

        SetupField(
            label = "Your name",
            value = draft.ownerName,
            onValueChange = { onChange(draft.copy(ownerName = it)) },
            placeholder = "e.g. Kasun Perera",
            icon = Icons.Default.Person,
            error = if (touched) ownerError else null,
            testTag = "owner_name_input"
        )

        SetupField(
            label = "Phone number",
            value = draft.phone,
            onValueChange = { input ->
                onChange(draft.copy(phone = input.filter { it.isDigit() || it in " +-" }))
            },
            placeholder = "077 123 4567",
            icon = Icons.Default.Phone,
            keyboardType = KeyboardType.Phone,
            error = if (touched) phoneError else null,
            testTag = "phone_input"
        )

        SetupField(
            label = "Shop address",
            value = draft.address,
            onValueChange = { onChange(draft.copy(address = it)) },
            placeholder = "e.g. 24 Main Street, Negombo",
            icon = Icons.Default.LocationOn,
            singleLine = false,
            error = if (touched) addressError else null,
            testTag = "address_input"
        )
    }
}

// ---------------------------------------------------------------------------
// Step 3 — Shop type (drives the whole catalogue)
// ---------------------------------------------------------------------------

@Composable
private fun ShopTypeStep(
    draft: SetupDraft,
    onChange: (SetupDraft) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val chosen = ProductCatalogPresets.findShopType(draft.shopTypeKey)

    StepScaffold(
        step = 3,
        title = "What do you sell?",
        subtitle = "Pick the closest one. We'll load items to match.",
        onBack = onBack,
        canContinue = chosen != null,
        blockedReason = "Choose your shop type to continue",
        onContinue = onNext,
        testTag = "continue_shop_type"
    ) {
        ProductCatalogPresets.shopTypes.forEach { preset ->
            ChoiceCard(
                title = preset.displayName,
                subtitle = preset.description,
                icon = shopTypeIcon(preset.iconName),
                isSelected = draft.shopTypeKey == preset.key,
                onClick = { onChange(draft.copy(shopTypeKey = preset.key)) },
                modifier = Modifier.testTag("shop_type_${preset.key}")
            )
        }

        if (chosen != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (draft.loadStarterItems) BrandMintSurface else LightSurface
                ),
                border = BorderStroke(1.dp, if (draft.loadStarterItems) BrandTealPrimary else LightBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onChange(draft.copy(loadStarterItems = !draft.loadStarterItems)) }
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = draft.loadStarterItems,
                        onCheckedChange = { onChange(draft.copy(loadStarterItems = it)) },
                        colors = CheckboxDefaults.colors(checkedColor = BrandTealPrimary)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            "Add ${chosen.products.size} ready-made items",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            "Prices you can change any time. Untick if you'd rather add your own.",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            HintCard(
                text = "You'll only ever see ${chosen.displayName.lowercase()} items — " +
                    "other shop types stay out of your way.",
                tone = HintTone.BRAND
            )
        }
    }
}

private fun shopTypeIcon(name: String): ImageVector = when (name) {
    "shopping_basket" -> Icons.Default.ShoppingBasket
    "restaurant" -> Icons.Default.Restaurant
    "local_pharmacy" -> Icons.Default.LocalPharmacy
    "checkroom" -> Icons.Default.Checkroom
    "devices" -> Icons.Default.Devices
    "menu_book" -> Icons.Default.MenuBook
    "content_cut" -> Icons.Default.ContentCut
    "build" -> Icons.Default.Build
    else -> Icons.Default.Storefront
}

// ---------------------------------------------------------------------------
// Step 4 — Stock and credit
// ---------------------------------------------------------------------------

@Composable
private fun HowYouWorkStep(
    draft: SetupDraft,
    onChange: (SetupDraft) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val answered = draft.trackStock != null &&
        draft.creditEnabled != null &&
        draft.cashDrawerEnabled != null

    StepScaffold(
        step = 4,
        title = "How do you work?",
        subtitle = "A few quick questions so the app shows only what you need.",
        onBack = onBack,
        canContinue = answered,
        blockedReason = "Answer all three questions to continue",
        onContinue = onNext,
        testTag = "continue_how_you_work"
    ) {
        QuestionBlock("Do you want to keep count of your stock?") {
            ChoiceCard(
                title = "Yes, count my stock",
                subtitle = "See what's left and get told when something runs low",
                icon = Icons.Default.Inventory2,
                isSelected = draft.trackStock == true,
                onClick = { onChange(draft.copy(trackStock = true)) },
                modifier = Modifier.testTag("stock_yes")
            )
            ChoiceCard(
                title = "No, just make bills",
                subtitle = "Simplest option — no stock numbers anywhere",
                icon = Icons.Default.ReceiptLong,
                isSelected = draft.trackStock == false,
                onClick = { onChange(draft.copy(trackStock = false)) },
                modifier = Modifier.testTag("stock_no")
            )
        }

        QuestionBlock("Do customers ever take goods and pay later?") {
            ChoiceCard(
                title = "Yes, I keep a credit book",
                subtitle = "Track who owes you and record their payments",
                icon = Icons.Default.MenuBook,
                isSelected = draft.creditEnabled == true,
                onClick = { onChange(draft.copy(creditEnabled = true)) },
                modifier = Modifier.testTag("credit_yes")
            )
            ChoiceCard(
                title = "No, everyone pays now",
                subtitle = "Cash, card or QR at the counter",
                icon = Icons.Default.Payments,
                isSelected = draft.creditEnabled == false,
                onClick = { onChange(draft.copy(creditEnabled = false)) },
                modifier = Modifier.testTag("credit_no")
            )
        }

        QuestionBlock("Do you count the money in your drawer each day?") {
            ChoiceCard(
                title = "Yes, I count it",
                subtitle = "Count the float when you open and again when you close, " +
                    "so the app can tell you if cash is missing",
                icon = Icons.Default.PointOfSale,
                isSelected = draft.cashDrawerEnabled == true,
                onClick = { onChange(draft.copy(cashDrawerEnabled = true)) },
                modifier = Modifier.testTag("drawer_yes")
            )
            ChoiceCard(
                title = "No, money just goes in the box",
                subtitle = "Simplest option — no opening or closing routine",
                icon = Icons.Default.Savings,
                isSelected = draft.cashDrawerEnabled == false,
                onClick = { onChange(draft.copy(cashDrawerEnabled = false)) },
                modifier = Modifier.testTag("drawer_no")
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Step 5 — Team and PIN
// ---------------------------------------------------------------------------

@Composable
private fun TeamStep(
    draft: SetupDraft,
    onChange: (SetupDraft) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    var touched by rememberSaveable { mutableStateOf(false) }

    val pinError = if (draft.hasStaff == true) validatePin(draft.ownerPin) else null
    val confirmError = if (draft.hasStaff == true) {
        when {
            draft.ownerPinConfirm.isBlank() -> "Type the PIN again to be sure"
            draft.ownerPinConfirm != draft.ownerPin -> "The two PINs do not match"
            else -> null
        }
    } else {
        null
    }

    val canContinue = draft.hasStaff != null && pinError == null && confirmError == null

    StepScaffold(
        step = 5,
        title = "Who works here?",
        subtitle = "Staff members get their own PIN and only see what they need.",
        onBack = onBack,
        canContinue = canContinue,
        blockedReason = if (draft.hasStaff == null) {
            "Choose an answer to continue"
        } else {
            "Set a 4 digit PIN to continue"
        },
        onContinue = { touched = true; if (canContinue) onNext() },
        onBlockedAttempt = { touched = true },
        testTag = "continue_team"
    ) {
        ChoiceCard(
            title = "Just me",
            subtitle = "No sign-in screen — the app opens straight into selling",
            icon = Icons.Default.Person,
            isSelected = draft.hasStaff == false,
            onClick = { onChange(draft.copy(hasStaff = false)) },
            modifier = Modifier.testTag("team_solo")
        )
        ChoiceCard(
            title = "Me and my staff",
            subtitle = "Everyone signs in with a PIN; you decide what each can do",
            icon = Icons.Default.Groups,
            isSelected = draft.hasStaff == true,
            onClick = { onChange(draft.copy(hasStaff = true)) },
            modifier = Modifier.testTag("team_staff")
        )

        if (draft.hasStaff == true) {
            Spacer(modifier = Modifier.height(4.dp))
            HintCard(
                text = "Your PIN unlocks everything. Add your staff and their PINs later from More > My team.",
                tone = HintTone.BRAND
            )

            SetupField(
                label = "Your 4 digit PIN",
                value = draft.ownerPin,
                onValueChange = { onChange(draft.copy(ownerPin = it.filter(Char::isDigit).take(4))) },
                placeholder = "____",
                icon = Icons.Default.Lock,
                keyboardType = KeyboardType.NumberPassword,
                isSecret = true,
                error = if (touched) pinError else null,
                testTag = "owner_pin_input"
            )

            SetupField(
                label = "Type it again",
                value = draft.ownerPinConfirm,
                onValueChange = { onChange(draft.copy(ownerPinConfirm = it.filter(Char::isDigit).take(4))) },
                placeholder = "____",
                icon = Icons.Default.LockReset,
                keyboardType = KeyboardType.NumberPassword,
                isSecret = true,
                error = if (touched) confirmError else null,
                testTag = "owner_pin_confirm_input"
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Step 6 — Printer
// ---------------------------------------------------------------------------

@Composable
private fun PrinterStep(
    draft: SetupDraft,
    onChange: (SetupDraft) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onOpenPrinterSetup: () -> Unit
) {
    StepScaffold(
        step = 6,
        title = "Do you print receipts?",
        subtitle = "You can always share a bill by WhatsApp instead.",
        onBack = onBack,
        canContinue = draft.usesPrinter != null,
        blockedReason = "Choose an answer to continue",
        onContinue = onNext,
        testTag = "continue_printer"
    ) {
        ChoiceCard(
            title = "Yes, I have a receipt printer",
            subtitle = "Connect it over Bluetooth or Wi-Fi",
            icon = Icons.Default.Print,
            isSelected = draft.usesPrinter == true,
            onClick = { onChange(draft.copy(usesPrinter = true)) },
            modifier = Modifier.testTag("printer_yes")
        )
        ChoiceCard(
            title = "Not right now",
            subtitle = "Send bills by WhatsApp or just keep them in the app",
            icon = Icons.Default.Smartphone,
            isSelected = draft.usesPrinter == false,
            onClick = { onChange(draft.copy(usesPrinter = false)) },
            modifier = Modifier.testTag("printer_no")
        )

        if (draft.usesPrinter == true) {
            Spacer(modifier = Modifier.height(4.dp))
            Text("Paper size", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                listOf(
                    "58mm" to "Small — the usual pocket printer",
                    "80mm" to "Wide — counter-top printers"
                ).forEach { (width, detail) ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (draft.paperWidth == width) BrandMintSurface else LightSurface
                        ),
                        border = BorderStroke(
                            if (draft.paperWidth == width) 2.dp else 1.dp,
                            if (draft.paperWidth == width) BrandTealPrimary else LightBorder
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onChange(draft.copy(paperWidth = width)) }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(width, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text(detail, fontSize = 11.sp, color = TextSecondary, lineHeight = 15.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            HintCard(
                text = "Finish setup first, then connect your printer from More > Printer. " +
                    "We'll send a test page so you know it works.",
                tone = HintTone.INFO
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Step 7 — Ready
// ---------------------------------------------------------------------------

@Composable
private fun ReadyStep(
    draft: SetupDraft,
    onBack: () -> Unit,
    onFinish: () -> Unit
) {
    val preset = ProductCatalogPresets.findShopType(draft.shopTypeKey)

    Scaffold(
        topBar = { StepTopBar(step = 7, onBack = onBack) },
        containerColor = LightBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(StatusGreenBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = StatusGreen,
                    modifier = Modifier.size(34.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "${draft.businessName.trim()} is ready",
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary
            )
            Text(
                "Here's how your app is set up. Change anything later in Settings.",
                fontSize = 14.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
            )

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = LightSurface),
                border = BorderStroke(1.dp, LightBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    SummaryLine(Icons.Default.Storefront, "Shop type", preset?.displayName ?: "Not chosen")
                    SummaryLine(
                        Icons.Default.Inventory2,
                        "Items loaded",
                        if (draft.loadStarterItems && preset != null) {
                            "${preset.products.size} ready to sell"
                        } else {
                            "You'll add your own"
                        }
                    )
                    SummaryLine(
                        Icons.Default.Numbers,
                        "Stock counting",
                        if (draft.trackStock == true) "On" else "Off"
                    )
                    SummaryLine(
                        Icons.Default.MenuBook,
                        "Credit book",
                        if (draft.creditEnabled == true) "On" else "Off"
                    )
                    SummaryLine(
                        Icons.Default.PointOfSale,
                        "Cash drawer count",
                        if (draft.cashDrawerEnabled == true) "On" else "Off"
                    )
                    SummaryLine(
                        Icons.Default.Groups,
                        "Team",
                        if (draft.hasStaff == true) "Staff sign in with a PIN" else "Just you"
                    )
                    SummaryLine(
                        Icons.Default.Print,
                        "Receipts",
                        if (draft.usesPrinter == true) "Printer (${draft.paperWidth})" else "Share digitally",
                        isLast = true
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            HintCard(
                text = "To make your first bill: tap an item, then tap the big green button and take the money. That's it.",
                tone = HintTone.BRAND
            )

            Spacer(modifier = Modifier.height(24.dp))

            PrimaryActionButton(
                text = "Start selling",
                onClick = onFinish,
                icon = Icons.Default.PointOfSale,
                modifier = Modifier.testTag("start_selling_button")
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SummaryLine(
    icon: ImageVector,
    label: String,
    value: String,
    isLast: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = BrandTealPrimary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, fontSize = 14.sp, color = TextSecondary, modifier = Modifier.weight(1f))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
    }
    if (!isLast) HorizontalDivider(color = LightBorder)
}

// ---------------------------------------------------------------------------
// Shared step chrome
// ---------------------------------------------------------------------------

@Composable
private fun StepScaffold(
    step: Int,
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    canContinue: Boolean,
    blockedReason: String,
    onContinue: () -> Unit,
    testTag: String,
    onBlockedAttempt: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Scaffold(
        topBar = { StepTopBar(step = step, onBack = onBack) },
        containerColor = LightBackground,
        bottomBar = {
            Surface(color = LightSurface, shadowElevation = 8.dp) {
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp)) {
                    if (!canContinue) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(blockedReason, fontSize = 12.sp, color = TextSecondary)
                        }
                    }
                    PrimaryActionButton(
                        text = "Continue",
                        onClick = { if (canContinue) onContinue() else onBlockedAttempt() },
                        enabled = canContinue,
                        icon = Icons.AutoMirrored.Filled.ArrowForward,
                        modifier = Modifier.testTag(testTag)
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                title,
                fontSize = 25.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary,
                lineHeight = 31.sp
            )
            Text(subtitle, fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp)
            Spacer(modifier = Modifier.height(2.dp))
            content()
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun StepTopBar(step: Int, onBack: () -> Unit) {
    Column(modifier = Modifier.background(LightSurface)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("setup_back")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back", tint = TextPrimary)
            }
            Text(
                "Step $step of $TOTAL_STEPS",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary
            )
        }
        LinearProgressIndicator(
            progress = { step.toFloat() / TOTAL_STEPS },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
            color = BrandTealPrimary,
            trackColor = LightBorder,
            gapSize = 0.dp,
            drawStopIndicator = {}
        )
    }
}

@Composable
private fun QuestionBlock(question: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            question,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            modifier = Modifier.padding(top = 6.dp)
        )
        content()
    }
}

@Composable
private fun SetupField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    icon: ImageVector,
    error: String?,
    testTag: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    isSecret: Boolean = false
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag),
            shape = RoundedCornerShape(14.dp),
            textStyle = TextStyle(color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium),
            placeholder = { Text(placeholder, color = TextMuted, fontSize = 15.sp) },
            leadingIcon = { Icon(icon, contentDescription = null, tint = BrandTealPrimary) },
            trailingIcon = {
                if (error == null && value.isNotBlank()) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusGreen)
                }
            },
            isError = error != null,
            supportingText = if (error != null) {
                { Text(error, color = StatusRed, fontSize = 12.sp) }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = if (isSecret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            singleLine = singleLine,
            minLines = if (singleLine) 1 else 2,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BrandTealPrimary,
                unfocusedBorderColor = LightBorder,
                errorBorderColor = StatusRed,
                cursorColor = BrandTealPrimary
            )
        )
    }
}

// ---------------------------------------------------------------------------
// Validation — one place, so the rules never drift between screens
// ---------------------------------------------------------------------------

fun validateShopName(value: String): String? = when {
    value.isBlank() -> "Enter your shop name"
    value.trim().length < 2 -> "That looks too short"
    else -> null
}

fun validateOwnerName(value: String): String? = when {
    value.isBlank() -> "Enter your name"
    value.trim().length < 2 -> "That looks too short"
    else -> null
}

fun validatePhone(value: String): String? {
    val digits = value.filter(Char::isDigit)
    return when {
        digits.isEmpty() -> "Enter a phone number"
        digits.length < 9 -> "A phone number needs at least 9 digits"
        digits.length > 15 -> "That's too long for a phone number"
        else -> null
    }
}

fun validateAddress(value: String): String? = when {
    value.isBlank() -> "Enter your shop address"
    value.trim().length < 5 -> "Add a bit more so customers can find you"
    else -> null
}

fun validatePin(value: String): String? = when {
    value.isBlank() -> "Choose a 4 digit PIN"
    value.length != 4 -> "The PIN must be exactly 4 digits"
    value.toSet().size == 1 -> "Avoid the same digit four times"
    value in listOf("1234", "0000", "1111", "4321") -> "That PIN is too easy to guess"
    else -> null
}

// Keeps the draft alive across rotation.
private val SetupDraftSaver = androidx.compose.runtime.saveable.listSaver<SetupDraft, Any?>(
    save = {
        listOf(
            it.businessName, it.ownerName, it.phone, it.address, it.shopTypeKey,
            it.loadStarterItems, it.trackStock, it.creditEnabled, it.hasStaff,
            it.ownerPin, it.ownerPinConfirm, it.usesPrinter, it.paperWidth,
            it.language, it.receiptFooter, it.cashDrawerEnabled
        )
    },
    restore = {
        SetupDraft(
            businessName = it[0] as String,
            ownerName = it[1] as String,
            phone = it[2] as String,
            address = it[3] as String,
            shopTypeKey = it[4] as String,
            loadStarterItems = it[5] as Boolean,
            trackStock = it[6] as Boolean?,
            creditEnabled = it[7] as Boolean?,
            hasStaff = it[8] as Boolean?,
            ownerPin = it[9] as String,
            ownerPinConfirm = it[10] as String,
            usesPrinter = it[11] as Boolean?,
            paperWidth = it[12] as String,
            language = it[13] as String,
            receiptFooter = it[14] as String,
            cashDrawerEnabled = it[15] as Boolean?
        )
    }
)
