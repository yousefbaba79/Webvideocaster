package com.webvideocaster.app.dlna

import org.w3c.dom.Document
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class DlnaException(message: String) : IOException(message)

data class PositionInfo(val positionSeconds: Long?, val durationSeconds: Long?)

/** Controls playback on a renderer through UPnP AVTransport SOAP actions. All calls block. */
class AvTransportClient(
    private val controlUrl: String,
    private val serviceType: String = DEFAULT_SERVICE_TYPE,
    private val timeoutMillis: Int = 5000,
) {
    /** Stops whatever is playing, then loads and starts [url]. */
    fun load(url: String, title: String, mimeType: String) {
        // Many TVs refuse a new URI while playing; others error when already stopped.
        runCatching { stop() }
        setAvTransportUri(url, didlLite(url, title, mimeType))
        try {
            play()
        } catch (e: DlnaException) {
            // Some renderers are still TRANSITIONING right after SetAVTransportURI.
            Thread.sleep(1000)
            play()
        }
    }

    fun setAvTransportUri(uri: String, metadata: String) {
        invoke(
            "SetAVTransportURI",
            "<InstanceID>0</InstanceID>" +
                "<CurrentURI>${escapeXml(uri)}</CurrentURI>" +
                "<CurrentURIMetaData>${escapeXml(metadata)}</CurrentURIMetaData>",
        )
    }

    fun play() {
        invoke("Play", "<InstanceID>0</InstanceID><Speed>1</Speed>")
    }

    fun pause() {
        invoke("Pause", "<InstanceID>0</InstanceID>")
    }

    fun stop() {
        invoke("Stop", "<InstanceID>0</InstanceID>")
    }

    fun seek(seconds: Long) {
        invoke(
            "Seek",
            "<InstanceID>0</InstanceID><Unit>REL_TIME</Unit><Target>${formatTime(seconds)}</Target>",
        )
    }

    fun getPositionInfo(): PositionInfo {
        val response = invoke("GetPositionInfo", "<InstanceID>0</InstanceID>")
        return PositionInfo(
            positionSeconds = parseTime(Xml.firstText(response, "RelTime")),
            durationSeconds = parseTime(Xml.firstText(response, "TrackDuration")),
        )
    }

    /** One of PLAYING, PAUSED_PLAYBACK, STOPPED, TRANSITIONING, NO_MEDIA_PRESENT. */
    fun getTransportState(): String? =
        Xml.firstText(invoke("GetTransportInfo", "<InstanceID>0</InstanceID>"), "CurrentTransportState")

    private fun invoke(action: String, arguments: String): Document {
        val body = envelope(serviceType, action, arguments).toByteArray(Charsets.UTF_8)
        val connection = URL(controlUrl).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = timeoutMillis
            connection.readTimeout = timeoutMillis
            connection.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
            connection.setRequestProperty("SOAPAction", "\"$serviceType#$action\"")
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            val document = Xml.parse(text)
            if (code !in 200..299) {
                val errorCode = document?.let { Xml.firstText(it, "errorCode") }
                val description = document?.let { Xml.firstText(it, "errorDescription") }
                throw DlnaException(
                    buildString {
                        append("$action failed (HTTP $code")
                        if (errorCode != null) append(", UPnP error $errorCode")
                        append(")")
                        if (!description.isNullOrBlank()) append(": $description")
                    },
                )
            }
            return document ?: throw DlnaException("$action failed: invalid response from device")
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val DEFAULT_SERVICE_TYPE = "urn:schemas-upnp-org:service:AVTransport:1"

        fun envelope(serviceType: String, action: String, arguments: String): String =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
                "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
                "<s:Body><u:$action xmlns:u=\"$serviceType\">$arguments</u:$action></s:Body>" +
                "</s:Envelope>"

        /** DIDL-Lite metadata; many TVs (Samsung, LG) refuse SetAVTransportURI without it. */
        fun didlLite(url: String, title: String, mimeType: String): String =
            "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" " +
                "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" " +
                "xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">" +
                "<item id=\"0\" parentID=\"-1\" restricted=\"1\">" +
                "<dc:title>${escapeXml(title)}</dc:title>" +
                "<upnp:class>object.item.videoItem</upnp:class>" +
                "<res protocolInfo=\"http-get:*:${escapeXml(mimeType)}:*\">${escapeXml(url)}</res>" +
                "</item></DIDL-Lite>"

        fun escapeXml(text: String): String = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

        fun formatTime(totalSeconds: Long): String {
            val seconds = totalSeconds.coerceAtLeast(0)
            return "%02d:%02d:%02d".format(seconds / 3600, (seconds % 3600) / 60, seconds % 60)
        }

        /** Parses UPnP durations like "1:02:03" or "00:01:05.500"; null for NOT_IMPLEMENTED or garbage. */
        fun parseTime(value: String?): Long? {
            val parts = value?.trim()?.split(':') ?: return null
            if (parts.size != 3) return null
            val hours = parts[0].toLongOrNull() ?: return null
            val minutes = parts[1].toLongOrNull() ?: return null
            val seconds = parts[2].substringBefore('.').toLongOrNull() ?: return null
            return hours * 3600 + minutes * 60 + seconds
        }
    }
}
