package com.languoi110.applejrdns;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

final class DnsWire {
    private DnsWire() {}

    static byte[] buildAQuery(String host) {
        String clean = host == null ? "" : host.trim();
        if (clean.endsWith(".")) clean = clean.substring(0, clean.length() - 1);
        if (clean.isEmpty()) throw new IllegalArgumentException("Host is empty");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int id = (int) (System.nanoTime() & 0xFFFF);
        write16(out, id);
        write16(out, 0x0100);
        write16(out, 1);
        write16(out, 0);
        write16(out, 0);
        write16(out, 0);

        for (String label : clean.split("\\.")) {
            byte[] bytes = label.getBytes(StandardCharsets.UTF_8);
            if (bytes.length == 0 || bytes.length > 63) {
                throw new IllegalArgumentException("Invalid DNS label");
            }
            out.write(bytes.length);
            out.write(bytes, 0, bytes.length);
        }
        out.write(0);
        write16(out, 1);
        write16(out, 1);
        return out.toByteArray();
    }

    static String extractQuestionName(byte[] dns) {
        if (dns == null || dns.length < 13) return "";
        int p = 12;
        StringBuilder name = new StringBuilder();
        int guard = 0;
        while (p < dns.length && guard++ < 128) {
            int len = dns[p++] & 0xFF;
            if (len == 0) break;
            if ((len & 0xC0) != 0 || len > 63 || p + len > dns.length) return "";
            if (name.length() > 0) name.append('.');
            name.append(new String(dns, p, len, StandardCharsets.UTF_8));
            p += len;
        }
        return name.toString().toLowerCase(Locale.ROOT);
    }

    static boolean isDnsResponse(byte[] dns) {
        return dns != null && dns.length >= 12 && (dns[2] & 0x80) != 0;
    }

    static int readU16(byte[] b, int off) {
        return ((b[off] & 0xFF) << 8) | (b[off + 1] & 0xFF);
    }

    static void putU16(byte[] b, int off, int value) {
        b[off] = (byte) ((value >>> 8) & 0xFF);
        b[off + 1] = (byte) (value & 0xFF);
    }

    static int ipv4HeaderChecksum(byte[] packet, int off, int length) {
        long sum = 0;
        for (int i = 0; i < length; i += 2) {
            int hi = packet[off + i] & 0xFF;
            int lo = (i + 1 < length) ? (packet[off + i + 1] & 0xFF) : 0;
            sum += (hi << 8) | lo;
            while ((sum & 0xFFFF0000L) != 0) {
                sum = (sum & 0xFFFFL) + (sum >>> 16);
            }
        }
        return (int) (~sum) & 0xFFFF;
    }

    private static void write16(ByteArrayOutputStream out, int value) {
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }
}
