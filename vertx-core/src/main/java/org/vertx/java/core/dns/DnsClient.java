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
package org.vertx.java.core.dns;


import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.List;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public interface DnsClient {

  DnsClient lookup(String record, Handler<AsyncResult<InetAddress>> handler);

  DnsClient lookup4(String record, Handler<AsyncResult<Inet4Address>> handler);

  DnsClient lookup6(String record, Handler<AsyncResult<Inet6Address>> handler);

  DnsClient lookupARecords(String record, Handler<AsyncResult<List<InetAddress>>> handle);

  DnsClient lookupCName(String record, Handler<AsyncResult<String>> handler);

  DnsClient lookupMXRecords(String record, Handler<AsyncResult<List<MxRecord>>> handler);

  DnsClient lookupTXTRecords(String record, Handler<AsyncResult<List<String>>> handler);

  DnsClient lookupPTRRecord(String record, Handler<AsyncResult<List>> handler);

  DnsClient lookupAAAARecord(String record, Handler<AsyncResult<List<InetAddress>>> handler);

}
