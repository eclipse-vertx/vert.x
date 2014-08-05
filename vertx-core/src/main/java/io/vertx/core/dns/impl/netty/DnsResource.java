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
import io.netty.buffer.ByteBufHolder;

/**
 * Represents any resource record (answer, authority, or additional resource
 * records).
 */
public class DnsResource extends DnsEntry implements ByteBufHolder {

  private final int contentIndex;
  private final long ttl;
  private final ByteBuf content;

  /**
   * Constructs a resource record.
   *
   * @param name         the domain name
   * @param type         the type of record being returned
   * @param aClass       the class for this resource record
   * @param ttl          the time to live after reading
   * @param contentIndex the {@code writerIndex} at which the content appears in the
   *                     original packet
   * @param content      the data contained in this record
   */
  public DnsResource(String name, int type, int aClass, long ttl, int contentIndex, ByteBuf content) {
    super(name, type, aClass);
    this.ttl = ttl;
    this.contentIndex = contentIndex;
    this.content = content;
  }

  /**
   * Returns the time to live after reading for this resource record.
   */
  public long timeToLive() {
    return ttl;
  }

  /**
   * Returns the index at which the content of this resource record
   * appears in the original packet.
   */
  public int contentIndex() {
    return contentIndex;
  }

  /**
   * Returns the length of the content in this resource record.
   */
  public int contentLength() {
    return content.writerIndex() - content.readerIndex();
  }

  /**
   * Returns the data contained in this resource record.
   */
  @Override
  public ByteBuf content() {
    return content;
  }

  /**
   * Returns a deep copy of this resource record.
   */
  @Override
  public DnsResource copy() {
    return new DnsResource(name(), type(), dnsClass(), ttl, contentIndex, content.copy());
  }

  /**
   * Returns a duplicate of this resource record.
   */
  @Override
  public ByteBufHolder duplicate() {
    return new DnsResource(name(), type(), dnsClass(), ttl, contentIndex, content.duplicate());
  }

  @Override
  public int refCnt() {
    return content.refCnt();
  }

  @Override
  public DnsResource retain() {
    content.retain();
    return this;
  }

  @Override
  public DnsResource retain(int increment) {
    content.retain(increment);
    return this;
  }

  @Override
  public boolean release() {
    return content.release();
  }

  @Override
  public boolean release(int decrement) {
    return content.release(decrement);
  }

}
