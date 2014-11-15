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

import io.netty.handler.codec.DecoderException;
import io.vertx.core.dns.impl.netty.DnsResource;
import io.vertx.core.dns.impl.netty.DnsResponse;

/**
 * Used for decoding resource records.
 *
 * @param <T>
 *            the type of data to return after decoding a resource record (for
 *            example, an {@link AddressDecoder} will return a {@link io.netty.buffer.ByteBuf})
 */
public interface RecordDecoder<T> {

    /**
     * Returns a generic type {@code T} defined in a class implementing
     * {@link io.vertx.core.dns.impl.netty.decoder.RecordDecoder} after decoding a resource record when given a DNS
     * response packet.
     *
     * @param response
     *            the DNS response that contains the resource record being
     *            decoded
     * @param resource
     *            the resource record being decoded
     */
    T decode(DnsResponse response, DnsResource resource) throws DecoderException;

}
