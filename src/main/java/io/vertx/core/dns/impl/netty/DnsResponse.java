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

import io.netty.buffer.ByteBuf;

/**
 * A DNS response packet which is sent to a client after a server receives a
 * query.
 */
public class DnsResponse extends DnsMessage<DnsResponseHeader> {

  private final ByteBuf rawPacket;
  private final int originalIndex;

  public DnsResponse(ByteBuf rawPacket) {
    this.rawPacket = rawPacket;
    this.originalIndex = rawPacket.readerIndex();
  }

  /**
   * Returns the non-decoded DNS response packet.
   */
  public ByteBuf content() {
    return rawPacket;
  }


  /**
   * Returns the original index at which the DNS response packet starts for the {@link io.netty.buffer.ByteBuf}
   * stored in this {@link io.netty.buffer.ByteBufHolder}.
   */
  public int originalIndex() {
    return originalIndex;
  }

}
