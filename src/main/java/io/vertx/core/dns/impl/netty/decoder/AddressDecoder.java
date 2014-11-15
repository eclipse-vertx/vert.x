/*
 * Copyright (c) 2013 The Netty Project
 * ------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */
package io.vertx.core.dns.impl.netty.decoder;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import io.netty.util.CharsetUtil;
import io.vertx.core.dns.impl.netty.DnsResource;
import io.vertx.core.dns.impl.netty.DnsResponse;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Decodes A and AAAA resource records into IPv4 and IPv6 addresses,
 * respectively.
 */
public class AddressDecoder implements RecordDecoder<InetAddress> {

    private final int octets;

    /**
     * Constructs an {@code AddressDecoder}, which decodes A and AAAA resource
     * records.
     *
     * @param octets
     *            the number of octets an address has. 4 for type A records and
     *            16 for type AAAA records
     */
    public AddressDecoder(int octets) {
        this.octets = octets;
    }

    /**
     * Returns an {@link java.net.InetAddress} containing a decoded address from either an A
     * or AAAA resource record.
     *
     * @param response
     *            the {@link io.vertx.core.dns.impl.netty.DnsResponse} received that contained the resource
     *            record being decoded
     * @param resource
     *            the {@link DnsResource} being decoded
     */
    @Override
    public InetAddress decode(DnsResponse response, DnsResource resource) {
        ByteBuf data = resource.content().copy().readerIndex(response.originalIndex());
        int size = data.writerIndex() - data.readerIndex();
        if (data.readerIndex() != 0 || size != octets) {
            throw new DecoderException("Invalid content length, or reader index when decoding address [index: "
                    + data.readerIndex() + ", expected length: " + octets + ", actual: " + size + "].");
        }
        byte[] address = new byte[octets];
        data.getBytes(data.readerIndex(), address);
        try {
            return InetAddress.getByAddress(address);
        } catch (UnknownHostException e) {
            throw new DecoderException("Could not convert address "
                    + data.toString(data.readerIndex(), size, CharsetUtil.UTF_8) + " to InetAddress.");
        }
    }

}
