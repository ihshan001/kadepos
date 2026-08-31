package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BusinessProfileEntity
import com.example.ui.screens.customers.CustomersCreditScreen
import com.example.ui.screens.inventory.InventoryScreen
import com.example.ui.screens.more.MoreManagementHubScreen
import com.example.ui.screens.onboarding.OnboardingFlow
import com.example.ui.screens.products.ProductsScreen
import com.example.ui.screens.sales.SalesHistoryScreen
import com.example.ui.screens.sell.SellScreen
import com.example.ui.theme.*
import com.example.ui.util.CurrencyUtils
import com.example.ui.viewmodel.MoreDestination
import com.example.ui.viewmodel.PosTab
import com.example.ui.viewmodel.PosViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: PosViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppContainer(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContainer(viewModel: PosViewModel) {
    val profile by viewModel.profile.collectAsState()
    val products by viewModel.products.collectAsState()
    val cart by viewModel.cart.collectAsState()
    val selectedCustomer by viewModel.selectedCustomer.collectAsState()
    val billDiscount by viewModel.billDiscount.collectAsState()
    val billNote by viewModel.billNote.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val moreDestination by viewModel.moreDestination.collectAsState()
    val onboardingStep by viewModel.onboardingStep.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()
    val heldSales by viewModel.heldSales.collectAsState()
    val lowStockProducts by viewModel.lowStockProducts.collectAsState()
    val customers by viewModel.customers.collectAsState()
    val suppliers by viewModel.suppliers.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    var showHeldSalesDialog by remember { mutableStateOf(false) }

    LaunchedEffect(userMessage) {
        userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    // Check if onboarding is active
    val isUnconfigured = profile?.isConfigured == false
    val effectiveOnboardingStep = if (onboardingStep > 0) onboardingStep else if (isUnconfigured) 1 else 0

    if (effectiveOnboardingStep > 0) {
        OnboardingFlow(
            profile = profile ?: BusinessProfileEntity(),
            currentStep = effectiveOnboardingStep,
            onStepChange = { viewModel.setOnboardingStep(it) },
            onFinishOnboarding = { updatedProfile ->
                viewModel.saveBusinessProfile(updatedProfile)
                viewModel.setOnboardingStep(0)
            },
            onPreloadProducts = { shopTypeKey ->
                viewModel.preloadProductsForShopType(shopTypeKey)
            },
            onTestPrint = { paperWidth ->
                viewModel.printTestReceipt(paperWidth)
            }
        )
        return
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = LightSurface,
                modifier = Modifier.width(300.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(BrandTealPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Store, contentDescription = null, tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(profile?.name ?: "ABC Stores", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                            Text("The System POS 🇱🇰", fontSize = 12.sp, color = TextSecondary)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = LightBorder)

                    NavigationDrawerItem(
                        colors = NavigationDrawerItemDefaults.colors(
                            unselectedTextColor = TextPrimary,
                            unselectedIconColor = TextSecondary,
                            selectedTextColor = BrandTealPrimary,
                            selectedIconColor = BrandTealPrimary,
                            unselectedContainerColor = Color.Transparent,
                            selectedContainerColor = BrandMintSurface
                        ),
                        icon = { Icon(Icons.Default.Pause, contentDescription = null, tint = StatusAmber) },
                        label = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Parked / Held Bills", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                if (heldSales.isNotEmpty()) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = StatusAmberBg
                                    ) {
                                        Text(
                                            "${heldSales.size}",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = StatusAmber
                                        )
                                    }
                                }
                            }
                        },
                        selected = false,
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                            showHeldSalesDialog = true
                        }
                    )

                    NavigationDrawerItem(
                        colors = NavigationDrawerItemDefaults.colors(
                            unselectedTextColor = TextPrimary,
                            unselectedIconColor = TextSecondary,
                            selectedTextColor = BrandTealPrimary,
                            selectedIconColor = BrandTealPrimary,
                            unselectedContainerColor = Color.Transparent,
                            selectedContainerColor = BrandMintSurface
                        ),
                        icon = {
                            BadgedBox(badge = {
                                if (lowStockProducts.isNotEmpty()) {
                                    Badge(containerColor = StatusRed) {
                                        Text("${lowStockProducts.size}")
                                    }
                                }
                            }) {
                                Icon(Icons.Default.Tune, contentDescription = null, tint = if (lowStockProducts.isNotEmpty()) StatusRed else BrandTealPrimary)
                            }
                        },
                        label = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Stock & Inventory", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                if (lowStockProducts.isNotEmpty()) {
                                    Surface(shape = RoundedCornerShape(10.dp), color = StatusRedBg) {
                                        Text(
                                            "${lowStockProducts.size} Low",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = StatusRed
                                        )
                                    }
                                }
                            }
                        },
                        selected = selectedTab == PosTab.INVENTORY,
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                            viewModel.selectTab(PosTab.INVENTORY)
                        }
                    )

                    val totalCreditDueCount = customers.count { it.creditBalance > 0 }
                    NavigationDrawerItem(
                        colors = NavigationDrawerItemDefaults.colors(
                            unselectedTextColor = TextPrimary,
                            unselectedIconColor = TextSecondary,
                            selectedTextColor = BrandTealPrimary,
                            selectedIconColor = BrandTealPrimary,
                            unselectedContainerColor = Color.Transparent,
                            selectedContainerColor = BrandMintSurface
                        ),
                        icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = StatusBlue) },
                        label = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Customer Credit Book", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                if (totalCreditDueCount > 0) {
                                    Surface(shape = RoundedCornerShape(10.dp), color = StatusAmberBg) {
                                        Text(
                                            "$totalCreditDueCount",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = StatusAmber
                                        )
                                    }
                                }
                            }
                        },
                        selected = false,
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                            viewModel.selectTab(PosTab.MORE)
                            viewModel.navigateMore(MoreDestination.CREDIT_BOOK)
                        }
                    )

                    val suppliersWithDuesCount = suppliers.count { it.outstandingBalance > 0 }
                    NavigationDrawerItem(
                        colors = NavigationDrawerItemDefaults.colors(
                            unselectedTextColor = TextPrimary,
                            unselectedIconColor = TextSecondary,
                            selectedTextColor = BrandTealPrimary,
                            selectedIconColor = BrandTealPrimary,
                            unselectedContainerColor = Color.Transparent,
                            selectedContainerColor = BrandMintSurface
                        ),
                        icon = { Icon(Icons.Default.LocalShipping, contentDescription = null, tint = BrandTealPrimary) },
                        label = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Suppliers & Purchases", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                if (suppliersWithDuesCount > 0) {
                                    Surface(shape = RoundedCornerShape(10.dp), color = StatusAmberBg) {
                                        Text(
                                            "$suppliersWithDuesCount",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = StatusAmber
                                        )
                                    }
                                }
                            }
                        },
                        selected = false,
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                            viewModel.selectTab(PosTab.MORE)
                            viewModel.navigateMore(MoreDestination.SUPPLIERS)
                        }
                    )

                    NavigationDrawerItem(
                        colors = NavigationDrawerItemDefaults.colors(
                            unselectedTextColor = TextPrimary,
                            unselectedIconColor = TextSecondary,
                            selectedTextColor = BrandTealPrimary,
                            selectedIconColor = BrandTealPrimary,
                            unselectedContainerColor = Color.Transparent,
                            selectedContainerColor = BrandMintSurface
                        ),
                        icon = { Icon(Icons.Default.PointOfSale, contentDescription = null, tint = StatusGreen) },
                        label = { Text("Cash Register Drawer", fontWeight = FontWeight.SemiBold, color = TextPrimary) },
                        selected = false,
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                            viewModel.selectTab(PosTab.MORE)
                            viewModel.navigateMore(MoreDestination.REGISTER)
                        }
                    )

                    NavigationDrawerItem(
                        colors = NavigationDrawerItemDefaults.colors(
                            unselectedTextColor = TextPrimary,
                            unselectedIconColor = TextSecondary,
                            selectedTextColor = BrandTealPrimary,
                            selectedIconColor = BrandTealPrimary,
                            unselectedContainerColor = Color.Transparent,
                            selectedContainerColor = BrandMintSurface
                        ),
                        icon = { Icon(Icons.Default.Badge, contentDescription = null, tint = TextSecondary) },
                        label = { Text("Switch Cashier Staff", fontWeight = FontWeight.SemiBold, color = TextPrimary) },
                        selected = false,
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                            viewModel.selectTab(PosTab.MORE)
                            viewModel.navigateMore(MoreDestination.STAFF)
                        }
                    )

                    NavigationDrawerItem(
                        colors = NavigationDrawerItemDefaults.colors(
                            unselectedTextColor = TextPrimary,
                            unselectedIconColor = TextSecondary,
                            selectedTextColor = BrandTealPrimary,
                            selectedIconColor = BrandTealPrimary,
                            unselectedContainerColor = Color.Transparent,
                            selectedContainerColor = BrandMintSurface
                        ),
                        icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = BrandTealPrimary) },
                        label = { Text("Setup Wizard (10 Steps)", fontWeight = FontWeight.SemiBold, color = TextPrimary) },
                        selected = false,
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                            viewModel.setOnboardingStep(1)
                        }
                    )
                }
            }
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                NavigationBar(
                    containerColor = LightSurface,
                    contentColor = BrandTealPrimary,
                    tonalElevation = 8.dp
                ) {
                    val navItemColors = NavigationBarItemDefaults.colors(
                        selectedIconColor = BrandTealPrimary,
                        selectedTextColor = BrandTealPrimary,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary,
                        indicatorColor = BrandMintSurface
                    )

                    NavigationBarItem(
                        selected = selectedTab == PosTab.SELL,
                        onClick = { viewModel.selectTab(PosTab.SELL) },
                        icon = { Icon(Icons.Default.PointOfSale, contentDescription = "Sell") },
                        label = { Text("Sell", fontWeight = FontWeight.SemiBold) },
                        colors = navItemColors,
                        modifier = Modifier.testTag("nav_sell")
                    )

                    NavigationBarItem(
                        selected = selectedTab == PosTab.SALES,
                        onClick = { viewModel.selectTab(PosTab.SALES) },
                        icon = { Icon(Icons.Default.ReceiptLong, contentDescription = "Sales") },
                        label = { Text("Sales", fontWeight = FontWeight.SemiBold) },
                        colors = navItemColors,
                        modifier = Modifier.testTag("nav_sales")
                    )

                    NavigationBarItem(
                        selected = selectedTab == PosTab.PRODUCTS,
                        onClick = { viewModel.selectTab(PosTab.PRODUCTS) },
                        icon = { Icon(Icons.Default.Inventory2, contentDescription = "Products") },
                        label = { Text("Products", fontWeight = FontWeight.SemiBold) },
                        colors = navItemColors,
                        modifier = Modifier.testTag("nav_products")
                    )

                    NavigationBarItem(
                        selected = selectedTab == PosTab.INVENTORY,
                        onClick = { viewModel.selectTab(PosTab.INVENTORY) },
                        icon = {
                            BadgedBox(badge = {
                                if (lowStockProducts.isNotEmpty()) {
                                    Badge(containerColor = StatusRed) {
                                        Text("${lowStockProducts.size}")
                                    }
                                }
                            }) {
                                Icon(Icons.Default.Tune, contentDescription = "Inventory")
                            }
                        },
                        label = { Text("Stock", fontWeight = FontWeight.SemiBold) },
                        colors = navItemColors,
                        modifier = Modifier.testTag("nav_inventory")
                    )

                    NavigationBarItem(
                        selected = selectedTab == PosTab.MORE,
                        onClick = { viewModel.selectTab(PosTab.MORE) },
                        icon = { Icon(Icons.Default.Apps, contentDescription = "More") },
                        label = { Text("More", fontWeight = FontWeight.SemiBold) },
                        colors = navItemColors,
                        modifier = Modifier.testTag("nav_more")
                    )
                }
            },
            containerColor = LightBackground
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when (selectedTab) {
                    PosTab.SELL -> {
                        SellScreen(
                            viewModel = viewModel,
                            profile = profile,
                            products = products,
                            cart = cart,
                            selectedCustomer = selectedCustomer,
                            billDiscount = billDiscount,
                            billNote = billNote,
                            onOpenDrawer = {
                                coroutineScope.launch { drawerState.open() }
                            }
                        )
                    }
                    PosTab.SALES -> {
                        SalesHistoryScreen(viewModel = viewModel)
                    }
                    PosTab.PRODUCTS -> {
                        ProductsScreen(viewModel = viewModel)
                    }
                    PosTab.INVENTORY -> {
                        InventoryScreen(viewModel = viewModel)
                    }
                    PosTab.MORE -> {
                        MoreManagementHubScreen(
                            viewModel = viewModel,
                            destination = moreDestination,
                            onSelectDestination = { viewModel.navigateMore(it) },
                            onBackToHub = { viewModel.clearMoreDestination() }
                        )
                    }
                }
            }
        }
    }

    if (showHeldSalesDialog) {
        HeldSalesDialog(
            heldSales = heldSales,
            onResume = { held ->
                viewModel.resumeHeldSale(held)
                showHeldSalesDialog = false
            },
            onDismiss = { showHeldSalesDialog = false }
        )
    }
}

@Composable
fun HeldSalesDialog(
    heldSales: List<com.example.data.model.HeldSaleEntity>,
    onResume: (com.example.data.model.HeldSaleEntity) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = LightSurface),
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.6f)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("PARKED / HELD BILLS", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (heldSales.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No parked bills", color = TextSecondary)
                    }
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(heldSales.size) { idx ->
                            val held = heldSales[idx]
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = BrandMintSurface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(held.label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text("${CurrencyUtils.formatTimeOnly(held.timestamp)} • ${held.customerName}", fontSize = 12.sp, color = TextSecondary)
                                    }
                                    Button(
                                        onClick = { onResume(held) },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("Resume", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
