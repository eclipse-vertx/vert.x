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
 * Provides a way to asynchronous lookup informations from DNS-Servers.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public interface DnsClient {

  /**
   * Try to lookup the A (ipv4) or AAAA (ipv6) record for the given name. The first found will be used.
   *
   * @param name      The name to resolve
   * @param handler   the {@link Handler} to notify with the {@link AsyncResult}. The {@link AsyncResult} will get
   *                  notified with the resolved {@link InetAddress} if a record was found. If non was found it will
   *                  get notifed with {@code null}.
   *                  If an error accours it will get failed.
   * @return          itself for method-chaining.
   */
  DnsClient lookup(String name, Handler<AsyncResult<InetAddress>> handler);

  /**
   * Try to lookup the A (ipv4) record for the given name. The first found will be used.
   *
   * @param name      The name to resolve
   * @param handler   the {@link Handler} to notify with the {@link AsyncResult}. The {@link AsyncResult} will get
   *                  notified with the resolved {@link Inet4Address} if a record was found. If non was found it will
   *                  get notifed with {@code null}.
   *                  If an error accours it will get failed.
   * @return          itself for method-chaining.
   */
  DnsClient lookup4(String name, Handler<AsyncResult<Inet4Address>> handler);

  /**
   * Try to lookup the AAAA (ipv6) record for the given name. The first found will be used.
   *
   * @param name      The name to resolve
   * @param handler   the {@link Handler} to notify with the {@link AsyncResult}. The {@link AsyncResult} will get
   *                  notified with the resolved {@link Inet6Address} if a record was found. If non was found it will
   *                  get notifed with {@code null}.
   *                  If an error accours it will get failed.
   * @return          itself for method-chaining.
   */
  DnsClient lookup6(String name, Handler<AsyncResult<Inet6Address>> handler);

  /**
   * Try to resolve all A (ipv4) records for the given name.
   *
   *
   *
   * @param name      The name to resolve
   * @param handler   the {@link org.vertx.java.core.Handler} to notify with the {@link org.vertx.java.core.AsyncResult}. The {@link org.vertx.java.core.AsyncResult} will get
   *                  notified with a {@link java.util.List} that contains all the resolved {@link java.net.Inet4Address}es. If non was found
   *                  and empty {@link java.util.List} will be used.
   *                  If an error accours it will get failed.
   * @return          itself for method-chaining.
   */
  DnsClient resolveA(String name, Handler<AsyncResult<List<Inet4Address>>> handler);

  /**
   * Try to resolve all AAAA (ipv6) records for the given name.
   *
   *
   * @param name      The name to resolve
   * @param handler   the {@link org.vertx.java.core.Handler} to notify with the {@link org.vertx.java.core.AsyncResult}. The {@link org.vertx.java.core.AsyncResult} will get
   *                  notified with a {@link java.util.List} that contains all the resolved {@link java.net.Inet6Address}es. If non was found
   *                  and empty {@link java.util.List} will be used.
   *                  If an error accours it will get failed.
   * @return          itself for method-chaining.
   */
  DnsClient resolveAAAA(String name, Handler<AsyncResult<List<Inet6Address>>> handler);

  /**
   * Try to resolve the CNAME record for the given name.
   *
   * @param name      The name to resolve the CNAME for
   * @param handler   the {@link Handler} to notify with the {@link AsyncResult}. The {@link AsyncResult} will get
   *                  notified with the resolved {@link String} if a record was found. If non was found it will
   *                  get notified with {@code null}.
   *                  If an error accours it will get failed.
   * @return          itself for method-chaining.
   */
  DnsClient resolveCNAME(String name, Handler<AsyncResult<String>> handler);

  /**
   * Try to resolve the MX records for the given name.
   *
   *
   * @param name      The name for which the MX records should be resolved
   * @param handler   the {@link org.vertx.java.core.Handler} to notify with the {@link org.vertx.java.core.AsyncResult}. The {@link org.vertx.java.core.AsyncResult} will get
   *                  notified with a List that contains all resolved {@link org.vertx.java.core.dns.MxRecord}s, sorted by their
   *                  {@link org.vertx.java.core.dns.MxRecord#priority()}. If non was found it will get notified with an empty {@link java.util.List}
   *                  If an error accours it will get failed.
   * @return          itself for method-chaining.
   */
  DnsClient resolveMX(String name, Handler<AsyncResult<List<MxRecord>>> handler);

  /**
   * Try to resolve the TXT records for the given name.
   *
   * @param name      The name for which the TXT records should be resolved
   * @param handler   the {@link Handler} to notify with the {@link AsyncResult}. The {@link AsyncResult} will get
   *                  notified with a List that contains all resolved {@link String}s. If non was found it will
   *                  get notified with an empty {@link List}
   *                  If an error accours it will get failed.
   * @return          itself for method-chaining.
   */
  DnsClient resolveTXT(String name, Handler<AsyncResult<List<String>>> handler);

  /**
   * Try to resolve the PTR record for the given name.
   *
   * @param name      The name to resolve the PTR for
   * @param handler   the {@link Handler} to notify with the {@link AsyncResult}. The {@link AsyncResult} will get
   *                  notified with the resolved {@link String} if a record was found. If non was found it will
   *                  get notified with {@code null}.
   *                  If an error accours it will get failed.
   * @return          itself for method-chaining.
   */
  DnsClient resolvePTR(String name, Handler<AsyncResult<String>> handler);
}
