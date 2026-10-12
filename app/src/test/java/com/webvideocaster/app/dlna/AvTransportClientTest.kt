package com.webvideocaster.app.dlna

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.BufferedInputStream
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

class AvTransportClientTest {
    private lateinit var server: ServerSocket
    private val requests = mutableListOf<Pair<String, String>>()
    @Volatile private var nextStatus = 200
    @Volatile private var nextBody = ""

    /** Minimal HTTP/1.1 server: the JDK's HttpServer is not on the Android unit-test classpath. */
    @Before
    fun startServer() {
        server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                socket.use { handle(BufferedInputStream(it.getInputStream()), it.getOutputStream()) }
            }
        }
    }

    private fun handle(input: BufferedInputStream, output: java.io.OutputStream) {
        val headers = mutableMapOf<String, String>()
        generateSequence { readLine(input) }.drop(1).takeWhile { it.isNotEmpty() }.forEach { line ->
            headers[line.substringBefore(':').trim().lowercase()] = line.substringAfter(':').trim()
        }
        val body = ByteArray(headers["content-length"]?.toInt() ?: 0)
        var read = 0
        while (read < body.size) read += input.read(body, read, body.size - read).takeIf { it > 0 } ?: break
        synchronized(requests) { requests += headers["soapaction"].orEmpty() to body.toString(Charsets.UTF_8) }
        val bytes = nextBody.toByteArray()
        output.write("HTTP/1.1 $nextStatus X\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
        output.write(bytes)
        output.flush()
    }

    private fun readLine(input: BufferedInputStream): String? {
        val line = StringBuilder()
        while (true) {
            val c = input.read()
            if (c < 0) return if (line.isEmpty()) null else line.toString()
            if (c == '\n'.code) return line.toString().trimEnd('\r')
            line.append(c.toChar())
        }
    }

    @After
    fun stopServer() {
        server.close()
    }

    private fun client() = AvTransportClient("http://127.0.0.1:${server.localPort}/ctl")

    private fun respond(action: String, inner: String = "") {
        nextStatus = 200
        nextBody = "<?xml version=\"1.0\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body>" +
            "<u:${action}Response xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\">$inner</u:${action}Response>" +
            "</s:Body></s:Envelope>"
    }

    @Test
    fun sendsSetUriWithEscapedMetadata() {
        respond("SetAVTransportURI")
        val url = "http://example.com/v.mp4?a=1&b=2"
        client().setAvTransportUri(url, AvTransportClient.didlLite(url, "Tom & Jerry", "video/mp4"))

        val (action, body) = requests.single()
        assertEquals("\"urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI\"", action)
        assertTrue(body.contains("<CurrentURI>http://example.com/v.mp4?a=1&amp;b=2</CurrentURI>"))
        // Metadata is XML escaped once in the DIDL and once more inside the SOAP body.
        assertTrue(body.contains("&lt;dc:title&gt;Tom &amp;amp; Jerry&lt;/dc:title&gt;"))
        assertTrue(body.contains("protocolInfo=&quot;http-get:*:video/mp4:*&quot;"))
    }

    @Test
    fun readsPositionAndState() {
        respond("GetPositionInfo", "<Track>1</Track><TrackDuration>0:10:05</TrackDuration><RelTime>00:01:02.500</RelTime>")
        assertEquals(PositionInfo(62, 605), client().getPositionInfo())

        respond("GetTransportInfo", "<CurrentTransportState>PLAYING</CurrentTransportState>")
        assertEquals("PLAYING", client().getTransportState())
    }

    @Test
    fun loadStopsSetsUriAndPlays() {
        respond("Any")
        client().load("http://example.com/v.mp4", "Video", "video/mp4")
        assertEquals(
            listOf("Stop", "SetAVTransportURI", "Play"),
            requests.map { it.first.substringAfter('#').trimEnd('"') },
        )
    }

    @Test
    fun reportsUpnpErrors() {
        nextStatus = 500
        nextBody = "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body><s:Fault>" +
            "<detail><UPnPError xmlns=\"urn:schemas-upnp-org:control-1-0\"><errorCode>714</errorCode>" +
            "<errorDescription>Illegal MIME-type</errorDescription></UPnPError></detail></s:Fault></s:Body></s:Envelope>"
        try {
            client().play()
            fail("expected DlnaException")
        } catch (e: DlnaException) {
            assertEquals("Play failed (HTTP 500, UPnP error 714): Illegal MIME-type", e.message)
        }
    }

    @Test
    fun formatsAndParsesTimes() {
        assertEquals("00:00:00", AvTransportClient.formatTime(-5))
        assertEquals("01:02:03", AvTransportClient.formatTime(3723))
        assertEquals(3723L, AvTransportClient.parseTime("1:02:03"))
        assertEquals(65L, AvTransportClient.parseTime("00:01:05.123"))
        assertNull(AvTransportClient.parseTime("NOT_IMPLEMENTED"))
        assertNull(AvTransportClient.parseTime(null))
    }
}
