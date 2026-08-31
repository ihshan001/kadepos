package com.example.data.service

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

/** How a printer is physically reached. */
enum class PrinterTransport(val label: String) {
    BLUETOOTH("Bluetooth"),
    WIFI("Wi-Fi / Network")
}

/** A printer the user can pick from the list. */
data class PrinterDevice(
    val name: String,
    val address: String, // MAC for Bluetooth, IP for Wi-Fi
    val transport: PrinterTransport,
    val port: Int = 9100,
    val isPaired: Boolean = false
) {
    val id: String get() = "${transport.name}:$address:$port"
}

sealed class PrinterStatus {
    /** Nothing connected. */
    data object Disconnected : PrinterStatus()
    data class Searching(val what: String) : PrinterStatus()
    data class Connecting(val deviceName: String) : PrinterStatus()
    data class Connected(val device: PrinterDevice) : PrinterStatus()
    data class Printing(val deviceName: String) : PrinterStatus()
    data class Failed(val reason: String) : PrinterStatus()
}

/**
 * Real thermal receipt printing over Bluetooth SPP (RFCOMM) and over Wi-Fi
 * (raw TCP to the ESC/POS port, normally 9100).
 *
 * There is deliberately no fake "simulated" success anywhere in here: when
 * there is no printer the caller gets an honest failure so the UI can tell the
 * shopkeeper what to fix.
 */
class PrinterService(private val context: Context) {

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var btSocket: BluetoothSocket? = null
    private var wifiSocket: Socket? = null
    private var outputStream: OutputStream? = null

    private val _status = MutableStateFlow<PrinterStatus>(PrinterStatus.Disconnected)
    val status: StateFlow<PrinterStatus> = _status.asStateFlow()

    private val _bluetoothPrinters = MutableStateFlow<List<PrinterDevice>>(emptyList())
    val bluetoothPrinters: StateFlow<List<PrinterDevice>> = _bluetoothPrinters.asStateFlow()

    private val _wifiPrinters = MutableStateFlow<List<PrinterDevice>>(emptyList())
    val wifiPrinters: StateFlow<List<PrinterDevice>> = _wifiPrinters.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    /** Standard Serial Port Profile UUID used by every thermal printer. */
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    val connectedDevice: PrinterDevice?
        get() = (_status.value as? PrinterStatus.Connected)?.device
            ?: (_status.value as? PrinterStatus.Printing)?.let { null }

    val isBluetoothSupported: Boolean get() = bluetoothAdapter != null
    val isBluetoothEnabled: Boolean get() = bluetoothAdapter?.isEnabled == true

