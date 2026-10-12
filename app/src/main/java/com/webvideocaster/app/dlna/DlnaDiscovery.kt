package com.webvideocaster.app.dlna

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Runs SSDP discovery on the current Wi-Fi network and reports DLNA renderers on the main thread. */
class DlnaDiscovery(context: Context) {
    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    suspend fun discover(rounds: Int = 2, onDevice: (DlnaDevice) -> Unit) = withContext(Dispatchers.IO) {
        // Some routers/phones drop SSDP replies unless a multicast lock is held.
        val lock = wifi.createMulticastLock("webvideocaster-ssdp").apply {
            setReferenceCounted(false)
            acquire()
        }
        val seenLocations = HashSet<String>()
        try {
            repeat(rounds) {
                if (!isActive) return@repeat
                runCatching {
                    SsdpClient().search(timeoutMillis = 3000, shouldContinue = { isActive }) { response ->
                        if (!seenLocations.add(response.location)) return@search
                        val device = DeviceDescriptionParser.fetch(response.location) ?: return@search
                        DlnaState.remember(device)
                        launch(Dispatchers.Main) { onDevice(device) }
                    }
                }
            }
        } finally {
            lock.release()
        }
    }
}
