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

package io.vertx.tests.tls;

import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpVersion;
import io.vertx.test.http.HttpConfig;
import io.vertx.test.http.HttpTestBase;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http2TLSTest extends HttpTCPTLSTest {

  public Http2TLSTest() {
    this(false);
  }

  protected Http2TLSTest(boolean multiplex) {
    super(new HttpConfig.Http2(multiplex) {
      @Override
      protected HttpServerOptions createBaseServerOptions(int port, String host, boolean multiplex) {
        return new HttpServerOptions()
          .setPort(HttpTestBase.DEFAULT_HTTPS_PORT)
          .setUseAlpn(true);
      }
      @Override
      protected HttpClientOptions createBaseClientOptions(int port, String host, boolean multiplex) {
        return new HttpClientOptions()
          .setUseAlpn(true)
          .setProtocolVersion(HttpVersion.HTTP_2);
      }
    });
  }
}
