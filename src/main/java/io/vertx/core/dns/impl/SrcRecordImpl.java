/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
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
package io.vertx.core.dns.impl;

import io.vertx.core.dns.SrvRecord;
import io.vertx.core.dns.impl.netty.decoder.record.ServiceRecord;


/**
 *
 */
final class SrcRecordImpl implements SrvRecord, Comparable<SrvRecord> {
  private final ServiceRecord record;

  SrcRecordImpl(ServiceRecord record) {
    this.record = record;
  }

  @Override
  public int priority() {
    return record.priority();
  }

  @Override
  public int weight() {
    return record.weight();
  }

  @Override
  public int port() {
    return record.port();
  }

  @Override
  public String name() {
    return record.name();
  }

  @Override
  public String protocol() {
    return record.protocol();
  }

  @Override
  public String service() {
    return record.service();
  }

  @Override
  public String target() {
    return record.target();
  }

  @Override
  public int compareTo(SrvRecord o) {
    return Integer.valueOf(priority()).compareTo(o.priority());
  }
}
