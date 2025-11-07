package com.example.mirroringapp.mirroring

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbManager
import android.view.Display
import com.example.mirroringapp.util.PersistentLogger
import timber.log.Timber

/**
 * Manages USB display connections.
 * Supports both DisplayPort Alt Mode and USB Accessory Mode.
 */
class UsbDisplayManager(private val context: Context) {
    
    private val displayManager = context.getSystemService(DisplayManager::class.java)
    private val usbManager = context.getSystemService(UsbManager::class.java)
    
    enum class UsbDisplayType {
        NONE,                    // No USB display connected
        DISPLAYPORT_ALT_MODE,    // Direct video output (passive adapter)
        USB_ACCESSORY,           // USB accessory with display capability (active adapter)
        UNKNOWN                  // Connected but type unknown
    }
    
    data class UsbDisplayInfo(
        val type: UsbDisplayType,
        val externalDisplay: Display?,
        val usbAccessory: UsbAccessory?,
        val displayCount: Int,
        val hasHdmiConnection: Boolean
    )
    
    /**
     * Detect USB display connection and determine type.
     */
    fun detectUsbDisplay(): UsbDisplayInfo {
        Timber.d("=== USB DISPLAY DETECTION ===")
        PersistentLogger.i("=== USB DISPLAY DETECTION ===")
        
        val displays = displayManager?.displays ?: emptyArray()
        val displayCount = displays.size
        
        Timber.d("Total displays: $displayCount")
        PersistentLogger.i("Total displays: $displayCount")
        
        // Log all displays
        displays.forEachIndexed { index, display ->
            val isExternal = display.displayId != Display.DEFAULT_DISPLAY
            val displayInfo = "Display $index: id=${display.displayId}, " +
                    "name=${display.name}, " +
                    "size=${display.mode.physicalWidth}x${display.mode.physicalHeight}, " +
                    "external=$isExternal"
            Timber.d(displayInfo)
            PersistentLogger.i(displayInfo)
        }
        
        // Check for external display (DisplayPort Alt Mode)
        val externalDisplay = displays.find { it.displayId != Display.DEFAULT_DISPLAY }
        
        // Check for HDMI in display name
        val hasHdmiConnection = displays.any { 
            it.name?.contains("HDMI", ignoreCase = true) == true ||
            it.name?.contains("External", ignoreCase = true) == true
        }
        
        // Check for USB accessories
        val usbAccessories = usbManager?.accessoryList
        val hasUsbAccessory = !usbAccessories.isNullOrEmpty()
        
        if (hasUsbAccessory) {
            Timber.d("USB Accessories detected: ${usbAccessories?.size}")
            PersistentLogger.i("USB Accessories detected: ${usbAccessories?.size}")
            usbAccessories?.forEach { accessory ->
                val accessoryInfo = "  Manufacturer: ${accessory.manufacturer}, " +
                        "Model: ${accessory.model}, " +
                        "Description: ${accessory.description}"
                Timber.d(accessoryInfo)
                PersistentLogger.i(accessoryInfo)
            }
        }
        
        // Check for known display adapters by manufacturer/model
        val isKnownDisplayAdapter = usbAccessories?.any { accessory ->
            val manufacturer = accessory.manufacturer?.lowercase() ?: ""
            val model = accessory.model?.lowercase() ?: ""
            val description = accessory.description?.lowercase() ?: ""
            
            // Known display adapter manufacturers/models
            manufacturer.contains("onebit") ||
            manufacturer.contains("displaylink") ||
            manufacturer.contains("sagetech") ||
            model.contains("mirror") ||
            model.contains("display") ||
            description.contains("mirror") ||
            description.contains("display")
        } ?: false
        
        // Determine connection type
        val type = when {
            externalDisplay != null -> {
                Timber.i("✅ DisplayPort Alt Mode detected - External display found")
                PersistentLogger.i("✅ DisplayPort Alt Mode detected - External display found")
                UsbDisplayType.DISPLAYPORT_ALT_MODE
            }
            hasUsbAccessory && hasHdmiConnection -> {
                Timber.i("✅ USB Accessory with HDMI detected")
                PersistentLogger.i("✅ USB Accessory with HDMI detected")
                UsbDisplayType.USB_ACCESSORY
            }
            hasUsbAccessory && isKnownDisplayAdapter -> {
                Timber.i("✅ Known display adapter detected (${usbAccessories?.firstOrNull()?.manufacturer})")
                PersistentLogger.i("✅ Known display adapter detected - will wait for display")
                UsbDisplayType.USB_ACCESSORY
            }
            hasUsbAccessory -> {
                Timber.w("⚠️ USB Accessory detected but no HDMI connection")
                PersistentLogger.w("⚠️ USB Accessory detected but no HDMI connection")
                UsbDisplayType.UNKNOWN
            }
            else -> {
                Timber.w("❌ No USB display connection detected")
                PersistentLogger.w("❌ No USB display connection detected")
                UsbDisplayType.NONE
            }
        }
        
        val info = UsbDisplayInfo(
            type = type,
            externalDisplay = externalDisplay,
            usbAccessory = usbAccessories?.firstOrNull(),
            displayCount = displayCount,
            hasHdmiConnection = hasHdmiConnection
        )
        
        logConnectionSummary(info)
        
        return info
    }
    
    /**
     * Check if USB display is ready for mirroring.
     */
    fun isUsbDisplayReady(): Boolean {
        val info = detectUsbDisplay()
        return info.type == UsbDisplayType.DISPLAYPORT_ALT_MODE || 
               info.type == UsbDisplayType.USB_ACCESSORY
    }
    