    /** Permissions the app must hold before it can talk to a Bluetooth printer. */
    val requiredBluetoothPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        } else {
            arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)
        }

    fun hasBluetoothPermission(): Boolean = requiredBluetoothPermissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    // ------------------------------------------------------------------
    // Discovery
    // ------------------------------------------------------------------

    /**
     * Lists printers already paired in Android's Bluetooth settings.
     * Returns a human readable problem string, or null when it worked.
     */
    @SuppressLint("MissingPermission")
    fun refreshBluetoothPrinters(): String? {
        if (!isBluetoothSupported) {
            _bluetoothPrinters.value = emptyList()
            return "This phone does not have Bluetooth."
        }
        if (!hasBluetoothPermission()) {
            _bluetoothPrinters.value = emptyList()
            return "Allow Bluetooth access so we can find your printer."
        }
        if (!isBluetoothEnabled) {
            _bluetoothPrinters.value = emptyList()
            return "Turn on Bluetooth to see your printer."
        }
        return try {
            _bluetoothPrinters.value = bluetoothAdapter?.bondedDevices.orEmpty()
                .map { device ->
                    PrinterDevice(
                        name = device.name ?: "Bluetooth device",
                        address = device.address,
                        transport = PrinterTransport.BLUETOOTH,
                        isPaired = true
                    )
                }
                .sortedBy { it.name }
            if (_bluetoothPrinters.value.isEmpty()) {
                "No paired printers yet. Pair your printer in phone Settings > Bluetooth first."
            } else {
                null
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Bluetooth permission denied: ${e.message}")
            _bluetoothPrinters.value = emptyList()
            "Allow Bluetooth access so we can find your printer."
        }
    }

    /**
     * Scans the phone's Wi-Fi subnet for devices answering on the ESC/POS port.
     * Real TCP connects — anything that answers is a printer we can use.
     */
    suspend fun scanWifiPrinters(port: Int = 9100): String? = withContext(Dispatchers.IO) {
        _isScanning.value = true
        _status.value = PrinterStatus.Searching("Wi-Fi printers")
        try {
            val base = localSubnetPrefix()
            if (base == null) {
                _wifiPrinters.value = emptyList()
                return@withContext "Connect this phone to the same Wi-Fi as your printer."
            }

            val found = coroutineScope {
                (1..254).map { host ->
                    async {
                        val ip = "$base.$host"
                        if (probe(ip, port, timeoutMs = 220)) {
                            PrinterDevice(
                                name = "Network printer ($ip)",
                                address = ip,
                                transport = PrinterTransport.WIFI,
                                port = port
                            )
                        } else {
                            null
                        }
                    }
                }.awaitAll().filterNotNull()
            }

            _wifiPrinters.value = found
            if (found.isEmpty()) {
                "No Wi-Fi printer answered on $base.x. Check the printer is on the same Wi-Fi."
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Wi-Fi scan failed: ${e.message}")
            "Could not search the network: ${e.localizedMessage ?: "unknown error"}"
        } finally {
            _isScanning.value = false
            if (_status.value is PrinterStatus.Searching) {
                _status.value = PrinterStatus.Disconnected
            }
        }
    }

    /** Checks one manually typed IP address so users can skip the scan. */
    suspend fun checkWifiPrinter(ip: String, port: Int = 9100): Boolean =
        withContext(Dispatchers.IO) { probe(ip, port, timeoutMs = 2500) }

    private fun probe(ip: String, port: Int, timeoutMs: Int): Boolean = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(ip, port), timeoutMs)
            socket.isConnected
        }
    } catch (e: Exception) {
        false
    }

    @Suppress("DEPRECATION")
    private fun localSubnetPrefix(): String? {
        return try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val ipInt = wifi?.connectionInfo?.ipAddress ?: 0
            if (ipInt != 0) {
                val ip = String.format(
                    "%d.%d.%d",
                    ipInt and 0xff,
                    ipInt shr 8 and 0xff,
                    ipInt shr 16 and 0xff
                )
                return ip
            }
            // Fall back to walking the network interfaces (works on hotspot/ethernet too)
            java.net.NetworkInterface.getNetworkInterfaces().toList()
                .asSequence()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList().asSequence() }
                .filterIsInstance<java.net.Inet4Address>()
                .map { it.hostAddress ?: "" }
                .firstOrNull { it.count { ch -> ch == '.' } == 3 }
                ?.substringBeforeLast(".")
        } catch (e: Exception) {
            Log.w(TAG, "Could not determine local subnet: ${e.message}")
            null
        }
    }

    // ------------------------------------------------------------------
    // Connect / disconnect
    // ------------------------------------------------------------------

    /** Returns null on success, or a plain-language error for the user. */
    @SuppressLint("MissingPermission")
    suspend fun connect(device: PrinterDevice): String? = withContext(Dispatchers.IO) {
        disconnect()
        _status.value = PrinterStatus.Connecting(device.name)

        val error = when (device.transport) {
            PrinterTransport.BLUETOOTH -> connectBluetooth(device)
            PrinterTransport.WIFI -> connectWifi(device)
        }

        if (error == null) {
            _status.value = PrinterStatus.Connected(device)
        } else {
            _status.value = PrinterStatus.Failed(error)
        }
        error
    }

    @SuppressLint("MissingPermission")
    private fun connectBluetooth(device: PrinterDevice): String? {
        if (!isBluetoothSupported) return "This phone does not have Bluetooth."
        if (!hasBluetoothPermission()) return "Allow Bluetooth access, then try again."
        if (!isBluetoothEnabled) return "Turn on Bluetooth, then try again."

        return try {
            val remote: BluetoothDevice = bluetoothAdapter!!.getRemoteDevice(device.address)
            // Stop discovery first — it makes RFCOMM connects fail.
            try {
                bluetoothAdapter.cancelDiscovery()
            } catch (ignored: SecurityException) {
            }

            val socket = remote.createRfcommSocketToServiceRecord(sppUuid)
            try {
                socket.connect()
            } catch (first: IOException) {
                // Well known workaround for printers that don't publish the SPP record.
                Log.w(TAG, "Standard RFCOMM failed, trying fallback channel: ${first.message}")
                try {
                    socket.close()
                } catch (ignored: IOException) {
                }
                val fallback = remote.javaClass
                    .getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                    .invoke(remote, 1) as BluetoothSocket
                fallback.connect()
                btSocket = fallback
                outputStream = fallback.outputStream
                return null
            }
            btSocket = socket
            outputStream = socket.outputStream
            null
        } catch (e: SecurityException) {
            "Allow Bluetooth access, then try again."
        } catch (e: IOException) {
            "Could not reach ${device.name}. Make sure it is switched on and close by."
        } catch (e: Exception) {
            "Could not connect: ${e.localizedMessage ?: "unknown error"}"
        }
    }

    private fun connectWifi(device: PrinterDevice): String? = try {
        val socket = Socket()
        socket.connect(InetSocketAddress(device.address, device.port), 6000)
        socket.keepAlive = true
        wifiSocket = socket
        outputStream = socket.getOutputStream()
        null
    } catch (e: Exception) {
        "Could not reach ${device.address}:${device.port}. Check the printer is on the same Wi-Fi."
    }

    fun disconnect() {
        try {
            outputStream?.flush()
        } catch (ignored: Exception) {
        }
        try {
            outputStream?.close()
        } catch (ignored: Exception) {
        }
        try {
            btSocket?.close()
        } catch (ignored: Exception) {
        }
        try {
            wifiSocket?.close()
        } catch (ignored: Exception) {
        }
        outputStream = null
        btSocket = null
        wifiSocket = null
        _status.value = PrinterStatus.Disconnected
    }

    val isConnected: Boolean
        get() = outputStream != null && (btSocket?.isConnected == true || wifiSocket?.isConnected == true)

    /** Reconnects to a saved printer, e.g. after the app restarts. */
    suspend fun reconnectSaved(
        name: String,
        address: String,
        transport: PrinterTransport,
        port: Int
    ): String? {
        if (address.isBlank()) return "No printer saved yet."
        return connect(PrinterDevice(name, address, transport, port))
    }

    // ------------------------------------------------------------------
    // Printing
    // ------------------------------------------------------------------

    /** Returns null on success, or a plain-language error. */
    suspend fun printRaw(data: ByteArray): String? = withContext(Dispatchers.IO) {
        val stream = outputStream
        val device = (_status.value as? PrinterStatus.Connected)?.device
        if (stream == null || !isConnected) {
            val message = "No printer connected. Open More > Printer to connect one."
            _status.value = PrinterStatus.Failed(message)
            return@withContext message
        }
        _status.value = PrinterStatus.Printing(device?.name ?: "printer")
        try {
            // Chunk the payload: many cheap printers choke on large single writes.
            var offset = 0
            val chunk = 256
            while (offset < data.size) {
                val end = minOf(offset + chunk, data.size)
                stream.write(data, offset, end - offset)
                stream.flush()
                offset = end
                Thread.sleep(12)
            }
            if (device != null) {
                _status.value = PrinterStatus.Connected(device)
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Print failed: ${e.message}")
            disconnect()
            val message = "Printing stopped. The printer may be off, out of paper or out of range."
            _status.value = PrinterStatus.Failed(message)
            message
        }
    }

    suspend fun printTestPage(
        storeName: String,
        phone: String,
        address: String,
        paperWidth: String
    ): String? {
        val cols = columnsFor(paperWidth)
        val bytes = EscPosBuilder()
            .init()
            .alignCenter()
            .bold(true)
            .doubleSize(true)
            .textLine(storeName.ifBlank { "My Shop" })
            .doubleSize(false)
            .apply { if (address.isNotBlank()) textLine(address) }
            .apply { if (phone.isNotBlank()) textLine(phone) }
            .bold(false)
            .textLine("-".repeat(cols))
            .bold(true)
            .textLine("TEST PRINT")
            .bold(false)
            .alignLeft()
            .textLine(
                "Date: " + java.text.SimpleDateFormat(
                    "dd/MM/yyyy HH:mm",
                    java.util.Locale.getDefault()
                ).format(java.util.Date())
            )
            .textLine("Paper: $paperWidth")
            .textLine("-".repeat(cols))
            .twoColumn("Sample item", "1,250.00", cols)
            .twoColumn("Another item", "480.00", cols)
            .textLine("-".repeat(cols))
            .bold(true)
            .twoColumn("TOTAL", "1,730.00", cols)
            .bold(false)
            .textLine("-".repeat(cols))
            .alignCenter()
            .textLine("If you can read this,")
            .textLine("your printer is ready.")
            .feed(4)
            .cutPaper()
            .build()
        return printRaw(bytes)
    }

    data class ReceiptLine(val name: String, val qty: Double, val lineTotal: Double)

    @Suppress("LongParameterList")
    suspend fun printReceipt(
        storeName: String,
        phone: String,
        address: String,
        invoiceNo: String,
        cashier: String,
        customerName: String,
        items: List<ReceiptLine>,
        subtotal: Double,
        discount: Double,
        tax: Double,
        grandTotal: Double,
        paymentMethod: String,
        cashReceived: Double,
        change: Double,
        footer: String,
        currencySymbol: String,
        paperWidth: String
    ): String? {
        val cols = columnsFor(paperWidth)
        fun money(value: Double) = "%,.2f".format(value)

        val builder = EscPosBuilder()
            .init()
            .alignCenter()
            .bold(true)
            .doubleSize(true)
            .textLine(storeName.ifBlank { "My Shop" })
            .doubleSize(false)
            .bold(false)
        if (address.isNotBlank()) builder.textLine(address)
        if (phone.isNotBlank()) builder.textLine(phone)
        builder.textLine("-".repeat(cols))
            .alignLeft()
            .textLine("Bill: $invoiceNo")
            .textLine(
                "Date: " + java.text.SimpleDateFormat(
                    "dd/MM/yyyy HH:mm",
                    java.util.Locale.getDefault()
                ).format(java.util.Date())
            )
        if (cashier.isNotBlank()) builder.textLine("Served by: $cashier")
        if (customerName.isNotBlank() && !customerName.equals("Walk-in", true)) {
            builder.textLine("Customer: $customerName")
        }
        builder.textLine("-".repeat(cols))

        for (item in items) {
            val qtyStr = if (item.qty % 1.0 == 0.0) item.qty.toInt().toString() else "%.2f".format(item.qty)
            builder.textLine(item.name.take(cols))
            builder.twoColumn("  x $qtyStr", money(item.lineTotal), cols)
        }

        builder.textLine("-".repeat(cols))
            .twoColumn("Subtotal", money(subtotal), cols)
        if (discount > 0) builder.twoColumn("Discount", "-" + money(discount), cols)
        if (tax > 0) builder.twoColumn("Tax", money(tax), cols)
        builder.bold(true)
            .doubleHeight(true)
            .twoColumn("TOTAL", "$currencySymbol ${money(grandTotal)}", cols)
            .doubleHeight(false)
            .bold(false)
            .textLine("-".repeat(cols))
            .twoColumn("Paid by", paymentMethod.lowercase().replaceFirstChar { it.uppercase() }, cols)

        if (paymentMethod.equals("CASH", true)) {
            builder.twoColumn("Received", money(cashReceived), cols)
            if (change > 0) builder.twoColumn("Change", money(change), cols)
        }

        builder.textLine("-".repeat(cols))
            .alignCenter()
            .textLine(footer.ifBlank { "Thank you! Please come again." })
            .feed(4)
            .cutPaper()

        return printRaw(builder.build())
    }

    private fun columnsFor(paperWidth: String) = if (paperWidth == "80mm") 48 else 32

    companion object {
        private const val TAG = "PrinterService"
    }
}

