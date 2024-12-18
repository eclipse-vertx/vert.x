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
import io.vertx.test.http.HttpTestBase;
import io.vertx.test.tls.Cert;
import io.vertx.test.tls.Trust;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
public class Http3TLSTest extends HttpTLSTest {

  @Override
  protected HttpServerOptions createBaseServerOptions() {
    HttpServerOptions serverOptions = new HttpServerOptions()
      .setPort(HttpTestBase.DEFAULT_HTTPS_PORT)
      .setHttp3(true)
      .setUseAlpn(true)
      .setSsl(true);

    return serverOptions;
  }

  @Override
  protected HttpClientOptions createBaseClientOptions() {
    HttpClientOptions httpClientOptions = new HttpClientOptions()
      .setUseAlpn(true)
      .setSsl(true)
      .setProtocolVersion(HttpVersion.HTTP_3);
    httpClientOptions.setHttp3(true);
    return httpClientOptions;
  }

  @Override
  protected TLSTest testTLS(Cert<?> clientCert, Trust<?> clientTrust, Cert<?> serverCert, Trust<?> serverTrust) throws Exception {
    return super.testTLS(clientCert, clientTrust, serverCert, serverTrust).version(HttpVersion.HTTP_3);
  }
}
