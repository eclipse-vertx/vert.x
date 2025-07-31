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

package io.vertx.tests.http;

import io.netty.handler.codec.http3.DefaultHttp3Headers;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.impl.http2.Http2HeadersMultiMap;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
public class Http2H3ServerTest extends Http2ServerTest {

  @Override
  protected HttpServerOptions createBaseServerOptions() {
    return createH3HttpServerOptions(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST);
  }

  @Override
  protected HttpClientOptions createBaseClientOptions() {
    return createH3HttpClientOptions().setDefaultPort(DEFAULT_HTTPS_PORT).setDefaultHost(DEFAULT_HTTPS_HOST);
  }

  @Override
  protected Http2TestClient createClient() {
    return new Http2H3TestClient(vertx, eventLoopGroups, new Http2H3RequestHandler());
  }

  @Override
  protected void setInvalidAuthority(Http2HeadersMultiMap http2HeadersMultiMap, String authority) {
    ((DefaultHttp3Headers) http2HeadersMultiMap.unwrap()).authority(authority);
  }

  @Override
  protected Http2HeadersMultiMap createHttpHeader() {
    return new Http2HeadersMultiMap(new DefaultHttp3Headers());
  }

  @Override
  public void testGet() throws Exception {
    super.testGet();
  }
}
