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
import androidx.compose.material.icons.filled.Groups
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.BusinessProfileEntity
import com.example.data.model.Permission
import com.example.ui.screens.auth.SignInScreen
import com.example.ui.screens.customers.CustomersCreditScreen
import com.example.ui.screens.inventory.InventoryScreen
import com.example.ui.screens.more.MoreManagementHubScreen
import com.example.ui.screens.onboarding.OnboardingFlow
import com.example.ui.screens.products.ProductsScreen
import com.example.ui.screens.sales.SalesHistoryScreen
import com.example.ui.screens.sell.SellScreen
import com.example.ui.theme.BrandMintSurface
import com.example.ui.theme.BrandTealPrimary
import com.example.ui.theme.KadePosTheme
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
            KadePosTheme {
                KadePosApp(viewModel = viewModel)
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
fun KadePosApp(viewModel: PosViewModel) {
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
    val lowStockProducts by viewModel.lowStockProducts.collectAsStateWithLifecycle()
    val staffList by viewModel.staffList.collectAsStateWithLifecycle()
    val requiresSignIn by viewModel.requiresSignIn.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(userMessage) {
        userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

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
        add(NavEntry(PosTab.SALES, "Bills", Icons.Default.ReceiptLong))

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
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = LightBackground,
        bottomBar = {
            NavigationBar(
                containerColor = LightSurface,
                contentColor = BrandTealPrimary,
                tonalElevation = 8.dp
            ) {
                val colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = BrandTealPrimary,
                    selectedTextColor = BrandTealPrimary,
                    unselectedIconColor = TextSecondary,
                    unselectedTextColor = TextSecondary,
                    indicatorColor = BrandMintSurface
                )
                navEntries.forEach { entry ->
                    NavigationBarItem(
                        selected = selectedTab == entry.tab,
                        onClick = { viewModel.selectTab(entry.tab) },
                        icon = {
                            if (entry.badgeCount > 0) {
                                BadgedBox(badge = {
                                    Badge(containerColor = StatusRed) { Text("${entry.badgeCount}") }
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

                PosTab.PRODUCTS -> ProductsScreen(viewModel = viewModel)

                PosTab.INVENTORY -> InventoryScreen(viewModel = viewModel)

                PosTab.MORE -> MoreManagementHubScreen(
                    viewModel = viewModel,
                    destination = moreDestination,
                    onSelectDestination = { viewModel.navigateMore(it) },
                    onBackToHub = { viewModel.clearMoreDestination() }
                )
            }
        }
    }
}
