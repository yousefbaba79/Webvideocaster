package com.webvideocaster.app.dlna

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceDescriptionParserTest {
    private val samsungStyle = """
        <?xml version="1.0"?>
        <root xmlns="urn:schemas-upnp-org:device-1-0" xmlns:dlna="urn:schemas-dlna-org:device-1-0">
          <specVersion><major>1</major><minor>0</minor></specVersion>
          <device>
            <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
            <friendlyName>[TV] Living Room</friendlyName>
            <manufacturer>Samsung Electronics</manufacturer>
            <modelName>UE55</modelName>
            <UDN>uuid:1234-5678</UDN>
            <serviceList>
              <service>
                <serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType>
                <controlURL>/upnp/control/RenderingControl1</controlURL>
              </service>
              <service>
                <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
                <serviceId>urn:upnp-org:serviceId:AVTransport</serviceId>
                <controlURL>upnp/control/AVTransport1</controlURL>
              </service>
            </serviceList>
          </device>
        </root>
    """.trimIndent()

    @Test
    fun parsesRendererWithRelativeControlUrl() {
        val device = DeviceDescriptionParser.parse(samsungStyle, "http://192.168.1.20:9197/dmr")!!
        assertEquals("[TV] Living Room", device.friendlyName)
        assertEquals("uuid:1234-5678", device.id)
        assertEquals("Samsung Electronics", device.manufacturer)
        assertEquals("UE55", device.modelName)
        assertEquals("http://192.168.1.20:9197/upnp/control/AVTransport1", device.avTransportControlUrl)
        assertEquals("urn:schemas-upnp-org:service:AVTransport:1", device.avTransportServiceType)
    }

    @Test
    fun findsAvTransportInEmbeddedDeviceAndHonoursUrlBase() {
        val xml = """
            <root xmlns="urn:schemas-upnp-org:device-1-0">
              <URLBase>http://10.0.0.5:8080</URLBase>
              <device>
                <friendlyName>Receiver</friendlyName>
                <deviceList>
                  <device>
                    <friendlyName>Receiver Renderer</friendlyName>
                    <UDN>uuid:inner</UDN>
                    <serviceList>
                      <service>
                        <serviceType>urn:schemas-upnp-org:service:AVTransport:2</serviceType>
                        <controlURL>ctl/avt</controlURL>
                      </service>
                    </serviceList>
                  </device>
                </deviceList>
              </device>
            </root>
        """.trimIndent()
        val device = DeviceDescriptionParser.parse(xml, "http://10.0.0.5:49152/desc.xml")!!
        assertEquals("Receiver Renderer", device.friendlyName)
        assertEquals("uuid:inner", device.id)
        assertEquals("http://10.0.0.5:8080/ctl/avt", device.avTransportControlUrl)
        assertEquals("urn:schemas-upnp-org:service:AVTransport:2", device.avTransportServiceType)
    }

    @Test
    fun returnsNullWithoutAvTransport() {
        val xml = """
            <root xmlns="urn:schemas-upnp-org:device-1-0"><device><friendlyName>Router</friendlyName>
            <serviceList><service><serviceType>urn:schemas-upnp-org:service:WANIPConnection:1</serviceType>
            <controlURL>/ctl</controlURL></service></serviceList></device></root>
        """.trimIndent()
        assertNull(DeviceDescriptionParser.parse(xml, "http://192.168.1.1/desc.xml"))
        assertNull(DeviceDescriptionParser.parse("not xml", "http://192.168.1.1/desc.xml"))
    }

    @Test
    fun resolvesControlUrls() {
        assertEquals("http://h:1/ctl", DeviceDescriptionParser.resolve("http://h:1", "ctl"))
        assertEquals("http://h:1/ctl", DeviceDescriptionParser.resolve("http://h:1/desc.xml", "/ctl"))
        assertEquals("http://h:1/a/ctl", DeviceDescriptionParser.resolve("http://h:1/a/desc.xml", "ctl"))
        assertEquals("http://other:2/x", DeviceDescriptionParser.resolve("http://h:1/", "http://other:2/x"))
    }
}
