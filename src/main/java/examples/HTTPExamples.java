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

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.http.*;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.ProxyType;
import io.vertx.core.streams.Pump;

/**
 * Created by tim on 09/01/15.
 */
public class HTTPExamples {

  public void example1(Vertx vertx) {

    HttpServer server = vertx.createHttpServer();
  }

  public void example2(Vertx vertx) {

    HttpServerOptions options = new HttpServerOptions().setMaxWebSocketFrameSize(1000000);

    HttpServer server = vertx.createHttpServer(options);
  }

  public void exampleServerLogging(Vertx vertx) {

    HttpServerOptions options = new HttpServerOptions().setLogActivity(true);

    HttpServer server = vertx.createHttpServer(options);
  }

  public void example3(Vertx vertx) {

    HttpServer server = vertx.createHttpServer();
    server.listen();
  }

  public void example4(Vertx vertx) {

    HttpServer server = vertx.createHttpServer();
    server.listen(8080, "myhost.com");
  }

  public void example5(Vertx vertx) {

    HttpServer server = vertx.createHttpServer();
    server.listen(8080, "myhost.com", res -> {
      if (res.succeeded()) {
        System.out.println("Server is now listening!");
      } else {
        System.out.println("Failed to bind!");
      }
    });
  }

  public void example6(Vertx vertx) {

    HttpServer server = vertx.createHttpServer();
    server.requestHandler(request -> {
      // Handle the request in here
    });
  }

  public void example7(Vertx vertx) {

    HttpServer server = vertx.createHttpServer();
    server.requestHandler(request -> {
      // Handle the request in here
      HttpMethod method = request.method();
    });
  }

  public void example7_1(Vertx vertx) {

    vertx.createHttpServer().requestHandler(request -> {
      request.response().end("Hello world");
    }).listen(8080);

  }


  public void example8(HttpServerRequest request) {

    MultiMap headers = request.headers();

    // Get the User-Agent:
    System.out.println("User agent is " + headers.get("user-agent"));

    // You can also do this and get the same result:
    System.out.println("User agent is " + headers.get("User-Agent"));
  }

  public void example9(HttpServerRequest request) {

    request.handler(buffer -> {
      System.out.println("I have received a chunk of the body of length " + buffer.length());
    });
  }

  public void example10(HttpServerRequest request) {

    // Create an empty buffer
    Buffer totalBuffer = Buffer.buffer();

    request.handler(buffer -> {
      System.out.println("I have received a chunk of the body of length " + buffer.length());
      totalBuffer.appendBuffer(buffer);
    });

    request.endHandler(v -> {
      System.out.println("Full body received, length = " + totalBuffer.length());
    });
  }

  public void example11(HttpServerRequest request) {

    request.bodyHandler(totalBuffer -> {
      System.out.println("Full body received, length = " + totalBuffer.length());
    });
  }

  public void example12(HttpServer server) {

    server.requestHandler(request -> {
      request.setExpectMultipart(true);
      request.endHandler(v -> {
        // The body has now been fully read, so retrieve the form attributes
        MultiMap formAttributes = request.formAttributes();
      });
    });
  }

  public void example13(HttpServer server) {

    server.requestHandler(request -> {
      request.setExpectMultipart(true);
      request.uploadHandler(upload -> {
        System.out.println("Got a file upload " + upload.name());
      });
    });
  }

  public void example14(HttpServerRequest request) {

    request.uploadHandler(upload -> {
      upload.handler(chunk -> {
        System.out.println("Received a chunk of the upload of length " + chunk.length());
      });
    });
  }

  public void example15(HttpServerRequest request) {

    request.uploadHandler(upload -> {
      upload.streamToFileSystem("myuploads_directory/" + upload.filename());
    });
  }

  public void exampleHandlingCookies(HttpServerRequest request) {
    Cookie someCookie = request.getCookie("mycookie");
    String cookieValue = someCookie.getValue();

    // Do something with cookie...

    // Add a cookie - this will get written back in the response automatically
    request.response().addCookie(Cookie.cookie("othercookie", "somevalue"));
  }

  public void example16(HttpServerRequest request, Buffer buffer) {
    HttpServerResponse response = request.response();
    response.write(buffer);
  }

