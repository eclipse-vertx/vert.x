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

package examples.h3devexamples;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class Http2ClientExampleSni {
  public void example7Client(Vertx vertx) {
    HttpClientOptions options = new HttpClientOptions();
    options.setSsl(true);
    options.setUseAlpn(true);
    options.setTrustAll(true);
    options.setForceSni(true);
    options.setAlpnVersions(List.of(HttpVersion.HTTP_2));

    HttpClient client = vertx.createHttpClient(options);

    String path = "/";
    int port = 8090;
    String host = "localhost";

    AtomicInteger requests = new AtomicInteger();

    int n = 1;

    for (int i = 0; i < n; i++) {
      int counter = i + 1;
      client.request(HttpMethod.GET, port, host, path)
        .compose(req -> req.send("Msg " + counter))
        .compose(HttpClientResponse::body)
        .onSuccess(body -> System.out.println(
          "Msg" + counter + " response body is: " + body))
        .onComplete(event -> requests.incrementAndGet())
        .onFailure(Throwable::printStackTrace)
      ;
    }

    while (requests.get() != n) {
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
    vertx.close();

  }

  public static void main(String[] args) {
    Vertx vertx =
      Vertx.vertx(new VertxOptions().setBlockedThreadCheckInterval(1_000_000_000));
    new Http2ClientExampleSni().example7Client(vertx);
  }
}
