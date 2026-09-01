package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import com.example.ui.theme.BrandPrimary
import com.example.ui.theme.BrandSurface
import com.example.ui.theme.BrandPrimaryDark
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusAmberBg
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.BusinessProfileEntity
import com.example.data.model.Permission
import com.example.ui.screens.auth.SignInScreen
import com.example.ui.screens.inventory.InventoryScreen
import com.example.ui.screens.more.MoreManagementHubScreen
import com.example.ui.screens.onboarding.OnboardingFlow
import com.example.ui.screens.products.ProductsScreen
import com.example.ui.screens.sales.SalesHistoryScreen
import com.example.ui.screens.sell.SellScreen
import com.example.ui.theme.ArroPosTheme
import com.example.ui.theme.LightBackground
import com.example.ui.theme.LightSurface
import com.example.ui.theme.StatusRed
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.MoreDestination
import com.example.ui.viewmodel.PosTab
import com.example.ui.viewmodel.PosViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: PosViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ArroPosTheme {
                ArroPosApp(viewModel = viewModel)
            }
        }
    }
}

/**
 * One bottom bar entry. Which of these appear depends on the answers given
 * during setup and on what the signed-in person is allowed to do — a tiny shop
 * sees three tabs, a full retail business sees five.
 */
private data class NavEntry(
    val tab: PosTab,
    val label: String,
    val icon: ImageVector,
    val badgeCount: Int = 0
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArroPosApp(viewModel: PosViewModel) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val cart by viewModel.cart.collectAsStateWithLifecycle()
    val selectedCustomer by viewModel.selectedCustomer.collectAsStateWithLifecycle()
    val billDiscount by viewModel.billDiscount.collectAsStateWithLifecycle()
    val billNote by viewModel.billNote.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val moreDestination by viewModel.moreDestination.collectAsStateWithLifecycle()
    val onboardingStep by viewModel.onboardingStep.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
    val messageImportant by viewModel.messageImportant.collectAsStateWithLifecycle()
    val lowStockProducts by viewModel.lowStockProducts.collectAsStateWithLifecycle()
    val staffList by viewModel.staffList.collectAsStateWithLifecycle()
    val requiresSignIn by viewModel.requiresSignIn.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()

    // ---- Setup wizard ----------------------------------------------------
    val needsSetup = profile != null && profile?.isConfigured == false
    val activeStep = when {
        onboardingStep > 0 -> onboardingStep
        needsSetup -> 1
        else -> 0
    }

    if (profile == null) {
        // First composition before the profile row exists; nothing to show yet.
        Box(modifier = Modifier.fillMaxSize())
        return
    }

    if (activeStep > 0) {
        OnboardingFlow(
            profile = profile ?: BusinessProfileEntity(),
            currentStep = activeStep,
            onStepChange = { viewModel.setOnboardingStep(it) },
            onFinish = { updatedProfile, draft ->
                viewModel.finishSetup(
                    profileToSave = updatedProfile,
                    shopTypeKey = draft.shopTypeKey,
                    loadStarterItems = draft.loadStarterItems,
                    ownerName = draft.ownerName.trim(),
                    ownerPin = draft.ownerPin
                )
            },
            onOpenPrinterSetup = {
                viewModel.selectTab(PosTab.MORE)
                viewModel.navigateMore(MoreDestination.PRINTER)
            }
        )
        return
    }

    // ---- PIN gate --------------------------------------------------------
    if (requiresSignIn) {
        SignInScreen(
            shopName = profile?.name.orEmpty(),
            staff = staffList,
            onSubmitPin = { viewModel.signInWithPin(it) }
        )
        return
    }

    // ---- Adaptive bottom navigation --------------------------------------
    val navEntries = buildList {
        add(NavEntry(PosTab.SELL, "Sell", Icons.Default.PointOfSale))
        if (permissions.can(Permission.VIEW_SALES_HISTORY)) {
            add(NavEntry(PosTab.SALES, "Bills", Icons.Default.ReceiptLong))
        }

        val canSeeItems = permissions.can(Permission.MANAGE_PRODUCTS)
        if (canSeeItems) {
            add(NavEntry(PosTab.PRODUCTS, "Items", Icons.Default.Sell))
        }
        // The stock tab only exists for businesses that said yes to counting stock.
        if (profile?.trackStock == true && permissions.can(Permission.MANAGE_INVENTORY)) {
            add(
                NavEntry(
                    PosTab.INVENTORY,
                    "Stock",
                    Icons.Default.Inventory2,
                    badgeCount = lowStockProducts.size
                )
            )
        }
        add(NavEntry(PosTab.MORE, "More", Icons.Default.Apps))
    }

    // If the current tab got hidden (e.g. a cashier signed in), fall back to Sell.
    LaunchedEffect(navEntries, selectedTab) {
        if (navEntries.none { it.tab == selectedTab }) {
            viewModel.selectTab(PosTab.SELL)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = LightBackground,
        bottomBar = {
            NavigationBar(
                containerColor = LightSurface,
                contentColor = BrandPrimary,
                tonalElevation = 8.dp
            ) {
                val colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = BrandPrimary,
                    selectedTextColor = BrandPrimary,
                    unselectedIconColor = TextSecondary,
                    unselectedTextColor = TextSecondary,
                    indicatorColor = BrandSurface
                )
                navEntries.forEach { entry ->
                    NavigationBarItem(
                        selected = selectedTab == entry.tab,
                        onClick = { viewModel.selectTab(entry.tab) },
                        icon = {
                            if (entry.badgeCount > 0) {
                                BadgedBox(badge = {
                                    Badge(containerColor = StatusRed) {
                                        Text("${entry.badgeCount}", color = Color.White)
                                    }
                                }) {
                                    Icon(entry.icon, contentDescription = entry.label)
                                }
                            } else {
                                Icon(entry.icon, contentDescription = entry.label)
                            }
                        },
                        label = { Text(entry.label, fontWeight = FontWeight.SemiBold) },
                        colors = colors,
                        modifier = Modifier.testTag("nav_${entry.tab.name.lowercase()}")
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (selectedTab) {
                PosTab.SELL -> SellScreen(
                    viewModel = viewModel,
                    profile = profile,
                    products = products,
                    cart = cart,
                    selectedCustomer = selectedCustomer,
                    billDiscount = billDiscount,
                    billNote = billNote,
                    onOpenMore = {
                        viewModel.selectTab(PosTab.MORE)
                        viewModel.clearMoreDestination()
                    }
                )

                PosTab.SALES -> SalesHistoryScreen(viewModel = viewModel)

                PosTab.PRODUCTS -> ProductsScreen(
                    viewModel = viewModel,
                    onSellItem = { product -> viewModel.openVariantPickerOnSellTab(product.id) }
                )

                PosTab.INVENTORY -> InventoryScreen(viewModel = viewModel)

                PosTab.MORE -> MoreManagementHubScreen(
                    viewModel = viewModel,
                    destination = moreDestination,
                    onSelectDestination = { viewModel.navigateMore(it) },
                    onBackToHub = { viewModel.clearMoreDestination() }
                )
            }

            // Warnings: a banner at the top that stays until it is read and
            // tapped away. Real problems deserve the interruption.
            AnimatedVisibility(
                visible = userMessage != null && messageImportant,
                enter = fadeIn() + slideInVertically { -it / 2 },
                exit = fadeOut() + slideOutVertically { -it / 2 },
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = StatusAmberBg,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .clickable { viewModel.clearMessage() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = StatusAmber,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            userMessage.orEmpty(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = StatusAmber,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.clearMessage() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = StatusAmber,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // Confirmations ("Added Biryani"): a small pill around the middle
            // of the screen that clears itself in about a second. It used to
            // sit at the very top, where nobody looks while tapping.
            AnimatedVisibility(
                visible = userMessage != null && !messageImportant,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = BrandSurface,
                    shadowElevation = 6.dp,
                    modifier = Modifier.clickable { viewModel.clearMessage() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = BrandPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            userMessage.orEmpty(),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrandPrimaryDark
                        )
                    }
                }
            }
        }
    }
}
