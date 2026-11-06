package com.fariddroid.gtbikecompanion.data

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.util.size
import com.fariddroid.gtbikecompanion.core.model.BleAdvertisement
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Why the phone can or cannot list Bluetooth sensors right now. */
enum class ScanAvailability { READY, NO_BLUETOOTH, PERMISSION_NEEDED, BLUETOOTH_OFF, LOCATION_OFF }

class ScanFailedException(val errorCode: Int) : Exception("BLE scan failed: $errorCode")

/**
 * P11: listens to BLE advertisements only. It never connects, bonds or reads GATT, so the sensor stays free for
 * the PC (most sensors accept a single connection). Android 12+: BLUETOOTH_SCAN declared with neverForLocation.
 * Android 8-11: the platform requires location permission and location services for any BLE scan.
 */
@Singleton
class PhoneSensorScanner @Inject constructor(@ApplicationContext private val context: Context) {
    private val manager get() = context.getSystemService(BluetoothManager::class.java)

    val requiredPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) arrayOf(Manifest.permission.BLUETOOTH_SCAN)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    fun hasPermission(): Boolean = requiredPermissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    fun availability(): ScanAvailability {
        val adapter = manager?.adapter
        if (adapter == null || !context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) return ScanAvailability.NO_BLUETOOTH
        if (!hasPermission()) return ScanAvailability.PERMISSION_NEEDED
        if (!adapter.isEnabled) return ScanAvailability.BLUETOOTH_OFF
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            val location = context.getSystemService(LocationManager::class.java)
            if (location == null || !LocationManagerCompat.isLocationEnabled(location)) return ScanAvailability.LOCATION_OFF
        }
        return ScanAvailability.READY
    }

    /** Cold flow: scanning starts on collection and stops when the collector is cancelled. */
    @SuppressLint("MissingPermission") // availability() == READY is checked right before startScan.
    fun advertisements(): Flow<BleAdvertisement> = callbackFlow {
        val scanner = manager?.adapter?.bluetoothLeScanner
        if (availability() != ScanAvailability.READY || scanner == null) {
            close(ScanFailedException(-1))
            return@callbackFlow
        }
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                trySend(result.toAdvertisement())
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { trySend(it.toAdvertisement()) }
            }

            override fun onScanFailed(errorCode: Int) {
                close(ScanFailedException(errorCode))
            }
        }
        // No filters: the PC's own rules decide what counts as a sensor (BleSensorClassifier).
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(null, settings, callback)
        awaitClose { runCatching { scanner.stopScan(callback) } }
    }

    private fun ScanResult.toAdvertisement(): BleAdvertisement {
        val record = scanRecord
        val mfg = buildMap {
            val data = record?.manufacturerSpecificData
            if (data != null) for (i in 0 until data.size) put(data.keyAt(i), data.valueAt(i))
        }
        return BleAdvertisement(
            // BluetoothDevice.getAddress needs no permission; the name comes from the advertisement, not the device.
            address = device.address.uppercase(Locale.ROOT),
            name = record?.deviceName,
            serviceUuids = record?.serviceUuids.orEmpty().map { it.uuid.toString().lowercase(Locale.ROOT) }.toSet(),
            manufacturerData = mfg,
            rssi = rssi,
        )
    }
}
