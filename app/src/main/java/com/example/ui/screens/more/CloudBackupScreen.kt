package com.example.ui.screens.more

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.data.cloud.CloudSettings
import com.example.data.cloud.CloudSyncEvent
import com.example.data.cloud.GoogleDriveCloudTransport
import com.example.ui.components.AppTextField
import com.example.ui.components.keyboardPadding
import com.example.ui.theme.*
import com.example.ui.viewmodel.PosViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Owner-facing backup screen. Visible only when the provider has enabled the
 * cloud feature.
 *
 * The model is one **hub** Gmail (the owner's main account) that stores the
 * whole shop's data, plus any number of **linked** staff Gmails. A cashier
 * signs in with their own account; every device still writes into the same
 * shared Drive folder and the owner sees the snapshots arrive hour by hour.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudBackupScreen(
    viewModel: PosViewModel,
    onBack: () -> Unit
) {
    val settings by viewModel.cloudSettings.collectAsState()
    val cloud = settings ?: CloudSettings()
    val context = LocalContext.current
    var showAccountDialog by remember { mutableStateOf(false) }
    var showHubDialog by remember { mutableStateOf(false) }
    var showLinkDialog by remember { mutableStateOf(false) }

    // Hour-by-hour record: the local rolling history plus the live Drive folder.
    val localHistory = remember(cloud.syncHistory) { cloud.syncEvents() }
    var driveSnapshots by remember { mutableStateOf<List<GoogleDriveCloudTransport.DriveFileInfo>?>(null) }
    var loadingDrive by remember { mutableStateOf(false) }

    val accountsPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { showAccountDialog = true }

    LaunchedEffect(Unit) { viewModel.refreshCloud() }

    fun requestAccountPermission() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.GET_ACCOUNTS
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            showAccountDialog = true
        } else {
            accountsPermission.launch(Manifest.permission.GET_ACCOUNTS)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup & Cloud", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LightSurface)
            )
        },
        containerColor = LightBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!cloud.providerEnabled) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = LightSurfaceVariant),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Icon(Icons.Default.CloudOff, contentDescription = null, tint = TextSecondary)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Cloud backup is not available on this device", fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Ask your POS provider to activate backup and cloud for this app. The shop still records every sale on this phone.",
                                fontSize = 13.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
                item {
                    TextButton(onClick = { onBack() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Back to settings")
                    }
                }
            } else {
                item { StatusCard(cloud) }

                // --- The owner's main account (the hub) --------------------
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = LightSurface),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Cloud, contentDescription = null, tint = BrandPrimary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Main Google account", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "This is where the whole shop's data is stored. Staff phones feed into it with their own accounts.",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            if (cloud.hub().isNotBlank()) {
                                Text(cloud.hub(), color = BrandPrimary, fontWeight = FontWeight.SemiBold)
                            } else {
                                Text("No main account set yet.", fontSize = 13.sp, color = StatusRed)
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = { showHubDialog = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.AccountCircle, contentDescription = null, Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (cloud.hub().isBlank()) "Set main account" else "Change main account")
                            }
                        }
                    }
                }

                // --- The account this device signs in with -----------------
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = LightSurface),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("This phone's Google account", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            if (cloud.ownerGmail.isNotBlank()) {
                                Text(cloud.ownerGmail, color = BrandPrimary, fontWeight = FontWeight.SemiBold)
                                Text("This phone backs up under this account.", fontSize = 12.sp, color = TextSecondary)
                            } else {
                                Text("No account connected yet. Selling still works normally.", fontSize = 13.sp, color = StatusRed)
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = { requestAccountPermission() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Smartphone, contentDescription = null, Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (cloud.ownerGmail.isBlank()) "Connect Google account" else "Change this phone's account")
                            }
                        }
                    }
                }

                // --- Linked staff Gmails -----------------------------------
                item {
                    LinkedAccountsCard(
                        cloud = cloud,
                        onAdd = { showLinkDialog = true },
                        onRemove = { email -> viewModel.unlinkGmail(email) }
                    )
                }

                // --- Copy switch -------------------------------------------
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = LightSurface),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Copy this shop's data", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(
                                        "Turn it off to stop backups from this phone. Nothing is deleted.",
                                        fontSize = 12.sp,
                                        color = TextSecondary
                                    )
                                }
                                Switch(
                                    checked = cloud.ownerBackupEnabled,
                                    onCheckedChange = { viewModel.setOwnerBackupEnabled(it) }
                                )
                            }
                        }
                    }
                }

                // --- Backup / Sync actions --------------------------------
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = BrandSurface),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.backupNow() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Backup now", fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = { viewModel.syncNow() },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
                                ) {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null, Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Sync now", fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "A backup is created before every sync. Sync runs hourly, but only when there are new changes.",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }

                if (cloud.lastError.isNotBlank()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = StatusAmberBg),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(cloud.lastError, modifier = Modifier.padding(12.dp), color = StatusAmber, fontSize = 12.sp)
                        }
                    }
                }

                // --- Hour-by-hour history ----------------------------------
                item {
                    SyncHistoryCard(
                        localEvents = localHistory,
                        driveFiles = driveSnapshots,
                        loading = loadingDrive,
                        onRefresh = {
                            loadingDrive = true
                            driveSnapshots = null
                        }
                    )
                    LaunchedEffect(driveSnapshots, loadingDrive) {
                        if (loadingDrive) {
                            val files = viewModel.fetchDriveSnapshots()
                            driveSnapshots = files
                            loadingDrive = false
                        }
                    }
                }

                item {
                    Text(
                        "Google Drive holds the shop's backup copies. The shop keeps working on this phone even with no internet, and selling never waits for the cloud.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }

    if (showAccountDialog) {
        ConnectAccountDialog(
            settings = cloud,
            accounts = viewModel.googleAccounts(),
            onSelect = { email ->
                viewModel.setOwnerGmail(email)
                showAccountDialog = false
            },
            onDismiss = { showAccountDialog = false }
        )
    }

    if (showHubDialog) {
        ConnectAccountDialog(
            settings = cloud.copy(ownerGmail = cloud.hub()),
            accounts = viewModel.googleAccounts(),
            title = "Main Google account",
            onSelect = { email ->
                viewModel.setHubGmail(email)
                showHubDialog = false
            },
            onDismiss = { showHubDialog = false }
        )
    }

    if (showLinkDialog) {
        LinkGmailDialog(
            accounts = viewModel.googleAccounts(),
            alreadyLinked = cloud.linkedEmails().toSet(),
            hub = cloud.hub(),
            onSelect = { email ->
                viewModel.linkGmail(email)
                showLinkDialog = false
            },
            onDismiss = { showLinkDialog = false }
        )
    }
}

@Composable
private fun LinkedAccountsCard(
    cloud: CloudSettings,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit
) {
    val linked = cloud.linkedEmails()
    Card(
        colors = CardDefaults.cardColors(containerColor = LightSurface),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Group, contentDescription = null, tint = BrandPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Linked team accounts", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        "Staff back up with their own Gmail, not yours.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
                TextButton(onClick = onAdd) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Link")
                }
            }

            if (linked.isEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "No staff accounts linked. Link a staff member's Gmail so their phone backs up under their own account.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            } else {
                Spacer(modifier = Modifier.height(6.dp))
                linked.forEach { email ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    ) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(email, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onRemove(email) }) {
                            Text("Remove", fontSize = 12.sp, color = StatusRed)
                        }
                    }
                }
            }
        }
    }
}

