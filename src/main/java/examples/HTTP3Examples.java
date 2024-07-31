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

import io.netty.util.NetUtil;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
public class HTTP3Examples {

  public void example01(Vertx vertx) {

    HttpClientOptions options = new HttpClientOptions().
      setSsl(true).
      setUseAlpn(true).
      setTrustAll(true);
    options.setProtocolVersion(HttpVersion.HTTP_3);

    HttpClient client = vertx.createHttpClient(options);
    client.request(HttpMethod.GET, 9999, NetUtil.LOCALHOST4.getHostAddress(),
        "/")
//    client.request(HttpMethod.GET, 443, "www.google.com",
//    client.request(HttpMethod.GET, 443, "216.239.38.120", "/")
      .compose(req -> {
        req.send("");
        req.end();
        return req.response();
      })
      .compose(res -> {
        MultiMap headers = res.headers();

        System.out.println("res.statusCode() = " + res.statusCode());
        System.out.println("https = " + headers.get("Alt-Svc"));
        return res.body();
      })
      .onSuccess(buffer -> System.out.println("buffer = " + buffer.toString().substring(0, 100)))
      .onFailure(Throwable::printStackTrace)
      .onComplete(event -> vertx.close());
  }

  public static void main(String[] args) {
    new HTTP3Examples().example01(Vertx.vertx());
  }
}
