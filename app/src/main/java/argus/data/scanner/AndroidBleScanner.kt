package argus.data.scanner

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import argus.domain.model.RawBleSignal

sealed interface BleScannerError {
    data object BleUnavailable : BleScannerError
    data class ScanFailed(val errorCode: Int) : BleScannerError
}

/**
 * Android BluetoothLeScanner를 LOW_LATENCY 모드로 래핑하여 RawBleSignal을 방출한다.
 */
class AndroidBleScanner(
    context: Context, private val onSignal: (RawBleSignal) -> Unit, private val onError: (BleScannerError) -> Unit = {}
) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val scanner: BluetoothLeScanner?
        get() = bluetoothManager?.adapter?.bluetoothLeScanner

    private var isScanning = false

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val record = result.scanRecord ?: return
            val uuids16Bit = record.serviceUuids?.mapNotNull { parcelUuid ->
                    val uuidStr = parcelUuid.uuid.toString()
                    if (uuidStr.length >= 8) uuidStr.substring(4, 8).uppercase() else null
                }.orEmpty()

            var companyId: Int? = null
            var manufacturerBytes: ByteArray? = null
            record.manufacturerSpecificData?.let { sparseArray ->
                if (sparseArray.size() > 0) {
                    companyId = sparseArray.keyAt(0)
                    manufacturerBytes = sparseArray.valueAt(0)
                }
            }

            @SuppressLint("MissingPermission") val name = record.deviceName ?: result.device?.name

            onSignal(
                RawBleSignal(
                    deviceName = name,
                    serviceUuids16Bit = uuids16Bit,
                    companyId = companyId,
                    manufacturerData = manufacturerBytes,
                    rssi = result.rssi,
                    timestampMs = System.currentTimeMillis()
                )
            )
        }

        override fun onScanFailed(errorCode: Int) {
            isScanning = false
            onError(BleScannerError.ScanFailed(errorCode))
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (isScanning) return
        val activeScanner = scanner
        if (activeScanner == null) {
            onError(BleScannerError.BleUnavailable)
            return
        }

        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE).build()

        activeScanner.startScan(null, settings, scanCallback)
        isScanning = true
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        if (!isScanning) return
        runCatching {
            scanner?.stopScan(scanCallback)
        }
        isScanning = false
    }
}