  public void example17(HttpServerRequest request) {
    HttpServerResponse response = request.response();
    response.write("hello world!");
  }

  public void example18(HttpServerRequest request) {
    HttpServerResponse response = request.response();
    response.write("hello world!", "UTF-16");
  }

  public void example19(HttpServerRequest request) {
    HttpServerResponse response = request.response();
    response.write("hello world!");
    response.end();
  }

  public void example20(HttpServerRequest request) {
    HttpServerResponse response = request.response();
    response.end("hello world!");
  }

  public void example21(HttpServerRequest request) {
    HttpServerResponse response = request.response();
    MultiMap headers = response.headers();
    headers.set("content-type", "text/html");
    headers.set("other-header", "wibble");
  }

  public void example22(HttpServerRequest request) {
    HttpServerResponse response = request.response();
    response.putHeader("content-type", "text/html").putHeader("other-header", "wibble");
  }

  public void example23(HttpServerRequest request) {
    HttpServerResponse response = request.response();
    response.setChunked(true);
  }

  public void example24(HttpServerRequest request) {
    HttpServerResponse response = request.response();
    response.setChunked(true);
    MultiMap trailers = response.trailers();
    trailers.set("X-wibble", "woobble").set("X-quux", "flooble");
  }

  public void example25(HttpServerRequest request) {
    HttpServerResponse response = request.response();
    response.setChunked(true);
    response.putTrailer("X-wibble", "woobble").putTrailer("X-quux", "flooble");
  }

  public void example26(Vertx vertx) {
    vertx.createHttpServer().requestHandler(request -> {
      String file = "";
      if (request.path().equals("/")) {
        file = "index.html";
      } else if (!request.path().contains("..")) {
        file = request.path();
      }
      request.response().sendFile("web/" + file);
    }).listen(8080);
  }

  public void example26b(Vertx vertx) {
    vertx.createHttpServer().requestHandler(request -> {
      long offset = 0;
      try {
        offset = Long.parseLong(request.getParam("start"));
      } catch (NumberFormatException e) {
        // error handling...
      }

      long end = Long.MAX_VALUE;
      try {
        end = Long.parseLong(request.getParam("end"));
      } catch (NumberFormatException e) {
        // error handling...
      }

      request.response().sendFile("web/mybigfile.txt", offset, end);
    }).listen(8080);
  }

  public void example26c(Vertx vertx) {
    vertx.createHttpServer().requestHandler(request -> {
      long offset = 0;
      try {
        offset = Long.parseLong(request.getParam("start"));
      } catch (NumberFormatException e) {
        // error handling...
      }

      request.response().sendFile("web/mybigfile.txt", offset);
    }).listen(8080);
  }

  public void example27(Vertx vertx) {
    vertx.createHttpServer().requestHandler(request -> {
      HttpServerResponse response = request.response();
      if (request.method() == HttpMethod.PUT) {
        response.setChunked(true);
        request.pipeTo(response);
      } else {
        response.setStatusCode(400).end();
      }
    }).listen(8080);
  }

  public void example28(Vertx vertx) {
    HttpClient client = vertx.createHttpClient();
  }

  public void example29(Vertx vertx) {
    HttpClientOptions options = new HttpClientOptions().setKeepAlive(false);
    HttpClient client = vertx.createHttpClient(options);
  }

  public void exampleClientLogging(Vertx vertx) {
    HttpClientOptions options = new HttpClientOptions().setLogActivity(true);
    HttpClient client = vertx.createHttpClient(options);
  }

  public void example30(Vertx vertx) {
    // Set the default host
    HttpClientOptions options = new HttpClientOptions().setDefaultHost("wibble.com");
    // Can also set default port if you want...
    HttpClient client = vertx.createHttpClient(options);
    client.get("/some-uri", ar -> {
      if (ar.succeeded()) {
        HttpClientResponse response = ar.result();
        System.out.println("Received response with status code " + response.statusCode());
      }
    });
  }

  public void example31(Vertx vertx) {
    HttpClient client = vertx.createHttpClient();

    // Specify both port and host name
    client.get(8080, "myserver.mycompany.com", "/some-uri", ar -> {
      if (ar.succeeded()) {
        HttpClientResponse response = ar.result();
        System.out.println("Received response with status code " + response.statusCode());
      }
    });

    // This time use the default port 80 but specify the host name
    client.get("foo.othercompany.com", "/other-uri", ar -> {
      if (ar.succeeded()) {
        HttpClientResponse response = ar.result();
        System.out.println("Received response with status code " + response.statusCode());
      }
    });
  }