/**
 * The hour-by-hour record. Shows the local rolling history plus, when
 * refreshed, the live list of snapshots in the shared Drive folder.
 */
@Composable
private fun SyncHistoryCard(
    localEvents: List<CloudSyncEvent>,
    driveFiles: List<GoogleDriveCloudTransport.DriveFileInfo>?,
    loading: Boolean,
    onRefresh: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = LightSurface),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.History, contentDescription = null, tint = BrandPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Hour-by-hour data", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Each copy is one snapshot of the shop.", fontSize = 12.sp, color = TextSecondary)
                }
                TextButton(onClick = onRefresh, enabled = !loading) {
                    Icon(Icons.Default.Refresh, contentDescription = null, Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (loading) "Loading…" else "Refresh")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when {
                loading -> {
                    Text("Reading the shared Drive folder…", fontSize = 12.sp, color = TextSecondary)
                }

                driveFiles != null -> {
                    if (driveFiles.isEmpty()) {
                        Text("No snapshots in Drive yet. Tap Sync now to create the first one.", fontSize = 12.sp, color = TextSecondary)
                    } else {
                        driveFiles.take(25).forEach { file ->
                            HistoryRow(name = file.name, whenText = driveTime(file.modifiedTime))
                        }
                    }
                }

                localEvents.isNotEmpty() -> {
                    localEvents.take(25).forEach { event ->
                        HistoryRow(
                            name = event.fileName,
                            whenText = timestamp(event.at),
                            detail = event.device.ifBlank { event.account },
                            ok = event.ok
                        )
                    }
                }

                else -> {
                    Text("No history yet. Syncs will appear here as they happen.", fontSize = 12.sp, color = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(name: String, whenText: String, detail: String = "", ok: Boolean = true) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
    ) {
        Box(
            modifier = Modifier.size(8.dp).clip(CircleShape)
                .background(if (ok) StatusGreen else StatusRed)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            if (detail.isNotBlank()) {
                Text(detail, fontSize = 11.sp, color = TextSecondary)
            }
        }
        Text(whenText, fontSize = 11.sp, color = TextSecondary)
    }
}

@Composable
private fun StatusCard(cloud: CloudSettings) {
    val lastBackup = cloud.lastBackupAt.takeIf { it > 0L }?.let { timestamp(it) }
    val lastSync = cloud.lastSyncAt.takeIf { it > 0L }?.let { timestamp(it) }
    Card(
        colors = CardDefaults.cardColors(containerColor = LightSurfaceVariant),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val healthy = cloud.lastError.isBlank() && cloud.ownerBackupEnabled
                Box(
                    modifier = Modifier.size(10.dp).clip(CircleShape)
                        .background(if (healthy) StatusGreen else StatusAmber)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    when {
                        !cloud.ownerBackupEnabled -> "Backup is switched off on this phone"
                        cloud.lastError.isNotBlank() -> "Cloud backup needs attention"
                        else -> "Cloud backup is active"
                    },
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Last backup: ${lastBackup ?: "Not yet"}", fontSize = 13.sp, color = TextSecondary)
            Text("Last sync: ${lastSync ?: "Not yet"}", fontSize = 13.sp, color = TextSecondary)
            Text("Device: ${cloud.deviceName.ifBlank { "This phone" }}", fontSize = 13.sp, color = TextSecondary)
            Text("Shared folder: arro-pos-${cloud.shopKey.ifBlank { "…" }}", fontSize = 11.sp, color = TextMuted)
        }
    }
}

@Composable
fun ConnectAccountDialog(
    settings: CloudSettings,
    accounts: List<String>,
    title: String = "Connect Google account",
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var manual by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    "Choose the Google account that should be used. Sync runs hourly only when new changes exist.",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))
                if (accounts.isNotEmpty()) {
                    Text("Accounts on this device", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    accounts.take(6).forEach { email ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (settings.ownerGmail.equals(email, true)) BrandSurface else LightSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { onSelect(email) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.AccountCircle, contentDescription = null, tint = BrandPrimary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(email, fontSize = 14.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
                OutlinedTextField(
                    value = manual,
                    onValueChange = { manual = it },
                    label = { Text("Or type a Gmail address") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (manual.isNotBlank()) onSelect(manual.trim()) },
                enabled = manual.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
            ) { Text("Connect") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun LinkGmailDialog(
    accounts: List<String>,
    alreadyLinked: Set<String>,
    hub: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var manual by remember { mutableStateOf("") }
    val candidates = accounts.filter { it !in alreadyLinked && !it.equals(hub, true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Link a staff Gmail") },
        text = {
            Column {
                Text(
                    "Pick a staff member's Gmail. Their phone will back up under their own account and feed into the main account.",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))
                if (candidates.isNotEmpty()) {
                    Text("Accounts on this device", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    candidates.take(6).forEach { email ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = LightSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { onSelect(email) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.AccountCircle, contentDescription = null, tint = BrandPrimary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(email, fontSize = 14.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
                OutlinedTextField(
                    value = manual,
                    onValueChange = { manual = it },
                    label = { Text("Or type a Gmail address") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (manual.isNotBlank()) onSelect(manual.trim()) },
                enabled = manual.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
            ) { Text("Link") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/**
 * Provider-only setup. The owner never reaches this by normal navigation; it is
 * opened by a hidden long-press in Settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderCloudScreen(
    viewModel: PosViewModel,
    onDismiss: () -> Unit
) {
    val settings by viewModel.cloudSettings.collectAsState()
    val cloud = settings ?: CloudSettings()
    var unlocked by remember { mutableStateOf(viewModel.isProviderUnlocked || cloud.providerAccessCodeHash.isBlank()) }
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val formScroll = rememberScrollState()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = LightSurface)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(formScroll)
                    .keyboardPadding()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Provider access", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
                }

                if (!unlocked) {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Provider Gmail") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        shape = RoundedCornerShape(10.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    AppTextField(
                        value = code,
                        onValueChange = { code = it },
                        label = "Access code",
                        keyboardType = KeyboardType.NumberPassword,
                        isSecret = true,
                        scrollState = formScroll,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (error.isNotBlank()) {
                        Text(error, color = StatusRed, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (viewModel.unlockProvider(email.trim(), code.trim())) {
                                unlocked = true
                                error = ""
                            } else {
                                error = "Access code or provider Gmail does not match."
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
                    ) { Text("Unlock") }
                } else {
                    var providerEmail by remember { mutableStateOf(cloud.providerEmail) }
                    var enabled by remember { mutableStateOf(cloud.providerEnabled) }
                    var hourly by remember { mutableStateOf(cloud.hourlySyncEnabled) }
                    var daily by remember { mutableStateOf(cloud.dailyBackupEnabled) }
                    var accessCode by remember { mutableStateOf("") }

                    Text(
                        "This controls cloud backup and Google Drive sync. The owner can only connect accounts and press Backup/Sync.",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Enable backup & cloud", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Owner sees Backup/Sync and can connect a Gmail", fontSize = 11.sp, color = TextSecondary)
                        }
                        Switch(checked = enabled, onCheckedChange = { enabled = it })
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = providerEmail,
                        onValueChange = { providerEmail = it },
                        label = { Text("Provider Gmail") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    AppTextField(
                        value = accessCode,
                        onValueChange = { accessCode = it },
                        label = "Access code (required on first setup)",
                        keyboardType = KeyboardType.NumberPassword,
                        isSecret = true,
                        scrollState = formScroll,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Hourly sync", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Only when there are new changes", fontSize = 11.sp, color = TextSecondary)
                        }
                        Switch(checked = hourly, onCheckedChange = { hourly = it })
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Daily backup", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Keep rolling safety copies on this phone", fontSize = 11.sp, color = TextSecondary)
                        }
                        Switch(checked = daily, onCheckedChange = { daily = it })
                    }
                    if (cloud.lastError.isNotBlank()) {
                        Text(cloud.lastError, color = StatusAmber, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val saved = viewModel.saveProviderCloud(
                                enabled = enabled,
                                providerEmail = providerEmail.trim(),
                                hourlySync = hourly,
                                dailyBackup = daily,
                                accessCode = accessCode.trim()
                            )
                            if (saved) onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
                    ) { Text("Save provider settings") }
                }
            }
        }
    }
}

private fun timestamp(value: Long): String = SimpleDateFormat("EEE, d MMM, h:mm a", Locale.getDefault()).format(Date(value))

private fun driveTime(iso: String): String = runCatching {
    // Drive returns e.g. "2026-09-03T10:30:00.000Z"; show just the readable time.
    val parsed = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).parse(iso)
    if (parsed == null) iso else SimpleDateFormat("EEE, d MMM, h:mm a", Locale.getDefault()).format(parsed)
}.getOrDefault(iso)
