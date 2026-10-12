package com.webvideocaster.app.dlna

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

data class SsdpResponse(val location: String, val usn: String?, val server: String?, val searchTarget: String?)

/** Finds UPnP media renderers on the local network with SSDP M-SEARCH. */
class SsdpClient(
    private val searchTargets: List<String> = listOf(AV_TRANSPORT, MEDIA_RENDERER),
) {
    /** Sends a search and reports replies until [timeoutMillis] passes or [shouldContinue] returns false. Blocking. */
    fun search(timeoutMillis: Int, shouldContinue: () -> Boolean = { true }, onResponse: (SsdpResponse) -> Unit) {
        DatagramSocket().use { socket ->
            socket.soTimeout = 500
            val group = InetAddress.getByName(MULTICAST_ADDRESS)
            for (target in searchTargets) {
                val data = buildSearch(target).toByteArray(Charsets.UTF_8)
                // UDP is lossy; send each search twice.
                repeat(2) { socket.send(DatagramPacket(data, data.size, group, MULTICAST_PORT)) }
            }

            val buffer = ByteArray(8192)
            val deadline = System.currentTimeMillis() + timeoutMillis
            while (System.currentTimeMillis() < deadline && shouldContinue()) {
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (e: SocketTimeoutException) {
                    continue
                }
                parse(String(packet.data, packet.offset, packet.length, Charsets.UTF_8))?.let(onResponse)
            }
        }
    }

    companion object {
        const val MULTICAST_ADDRESS = "239.255.255.250"
        const val MULTICAST_PORT = 1900
        const val AV_TRANSPORT = "urn:schemas-upnp-org:service:AVTransport:1"
        const val MEDIA_RENDERER = "urn:schemas-upnp-org:device:MediaRenderer:1"

        fun buildSearch(searchTarget: String, mx: Int = 2): String =
            "M-SEARCH * HTTP/1.1\r\n" +
                "HOST: $MULTICAST_ADDRESS:$MULTICAST_PORT\r\n" +
                "MAN: \"ssdp:discover\"\r\n" +
                "MX: $mx\r\n" +
                "ST: $searchTarget\r\n\r\n"

        fun parse(packet: String): SsdpResponse? {
            val lines = packet.split("\r\n", "\n")
            val status = lines.firstOrNull()?.trim() ?: return null
            if (!status.startsWith("HTTP/1.1 200", ignoreCase = true) &&
                !status.startsWith("NOTIFY", ignoreCase = true)
            ) return null

            val headers = lines.drop(1).mapNotNull { line ->
                val colon = line.indexOf(':')
                if (colon <= 0) null else line.substring(0, colon).trim().uppercase() to line.substring(colon + 1).trim()
            }.toMap()
            val location = headers["LOCATION"]?.takeIf { it.isNotBlank() } ?: return null
            return SsdpResponse(location, headers["USN"], headers["SERVER"], headers["ST"] ?: headers["NT"])
        }
    }
}
