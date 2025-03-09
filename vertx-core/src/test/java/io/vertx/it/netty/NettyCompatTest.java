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

package io.vertx.it.netty;

import io.vertx.core.http.*;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.net.OpenSSLEngineOptions;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.tls.Cert;
import io.vertx.test.tls.Trust;
import org.junit.Test;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class NettyCompatTest extends VertxTestBase {

  @Test
  public void testAddressResolver() {
    VertxInternal vertx = (VertxInternal) super.vertx;
    vertx.nameResolver().resolve("localhost").onComplete(onSuccess(v -> testComplete()));
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
        .listen(8443, "localhost").onComplete(onSuccess(s -> {
          HttpClient client = vertx.createHttpClient(new HttpClientOptions()
              .setSsl(true)
              .setSslEngineOptions(new OpenSSLEngineOptions())
              .setTrustOptions(Trust.SERVER_JKS.get()));
          client
            .request(HttpMethod.GET, 8443, "localhost", "/somepath")
            .compose(req -> req.send().compose(HttpClientResponse::body))
            .onComplete(buff -> {
              assertEquals("OK", buff.toString());
              testComplete();
            });
        }));
    await();
  }
}
