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

import io.vertx.core.dns.impl.netty.DnsResource;
import io.vertx.core.dns.impl.netty.DnsResponse;
import io.vertx.core.dns.impl.netty.DnsResponseDecoder;

/**
 * Decodes any record that simply returns a domain name, such as NS (name
 * server) and CNAME (canonical name) resource records.
 */
public class DomainDecoder implements RecordDecoder<String> {

    /**
     * Returns the decoded domain name for a resource record.
     *
     * @param response
     *            the {@link io.vertx.core.dns.impl.netty.DnsResponse} received that contained the resource
     *            record being decoded
     * @param resource
     *            the {@link DnsResource} being decoded
     */
    @Override
    public String decode(DnsResponse response, DnsResource resource) {
        return DnsResponseDecoder.getName(response.content(), resource.contentIndex());
    }

}
