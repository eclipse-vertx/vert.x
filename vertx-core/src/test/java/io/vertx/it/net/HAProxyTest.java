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

package io.vertx.it.net;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import static io.vertx.test.http.HttpTestBase.DEFAULT_HTTP_HOST;
import static io.vertx.test.http.HttpTestBase.DEFAULT_HTTP_PORT;

/**
 * @author <a href="mailto:dimitris.zenios@gmail.com">Dimitris Zenios</a>
 */
public class HAProxyTest extends VertxTestBase {
  @Test
  public void testHttpWithoutHAProxyOnTheClassPath() {
    ClassLoader cl = Vertx.class.getClassLoader();
    try {
      cl.loadClass("io.netty.handler.codec.haproxy.HAProxyMessage");
      fail();
    } catch (ClassNotFoundException ignore) {
    }
    Vertx vertx = Vertx.vertx();
    try {
      vertx.createHttpServer(new HttpServerOptions().setUseProxyProtocol(true))
        .requestHandler(req -> {
          req.response().end("hello");
        })
        .listen(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST).onComplete(onSuccess(s -> {
          HttpClient client = vertx.createHttpClient();
          client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/")
            .compose(req -> req.send().compose(HttpClientResponse::body))
            .onComplete(onSuccess(body -> {
              assertEquals("hello", body.toString());
              testComplete();
            }));
        }));
      await();
    } finally {
      vertx.close();
    }
  }
}
