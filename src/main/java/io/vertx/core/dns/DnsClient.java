/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.dns;

import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Promise;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.util.List;

/**
 * Provides a way to asynchronously lookup information from DNS servers.
 * <p>
 * Please consult the documentation for more information on DNS clients.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
@VertxGen
public interface DnsClient {

  /**
   * Try to lookup the A (ipv4) or AAAA (ipv6) record for the given name. The first found will be used.
   *
   * @param name  the name to resolve
   * @param handler  the {@link io.vertx.core.Handler} to notify with the {@link io.vertx.core.AsyncResult}.
   *                 The handler will get notified with the resolved address if a record was found. If non was found it
   *                 will get notifed with {@code null}. If an error accours it will get failed.
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  DnsClient lookup(String name, Handler<AsyncResult<@Nullable String>> handler);

  /**
   * Like {@link #lookup(String, Handler)} but returns a {@code Future} of the asynchronous result
   */
  default Future<@Nullable String> lookup(String name) {
    Promise<String> promise = Promise.promise();
    lookup(name, promise);
    return promise.future();
  }

  /**
   * Try to lookup the A (ipv4) record for the given name. The first found will be used.
   *
   * @param name  the name to resolve
   * @param handler  the {@link Handler} to notify with the {@link io.vertx.core.AsyncResult}.
   *                 The handler will get notified with the resolved {@link java.net.Inet4Address} if a record was found.
   *                 If non was found it will get notifed with {@code null}. If an error accours it will get failed.
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  DnsClient lookup4(String name, Handler<AsyncResult<@Nullable String>> handler);

  /**
   * Like {@link #lookup4(String, Handler)} but returns a {@code Future} of the asynchronous result
   */
  default Future<@Nullable String> lookup4(String name) {
    Promise<String> promise = Promise.promise();
    lookup4(name, promise);
    return promise.future();
  }

  /**
   * Try to lookup the AAAA (ipv6) record for the given name. The first found will be used.
   *
   * @param name  the name to resolve
   * @param handler  the {@link Handler} to notify with the {@link AsyncResult}. The handler will get
   *                 notified with the resolved {@link java.net.Inet6Address} if a record was found. If non was found
   *                 it will get notifed with {@code null}. If an error accours it will get failed.
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  DnsClient lookup6(String name, Handler<AsyncResult<@Nullable String>> handler);

  /**
   * Like {@link #lookup6(String, Handler)} but returns a {@code Future} of the asynchronous result
   */
  default Future<@Nullable String> lookup6(String name) {
    Promise<String> promise = Promise.promise();
    lookup6(name, promise);
    return promise.future();
  }

  /**
   * Try to resolve all A (ipv4) records for the given name.
   *
   * @param name  the name to resolve
   * @param handler  the {@link io.vertx.core.Handler} to notify with the {@link io.vertx.core.AsyncResult}.
   *                 The handler will get notified with a {@link java.util.List} that contains all the resolved
   *                 {@link java.net.Inet4Address}es. If none was found an empty {@link java.util.List} will be used.
   *                 If an error accours it will get failed.
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  DnsClient resolveA(String name, Handler<AsyncResult<List<String>>> handler);

  /**
   * Like {@link #resolveA(String, Handler)} but returns a {@code Future} of the asynchronous result
   */
  default Future<List<String>> resolveA(String name) {
    Promise<List<String>> promise = Promise.promise();
    resolveA(name, promise);
    return promise.future();
  }

  /**
   * Try to resolve all AAAA (ipv6) records for the given name.
   *
   * @param name  the name to resolve
   * @param handler the {@link io.vertx.core.Handler} to notify with the {@link io.vertx.core.AsyncResult}.
   *                The handler will get notified with a {@link java.util.List} that contains all the resolved
   *                {@link java.net.Inet6Address}es. If none was found an empty {@link java.util.List} will be used.
   *                If an error accours it will get failed.
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  DnsClient resolveAAAA(String name, Handler<AsyncResult<List<String>>> handler);

  /**
   * Like {@link #resolveAAAA(String, Handler)} but returns a {@code Future} of the asynchronous result
   */
  default Future<List<String>> resolveAAAA(String name) {
    Promise<List<String>> promise = Promise.promise();
    resolveAAAA(name, promise);
    return promise.future();
  }

  /**
   * Try to resolve the CNAME record for the given name.
   *
   * @param name  the name to resolve the CNAME for
   * @param handler  the {@link Handler} to notify with the {@link AsyncResult}. The handler will get
   *                 notified with the resolved {@link String} if a record was found. If none was found it will
   *                 get notified with {@code null}. If an error accours it will get failed.
   * @return a reference to this, so the API can be used fluently.
   */
  @Fluent
  DnsClient resolveCNAME(String name, Handler<AsyncResult<List<String>>> handler);

  /**
   * Like {@link #resolveCNAME(String, Handler)} but returns a {@code Future} of the asynchronous result
   */
  default Future<List<String>> resolveCNAME(String name) {
    Promise<List<String>> promise = Promise.promise();
    resolveCNAME(name, promise);
    return promise.future();
  }

  /**
   * Try to resolve the MX records for the given name.
   *
   * @param name  the name for which the MX records should be resolved
   * @param handler  the {@link io.vertx.core.Handler} to notify with the {@link io.vertx.core.AsyncResult}.
   *                 The handler will get notified with a List that contains all resolved {@link MxRecord}s, sorted by
   *                 their {@link MxRecord#priority()}. If non was found it will get notified with an empty
   *                 {@link java.util.List}.  If an error accours it will get failed.
   * @return a reference to this, so the API can be used fluently.
   */
  @Fluent
  DnsClient resolveMX(String name, Handler<AsyncResult<List<MxRecord>>> handler);

  /**
   * Like {@link #resolveMX(String, Handler)} but returns a {@code Future} of the asynchronous result
   */
  default Future<List<MxRecord>> resolveMX(String name) {
    Promise<List<MxRecord>> promise = Promise.promise();
    resolveMX(name, promise);
    return promise.future();
  }

  /**
   * Try to resolve the TXT records for the given name.
   *
   * @param name  the name for which the TXT records should be resolved
   * @param handler  the {@link Handler} to notify with the {@link AsyncResult}. The handler will get
   *                 notified with a List that contains all resolved {@link String}s. If none was found it will
   *                 get notified with an empty {@link java.util.List}. If an error accours it will get failed.
   * @return a reference to this, so the API can be used fluently.
   */
  @Fluent
  DnsClient resolveTXT(String name, Handler<AsyncResult<List<String>>> handler);

  /**
   * Like {@link #resolveTXT(String, Handler)} but returns a {@code Future} of the asynchronous result
   */
  default Future<List<String>> resolveTXT(String name) {
    Promise<List<String>> promise = Promise.promise();
    resolveTXT(name, promise);
    return promise.future();
  }

  /**
   * Try to resolve the PTR record for the given name.
   *
   * @param name  the name to resolve the PTR for
   * @param handler  the {@link Handler} to notify with the {@link AsyncResult}. The handler will get
   *                 notified with the resolved {@link String} if a record was found. If none was found it will
   *                 get notified with {@code null}. If an error accours it will get failed.
   * @return a reference to this, so the API can be used fluently.
   */
  @Fluent
  DnsClient resolvePTR(String name, Handler<AsyncResult<@Nullable String>> handler);

  /**
   * Like {@link #resolvePTR(String, Handler)} but returns a {@code Future} of the asynchronous result
   */
  default Future<@Nullable String> resolvePTR(String name) {
    Promise<String> promise = Promise.promise();
    resolvePTR(name, promise);
    return promise.future();
  }

  /**
   * Try to resolve the NS records for the given name.
   *
   * @param name  the name for which the NS records should be resolved
   * @param handler  the {@link Handler} to notify with the {@link AsyncResult}. The handler will get
   *                 notified with a List that contains all resolved {@link String}s. If none was found it will
   *                 get notified with an empty {@link java.util.List}.  If an error accours it will get failed.
   * @return a reference to this, so the API can be used fluently.
   */
  @Fluent
  DnsClient resolveNS(String name, Handler<AsyncResult<List<String>>> handler);

  /**
   * Like {@link #resolveNS(String, Handler)} but returns a {@code Future} of the asynchronous result
   */
  default Future<List<String>> resolveNS(String name) {
    Promise<List<String>> promise = Promise.promise();
    resolveNS(name, promise);
    return promise.future();
  }

  /**
   * Try to resolve the SRV records for the given name.
   *
   * @param name  the name for which the SRV records should be resolved
   * @param handler  the {@link Handler} to notify with the {@link AsyncResult}. The handler will get
   *                 notified with a List that contains all resolved {@link SrvRecord}s. If none was found it will
   *                 get notified with an empty {@link java.util.List}. If an error accours it will get failed.
   * @return a reference to this, so the API can be used fluently.
   */
  @Fluent
  DnsClient resolveSRV(String name, Handler<AsyncResult<List<SrvRecord>>> handler);

  /**
   * Like {@link #resolveSRV(String, Handler)} but returns a {@code Future} of the asynchronous result
   */
  default Future<List<SrvRecord>> resolveSRV(String name) {
    Promise<List<SrvRecord>> promise = Promise.promise();
    resolveSRV(name, promise);
    return promise.future();
  }

  /**
   * Try to do a reverse lookup of an IP address. This is basically the same as doing trying to resolve a PTR record
   * but allows you to just pass in the IP address and not a valid ptr query string.
   *
   * @param ipaddress  the IP address to resolve the PTR for
   * @param handler  the {@link Handler} to notify with the {@link AsyncResult}. The handler will get
   *                 notified with the resolved {@link String} if a record was found. If none was found it will
   *                 get notified with {@code null}. If an error accours it will get failed.
   * @return a reference to this, so the API can be used fluently.
   */
  @Fluent
  DnsClient reverseLookup(String ipaddress, Handler<AsyncResult<@Nullable String>> handler);

  /**
   * Like {@link #reverseLookup(String, Handler)} but returns a {@code Future} of the asynchronous result
   */
  default Future<@Nullable String> reverseLookup(String ipaddress) {
    Promise<String> promise = Promise.promise();
    reverseLookup(ipaddress, promise);
    return promise.future();
  }
}
