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
import com.example.ui.components.AppTextField
import com.example.ui.components.keyboardPadding
import com.example.ui.theme.*
import com.example.ui.viewmodel.PosViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Owner-facing backup screen. Visible only when the provider has enabled the
 * cloud feature. The owner can connect a Google account, back up and sync; the
 * master switch stays with the provider screen.
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
    val accountsPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { showAccountDialog = true }

    LaunchedEffect(Unit) { viewModel.refreshCloud() }

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
                item {
                    StatusCard(cloud)
                }

                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = LightSurface),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Connected Google Account", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            if (cloud.ownerGmail.isNotBlank()) {
                                Text(cloud.ownerGmail, color = BrandPrimary, fontWeight = FontWeight.SemiBold)
                                Text("Backups from this device use this account.", fontSize = 12.sp, color = TextSecondary)
                            } else {
                                Text("No account connected yet. Selling still works normally.", fontSize = 13.sp, color = StatusRed)
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.GET_ACCOUNTS
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) {
                                        showAccountDialog = true
                                    } else {
                                        accountsPermission.launch(Manifest.permission.GET_ACCOUNTS)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.AccountCircle, contentDescription = null, Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (cloud.ownerGmail.isBlank()) "Connect Google account" else "Change Google account")
                            }
                        }
                    }
                }

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
                                    Text(
                                        "Copy this shop's data",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
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
                                "A backup is created before every sync. Sync only runs when there are new changes.",
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

                item {
                    Text(
                        "Google Drive is used only for backup copies from this device. Your shop data stays on this phone and selling never waits for the cloud.",
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
        }
    }
}

@Composable
fun ConnectAccountDialog(
    settings: CloudSettings,
    accounts: List<String>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var manual by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connect Google account") },
        text = {
            Column {
                Text(
                    "Use the Google account that should receive this device's backups. Sync runs hourly only when new changes exist.",
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
            // A dialog has its own window, so the content scrolls and keeps the
            // focused field clear of the keyboard.
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
                        "This controls cloud backup and Google Drive sync. The owner can only connect an account and press Backup/Sync.",
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
