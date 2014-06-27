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
import io.netty.util.CharsetUtil;
import io.vertx.core.dns.impl.netty.DnsResource;
import io.vertx.core.dns.impl.netty.DnsResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * Decodes TXT (text) resource records.
 */
public class TextDecoder implements RecordDecoder<List<String>> {

    /**
     * Returns a decoded TXT (text) resource record, stored as an
     * {@link java.util.ArrayList} of {@code String}s.
     *
     * @param response
     *            the DNS response that contains the resource record being
     *            decoded
     * @param resource
     *            the resource record being decoded
     */
    @Override
    public List<String> decode(DnsResponse response, DnsResource resource) {
        List<String> list = new ArrayList<String>();
        ByteBuf data = resource.content().readerIndex(response.originalIndex());
        int index = data.readerIndex();
        while (index < data.writerIndex()) {
            int len = data.getUnsignedByte(index++);
            list.add(data.toString(index, len, CharsetUtil.UTF_8));
            index += len;
        }
        return list;
    }

}
