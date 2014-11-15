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
package io.vertx.core.dns.impl.netty;

/**
 * The DNS query header class which is used to represent the 12 byte header in a
 * {@link DnsQuery}.
 */
public class DnsQueryHeader extends DnsHeader {

  /**
   * Constructor for a DNS packet query header. The id is user generated and
   * will be replicated in the response packet by the server.
   *
   * @param parent the {@link DnsMessage} this header belongs to
   * @param id     a 2 bit unsigned identification number for this query
   */
  public DnsQueryHeader(DnsMessage<? extends DnsQueryHeader> parent, int id) {
    super(parent);
    setId(id);
    setType(TYPE_QUERY);
    setRecursionDesired(true);
  }

  /**
   * Returns the {@link DnsMessage} type. This will always return
   * {@code TYPE_QUERY}.
   */
  @Override
  public final int getType() {
    return TYPE_QUERY;
  }

  /**
   * Sets the {@link DnsHeader} type. Must be {@code TYPE_RESPONSE}.
   *
   * @param type message type
   * @return the header to allow method chaining
   */
  @Override
  public final DnsQueryHeader setType(int type) {
    if (type != TYPE_QUERY) {
      throw new IllegalArgumentException("type cannot be anything but TYPE_QUERY (0) for a query header.");
    }
    super.setType(type);
    return this;
  }

}
