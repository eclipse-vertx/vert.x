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

import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.net.SocketAddress;

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

  private Supplier<String> username;
  protected int port;
  protected String lastUri;
  protected String forceUri;
  protected List<String> localAddresses = Collections.synchronizedList(new ArrayList<>());

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

  public abstract TestProxyBase start(Vertx vertx) throws Exception;
  public abstract void stop();

}
