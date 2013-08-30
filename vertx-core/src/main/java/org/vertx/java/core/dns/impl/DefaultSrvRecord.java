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
}