  public void example32(Vertx vertx) {
    HttpClient client = vertx.createHttpClient();

    // Send a GET request
    client.get("/some-uri", ar -> {
      if (ar.succeeded()) {
        HttpClientResponse response = ar.result();
        System.out.println("Received response with status code " + response.statusCode());
      }
    });

    // Send a POST request
    client.post("/other-uri", Buffer.buffer("some-data"), ar -> {
      if (ar.succeeded()) {
        HttpClientResponse response = ar.result();
        System.out.println("Received response with status code " + response.statusCode());
      }
    });

    // Send a HEAD request
    client.head("/other-uri", ar -> {
      if (ar.succeeded()) {
        HttpClientResponse response = ar.result();
        System.out.println("Received response with status code " + response.statusCode());
      }
    });
  }

  public void example32_(Vertx vertx, AsyncFile file) {
    HttpClient client = vertx.createHttpClient();

    // Send a POST request with a buffer
    client.post("/other-uri", Buffer.buffer("some-data"), ar -> {
      if (ar.succeeded()) {
        HttpClientResponse response = ar.result();
        System.out.println("Received response with status code " + response.statusCode());
      }
    });

    // Send a POST request with a stream
    client.post("/other-uri", file, ar -> {
      if (ar.succeeded()) {
        HttpClientResponse response = ar.result();
        System.out.println("Received response with status code " + response.statusCode());
      }
    });
  }

  public void example33(Vertx vertx) {
    HttpClient client = vertx.createHttpClient();

    client.send(HttpMethod.GET, "some-uri", ar -> {
      if (ar.succeeded()) {
        HttpClientResponse response = ar.result();
        System.out.println("Received response with status code " + response.statusCode());
      }
    });

    client.send(HttpMethod.POST, "foo-uri", Buffer.buffer("some-data"), ar -> {
      if (ar.succeeded()) {
        HttpClientResponse response = ar.result();
        System.out.println("Received response with status code " + response.statusCode());
      }
    });
  }

  public void example33_(Vertx vertx) {

    HttpClient client = vertx.createHttpClient();

    // Write some headers using the headers multi-map
    MultiMap headers = HttpHeaders.set("content-type", "application/json").set("other-header", "foo");

    client.send(HttpMethod.GET, "some-uri", headers, ar -> {
      if (ar.succeeded()) {
        HttpClientResponse response = ar.result();
        System.out.println("Received response with status code " + response.statusCode());
      }
    });

    client.send(HttpMethod.POST, "foo-uri", headers, Buffer.buffer("some-data"), ar -> {
      if (ar.succeeded()) {
        HttpClientResponse response = ar.result();
        System.out.println("Received response with status code " + response.statusCode());
      }
    });
  }

  public void example34(Vertx vertx, String body) {
    HttpClient client = vertx.createHttpClient();

    HttpClientRequest request = client.request(HttpMethod.POST, "some-uri")
      .onSuccess(response -> {
        System.out.println("Received response with status code " + response.statusCode());
      });

    // Now do stuff with the request
    request.putHeader("content-length", "1000");
    request.putHeader("content-type", "text/plain");
    request.write(body);

    // Make sure the request is ended when you're done with it
    request.end();

    // Or fluently:

    client.request(HttpMethod.POST, "some-uri")
      .onSuccess(response -> {
        System.out.println("Received response with status code " + response.statusCode());
      })
      .putHeader("content-length", "1000")
      .putHeader("content-type", "text/plain")
      .end(body);
  }

  public void example35(HttpClientRequest request) {

    // Write string encoded in UTF-8
    request.write("some data");

    // Write string encoded in specific encoding
    request.write("some other data", "UTF-16");

    // Write a buffer
    Buffer buffer = Buffer.buffer();
    buffer.appendInt(123).appendLong(245l);
    request.write(buffer);

  }

  public void example36(HttpClientRequest request) {

    // Write string and end the request (send it) in a single call
    request.end("some simple data");

    // Write buffer and end the request (send it) in a single call
    Buffer buffer = Buffer.buffer().appendDouble(12.34d).appendLong(432l);
    request.end(buffer);

  }

