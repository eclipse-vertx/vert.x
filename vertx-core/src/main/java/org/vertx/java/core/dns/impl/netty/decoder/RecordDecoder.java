/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.vertx.java.core.dns.impl.netty.decoder;

import io.netty.handler.codec.DecoderException;
import org.vertx.java.core.dns.impl.netty.DnsResource;
import org.vertx.java.core.dns.impl.netty.DnsResponse;

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
     * {@link RecordDecoder} after decoding a resource record when given a DNS
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
