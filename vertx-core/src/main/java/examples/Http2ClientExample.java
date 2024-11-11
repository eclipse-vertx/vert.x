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

package examples;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;

import java.util.List;

public class Http2ClientExample {
  public void example7Client(Vertx vertx) {
    HttpClientOptions options = new HttpClientOptions();
//    options.setSsl(true);
    options.setUseAlpn(true);
    options.setAlpnVersions(List.of(HttpVersion.HTTP_2));

    HttpClient client = vertx.createHttpClient(options);

    String path = "/";
    int port = 8090;
    String host = "localhost";


    client.request(HttpMethod.GET, port, host, path)
      .compose(req -> req.send(" M1 "))
      .compose(HttpClientResponse::body)
      .onSuccess(body -> System.out.println("M1 The response body is: " + body))
      .onFailure(Throwable::printStackTrace)
    ;
    client.request(HttpMethod.GET, port, host, path)
      .compose(req -> req.send(" M2 "))
      .compose(HttpClientResponse::body)
      .onSuccess(body -> System.out.println("M2 The response body is: " + body))
      .onFailure(Throwable::printStackTrace)
    ;

  }

  public static void main(String[] args) {
    Vertx vertx =
      Vertx.vertx(new VertxOptions().setBlockedThreadCheckInterval(1_000_000_000));
    new Http2ClientExample().example7Client(vertx);
  }
}
