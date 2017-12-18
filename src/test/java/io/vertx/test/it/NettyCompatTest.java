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

package io.vertx.test.it;

import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.OpenSSLEngineOptions;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.core.tls.Cert;
import io.vertx.test.core.tls.Trust;
import org.junit.Test;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class NettyCompatTest extends VertxTestBase {

  @Test
  public void testAddressResolver() {
    VertxInternal vertx = (VertxInternal) super.vertx;
    vertx.resolveAddress("localhost", onSuccess(v -> testComplete()));
    await();
  }

  @Test
  public void testHttp2() {
    vertx.createHttpServer(new HttpServerOptions()
        .setUseAlpn(true)
        .setSsl(true)
        .setSslEngineOptions(new OpenSSLEngineOptions())
        .setKeyCertOptions(Cert.SERVER_JKS.get())
    )
        .requestHandler(req -> req.response().end("OK"))
        .listen(8443, "localhost", onSuccess(s -> {
          HttpClient client = vertx.createHttpClient(new HttpClientOptions()
              .setSsl(true)
              .setSslEngineOptions(new OpenSSLEngineOptions())
              .setTrustStoreOptions(Trust.SERVER_JKS.get()));
          client.getNow(8443, "localhost", "/somepath", resp -> {
            resp.bodyHandler(buff -> {
              assertEquals("OK", buff.toString());
              testComplete();
            });
          });
        }));
    await();
  }
}
