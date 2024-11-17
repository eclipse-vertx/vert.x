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

import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.incubator.codec.http3.Http3;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.net.PemKeyCertOptions;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
public class HTTP3ServerExamplesAsyncTestCase {
  public void example03ServerAsync(Vertx vertx) throws Exception {

    HttpServerOptions options = new HttpServerOptions();

    options.setAlpnVersions(List.of(
      HttpVersion.HTTP_3,
      HttpVersion.HTTP_3_27,
      HttpVersion.HTTP_3_29,
      HttpVersion.HTTP_3_30,
      HttpVersion.HTTP_3_31,
      HttpVersion.HTTP_3_32,
      HttpVersion.HTTP_2,
      HttpVersion.HTTP_1_1,
      HttpVersion.HTTP_1_0
    ));

    options
      .setIdleTimeout(1)
      .setReadIdleTimeout(1)
      .setWriteIdleTimeout(1)
      .setIdleTimeoutUnit(TimeUnit.HOURS)
      .setHttp3(true)
      .setUseAlpn(true)
      .setSsl(true)
      .getSslOptions()
      .setApplicationLayerProtocols(
        List.of(Http3.supportedApplicationProtocols())
      ).setSslHandshakeTimeout(1)
      .setSslHandshakeTimeoutUnit(TimeUnit.HOURS)
    ;

    SelfSignedCertificate ssc = new SelfSignedCertificate();
    options.setKeyCertOptions(new PemKeyCertOptions()
      .setCertPath(ssc.certificate().getAbsolutePath())
      .setKeyPath(ssc.privateKey().getAbsolutePath())
    );


    HttpServer server = vertx.createHttpServer(options);


    server.requestHandler(request -> {
      runAsync(() -> {
        request.response().end();
      });
    });


    server.connectionHandler(connection -> {
      System.out.println("A client connected");
    });

    server.exceptionHandler(Throwable::printStackTrace);

    int port = 8090;
    server.listen(port)
      .onComplete(ar -> {
        if (ar.succeeded()) {
          System.out.println("HTTP/3 server is now listening on port: " + port);
        } else {
          ar.cause().printStackTrace();
        }
      });
  }

  void runAsync(Runnable runnable) {
    new Thread(() -> {
      try {
        runnable.run();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }).start();
  }

  public static void main(String[] args) throws Exception {
    VertxOptions options = new VertxOptions()
      .setBlockedThreadCheckInterval(1_000_000_000);

    Vertx vertx = Vertx.vertx(options);
    new HTTP3ServerExamplesAsyncTestCase().example03ServerAsync(vertx);
  }
}
