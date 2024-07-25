/*
 * Copyright (c) 2011-2024 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.tls;

import io.vertx.core.Future;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.dns.AddressResolverOptions;
import io.vertx.core.http.*;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.http.HttpTestBase;
import io.vertx.test.tls.Cert;
import org.junit.Test;

public class HttpHostnameVerificationTest extends VertxTestBase {

  @Override
  protected VertxOptions getOptions() {
    return new VertxOptions(super.getOptions())
      .setAddressResolverOptions(new AddressResolverOptions()
        .setHostsValue(Buffer.buffer("127.0.0.1 example.com\n")));
  }

  @Test
  public void testDisableVerifyHost() throws Exception {
    HttpServer server = vertx.createHttpServer(new HttpServerOptions()
      .setSsl(true)
      .setKeyCertOptions(Cert.SERVER_JKS.get()));
    server.requestHandler(req -> {
      req.response().end("example.com");
    });
    awaitFuture(server.listen(HttpTestBase.DEFAULT_HTTPS_PORT, HttpTestBase.DEFAULT_HTTPS_HOST));
    HttpClient client = vertx.createHttpClient(new HttpClientOptions().setVerifyHost(false).setSsl(true).setTrustAll(true));
    RequestOptions options = new RequestOptions().setHost("example.com").setPort(HttpTestBase.DEFAULT_HTTPS_PORT);
    Future<Buffer> response = client.request(options)
      .compose(req -> req
        .send()
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(HttpClientResponse::body));
    assertEquals("example.com", awaitFuture(response).toString());
  }
}
