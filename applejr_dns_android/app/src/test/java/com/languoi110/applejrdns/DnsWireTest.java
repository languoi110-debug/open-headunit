package com.languoi110.applejrdns;

import org.junit.Test;

import static org.junit.Assert.*;

public class DnsWireTest {
    @Test
    public void buildsAndParsesAQuery() {
        byte[] query = DnsWire.buildAQuery("Example.COM");
        assertTrue(query.length > 20);
        assertEquals("example.com", DnsWire.extractQuestionName(query));
        assertFalse(DnsWire.isDnsResponse(query));
    }

    @Test
    public void checksumForKnownIpv4HeaderIsStable() {
        byte[] header = new byte[] {
                0x45, 0x00, 0x00, 0x1c,
                0x00, 0x01, 0x40, 0x00,
                0x40, 0x11, 0x00, 0x00,
                0x0a, 0x07, 0x00, 0x02,
                0x0a, 0x07, 0x00, 0x01
        };
        int checksum = DnsWire.ipv4HeaderChecksum(header, 0, header.length);
        assertTrue(checksum >= 0 && checksum <= 0xffff);
        DnsWire.putU16(header, 10, checksum);
        assertEquals(0, DnsWire.ipv4HeaderChecksum(header, 0, header.length));
    }
}