  public void example38(HttpClientRequest request) {

    // Write some headers using the putHeader method

    request.putHeader("content-type", "application/json").putHeader("other-header", "foo");

  }

  public void example39(HttpClientRequest request) {
    request.end();
  }

  public void example40(HttpClientRequest request) {
    // End the request with a string
    request.end("some-data");

    // End it with a buffer
    Buffer buffer = Buffer.buffer().appendFloat(12.3f).appendInt(321);
    request.end(buffer);
  }

  public void example41(HttpClientRequest request) {

    request.setChunked(true);

    // Write some chunks
    for (int i = 0; i < 10; i++) {
      request.write("this-is-chunk-" + i);
    }

    request.end();
  }

  public void example42(HttpClient client) {
    client
      .post("some-uri", Buffer.buffer("some-data"))
      .onComplete(ar -> {
        if (ar.succeeded()) {
          HttpClientResponse response = ar.result();
          System.out.println("Received response with status code " + response.statusCode());
        } else {
          System.out.println("Received exception: " + ar.cause().getMessage());
        }
      });
  }

  public void statusCodeHandling(HttpClient client) {
    client.post("some-uri", Buffer.buffer("some-data"))
      .onSuccess(response -> {
        switch (response.statusCode()) {
          case 200:
            System.out.println("Everything fine");
            break;
          case 500:
            System.out.println("Unexpected behavior on the server side");
            break;
        }
      });
  }

  public void useRequestAsStream(HttpClientRequest request) {

    request.setChunked(true);
    request.write("chunk-1");
    request.write("chunk-2");
    request.end();
  }

  public void setRequestExceptionHandler(HttpClientRequest request) {

    request.exceptionHandler(err -> {
      System.out.println("Write failure " + err.getMessage());
    });
  }

  public void example44(HttpClientRequest request, AsyncFile file) {

    request.setChunked(true);
    file.pipeTo(request);
  }

  public void example45(HttpClient client) {

    client.get("some-uri", ar -> {
      if (ar.succeeded()) {
        HttpClientResponse response = ar.result();

        // the status code - e.g. 200 or 404
        System.out.println("Status code is " + response.statusCode());

        // the status message e.g. "OK" or "Not Found".
        System.out.println("Status message is " + response.statusMessage());
      }
    });

  }

  public void example46(HttpClientResponse response) {

    String contentType = response.headers().get("content-type");
    String contentLength = response.headers().get("content-lengh");

  }

  public void example47(HttpClient client) {

    client.get("some-uri", ar -> {

      if (ar.succeeded()) {
        HttpClientResponse response = ar.result();
        response.handler(buffer -> {
          System.out.println("Received a part of the response body: " + buffer);
        });
      }
    });
  }

  public void example48(HttpClient client) {

    client.get("some-uri", ar -> {

      if (ar.succeeded()) {
        HttpClientResponse response = ar.result();

        // Create an empty buffer
        Buffer totalBuffer = Buffer.buffer();

        response.handler(buffer -> {
          System.out.println("Received a part of the response body: " + buffer.length());

          totalBuffer.appendBuffer(buffer);
        });

        response.endHandler(v -> {
          // Now all the body has been read
          System.out.println("Total response body length is " + totalBuffer.length());
        });
      }
    });
  }

  public void example49(HttpClient client) {

    client.get("some-uri", ar -> {

      if (ar.succeeded()) {
        HttpClientResponse response = ar.result();
        response.bodyHandler(totalBuffer -> {
          // Now all the body has been read
          System.out.println("Total response body length is " + totalBuffer.length());
        });
      }
    });
  }

  public void exampleFollowRedirect01(HttpClient client) {
    client.get(new RequestOptions()
      .setURI("some-uri")
      .setFollowRedirects(true), ar -> {
      if (ar.succeeded()) {
        HttpClientResponse response = ar.result();
        System.out.println("Received response with status code " + response.statusCode());
      }
    });
  }

  public void exampleFollowRedirect02(Vertx vertx) {

    HttpClient client = vertx.createHttpClient(
        new HttpClientOptions()
            .setMaxRedirects(32));

    client.get(new RequestOptions()
      .setURI("some-uri")
      .setFollowRedirects(true), ar -> {
      if (ar.succeeded()) {
        HttpClientResponse response = ar.result();
        System.out.println("Received response with status code " + response.statusCode());
      }
    });
  }

