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
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.net.JdkSSLEngineOptions;
import io.vertx.core.net.PemKeyCertOptions;

import java.security.cert.CertificateException;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Http2ServerExampleSni {

  public void example7Server(Vertx vertx) {
    SelfSignedCertificate ssc = null;
    try {
      ssc = new SelfSignedCertificate();
    } catch (CertificateException e) {
      throw new RuntimeException(e);
    }

    // Get the paths for the certificate and private key
    String certPath = ssc.certificate().getAbsolutePath();
    String keyPath = ssc.privateKey().getAbsolutePath();

//    JksOptions jksOptions = new JksOptions().setPath("tls/server-keystore" +
//      ".jks").setPassword("wibble");

    HttpServerOptions options = new HttpServerOptions()
      .setPort(8090)
      .setHost("localhost")
      .setSslEngineOptions(new JdkSSLEngineOptions())
      .setUseAlpn(true)
      .setAlpnVersions(List.of(HttpVersion.HTTP_2))
      .setSsl(true)
      .setSni(true)
      .setSslHandshakeTimeout(1)
      .setSslHandshakeTimeoutUnit(TimeUnit.HOURS)

//      .addEnabledCipherSuite("TLS_RSA_WITH_AES_128_CBC_SHA")
//      .setKeyCertOptions(jksOptions)
      ;


//    HttpServerOptions options = new HttpServerOptions();
//    options.setSsl(true);
//    options.setUseAlpn(true);
//    options.setAlpnVersions(List.of(HttpVersion.HTTP_2));

    options.setKeyCertOptions(new PemKeyCertOptions()
      .setCertPath(certPath)
      .setKeyPath(keyPath)
    );

    HttpServer server = vertx.createHttpServer(options);

    server.requestHandler(request -> {
      System.out.println("A request received from " + request.remoteAddress().host());
      request
        .body()
        .onSuccess(body -> {
          System.out.println("body = " + body.toString());
          request.response().end("!Hello World! for -> " + body);
        })
        .onFailure(Throwable::printStackTrace);
    });

    server.connectionHandler(connection -> {
      System.out.println("A client connected");
    });

    server.exceptionHandler(Throwable::printStackTrace);

    server.listen();
  }

  public static void main(String[] args) throws Exception {
    VertxOptions options = new VertxOptions()
      .setBlockedThreadCheckInterval(1_000_000_000);

    Vertx vertx = Vertx.vertx(options);
    new Http2ServerExampleSni().example7Server(vertx);
  }
}
