/*
 * Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
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
