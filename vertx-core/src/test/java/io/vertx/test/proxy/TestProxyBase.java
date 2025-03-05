/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.test.proxy;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServerOptions;
import io.vertx.tests.http.HttpOptionsFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;

/**
 * @author <a href="http://oss.lehmann.cx/">Alexander Lehmann</a>
 *
 */
public abstract class TestProxyBase<P extends TestProxyBase<P>> {
  private static final Logger log = LoggerFactory.getLogger(TestProxyBase.class);

  private Supplier<String> username;
  protected int port;
  protected String lastUri;
  protected String forceUri;
  protected List<String> localAddresses = Collections.synchronizedList(new ArrayList<>());
  protected boolean http3 = false;

  public TestProxyBase() {
    port = defaultPort();
  }

  public P username(String username) {
    this.username = () -> username;
    return (P) this;
  }

  public P username(Supplier<String> username) {
    this.username = username;
    return (P) this;
  }

  public P username(Collection<String> username) {
    Iterator<String> it = username.iterator();
    this.username = () -> it.hasNext() ? it.next() : null;
    return (P) this;
  }

  public String nextUserName() {
    return username != null ? username.get() : null;
  }

  public P port(int port) {
    this.port = port;
    return (P)this;
  }

  public int port() {
    return port;
  }

  public abstract int defaultPort();

  public String lastLocalAddress() {
    int idx = localAddresses.size();
    return idx == 0 ? null : localAddresses.get(idx - 1);
  }

  public List<String> localAddresses() {
    return localAddresses;
  }

  /**
   * check the last accessed host:ip
   *
   * @return the lastUri
   */
  public String getLastUri() {
    return lastUri;
  }

  /**
   * check the last HTTP method
   *
   * @return the last method
   */
  public HttpMethod getLastMethod() {
    throw new UnsupportedOperationException();
  }

  public P http3(boolean http3) {
    this.http3 = http3;
    return (P)this;
  }

  public boolean isHttp3() {
    return http3;
  }

  /**
   * force uri to connect to a given string (e.g. "localhost:4443") this is used to simulate a host that only resolves
   * on the proxy
   */
  public void setForceUri(String uri) {
    forceUri = uri;
  }

  public MultiMap getLastRequestHeaders() {
    throw new UnsupportedOperationException();
  }

  public <T>Future<T> startProxy(Vertx vertx) {
    return (Future<T>) start0(vertx).onComplete(event -> {
      log.debug(TestProxyBase.this.getClass().getSimpleName() + " server started");
    });
  }

  protected NetClientOptions createNetClientOptions() {
    return http3 ? HttpOptionsFactory.createH3NetClientOptions() : new NetClientOptions();
  }

  protected NetServerOptions createNetServerOptions() {
    return http3 ? HttpOptionsFactory.createH3NetServerOptions() : new NetServerOptions();
  }

  protected HttpClientOptions createHttpClientOptions() {
    return http3 ? HttpOptionsFactory.createH3HttpClientOptions() : new HttpClientOptions();
  }

  protected HttpServerOptions createHttpServerOptions() {
    return http3 ? HttpOptionsFactory.createH3HttpServerOptions() : new HttpServerOptions();
  }

  protected abstract<T> Future<T> start0(Vertx vertx);

  /*
    This method is deprecated. Please use the 'startProxy' method instead, as it returns the result of the operation,
    allowing the user to inspect it. Additionally, the test can count down the status of the result
   */
  @Deprecated(since = "This method is deprecated. Please use the 'startProxy' method instead.")
  public abstract TestProxyBase start(Vertx vertx) throws Exception;
  public abstract void stop();

}
