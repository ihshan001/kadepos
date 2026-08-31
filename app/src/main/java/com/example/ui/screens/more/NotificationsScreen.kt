package com.example.ui.screens.more

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.NotificationImportance
import com.example.data.model.NotificationType
import com.example.ui.components.EmptyState
import com.example.ui.components.HintCard
import com.example.ui.theme.*
import com.example.ui.util.CurrencyUtils
import com.example.ui.viewmodel.PosViewModel

/**
 * What happened, and what you want to be told about. Two tabs rather than two
 * screens: the shopkeeper who is annoyed by a buzz is one tap from switching
 * it off, which is exactly when they want to.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    viewModel: PosViewModel,
    onBack: () -> Unit
) {
    val notifications by viewModel.notifications.collectAsState()
    val settings by viewModel.notificationSettings.collectAsState()
    val permissions by viewModel.permissions.collectAsState()
    val unread by viewModel.unreadNotificationCount.collectAsState()
    val profile by viewModel.profile.collectAsState()

    val usesCashDrawer = profile?.cashDrawerEnabled == true
    val tracksStock = profile?.trackStock == true
    val creditEnabled = profile?.creditEnabled == true
    val hasStaff = profile?.staffEnabled == true

    var selectedTab by remember { mutableStateOf(0) }

    // Opening the list counts as having seen them.
    LaunchedEffect(selectedTab) {
        if (selectedTab == 0 && unread > 0) viewModel.markAllNotificationsRead()
    }

    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
            .only(WindowInsetsSides.Top),
        topBar = {
            TopAppBar(
                title = { Text("Alerts", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (selectedTab == 0 && notifications.isNotEmpty()) {
                        TextButton(onClick = { viewModel.clearNotifications() }) {
                            Text("Clear", fontSize = 13.sp)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LightSurface)
            )
        },
        containerColor = LightBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = LightSurface,
                contentColor = BrandGoldPrimary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("What happened", fontSize = 13.sp) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Tell me about", fontSize = 13.sp) }
                )
            }

            if (selectedTab == 0) {
                if (notifications.isEmpty()) {
                    EmptyState(
                        icon = Icons.Default.NotificationsNone,
                        title = "Nothing to report",
                        message = "Sales, refunds, low stock and cash differences will " +
                            "show up here as they happen."
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(notifications, key = { it.id }) { entry ->
                            NotificationRow(
                                title = entry.title,
                                body = entry.body,
                                actor = entry.actorName,
                                timestamp = entry.timestamp,
                                importance = entry.importance,
                                isRead = entry.isRead
                            )
                        }
                    }
                }
            } else {
                NotificationPreferences(
                    settings = settings,
                    // Hide alerts for features this shop does not use: no point
                    // offering "cash does not match" to a shop with no drawer count.
                    visibleTypes = NotificationType.visibleTo(permissions).filter { type ->
                        when (type) {
                            NotificationType.DAY_CLOSED,
                            NotificationType.CASH_SHORTAGE -> usesCashDrawer
                            NotificationType.LOW_STOCK,
                            NotificationType.OUT_OF_STOCK -> tracksStock
                            NotificationType.CREDIT_GIVEN,
                            NotificationType.CREDIT_LIMIT -> creditEnabled
                            NotificationType.STAFF_SIGN_IN -> hasStaff
                            else -> true
                        }
                    },
                    onMaster = { viewModel.setNotificationsEnabled(it) },
                    onType = { type, on -> viewModel.setNotificationType(type, on) },
                    onThresholds = { sale, disc -> viewModel.setNotificationThresholds(sale, disc) },
                    onQuietHours = { on -> viewModel.setQuietHours(on) }
                )
            }
        }
    }
}

@Composable
private fun NotificationRow(
    title: String,
    body: String,
    actor: String,
    timestamp: Long,
    importance: String,
    isRead: Boolean
) {
    val tone = when (importance) {
        NotificationImportance.HIGH.name -> StatusAmber
        NotificationImportance.QUIET.name -> TextMuted
        else -> BrandGoldPrimary
    }
    val toneBg = when (importance) {
        NotificationImportance.HIGH.name -> StatusAmberBg
        NotificationImportance.QUIET.name -> LightSurfaceVariant
        else -> BrandGoldSurface
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isRead) LightSurface else BrandGoldSurface
        ),
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(toneBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (importance == NotificationImportance.HIGH.name) {
                        Icons.Default.PriorityHigh
                    } else {
                        Icons.Default.Notifications
                    },
                    contentDescription = null,
                    tint = tone,
                    modifier = Modifier.size(17.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                Text(body, fontSize = 12.sp, color = TextSecondary)
                Text(
                    CurrencyUtils.formatDateTime(timestamp),
                    fontSize = 10.sp,
                    color = TextMuted,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
    }
}

/** The switches. Grouped so the list does not read as fourteen equal choices. */
@Composable
private fun NotificationPreferences(
    settings: com.example.data.model.NotificationSettingsEntity,
    visibleTypes: List<NotificationType>,
    onMaster: (Boolean) -> Unit,
    onType: (NotificationType, Boolean) -> Unit,
    onThresholds: (Double?, Double?) -> Unit,
    onQuietHours: (Boolean) -> Unit
) {
    var saleText by remember(settings.largeSaleThreshold) {
        mutableStateOf(settings.largeSaleThreshold.toLong().toString())
    }
    var discountText by remember(settings.largeDiscountThreshold) {
        mutableStateOf(settings.largeDiscountThreshold.toLong().toString())
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (settings.enabled) BrandGoldSurface else LightSurfaceVariant
                ),
                border = CardDefaults.outlinedCardBorder(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Tell me what is happening",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextPrimary
                        )
                        Text(
                            if (settings.enabled) {
                                "Alerts are on. Choose which ones below."
                            } else {
                                "All alerts are off. Nothing will be recorded."
                            },
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                    Switch(checked = settings.enabled, onCheckedChange = onMaster)
                }
            }
        }

        if (!settings.enabled) {
            item {
                HintCard(
                    text = "With alerts off you will not see sales, refunds or stock " +
                        "warnings here. Turn the switch above back on to start again."
                )
            }
            return@LazyColumn
        }

        item {
            Text(
                "Amounts worth telling me about",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextSecondary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = saleText,
                    onValueChange = { saleText = it.filter(Char::isDigit) },
                    label = { Text("Big sale over", fontSize = 11.sp) },
                    prefix = { Text("Rs. ", fontSize = 12.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = discountText,
                    onValueChange = { discountText = it.filter(Char::isDigit) },
                    label = { Text("Discount over", fontSize = 11.sp) },
                    prefix = { Text("Rs. ", fontSize = 12.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            TextButton(
                onClick = {
                    onThresholds(saleText.toDoubleOrNull(), discountText.toDoubleOrNull())
                }
            ) {
                Text("Save amounts", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Stay silent at night", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Between 9pm and 7am alerts are still recorded, " +
                            "but your phone will not buzz.",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
                Switch(checked = settings.quietHoursEnabled, onCheckedChange = onQuietHours)
            }
        }

        item {
            Text(
                "WHAT TO TELL ME ABOUT",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextSecondary,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        items(visibleTypes, key = { it.key }) { type ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            type.title,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        if (type.importance == NotificationImportance.HIGH) {
                            Spacer(modifier = Modifier.width(5.dp))
                            Icon(
                                Icons.Default.PriorityHigh,
                                contentDescription = "Important",
                                tint = StatusAmber,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                    Text(type.description, fontSize = 11.sp, color = TextSecondary)
                }
                Switch(
                    checked = settings.isOn(type),
                    onCheckedChange = { onType(type, it) }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}
