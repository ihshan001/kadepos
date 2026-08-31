package com.example.ui.screens.printer

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BusinessProfileEntity
import com.example.data.service.PrinterDevice
import com.example.data.service.PrinterStatus
import com.example.data.service.PrinterTransport
import com.example.ui.components.EmptyState
import com.example.ui.components.HintCard
import com.example.ui.components.HintTone
import com.example.ui.components.PrimaryActionButton
import com.example.ui.components.SectionLabel
import com.example.ui.theme.*
import com.example.ui.viewmodel.PosViewModel

/**
 * Real printer setup. Nothing here pretends: if no printer answers, the user is
 * told exactly what to check.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterSetupScreen(
    viewModel: PosViewModel,
    profile: BusinessProfileEntity?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val status by viewModel.printerStatus.collectAsState()
    val bluetoothPrinters by viewModel.bluetoothPrinters.collectAsState()
    val wifiPrinters by viewModel.wifiPrinters.collectAsState()
    val isScanning by viewModel.isScanningPrinters.collectAsState()

    var tab by remember { mutableIntStateOf(if (profile?.printerConnectionType == "WIFI") 1 else 0) }
    var manualIp by remember { mutableStateOf("") }
    var manualPort by remember { mutableStateOf("9100") }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            viewModel.refreshBluetoothPrinters()
        } else {
            viewModel.showMessage("Bluetooth access is needed to find your printer")
        }
    }

    LaunchedEffect(Unit) {
        if (viewModel.hasBluetoothPermission()) viewModel.refreshBluetoothPrinters()
    }

    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
            .only(WindowInsetsSides.Top),
        containerColor = LightBackground,
        topBar = {
            TopAppBar(
                title = { Text("Printer", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LightSurface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ConnectionStatusCard(
                status = status,
                paperWidth = profile?.printerPaperWidth ?: "58mm",
                onTestPrint = { viewModel.printTestReceipt() },
                onDisconnect = { viewModel.disconnectPrinter() },
                onForget = { viewModel.forgetPrinter() }
            )

            // Paper size
            Column {
                SectionLabel("Paper size")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf("58mm", "80mm").forEach { width ->
                        val selected = (profile?.printerPaperWidth ?: "58mm") == width
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected) BrandMintSurface else LightSurface
                            ),
                            border = BorderStroke(
                                if (selected) 2.dp else 1.dp,
                                if (selected) BrandTealPrimary else LightBorder
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    profile?.let {
                                        viewModel.saveBusinessProfile(it.copy(printerPaperWidth = width))
                                    }
                                }
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(width, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text(
                                    if (width == "58mm") "Pocket printers" else "Counter printers",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            // Auto print
            profile?.let { p ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = LightSurface),
                    border = BorderStroke(1.dp, LightBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Print as soon as paid", fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text(
                                "No extra tap after taking the money",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = p.autoPrint,
                            onCheckedChange = { viewModel.saveBusinessProfile(p.copy(autoPrint = it)) },
                            colors = SwitchDefaults.colors(checkedTrackColor = BrandTealPrimary)
                        )
                    }
                }
            }

            SectionLabel("Find your printer")

            TabRow(
                selectedTabIndex = tab,
                containerColor = LightSurface,
                contentColor = BrandTealPrimary
            ) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text("Bluetooth", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.Bluetooth, contentDescription = null) },
                    modifier = Modifier.testTag("tab_bluetooth")
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text("Wi-Fi", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.Wifi, contentDescription = null) },
                    modifier = Modifier.testTag("tab_wifi")
                )
            }

            if (tab == 0) {
                BluetoothSection(
                    printers = bluetoothPrinters,
                    connectedAddress = (status as? PrinterStatus.Connected)?.device?.address,
                    hasPermission = viewModel.hasBluetoothPermission(),
                    onRequestPermission = { permissionLauncher.launch(viewModel.bluetoothPermissions()) },
                    onRefresh = { viewModel.refreshBluetoothPrinters() },
                    onConnect = { viewModel.connectPrinter(it) },
                    onOpenSettings = {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                )
            } else {
                WifiSection(
                    printers = wifiPrinters,
                    isScanning = isScanning,
                    connectedAddress = (status as? PrinterStatus.Connected)?.device?.address,
                    manualIp = manualIp,
                    onManualIpChange = { manualIp = it },
                    manualPort = manualPort,
                    onManualPortChange = { manualPort = it.filter(Char::isDigit).take(5) },
                    onScan = { viewModel.scanWifiPrinters(manualPort.toIntOrNull() ?: 9100) },
                    onConnect = { viewModel.connectPrinter(it) },
                    onConnectManual = {
                        viewModel.connectWifiPrinterManually(
                            ip = manualIp.trim(),
                            port = manualPort.toIntOrNull() ?: 9100,
                            name = "Printer at ${manualIp.trim()}"
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ConnectionStatusCard(
    status: PrinterStatus,
    paperWidth: String,
    onTestPrint: () -> Unit,
    onDisconnect: () -> Unit,
    onForget: () -> Unit
) {
    val (dotColour, headline, detail) = when (status) {
        is PrinterStatus.Connected ->
            Triple(StatusGreen, status.device.name, "Ready • ${status.device.transport.label} • $paperWidth")
        is PrinterStatus.Printing ->
            Triple(StatusAmber, status.deviceName, "Printing…")
        is PrinterStatus.Connecting ->
            Triple(StatusAmber, status.deviceName, "Connecting…")
        is PrinterStatus.Searching ->
            Triple(StatusBlue, "Searching", "Looking for ${status.what}…")
        is PrinterStatus.Failed ->
            Triple(StatusRed, "Not connected", status.reason)
        PrinterStatus.Disconnected ->
            Triple(TextMuted, "No printer connected", "Pick one below to get started")
    }

    val isLive = status is PrinterStatus.Connected || status is PrinterStatus.Printing

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = LightSurface),
        border = BorderStroke(1.dp, if (isLive) BrandTealPrimary else LightBorder)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (isLive) BrandMintSurface else LightSurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Print,
                        contentDescription = null,
                        tint = if (isLive) BrandTealPrimary else TextMuted
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(headline, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(dotColour)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(detail, fontSize = 12.sp, color = TextSecondary, lineHeight = 16.sp)
                    }
                }
            }

            if (isLive) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onTestPrint,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("test_print_button")
                    ) {
                        Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test page", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    OutlinedButton(
                        onClick = onDisconnect,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Disconnect", fontSize = 13.sp)
                    }
                }
                TextButton(onClick = onForget, modifier = Modifier.padding(top = 2.dp)) {
                    Text("Forget this printer", fontSize = 12.sp, color = StatusRed)
                }
            }
        }
    }
}

@Composable
private fun BluetoothSection(
    printers: List<PrinterDevice>,
    connectedAddress: String?,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onRefresh: () -> Unit,
    onConnect: (PrinterDevice) -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!hasPermission) {
            HintCard(
                text = "Arro-POS needs permission to use Bluetooth so it can talk to your printer.",
                tone = HintTone.WARN
            )
            PrimaryActionButton(
                text = "Allow Bluetooth",
                onClick = onRequestPermission,
                icon = Icons.Default.Bluetooth
            )
            return@Column
        }

        HintCard(
            text = "Pair your printer once in phone Settings > Bluetooth, then pick it here.",
            tone = HintTone.INFO
        )

        if (printers.isEmpty()) {
            EmptyState(
                icon = Icons.Default.BluetoothSearching,
                title = "No paired printers",
                message = "Turn the printer on, pair it in your phone's Bluetooth settings, then come back and refresh.",
                actionText = "Open Bluetooth settings",
                onAction = onOpenSettings
            )
        } else {
            printers.forEach { printer ->
                PrinterRow(
                    printer = printer,
                    isConnected = printer.address == connectedAddress,
                    onConnect = { onConnect(printer) }
                )
            }
        }

        OutlinedButton(
            onClick = onRefresh,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("refresh_bluetooth")
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Refresh list")
        }
    }
}

@Composable
private fun WifiSection(
    printers: List<PrinterDevice>,
    isScanning: Boolean,
    connectedAddress: String?,
    manualIp: String,
    onManualIpChange: (String) -> Unit,
    manualPort: String,
    onManualPortChange: (String) -> Unit,
    onScan: () -> Unit,
    onConnect: (PrinterDevice) -> Unit,
    onConnectManual: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HintCard(
            text = "Your printer and this phone must be on the same Wi-Fi. Most network printers use port 9100.",
            tone = HintTone.INFO
        )

        if (isScanning) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = BrandTealPrimary)
                Spacer(modifier = Modifier.width(10.dp))
                Text("Searching your Wi-Fi…", fontSize = 13.sp, color = TextSecondary)
            }
        }

        printers.forEach { printer ->
            PrinterRow(
                printer = printer,
                isConnected = printer.address == connectedAddress,
                onConnect = { onConnect(printer) }
            )
        }

        if (printers.isEmpty() && !isScanning) {
            EmptyState(
                icon = Icons.Default.WifiFind,
                title = "No Wi-Fi printer found yet",
                message = "Tap search to scan your network, or type the printer's IP address below."
            )
        }

        Button(
            onClick = onScan,
            enabled = !isScanning,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BrandTealPrimary),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("scan_wifi")
        ) {
            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Search my Wi-Fi", fontWeight = FontWeight.Bold)
        }

        HorizontalDivider(color = LightBorder)

        SectionLabel("Or type the address")

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = manualIp,
                onValueChange = onManualIpChange,
                modifier = Modifier
                    .weight(2f)
                    .testTag("manual_ip"),
                placeholder = { Text("192.168.1.50", fontSize = 14.sp) },
                label = { Text("IP address") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandTealPrimary)
            )
            OutlinedTextField(
                value = manualPort,
                onValueChange = onManualPortChange,
                modifier = Modifier.weight(1f),
                label = { Text("Port") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandTealPrimary)
            )
        }

        OutlinedButton(
            onClick = onConnectManual,
            enabled = manualIp.isNotBlank(),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("connect_manual_wifi")
        ) {
            Text("Connect to this address")
        }
    }
}

@Composable
private fun PrinterRow(
    printer: PrinterDevice,
    isConnected: Boolean,
    onConnect: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) BrandMintSurface else LightSurface
        ),
        border = BorderStroke(1.dp, if (isConnected) BrandTealPrimary else LightBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isConnected, onClick = onConnect)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (printer.transport == PrinterTransport.BLUETOOTH) Icons.Default.Bluetooth else Icons.Default.Wifi,
                contentDescription = null,
                tint = if (isConnected) BrandTealPrimary else TextSecondary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(printer.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                Text(
                    if (printer.transport == PrinterTransport.WIFI) {
                        "${printer.address}:${printer.port}"
                    } else {
                        printer.address
                    },
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
            if (isConnected) {
                Surface(shape = RoundedCornerShape(20.dp), color = StatusGreenBg) {
                    Text(
                        "Connected",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = StatusGreen,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            } else {
                Text("Connect", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = BrandTealPrimary)
            }
        }
    }
}
