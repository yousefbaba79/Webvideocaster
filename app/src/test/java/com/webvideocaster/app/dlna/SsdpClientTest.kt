package com.webvideocaster.app.dlna

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SsdpClientTest {
    @Test
    fun parsesSearchResponse() {
        val packet = "HTTP/1.1 200 OK\r\n" +
            "CACHE-CONTROL: max-age=1800\r\n" +
            "Location: http://192.168.1.20:9197/dmr\r\n" +
            "SERVER: SHP, UPnP/1.0, Samsung UPnP SDK/1.0\r\n" +
            "ST: urn:schemas-upnp-org:service:AVTransport:1\r\n" +
            "USN: uuid:abc::urn:schemas-upnp-org:service:AVTransport:1\r\n\r\n"
        val response = SsdpClient.parse(packet)!!
        assertEquals("http://192.168.1.20:9197/dmr", response.location)
        assertEquals("urn:schemas-upnp-org:service:AVTransport:1", response.searchTarget)
        assertEquals("uuid:abc::urn:schemas-upnp-org:service:AVTransport:1", response.usn)
    }

    @Test
    fun ignoresPacketsWithoutLocationOrWithErrors() {
        assertNull(SsdpClient.parse("HTTP/1.1 200 OK\r\nST: x\r\n\r\n"))
        assertNull(SsdpClient.parse("HTTP/1.1 404 Not Found\r\nLOCATION: http://x/\r\n\r\n"))
        assertNull(SsdpClient.parse("M-SEARCH * HTTP/1.1\r\nLOCATION: http://x/\r\n\r\n"))
        assertNull(SsdpClient.parse(""))
    }

    @Test
    fun buildsSearchRequest() {
        val request = SsdpClient.buildSearch(SsdpClient.AV_TRANSPORT)
        assertTrue(request.startsWith("M-SEARCH * HTTP/1.1\r\n"))
        assertTrue(request.contains("MAN: \"ssdp:discover\"\r\n"))
        assertTrue(request.contains("ST: urn:schemas-upnp-org:service:AVTransport:1\r\n"))
        assertTrue(request.endsWith("\r\n\r\n"))
    }
}