  private String resolveURI(String base, String uriRef) {
    throw new UnsupportedOperationException();
  }

  public void exampleFollowRedirect03(HttpClient client) {

    client.redirectHandler(response -> {

      // Only follow 301 code
      if (response.statusCode() == 301 && response.getHeader("Location") != null) {

        // Compute the redirect URI
        String absoluteURI = resolveURI(response.request().absoluteURI(), response.getHeader("Location"));

        // Create a new ready to use request that the client will use
        return Future.succeededFuture(client.request(new RequestOptions().setAbsoluteURI(absoluteURI)));
      }

      // We don't redirect
      return null;
    });
  }

  public void example50(HttpClient client) {

    HttpClientRequest request = client.request(HttpMethod.PUT, "some-uri")
      .onSuccess(response -> {
        System.out.println("Received response with status code " + response.statusCode());
      });

    request.putHeader("Expect", "100-Continue");

    request.continueHandler(v -> {
      // OK to send rest of body
      request.write("Some data");
      request.write("Some more data");
      request.end();
    });
  }

  public void example50_1(HttpServer httpServer) {

    httpServer.requestHandler(request -> {
      if (request.getHeader("Expect").equalsIgnoreCase("100-Continue")) {

        // Send a 100 continue response
        request.response().writeContinue();

        // The client should send the body when it receives the 100 response
        request.bodyHandler(body -> {
          // Do something with body
        });

        request.endHandler(v -> {
          request.response().end();
        });
      }
    });
  }

  public void example50_2(HttpServer httpServer) {

    httpServer.requestHandler(request -> {
      if (request.getHeader("Expect").equalsIgnoreCase("100-Continue")) {

        //
        boolean rejectAndClose = true;
        if (rejectAndClose) {

          // Reject with a failure code and close the connection
          // this is probably best with persistent connection
          request.response()
              .setStatusCode(405)
              .putHeader("Connection", "close")
              .end();
        } else {

          // Reject with a failure code and ignore the body
          // this may be appropriate if the body is small
          request.response()
              .setStatusCode(405)
              .end();
        }
      }
    });
  }

  public void clientTunnel(HttpClient client) {

    HttpClientRequest request = client.request(HttpMethod.CONNECT, "some-uri")
      .onSuccess(response -> {
        if (response.statusCode() != 200) {
          // Connect failed for some reason
        }
      });

    request.netSocket(ar -> {
      if (ar.succeeded()) {
        NetSocket socket = ar.result();
        // Perform tunneling now
      }
    });

    request.end();
  }

  public void example51(HttpServer server) {

    server.webSocketHandler(webSocket -> {
      System.out.println("Connected!");
    });
  }

  public void example52(HttpServer server) {

    server.webSocketHandler(webSocket -> {
      if (webSocket.path().equals("/myapi")) {
        webSocket.reject();
      } else {
        // Do something
      }
    });
  }

  public void exampleAsynchronousHandshake(HttpServer server) {
    server.webSocketHandler(webSocket -> {
      Promise<Integer> promise = Promise.promise();
      webSocket.setHandshake(promise.future());
      authenticate(webSocket.headers(), ar -> {
        if (ar.succeeded()) {
          // Terminate the handshake with the status code 101 (Switching Protocol)
          // Reject the handshake with 401 (Unauthorized)
          promise.complete(ar.succeeded() ? 101 : 401);
        } else {
          // Will send a 500 error
          promise.fail(ar.cause());
        }
      });
    });
  }

  private static void authenticate(MultiMap headers, Handler<AsyncResult<Boolean>> handler) {

  }

  public void example53(HttpServer server) {

    server.requestHandler(request -> {
      if (request.path().equals("/myapi")) {

        ServerWebSocket webSocket = request.upgrade();
        // Do something

      } else {
        // Reject
        request.response().setStatusCode(400).end();
      }
    });
  }

  public void example54(HttpClient client) {
    client.webSocket("/some-uri", res -> {
      if (res.succeeded()) {
        WebSocket ws = res.result();
        System.out.println("Connected!");
      }
    });
  }

