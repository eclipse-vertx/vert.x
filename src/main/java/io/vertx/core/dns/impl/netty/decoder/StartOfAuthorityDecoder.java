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
import io.vertx.core.dns.impl.netty.DnsResource;
import io.vertx.core.dns.impl.netty.DnsResponse;
import io.vertx.core.dns.impl.netty.DnsResponseDecoder;
import io.vertx.core.dns.impl.netty.decoder.record.StartOfAuthorityRecord;

/**
 * Decodes SOA (start of authority) resource records.
 */
public class StartOfAuthorityDecoder implements RecordDecoder<StartOfAuthorityRecord> {

    /**
     * Returns a decoded SOA (start of authority) resource record, stored as an
     * instance of {@link StartOfAuthorityRecord}.
     *
     * @param response
     *            the DNS response that contains the resource record being
     *            decoded
     * @param resource
     *            the resource record being decoded
     */
    @Override
    public StartOfAuthorityRecord decode(DnsResponse response, DnsResource resource) {
        ByteBuf packet = response.content().readerIndex(resource.contentIndex());
        String mName = DnsResponseDecoder.readName(packet);
        String rName = DnsResponseDecoder.readName(packet);
        long serial = packet.readUnsignedInt();
        int refresh = packet.readInt();
        int retry = packet.readInt();
        int expire = packet.readInt();
        long minimum = packet.readUnsignedInt();
        return new StartOfAuthorityRecord(mName, rName, serial, refresh, retry, expire, minimum);
    }

}
