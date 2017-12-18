/*
 * Copyright (c) 2013 The Netty Project
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.dns.impl.decoder;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.dns.DnsPtrRecord;
import io.netty.handler.codec.dns.DnsRawRecord;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.util.CharsetUtil;
import io.vertx.core.dns.impl.MxRecordImpl;
import io.vertx.core.dns.impl.SrvRecordImpl;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Handles the decoding of resource records. Some default decoders are mapped to
 * their resource types in the map {@code decoders}.
 */
public class RecordDecoder {


    /**
     * Decodes MX (mail exchanger) resource records.
     */
    public static final Function<DnsRecord, MxRecordImpl> MX = record -> {
        ByteBuf packet = ((DnsRawRecord)record).content();
        int priority = packet.readShort();
        String name = RecordDecoder.readName(packet);
        return new MxRecordImpl(priority, name);
    };

    /**
     * Decodes any record that simply returns a domain name, such as NS (name
     * server) and CNAME (canonical name) resource records.
     */
    public static final Function<DnsRecord, String> DOMAIN = record -> {
        if (record instanceof DnsPtrRecord) {
            String val = ((DnsPtrRecord)record).hostname();
            if (val.endsWith(".")) {
                val = val.substring(0, val.length() - 1);
            }
            return val;
        } else {
            ByteBuf content = ((DnsRawRecord) record).content();
            return RecordDecoder.getName(content, content.readerIndex());
        }
    };

    /**
     * Decodes A resource records into IPv4 addresses.
     */
    public static final Function<DnsRecord, String> A = address(4);

    /**
     * Decodes AAAA resource records into IPv6 addresses.
     */
    public static final Function<DnsRecord, String> AAAA = address(16);

    /**
     * Decodes SRV (service) resource records.
     */
    public static final Function<DnsRecord, SrvRecordImpl> SRV = record -> {
        ByteBuf packet = ((DnsRawRecord)record).content();
        int priority = packet.readShort();
        int weight = packet.readShort();
        int port = packet.readUnsignedShort();
        String target = RecordDecoder.readName(packet);
        String[] parts = record.name().split("\\.", 3);
        String service = parts[0];
        String protocol = parts[1];
        String name = parts[2];
        return new SrvRecordImpl(priority, weight, port, name, protocol, service, target);
    };

    /**
     * Decodes SOA (start of authority) resource records.
     */
    public static final Function<DnsRecord, StartOfAuthorityRecord> SOA = record -> {
        ByteBuf packet = ((DnsRawRecord)record).content();
        String mName = RecordDecoder.readName(packet);
        String rName = RecordDecoder.readName(packet);
        long serial = packet.readUnsignedInt();
        int refresh = packet.readInt();
        int retry = packet.readInt();
        int expire = packet.readInt();
        long minimum = packet.readUnsignedInt();
        return new StartOfAuthorityRecord(mName, rName, serial, refresh, retry, expire, minimum);
    };

    public static final Function<DnsRecord, List<String>> TXT = record -> {
        List<String> list = new ArrayList<>();
        ByteBuf data = ((DnsRawRecord)record).content();
        int index = data.readerIndex();
        while (index < data.writerIndex()) {
            int len = data.getUnsignedByte(index++);
            list.add(data.toString(index, len, CharsetUtil.UTF_8));
            index += len;
        }
        return list;
    };

    static Function<DnsRecord, String> address(int octets) {
        return record -> {
            ByteBuf data = ((DnsRawRecord)record).content();
            int size = data.readableBytes();
            if (size != octets) {
                throw new DecoderException("Invalid content length, or reader index when decoding address [index: "
                    + data.readerIndex() + ", expected length: " + octets + ", actual: " + size + "].");
            }
            byte[] address = new byte[octets];
            data.getBytes(data.readerIndex(), address);
            try {
                return InetAddress.getByAddress(address).getHostAddress();
            } catch (UnknownHostException e) {
                throw new DecoderException("Could not convert address "
                    + data.toString(data.readerIndex(), size, CharsetUtil.UTF_8) + " to InetAddress.");
            }
        };
    }

    /**
     * Retrieves a domain name given a buffer containing a DNS packet. If the
     * name contains a pointer, the position of the buffer will be set to
     * directly after the pointer's index after the name has been read.
     *
     * @param buf the byte buffer containing the DNS packet
     * @return the domain name for an entry
     */
    static String readName(ByteBuf buf) {
        int position = -1;
        StringBuilder name = new StringBuilder();
        for (int len = buf.readUnsignedByte(); buf.isReadable() && len != 0; len = buf.readUnsignedByte()) {
            boolean pointer = (len & 0xc0) == 0xc0;
            if (pointer) {
                if (position == -1) {
                    position = buf.readerIndex() + 1;
                }
                buf.readerIndex((len & 0x3f) << 8 | buf.readUnsignedByte());
            } else {
                name.append(buf.toString(buf.readerIndex(), len, CharsetUtil.UTF_8)).append(".");
                buf.skipBytes(len);
            }
        }
        if (position != -1) {
            buf.readerIndex(position);
        }
        if (name.length() == 0) {
            return null;
        }
        return name.substring(0, name.length() - 1);
    }

    /**
     * Retrieves a domain name given a buffer containing a DNS packet without
     * advancing the readerIndex for the buffer.
     *
     * @param buf    the byte buffer containing the DNS packet
     * @param offset the position at which the name begins
     * @return the domain name for an entry
     */
    static String getName(ByteBuf buf, int offset) {
        StringBuilder name = new StringBuilder();
        for (int len = buf.getUnsignedByte(offset++); buf.writerIndex() > offset && len != 0; len = buf
            .getUnsignedByte(offset++)) {
            boolean pointer = (len & 0xc0) == 0xc0;
            if (pointer) {
                offset = (len & 0x3f) << 8 | buf.getUnsignedByte(offset++);
            } else {
                name.append(buf.toString(offset, len, CharsetUtil.UTF_8)).append(".");
                offset += len;
            }
        }
        if (name.length() == 0) {
            return null;
        }
        return name.substring(0, name.length() - 1);
    }

    private static final Map<DnsRecordType, Function<DnsRecord, ?>> decoders = new HashMap<>();

    static {
        decoders.put(DnsRecordType.A, RecordDecoder.A);
        decoders.put(DnsRecordType.AAAA, RecordDecoder.AAAA);
        decoders.put(DnsRecordType.MX, RecordDecoder.MX);
        decoders.put(DnsRecordType.TXT, RecordDecoder.TXT);
        decoders.put(DnsRecordType.SRV, RecordDecoder.SRV);
        decoders.put(DnsRecordType.NS, RecordDecoder.DOMAIN);
        decoders.put(DnsRecordType.CNAME, RecordDecoder.DOMAIN);
        decoders.put(DnsRecordType.PTR, RecordDecoder.DOMAIN);
        decoders.put(DnsRecordType.SOA, RecordDecoder.SOA);
    }

    /**
     * Decodes a resource record and returns the result.
     *
     * @param record
     * @return the decoded resource record
     */
    @SuppressWarnings("unchecked")
    public static <T> T decode(DnsRecord record) {
        DnsRecordType type = record.type();
        Function<DnsRecord, ?> decoder = decoders.get(type);
        if (decoder == null) {
            throw new IllegalStateException("Unsupported resource record type [id: " + type + "].");
        }
        T result = null;
        try {
            result = (T) decoder.apply(record);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }
}
