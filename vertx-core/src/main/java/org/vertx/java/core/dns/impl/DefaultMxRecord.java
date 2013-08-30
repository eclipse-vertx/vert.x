/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
}
