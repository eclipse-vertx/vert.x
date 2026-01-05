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

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.net.SocketAddress;
import io.vertx.test.core.TestUtils;
import io.vertx.test.http.HttpTestBase;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

public class HttpDomainSocketTest extends HttpTestBase {

  @Test
  public void testListenDomainSocketAddressNative() throws Exception {
    VertxInternal vx = (VertxInternal) vertx(() -> Vertx.vertx(new VertxOptions().setPreferNativeTransport(true)));
    assumeTrue("Native transport must be enabled", vx.isNativeTransportEnabled());
    testListenDomainSocketAddress(vx);
  }

  @Test
  public void testListenDomainSocketAddressJdk() throws Exception {
    VertxInternal vx = (VertxInternal) vertx(() -> Vertx.vertx(new VertxOptions().setPreferNativeTransport(false)));
    assumeFalse("Native transport must not be enabled", vx.isNativeTransportEnabled());
    testListenDomainSocketAddress(vx);
  }

  private void testListenDomainSocketAddress(VertxInternal vx) throws Exception {
    assumeTrue("Transport must support domain sockets", vx.transport().supportsDomainSockets());
    int len = 3;
    waitFor(len * len);
    List<SocketAddress> addresses = new ArrayList<>();
    for (int i = 0;i < len;i++) {
      File sockFile = TestUtils.tmpFile(".sock");
      SocketAddress sockAddress = SocketAddress.domainSocketAddress(sockFile.getAbsolutePath());
      HttpServer server = vx
        .createHttpServer(createBaseServerOptions())
        .requestHandler(req -> req.response().end(sockAddress.path()));
      startServer(sockAddress, server);
      addresses.add(sockAddress);
    }
    HttpClient client = vx.createHttpClient(createBaseClientOptions());
    for (int i = 0;i < len;i++) {
      SocketAddress sockAddress = addresses.get(i);
      for (int j = 0;j < len;j++) {
        client
          .request(new RequestOptions(requestOptions).setServer(sockAddress))
          .compose(req -> req
            .send()
            .compose(resp -> {
              assertEquals(200, resp.statusCode());
              return resp.body();
            }))
          .onComplete(onSuccess(body -> {
            assertEquals(sockAddress.path(), body.toString());
            complete();
          }));
      }
    }
    try {
      await();
    } finally {
      vx.close();
    }
  }
}
