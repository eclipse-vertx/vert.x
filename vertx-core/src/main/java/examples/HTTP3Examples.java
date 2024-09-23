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

import io.netty.incubator.codec.http3.DefaultHttp3SettingsFrame;
import io.netty.incubator.codec.http3.Http3SettingsFrame;
import io.netty.util.NetUtil;
import io.vertx.core.Vertx;
import io.vertx.core.http.*;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
public class HTTP3Examples {

  public void example01(Vertx vertx) {

    Http3Settings settings = new Http3Settings();
    settings.setMaxFieldSectionSize(100000000000L);

    String path = "/";
//    String path = "/cdn-cgi/trace";
    int port = 443;
//    int port = 9999;
//    String host = "http3.is";
    String host = "www.google.com";
//    String host = "quic.nginx.org";
//    String host = "www.cloudflare.com";
//    String host = NetUtil.LOCALHOST4.getHostAddress();
//    String host = "www.mozilla.org";
//    String host = "www.bing.com";
//    String host = "www.yahoo.com";

    HttpClientOptions options = new HttpClientOptions().
      setSsl(true).
      setUseAlpn(true).
      setForceSni(true).
      setDefaultHost(host).
      setInitialHttp3Settings(settings).
      setVerifyHost(false).
      setTrustAll(true).
      setProtocolVersion(HttpVersion.HTTP_3);

    HttpClient client = vertx.createHttpClient(options);

    System.out.printf("Trying to fetch %s:%s%s\n", host, port, path);
    client.request(HttpMethod.GET, port, host, path)
      .compose(req -> {

        req.connection().goAwayHandler(goAway -> {
          System.out.println(" Received goAway from server! ");
        });

        req.connection().shutdownHandler(v -> {
          System.out.println(" Received shutdown signal! ");
          req.connection().close();
          vertx.close();
        });

        return req
          .end()
          .compose(res -> req
            .response()
            .onSuccess(resp -> {
              System.out.println("The returned headers are: " + resp.headers());
              System.out.println("The returned Alt-Svc is: " + resp.headers().get(
                "Alt-Svc"));
            }).compose(HttpClientResponse::body).onSuccess(body ->
              System.out.println("The response body is: " + body.toString()))
          );
      })
      .onFailure(Throwable::printStackTrace)
      .onComplete(event -> vertx.close())
    ;
  }

  public static void main(String[] args) {
    new HTTP3Examples().example01(Vertx.vertx());
  }
}
