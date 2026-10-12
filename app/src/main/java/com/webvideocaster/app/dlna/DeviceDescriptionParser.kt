package com.webvideocaster.app.dlna

import com.webvideocaster.app.dlna.Xml.childElements
import com.webvideocaster.app.dlna.Xml.childText
import com.webvideocaster.app.dlna.Xml.localNameOrTag
import org.w3c.dom.Element
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** Reads a UPnP device description (the XML at an SSDP `LOCATION`) into a [DlnaDevice]. */
object DeviceDescriptionParser {
    private const val AV_TRANSPORT_PREFIX = "urn:schemas-upnp-org:service:AVTransport:"

    /** Downloads and parses the description at [location]; returns null if it is not a usable renderer. */
    fun fetch(location: String, timeoutMillis: Int = 3000): DlnaDevice? {
        return try {
            val connection = URL(location).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = timeoutMillis
                connection.readTimeout = timeoutMillis
                if (connection.responseCode !in 200..299) return null
                parse(connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }, location)
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            null
        }
    }

    fun parse(xml: String, location: String): DlnaDevice? {
        val document = Xml.parse(xml) ?: return null
        val root = document.documentElement ?: return null
        val urlBase = root.childText("URLBase")?.takeIf { it.isNotBlank() } ?: location

        val services = document.getElementsByTagNameNS("*", "service")
        for (i in 0 until services.length) {
            val service = services.item(i) as? Element ?: continue
            val serviceType = service.childText("serviceType") ?: continue
            if (!serviceType.startsWith(AV_TRANSPORT_PREFIX)) continue
            val controlUrl = service.childText("controlURL")?.takeIf { it.isNotBlank() } ?: continue

            // <device><serviceList><service>: the owning device is two levels up.
            val device = (service.parentNode?.parentNode as? Element)
                ?.takeIf { it.localNameOrTag() == "device" }
                ?: root.childElements().firstOrNull { it.localNameOrTag() == "device" }

            val name = device?.childText("friendlyName")?.takeIf { it.isNotBlank() }
                ?: runCatching { URI(location).host }.getOrNull()
                ?: location
            return DlnaDevice(
                id = device?.childText("UDN")?.takeIf { it.isNotBlank() } ?: location,
                friendlyName = name,
                location = location,
                avTransportControlUrl = resolve(urlBase, controlUrl),
                avTransportServiceType = serviceType,
                manufacturer = device?.childText("manufacturer"),
                modelName = device?.childText("modelName"),
            )
        }
        return null
    }

    fun resolve(base: String, relative: String): String = try {
        var baseUri = URI(base.trim())
        // URI.resolve drops the separator when the base has no path ("http://host:port" + "ctl").
        if (baseUri.rawPath.isNullOrEmpty()) baseUri = URI(baseUri.scheme, baseUri.rawAuthority, "/", null, null)
        baseUri.resolve(relative.trim()).toString()
    } catch (e: Exception) {
        relative
    }
}
