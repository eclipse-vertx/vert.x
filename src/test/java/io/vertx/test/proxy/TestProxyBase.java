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

package io.vertx.test.proxy;

import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;

/**
 * @author <a href="http://oss.lehmann.cx/">Alexander Lehmann</a>
 *
 */
public abstract class TestProxyBase {

  protected final String username;
  protected String lastUri;
  protected String forceUri;

  public TestProxyBase(String username) {
    this.username = username;
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

  public abstract int getPort();

  public abstract TestProxyBase start(Vertx vertx) throws Exception;
  public abstract void stop();

}