/**
 * Builds ESC/POS command bytes understood by 58mm and 80mm thermal printers.
 */
class EscPosBuilder {
    private val buffer = java.io.ByteArrayOutputStream()

    fun init() = apply { buffer.write(byteArrayOf(0x1B, 0x40)) }

    fun alignLeft() = apply { buffer.write(byteArrayOf(0x1B, 0x61, 0x00)) }

    fun alignCenter() = apply { buffer.write(byteArrayOf(0x1B, 0x61, 0x01)) }

    fun alignRight() = apply { buffer.write(byteArrayOf(0x1B, 0x61, 0x02)) }

    fun bold(enable: Boolean) = apply {
        buffer.write(byteArrayOf(0x1B, 0x45, if (enable) 0x01 else 0x00))
    }

    fun doubleSize(enable: Boolean) = apply {
        buffer.write(byteArrayOf(0x1D, 0x21, if (enable) 0x11 else 0x00))
    }

    fun doubleHeight(enable: Boolean) = apply {
        buffer.write(byteArrayOf(0x1D, 0x21, if (enable) 0x01 else 0x00))
    }

    fun text(value: String) = apply {
        buffer.write(value.toByteArray(charset("ISO-8859-1")))
    }

    fun textLine(value: String) = apply {
        text(value)
        buffer.write(0x0A)
    }

    fun twoColumn(left: String, right: String, totalCols: Int = 32) = apply {
        val maxLeft = (totalCols - right.length - 1).coerceAtLeast(1)
        val trimmedLeft = if (left.length > maxLeft) left.take(maxLeft) else left
        val gap = (totalCols - trimmedLeft.length - right.length).coerceAtLeast(1)
        textLine(trimmedLeft + " ".repeat(gap) + right)
    }

    fun feed(lines: Int = 1) = apply { buffer.write(byteArrayOf(0x1B, 0x64, lines.toByte())) }

    fun cutPaper() = apply { buffer.write(byteArrayOf(0x1D, 0x56, 0x42, 0x00)) }

    fun build(): ByteArray = buffer.toByteArray()
}