    /**
     * Get external display for rendering.
     */
    fun getExternalDisplay(): Display? {
        val displays = displayManager?.displays ?: return null
        return displays.find { it.displayId != Display.DEFAULT_DISPLAY }
    }
    
    /**
     * Wait for external display to appear (for USB accessories).
     * Some adapters take a moment to initialize.
     */
    suspend fun waitForExternalDisplay(timeoutMs: Long = 10000): Display? {
        val startTime = System.currentTimeMillis()
        
        Timber.i("Waiting for external display to appear (timeout: ${timeoutMs}ms)...")
        PersistentLogger.i("Waiting for external display to appear (timeout: ${timeoutMs}ms)...")
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val display = getExternalDisplay()
            if (display != null) {
                val elapsed = System.currentTimeMillis() - startTime
                Timber.i("✅ External display appeared after ${elapsed}ms")
                PersistentLogger.i("✅ External display appeared after ${elapsed}ms")
                return display
            }
            
            // Log progress every 2 seconds
            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed % 2000 < 100) {
                Timber.d("Still waiting for display... (${elapsed}ms elapsed)")
                PersistentLogger.i("Still waiting for display... (${elapsed}ms elapsed)")
            }
            
            kotlinx.coroutines.delay(100)
        }
        
        Timber.e("❌ External display did not appear within ${timeoutMs}ms")
        PersistentLogger.e("❌ External display did not appear within ${timeoutMs}ms")
        Timber.e("This adapter may require a special app or driver to create the display")
        PersistentLogger.e("This adapter may require a special app or driver to create the display")
        return null
    }
    
    /**
     * Register display listener for hot-plug detection.
     */
    fun registerDisplayListener(callback: (Display?) -> Unit): DisplayManager.DisplayListener {
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {
                Timber.i("🔌 Display added: $displayId")
                PersistentLogger.i("🔌 Display added: $displayId")
                
                val display = displayManager?.getDisplay(displayId)
                if (display != null && displayId != Display.DEFAULT_DISPLAY) {
                    Timber.i("✅ External display connected: ${display.name}")
                    PersistentLogger.i("✅ External display connected: ${display.name}")
                    callback(display)
                }
            }
            
            override fun onDisplayRemoved(displayId: Int) {
                Timber.i("🔌 Display removed: $displayId")
                PersistentLogger.i("🔌 Display removed: $displayId")
                
                if (displayId != Display.DEFAULT_DISPLAY) {
                    Timber.w("⚠️ External display disconnected")
                    PersistentLogger.w("⚠️ External display disconnected")
                    callback(null)
                }
            }
            
            override fun onDisplayChanged(displayId: Int) {
                Timber.d("Display changed: $displayId")
            }
        }
        
        displayManager?.registerDisplayListener(listener, null)
        return listener
    }
    
    /**
     * Unregister display listener.
     */
    fun unregisterDisplayListener(listener: DisplayManager.DisplayListener) {
        displayManager?.unregisterDisplayListener(listener)
    }
    
    private fun logConnectionSummary(info: UsbDisplayInfo) {
        val summary = buildString {
            appendLine("=== USB DISPLAY CONNECTION SUMMARY ===")
            appendLine("Type: ${info.type}")
            appendLine("Display Count: ${info.displayCount}")
            appendLine("Has External Display: ${info.externalDisplay != null}")
            appendLine("Has HDMI Connection: ${info.hasHdmiConnection}")
            appendLine("Has USB Accessory: ${info.usbAccessory != null}")
            
            if (info.externalDisplay != null) {
                appendLine("External Display:")
                appendLine("  Name: ${info.externalDisplay.name}")
                appendLine("  Size: ${info.externalDisplay.mode.physicalWidth}x${info.externalDisplay.mode.physicalHeight}")
                appendLine("  Refresh: ${info.externalDisplay.mode.refreshRate} Hz")
            }
            
            if (info.usbAccessory != null) {
                appendLine("USB Accessory:")
                appendLine("  Manufacturer: ${info.usbAccessory.manufacturer}")
                appendLine("  Model: ${info.usbAccessory.model}")
            }
            
            appendLine("========================================")
        }
        
        Timber.i(summary)
        PersistentLogger.i(summary)
    }
    
    /**
     * Get user-friendly status message.
     */
    fun getStatusMessage(info: UsbDisplayInfo): String {
        return when (info.type) {
            UsbDisplayType.DISPLAYPORT_ALT_MODE -> 
                "✅ USB-C display connected via DisplayPort Alt Mode\n" +
                "External display: ${info.externalDisplay?.name}\n" +
                "Resolution: ${info.externalDisplay?.mode?.physicalWidth}x${info.externalDisplay?.mode?.physicalHeight}\n" +
                "Ready for zero-lag mirroring!"
            
            UsbDisplayType.USB_ACCESSORY -> 
                "✅ USB-C adapter connected\n" +
                "Type: USB Accessory Mode\n" +
                "Adapter: ${info.usbAccessory?.manufacturer} ${info.usbAccessory?.model}\n" +
                "Waiting for display initialization..."
            
            UsbDisplayType.UNKNOWN -> 
                "⚠️ USB connection detected but display not ready\n" +
                "Please ensure:\n" +
                "• Adapter is fully connected\n" +
                "• HDMI cable is plugged into TV\n" +
                "• TV is on and set to correct HDMI input"
            
            UsbDisplayType.NONE -> 
                "❌ No USB display connection detected\n" +
                "Please connect USB-C to HDMI adapter\n" +
                "Or use wireless mirroring instead"
        }
    }
}
