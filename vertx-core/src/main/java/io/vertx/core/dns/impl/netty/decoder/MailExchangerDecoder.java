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
import io.vertx.core.dns.impl.netty.decoder.record.MailExchangerRecord;

/**
 * Decodes MX (mail exchanger) resource records.
 */
public class MailExchangerDecoder implements RecordDecoder<MailExchangerRecord> {

    /**
     * Returns a decoded MX (mail exchanger) resource record, stored as an
     * instance of {@link MailExchangerRecord}.
     *
     * @param response
     *            the {@link io.vertx.core.dns.impl.netty.DnsResponse} received that contained the resource
     *            record being decoded
     * @param resource
     *            the {@link DnsResource} being decoded
     */
    @Override
    public MailExchangerRecord decode(DnsResponse response, DnsResource resource) {
        ByteBuf packet = response.content().readerIndex(resource.contentIndex());
        int priority = packet.readShort();
        String name = DnsResponseDecoder.readName(packet);
        return new MailExchangerRecord(priority, name);
    }

}
