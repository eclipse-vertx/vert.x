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
import io.vertx.core.net.JksOptions;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class HTTP2Examples {

  public void example0(Vertx vertx) {
    HttpServerOptions options = new HttpServerOptions()
        .setUseAlpn(true)
        .setSsl(true)
        .setKeyCertOptions(new JksOptions().setPath("/path/to/my/keystore"));

    HttpServer server = vertx.createHttpServer(options);
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

  public void example3(HttpServerRequest request) {

    // Reset the stream
    request.response().reset();
  }

  public void example4(HttpServerRequest request) {

    // Cancel the stream
    request.response().reset(8);
  }

  public void example5(HttpServerRequest request) {

    request.response().exceptionHandler(err -> {
      if (err instanceof StreamResetException) {
        StreamResetException reset = (StreamResetException) err;
        System.out.println("Stream reset " + reset.getCode());
      }
    });
  }

  public void example6(HttpServerRequest request) {

    HttpServerResponse response = request.response();

    // Push main.js to the client
    response
      .push(HttpMethod.GET, "/main.js")
      .onComplete(ar -> {

        if (ar.succeeded()) {

          // The server is ready to push the response
          HttpServerResponse pushedResponse = ar.result();

          // Send main.js response
          pushedResponse.
            putHeader("content-type", "application/json").
            end("alert(\"Push response hello\")");
        } else {
          System.out.println("Could not push client resource " + ar.cause());
        }
      });

    // Send the requested resource
    response.sendFile("<html><head><script src=\"/main.js\"></script></head><body></body></html>");
  }

  public void example7(Vertx vertx) {

    HttpClientOptions options = new HttpClientOptions().
        setProtocolVersion(HttpVersion.HTTP_2).
        setSsl(true).
        setUseAlpn(true).
        setTrustAll(true);

    HttpClient client = vertx.createHttpClient(options);
  }

  public void example8(Vertx vertx) {

    HttpClientOptions options = new HttpClientOptions().setProtocolVersion(HttpVersion.HTTP_2);

    HttpClient client = vertx.createHttpClient(options);
  }

  public void example9(HttpClientRequest request) {

    int frameType = 40;
    int frameStatus = 10;
    Buffer payload = Buffer.buffer("some data");

    // Sending a frame to the server
    request.writeCustomFrame(frameType, frameStatus, payload);
  }

  public void example10(HttpClientRequest request) {

    request.reset();

  }

  public void example11(HttpClientRequest request) {

    request.reset(8);

  }

  public void example12(HttpClientRequest request) {

    request.exceptionHandler(err -> {
      if (err instanceof StreamResetException) {
        StreamResetException reset = (StreamResetException) err;
        System.out.println("Stream reset " + reset.getCode());
      }
    });
  }

  public void example13(HttpClient client) {

    client.request(HttpMethod.GET, "/index.html")
      .onSuccess(request -> {

        request
          .response().onComplete(response -> {
            // Process index.html response
          });

        // Set a push handler to be aware of any resource pushed by the server
        request.pushHandler(pushedRequest -> {

          // A resource is pushed for this request
          System.out.println("Server pushed " + pushedRequest.path());

          // Set an handler for the response
          pushedRequest.response().onComplete(pushedResponse -> {
            System.out.println("The response for the pushed request");
          });
        });

        // End the request
        request.end();
    });
  }

  public void example14(HttpClientRequest request) {
    request.pushHandler(pushedRequest -> {
      if (pushedRequest.path().equals("/main.js")) {
        pushedRequest.reset();
      } else {
        // Handle it
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

  public void example17(Vertx vertx, HttpServerOptions http2Options) {
    HttpServer server = vertx.createHttpServer(http2Options);

    server.connectionHandler(connection -> {
      System.out.println("A client connected");
    });
  }

  public void example18(HttpClientRequest request) {
    HttpConnection connection = request.connection();
  }

  public void example19(Vertx vertx, HttpClientOptions options) {
    vertx
      .httpClientBuilder()
      .with(options)
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
    connection.remoteHttp3SettingsHandler(settings -> {
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
    HttpClient client = vertx.createHttpClient(
      new HttpClientOptions().setHttp2MultiplexingLimit(10),
      new PoolOptions().setHttp2MaxSize(3)
    );
  }
}
