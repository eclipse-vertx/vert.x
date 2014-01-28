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

import org.vertx.java.core.dns.MxRecord;
import org.vertx.java.core.dns.impl.netty.decoder.record.MailExchangerRecord;


/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
final class DefaultMxRecord implements MxRecord, Comparable<MxRecord> {
  private final MailExchangerRecord record;

  DefaultMxRecord(MailExchangerRecord record) {
    this.record = record;
  }

  @Override
  public int priority() {
    return record.priority();
  }

  @Override
  public String name() {
    return record.name();
  }

  @Override
  public String toString() {
    return priority() + " " + name();
  }

  @Override
  public int compareTo(MxRecord o) {
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
    DefaultMxRecord other = (DefaultMxRecord) obj;
    if (record == null) {
      if (other.record != null)
        return false;
    }
    else if (!record.equals(other.record))
      return false;
    return true;
  }
}
