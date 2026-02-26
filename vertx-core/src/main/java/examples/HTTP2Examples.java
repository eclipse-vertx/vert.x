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
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.net.ClientSSLOptions;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.ServerSSLOptions;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class HTTP2Examples {

  public void example0(Vertx vertx) {
    HttpServerConfig config = new HttpServerConfig()
        .setSsl(true);

    ServerSSLOptions sslOptions = new ServerSSLOptions()
      .setKeyCertOptions(new JksOptions().setPath("/path/to/my/keystore"));

    HttpServer server = vertx.createHttpServer(config, sslOptions);
  }

  public void example1(HttpServerRequest request) {

    request.customFrameHandler(frame -> {

      System.out.println("Received a frame type=" + frame.type() +
          " payload" + frame.payload().toString());
    });
  }

  public void example2(HttpServerResponse response) {

    int frameType = 40;
    int frameStatus = 10;
    Buffer payload = Buffer.buffer("some data");

    // Sending a frame to the client
    response.writeCustomFrame(frameType, frameStatus, payload);
  }

  public void serverResponseCancellation(HttpServerRequest request) {

    // Cancel the stream
    request.response().cancel();
  }

  public void serverResponseCancellationException(HttpServerRequest request) {

    request.response().exceptionHandler(err -> {
      if (err instanceof StreamResetException) {
        StreamResetException reset = (StreamResetException) err;
        System.out.println("Stream reset " + reset.getCode());
      }
    });
  }

  public void example7_1(Vertx vertx) {

    HttpClientConfig config = new HttpClientConfig()
      .setSsl(true);

    ClientSSLOptions sslOptions = new ClientSSLOptions()
      .setTrustAll(true);

    HttpClient client = vertx.createHttpClient(config, sslOptions);
  }

  public void example7_2(Vertx vertx) {

    HttpClientConfig config = new HttpClientConfig().
      setVersions(HttpVersion.HTTP_2, HttpVersion.HTTP_1_1);

    HttpClient client = vertx.createHttpClient(config);
  }

  public void example9(HttpClientRequest request) {

    int frameType = 40;
    int frameStatus = 10;
    Buffer payload = Buffer.buffer("some data");

    // Sending a frame to the server
    request.writeCustomFrame(frameType, frameStatus, payload);
  }

  public void clientRequestCancellation(HttpClientRequest request) {

    request.cancel();

  }

  public void clientRequestCancellationException(HttpClientRequest request) {

    request.exceptionHandler(err -> {
      if (err instanceof StreamResetException) {
        StreamResetException reset = (StreamResetException) err;
        System.out.println("Stream reset " + reset.getCode());
      }
    });
  }

  public void example15(HttpClientResponse response) {
    response.customFrameHandler(frame -> {

      System.out.println("Received a frame type=" + frame.type() +
          " payload" + frame.payload().toString());
    });
  }

  public void example16(HttpServerRequest request) {
    HttpConnection connection = request.connection();
  }

  public void example17(Vertx vertx, HttpServerConfig http2Config) {
    HttpServer server = vertx.createHttpServer(http2Config);

    server.connectionHandler(connection -> {
      System.out.println("A client connected");
    });
  }

  public void example18(HttpClientRequest request) {
    HttpConnection connection = request.connection();
  }

  public void example19(Vertx vertx, HttpClientConfig config) {
    vertx
      .httpClientBuilder()
      .with(config)
      .withConnectHandler(connection -> {
        System.out.println("Connected to the server");
      })
      .build();
  }

  public void example20(HttpConnection connection) {
    connection.updateSettings(new Http2Settings().setMaxConcurrentStreams(100));
  }

  public void example21(HttpConnection connection) {
    connection
      .updateSettings(new Http2Settings().setMaxConcurrentStreams(100))
      .onSuccess(v -> System.out.println("The settings update has been acknowledged "));
  }

  public void example22(HttpConnection connection) {
    connection.remoteSettingsHandler(settings -> {
      System.out.println("Received new settings");
    });
  }

  public void example23(HttpConnection connection) {
    Buffer data = Buffer.buffer();
    for (byte i = 0;i < 8;i++) {
      data.appendByte(i);
    }
    connection
      .ping(data)
      .onSuccess(pong -> System.out.println("Remote side replied"));
  }

  public void example24(HttpConnection connection) {
    connection.pingHandler(ping -> {
      System.out.println("Got pinged by remote side");
    });
  }

  public void example25(HttpConnection connection) {
    connection.shutdown();
  }

  public void example26(HttpConnection connection) {
    connection.goAway(0);
  }

  public void example27(HttpConnection connection) {
    connection.goAwayHandler(goAway -> {
      System.out.println("Received a go away frame");
    });
  }

  public void example28(HttpConnection connection) {
    connection.goAway(0);
    connection.shutdownHandler(v -> {

      // All streams are closed, close the connection
      connection.close();
    });
  }

  public void useMaxStreams(Vertx vertx) {

    // Uses up to 3 connections and up to 10 streams per connection
    Http2ClientConfig http2Config = new Http2ClientConfig()
      .setMultiplexingLimit(10);

    HttpClient client = vertx.createHttpClient(
      new HttpClientConfig()
        .setHttp2Config(http2Config),
      new PoolOptions()
        .setHttp2MaxSize(3)
    );
  }
}
