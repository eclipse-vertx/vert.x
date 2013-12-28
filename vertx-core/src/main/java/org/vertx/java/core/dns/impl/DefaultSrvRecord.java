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
package org.vertx.java.core.dns.impl;

import org.vertx.java.core.dns.SrvRecord;
import org.vertx.java.core.dns.impl.netty.decoder.record.ServiceRecord;


/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
final class DefaultSrvRecord implements SrvRecord, Comparable<SrvRecord>{
  private final ServiceRecord record;

  DefaultSrvRecord(ServiceRecord record) {
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

  @Override
  public int hashCode()
  {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((record == null) ? 0 : record.hashCode());
      return result;
  }

  @Override
  public boolean equals(Object obj)
  {
      if (this == obj)
          return true;
      if (obj == null)
          return false;
      if (getClass() != obj.getClass())
          return false;
      DefaultSrvRecord other = (DefaultSrvRecord) obj;
      if (record == null) {
          if (other.record != null)
              return false;
      }
      else if (!record.equals(other.record))
          return false;
      return true;
  }
}