  public void example55(WebSocket webSocket) {
    // Write a simple binary message
    Buffer buffer = Buffer.buffer().appendInt(123).appendFloat(1.23f);
    webSocket.writeBinaryMessage(buffer);

    // Write a simple text message
    String message = "hello";
    webSocket.writeTextMessage(message);
  }

  public void example56(WebSocket webSocket, Buffer buffer1, Buffer buffer2, Buffer buffer3) {

    WebSocketFrame frame1 = WebSocketFrame.binaryFrame(buffer1, false);
    webSocket.writeFrame(frame1);

    WebSocketFrame frame2 = WebSocketFrame.continuationFrame(buffer2, false);
    webSocket.writeFrame(frame2);

    // Write the final frame
    WebSocketFrame frame3 = WebSocketFrame.continuationFrame(buffer2, true);
    webSocket.writeFrame(frame3);

  }

  public void example56_1(WebSocket webSocket) {

    // Send a WebSocket message consisting of a single final text frame:

    webSocket.writeFinalTextFrame("Geronimo!");

    // Send a WebSocket message consisting of a single final binary frame:

    Buffer buff = Buffer.buffer().appendInt(12).appendString("foo");

    webSocket.writeFinalBinaryFrame(buff);

  }

  public void example57(WebSocket webSocket) {

    webSocket.frameHandler(frame -> {
      System.out.println("Received a frame of size!");
    });

  }

  public void example58(Vertx vertx) {

    HttpClientOptions options = new HttpClientOptions()
        .setProxyOptions(new ProxyOptions().setType(ProxyType.HTTP)
            .setHost("localhost").setPort(3128)
            .setUsername("username").setPassword("secret"));
    HttpClient client = vertx.createHttpClient(options);

  }

  public void example59(Vertx vertx) {

    HttpClientOptions options = new HttpClientOptions()
        .setProxyOptions(new ProxyOptions().setType(ProxyType.SOCKS5)
            .setHost("localhost").setPort(1080)
            .setUsername("username").setPassword("secret"));
    HttpClient client = vertx.createHttpClient(options);

  }

  public void example60(Vertx vertx) {

    HttpClientOptions options = new HttpClientOptions()
        .setProxyOptions(new ProxyOptions().setType(ProxyType.HTTP));
    HttpClient client = vertx.createHttpClient(options);
    client.get(new RequestOptions().setURI("ftp://ftp.gnu.org/gnu/"), ar -> {
      if (ar.succeeded()) {
        HttpClientResponse response = ar.result();
        System.out.println("Received response with status code " + response.statusCode());
      }
    });

  }

  public void example61(Vertx vertx) {

    HttpServerOptions options = new HttpServerOptions()
      .setUseProxyProtocol(true);

    HttpServer server = vertx.createHttpServer(options);
    server.requestHandler(request -> {
      // Print the actual client address provided by the HA proxy protocol instead of the proxy address
      System.out.println(request.remoteAddress());

      // Print the address of the proxy
      System.out.println(request.localAddress());
    });
  }

  public void serversharing(Vertx vertx) {
    vertx.createHttpServer().requestHandler(request -> {
      request.response().end("Hello from server " + this);
    }).listen(8080);
  }

  public void serversharingclient(Vertx vertx) {
    vertx.setPeriodic(100, (l) -> {
      vertx.createHttpClient().get(8080, "localhost", "/", ar -> {
        if (ar.succeeded()) {
          HttpClientResponse resp = ar.result();
          resp.bodyHandler(body -> {
            System.out.println(body.toString("ISO-8859-1"));
          });
        }
      });
    });
  }

  public void setSSLPerRequest(HttpClient client) {
    client.get(new RequestOptions()
        .setHost("localhost")
        .setPort(8080)
        .setURI("/")
        .setSsl(true), ar -> {
      if (ar.succeeded()) {
        HttpClientResponse response = ar.result();
        System.out.println("Received response with status code " + response.statusCode());
      }
    });
  }

  public static void setIdentityContentEncodingHeader(HttpServerRequest request) {
    // Disable compression and send an image
    request.response()
      .putHeader(HttpHeaders.CONTENT_ENCODING, HttpHeaders.IDENTITY)
      .sendFile("/path/to/image.jpg");
  }
}
