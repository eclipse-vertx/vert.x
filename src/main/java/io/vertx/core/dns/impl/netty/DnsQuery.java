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
 * A DNS query packet which is sent to a server to receive a DNS response packet
 * with information answering a DnsQuery's questions.
 */
public class DnsQuery extends DnsMessage<DnsQueryHeader> {

  /**
   * Constructs a DNS query. By default recursion will be toggled on.
   */
  public DnsQuery(int id) {
    setHeader(new DnsQueryHeader(this, id));
  }

}
