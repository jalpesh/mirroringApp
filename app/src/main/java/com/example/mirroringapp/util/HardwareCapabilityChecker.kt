package com.example.mirroringapp.util

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import timber.log.Timber

/**
 * Checks hardware capabilities for different mirroring methods.
 */
object HardwareCapabilityChecker {
    
    data class Capabilities(
        val supportsDisplayPortAltMode: Boolean,
        val hasExternalDisplay: Boolean,
        val supportsWirelessDisplay: Boolean,
        val usbCVideoOutput: UsbCCapability,
        val deviceModel: String,
        val androidVersion: String
    )
    
    enum class UsbCCapability {
        SUPPORTED,           // Has DisplayPort Alt Mode
        NOT_SUPPORTED,       // USB-C is data/charging only
        UNKNOWN             // Cannot determine
    }
    
    /**
     * Check all hardware capabilities for mirroring.
     */
    fun checkCapabilities(context: Context): Capabilities {
        val displayManager = context.getSystemService(DisplayManager::class.java)
        val displays = displayManager?.displays ?: emptyArray()
        
        val hasExternalDisplay = displays.any { it.displayId != Display.DEFAULT_DISPLAY }
        
        // Check if device is known to support DisplayPort Alt Mode
        val usbCCapability = checkDisplayPortAltMode()
        
        val capabilities = Capabilities(
            supportsDisplayPortAltMode = usbCCapability == UsbCCapability.SUPPORTED,
            hasExternalDisplay = hasExternalDisplay,
            supportsWirelessDisplay = true, // All Android 8+ supports this
            usbCVideoOutput = usbCCapability,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            androidVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        )
        
        logCapabilities(capabilities)
        PersistentLogger.i("=== HARDWARE CAPABILITIES ===")
        PersistentLogger.i("Device: ${capabilities.deviceModel}")
        PersistentLogger.i("Android: ${capabilities.androidVersion}")
        PersistentLogger.i("DisplayPort Alt Mode: ${capabilities.supportsDisplayPortAltMode}")
        PersistentLogger.i("External Display Connected: ${capabilities.hasExternalDisplay}")
        PersistentLogger.i("USB-C Video: ${capabilities.usbCVideoOutput}")
        
        return capabilities
    }
    
    /**
     * Check if device supports DisplayPort Alt Mode.
     * Based on known device models.
     */
    private fun checkDisplayPortAltMode(): UsbCCapability {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val model = Build.MODEL.lowercase()
        
        // Known devices WITH DisplayPort Alt Mode
        val supportedDevices = listOf(
            // Samsung
            "sm-g95" to "samsung",  // Galaxy S8+
            "sm-g96" to "samsung",  // Galaxy S9
            "sm-g97" to "samsung",  // Galaxy S10
            "sm-g98" to "samsung",  // Galaxy S20
            "sm-g99" to "samsung",  // Galaxy S21
            "sm-s90" to "samsung",  // Galaxy S22
            "sm-s91" to "samsung",  // Galaxy S23
            "sm-s92" to "samsung",  // Galaxy S24
            
            // Google Pixel
            "pixel 3" to "google",
            "pixel 4" to "google",
            "pixel 5" to "google",
            "pixel 6" to "google",
            "pixel 7" to "google",
            "pixel 8" to "google",
            
            // OnePlus
            "gm1913" to "oneplus",  // OnePlus 7 Pro
            "hd1900" to "oneplus",  // OnePlus 7T
            "in2023" to "oneplus",  // OnePlus 8
            "le2123" to "oneplus",  // OnePlus 9
            
            // Huawei
            "mate 10" to "huawei",
            "mate 20" to "huawei",
            "mate 30" to "huawei",
            "p20" to "huawei",
            "p30" to "huawei",
            
            // LG
            "lg-h850" to "lge",     // LG G5
            "lg-h870" to "lge",     // LG G6 (some variants)
            "lg-h930" to "lge",     // LG V30
        )
        
        // Known devices WITHOUT DisplayPort Alt Mode
        val unsupportedDevices = listOf(
            "moto g6" to "motorola",
            "moto g7" to "motorola",
            "moto e" to "motorola",
            "moto g play" to "motorola",
            "moto g power" to "motorola",
        )
        
        // Check if device is in supported list
        for ((deviceModel, deviceManufacturer) in supportedDevices) {
            if (manufacturer.contains(deviceManufacturer) && model.contains(deviceModel)) {
                return UsbCCapability.SUPPORTED
            }
        }
        
        // Check if device is in unsupported list
        for ((deviceModel, deviceManufacturer) in unsupportedDevices) {
            if (manufacturer.contains(deviceManufacturer) && model.contains(deviceModel)) {
                return UsbCCapability.NOT_SUPPORTED
            }
        }
        
        // Unknown device - check Android version as hint
        // DisplayPort Alt Mode became more common with Android 9+
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            UsbCCapability.UNKNOWN
        } else {
            UsbCCapability.NOT_SUPPORTED
        }
    }
    
    private fun logCapabilities(capabilities: Capabilities) {
        Timber.i("=== HARDWARE CAPABILITIES ===")
        Timber.i("Device: ${capabilities.deviceModel}")
        Timber.i("Android: ${capabilities.androidVersion}")
        Timber.i("DisplayPort Alt Mode: ${capabilities.supportsDisplayPortAltMode}")
        Timber.i("External Display: ${capabilities.hasExternalDisplay}")
        Timber.i("Wireless Display: ${capabilities.supportsWirelessDisplay}")
        Timber.i("USB-C Video: ${capabilities.usbCVideoOutput}")
        
        if (capabilities.usbCVideoOutput == UsbCCapability.NOT_SUPPORTED) {
            Timber.w("⚠️ This device does NOT support USB-C video output (DisplayPort Alt Mode)")
            Timber.w("USB-C mirroring will NOT work on this device")
            Timber.w("Recommendation: Use Miracast or WiFi Direct instead")
        } else if (capabilities.usbCVideoOutput == UsbCCapability.UNKNOWN) {
            Timber.w("⚠️ Cannot determine if this device supports USB-C video output")
            Timber.w("Try connecting to external display to test")
        }
    }
    
    /**
     * Get user-friendly explanation for USB-C capability.
     */
    fun getUsbCExplanation(capability: UsbCCapability): String {
        return when (capability) {
            UsbCCapability.SUPPORTED -> 
                "✅ Your device supports USB-C video output (DisplayPort Alt Mode). " +
                "You can mirror your screen directly to a TV or monitor via USB-C cable."
            
            UsbCCapability.NOT_SUPPORTED -> 
                "❌ Your device does NOT support USB-C video output. " +
                "The USB-C port is for charging and data only, not video. " +
                "Please use Miracast or WiFi Direct for wireless mirroring instead."
            
            UsbCCapability.UNKNOWN -> 
                "⚠️ Cannot determine if your device supports USB-C video output. " +
                "Try connecting to an external display. If no display is detected, " +
                "your device likely doesn't support it. Use wireless mirroring as alternative."
        }
    }
}
