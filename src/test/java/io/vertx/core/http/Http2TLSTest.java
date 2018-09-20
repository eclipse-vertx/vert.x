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

package io.vertx.core.http;

import io.vertx.test.tls.Cert;
import io.vertx.test.tls.Trust;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http2TLSTest extends HttpTLSTest {

  @Override
  HttpServer createHttpServer(HttpServerOptions options) {
    return vertx.createHttpServer(options.setUseAlpn(true));
  }

  @Override
  HttpClient createHttpClient(HttpClientOptions options) {
    return vertx.createHttpClient(options.setUseAlpn(true));
  }

  @Override
  protected TLSTest testTLS(Cert<?> clientCert, Trust<?> clientTrust, Cert<?> serverCert, Trust<?> serverTrust) throws Exception {
    return super.testTLS(clientCert, clientTrust, serverCert, serverTrust).version(HttpVersion.HTTP_2);
  }
}
