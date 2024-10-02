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

import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.incubator.codec.http3.Http3;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.*;
import io.vertx.core.net.PemKeyCertOptions;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
public class HTTP3Examples {
  private final static String okText =
    "\n  ____   _  __  \n"+
    " / __ \\ | |/ /  \n"+
    "| |  | ||   <   \n"+
    "| |  | || |\\ \\  \n"+
    "| |__| || | \\ \\ \n"+
    " \\____/ |_|  \\_\\ \n";

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
      setIdleTimeout(1).
      setReadIdleTimeout(1).
      setWriteIdleTimeout(1).
      setIdleTimeoutUnit(TimeUnit.HOURS).
      setUseAlpn(true).
      setForceSni(true).
      setDefaultHost(host).
      setInitialHttp3Settings(settings).
      setVerifyHost(false).
      setTrustAll(true).
      setProtocolVersion(HttpVersion.HTTP_3);

    options
      .getSslOptions()
      .setSslHandshakeTimeout(1)
      .setSslHandshakeTimeoutUnit(TimeUnit.HOURS);


    HttpClient client = vertx.createHttpClient(options);

    System.out.print(String.format("Trying to fetch %s:%s%s\n", host, port,
      path));
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
//              System.out.println("The returned headers are: " + resp
//              .headers());
                System.out.println("The returned Alt-Svc is: " + resp.headers().get(
                  "Alt-Svc"));
              }).compose(HttpClientResponse::body).onSuccess(body -> {
                if (host.contains("google.com") && body.toString().endsWith(
                  "google.log(\"rcm\"," +
                    "\"&ei=\"+c+\"&tgtved=\"+f+\"&jsname=\"+(a||\"\"))}}else " +
                    "F=a,E=[c]}window.document.addEventListener" +
                    "(\"DOMContentLoaded\"," +
                    "function(){document.body.addEventListener(\"click\",G)})" +
                    ";" +
                    "}).call(this);</script></body></html>")) {
                  System.out.println(okText);
                } else {
                  System.out.println("The response body is: " + body);
                }
                vertx.close();
              })
          );
      })
      .onFailure(Throwable::printStackTrace)
      .onComplete(event -> vertx.close())
    ;

    try {
      Thread.sleep(1_000_000);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  public void example02Server(Vertx vertx) throws Exception {
    //TODO: set settings for http3
//    Http3Settings settings = new Http3Settings();

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
      System.out.println("A request received from " + request.remoteAddress());
      request.body().onSuccess(buf -> {
        System.out.println("request body is :  = " + buf.toString());
      }).onFailure(Throwable::printStackTrace);
      request.response().end(okText).onFailure(Throwable::printStackTrace);
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

  public static void main(String[] args) throws Exception {
    VertxOptions options = new VertxOptions()
      .setBlockedThreadCheckInterval(1_000_000_000);

    Vertx vertx = Vertx.vertx(options);
    new HTTP3Examples().example02Server(vertx);
//    new HTTP3Examples().example01(vertx);
  }
}
