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

import io.vertx.core.dns.impl.netty.DnsEntry;
import io.vertx.core.dns.impl.netty.DnsResource;
import io.vertx.core.dns.impl.netty.DnsResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Handles the decoding of resource records. Some default decoders are mapped to
 * their resource types in the map {@code decoders}.
 */
public final class RecordDecoderFactory {

    private static RecordDecoderFactory factory = new RecordDecoderFactory(null);

    /**
     * Returns the active {@link io.vertx.core.dns.impl.netty.decoder.RecordDecoderFactory}, which is the same as the
     * default factory if it has not been changed by the user.
     */
    public static RecordDecoderFactory getFactory() {
        return factory;
    }

    /**
     * Sets the active {@link io.vertx.core.dns.impl.netty.decoder.RecordDecoderFactory} to be used for decoding
     * resource records.
     *
     * @param factory
     *            the {@link io.vertx.core.dns.impl.netty.decoder.RecordDecoderFactory} to use
     */
    public static void setFactory(RecordDecoderFactory factory) {
      Objects.requireNonNull(factory, "Cannot set record decoder factory to null.");
      RecordDecoderFactory.factory = factory;
    }

    private final Map<Integer, RecordDecoder<?>> decoders = new HashMap<Integer, RecordDecoder<?>>();

    /**
     * Creates a new {@link io.vertx.core.dns.impl.netty.decoder.RecordDecoderFactory} only using the default
     * decoders.
     */
    public RecordDecoderFactory() {
        this(true, null);
    }

    /**
     * Creates a new {@link io.vertx.core.dns.impl.netty.decoder.RecordDecoderFactory} using the default decoders and
     * custom decoders (custom decoders override defaults).
     *
     * @param customDecoders
     *            user supplied resource record decoders, mapping the resource
     *            record's type to the decoder
     */
    public RecordDecoderFactory(Map<Integer, RecordDecoder<?>> customDecoders) {
        this(true, customDecoders);
    }

    /**
     * Creates a {@link io.vertx.core.dns.impl.netty.decoder.RecordDecoderFactory} using either custom resource
     * record decoders, default decoders, or both. If a custom decoder has the
     * same record type as a default decoder, the default decoder is overriden.
     *
     * @param useDefaultDecoders
     *            if {@code true}, adds default decoders
     * @param customDecoders
     *            if not {@code null} or empty, adds custom decoders
     */
    public RecordDecoderFactory(boolean useDefaultDecoders, Map<Integer, RecordDecoder<?>> customDecoders) {
        if (!useDefaultDecoders && (customDecoders == null || customDecoders.isEmpty())) {
            throw new IllegalStateException("No decoders have been included to be used with this factory.");
        }
        if (useDefaultDecoders) {
            decoders.put(DnsEntry.TYPE_A, new AddressDecoder(4));
            decoders.put(DnsEntry.TYPE_AAAA, new AddressDecoder(16));
            decoders.put(DnsEntry.TYPE_MX, new MailExchangerDecoder());
            decoders.put(DnsEntry.TYPE_TXT, new TextDecoder());
            decoders.put(DnsEntry.TYPE_SRV, new ServiceDecoder());
            RecordDecoder<?> decoder = new DomainDecoder();
            decoders.put(DnsEntry.TYPE_NS, decoder);
            decoders.put(DnsEntry.TYPE_CNAME, decoder);
            decoders.put(DnsEntry.TYPE_PTR, decoder);
            decoders.put(DnsEntry.TYPE_SOA, new StartOfAuthorityDecoder());
        }
        if (customDecoders != null) {
            decoders.putAll(customDecoders);
        }
    }

    /**
     * Decodes a resource record and returns the result.
     *
     * @param type
     *            the type of resource record
     * @param response
     *            the DNS response that contains the resource record being
     *            decoded
     * @param resource
     *            the resource record being decoded
     * @return the decoded resource record
     */
    @SuppressWarnings("unchecked")
    public <T> T decode(int type, DnsResponse response, DnsResource resource) {
        RecordDecoder<?> decoder = decoders.get(type);
        if (decoder == null) {
            throw new IllegalStateException("Unsupported resource record type [id: " + type + "].");
        }
        T result = null;
        try {
            result = (T) decoder.decode(response, resource);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

}
