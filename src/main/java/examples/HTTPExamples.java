/*
 * Copyright 2014 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package examples;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.http.*;
import io.vertx.core.net.NetSocket;
import io.vertx.core.streams.Pump;

/**
 * Created by tim on 09/01/15.
 */
public class HTTPExamples {

  public void example1(Vertx vertx) {

    HttpServer server = vertx.createHttpServer();
  }

  public void example2(Vertx vertx) {

    HttpServerOptions options = new HttpServerOptions().setMaxWebsocketFrameSize(1000000);

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
        System.out.println("Got a file upload " +  upload.name());
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

  public void example27(Vertx vertx) {
    vertx.createHttpServer().requestHandler(request -> {
      HttpServerResponse response = request.response();
      if (request.method() == HttpMethod.PUT) {
        response.setChunked(true);
        Pump.pump(request, response).start();
        request.endHandler(v -> response.end());
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
    HttpClient client = vertx.createHttpClient();
  }

  public void example30(Vertx vertx) {
    // Set the default host
    HttpClientOptions options = new HttpClientOptions().setDefaultHost("wibble.com");
    // Can also set default port if you want...
    HttpClient client = vertx.createHttpClient(options);
    client.getNow("/some-uri", response -> {
      System.out.println("Received response with status code " + response.statusCode());
    });
  }

  public void example31(Vertx vertx) {
    HttpClient client = vertx.createHttpClient();

    // Specify both port and host name
    client.getNow(8080, "myserver.mycompany.com", "/some-uri", response -> {
      System.out.println("Received response with status code " + response.statusCode());
    });

    // This time use the default port 80 but specify the host name
    client.getNow("foo.othercompany.com", "/other-uri", response -> {
      System.out.println("Received response with status code " + response.statusCode());
    });
  }

  public void example32(Vertx vertx) {
    HttpClient client = vertx.createHttpClient();

    // Send a GET request
    client.getNow("/some-uri", response -> {
      System.out.println("Received response with status code " + response.statusCode());
    });

    // Send a GET request
    client.headNow("/other-uri", response -> {
      System.out.println("Received response with status code " + response.statusCode());
    });

  }

  public void example33(Vertx vertx) {
    HttpClient client = vertx.createHttpClient();

    client.request(HttpMethod.GET, "some-uri", response -> {
      System.out.println("Received response with status code " + response.statusCode());
    }).end();

    client.request(HttpMethod.POST, "foo-uri", response -> {
      System.out.println("Received response with status code " + response.statusCode());
    }).end("some-data");
  }

  public void example34(Vertx vertx, String body) {
    HttpClient client = vertx.createHttpClient();

    HttpClientRequest request = client.post("some-uri", response -> {
      System.out.println("Received response with status code " + response.statusCode());
    });

    // Now do stuff with the request
    request.putHeader("content-length", "1000");
    request.putHeader("content-type", "text/plain");
    request.write(body);

    // Make sure the request is ended when you're done with it
    request.end();

    // Or fluently:

    client.post("some-uri", response -> {
      System.out.println("Received response with status code " + response.statusCode());
    }).putHeader("content-length", "1000").putHeader("content-type", "text/plain").write(body).end();

    // Or event more simply:

    client.post("some-uri", response -> {
      System.out.println("Received response with status code " + response.statusCode());
    }).putHeader("content-type", "text/plain").end(body);

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

  public void example37(HttpClientRequest request) {

    // Write some headers using the headers() multimap

    MultiMap headers = request.headers();
    headers.set("content-type", "application/json").set("other-header", "foo");

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

    HttpClientRequest request = client.post("some-uri", response -> {
      System.out.println("Received response with status code " + response.statusCode());
    });
    request.exceptionHandler(e -> {
      System.out.println("Received exception: " + e.getMessage());
      e.printStackTrace();
    });
  }

  public void example43(HttpClient client) {

    HttpClientRequest request = client.post("some-uri");
    request.handler(response -> {
      System.out.println("Received response with status code " + response.statusCode());
    });
  }

  public void example44(HttpClientRequest request, AsyncFile file) {

    request.setChunked(true);
    Pump pump = Pump.pump(file, request);
    file.endHandler(v -> request.end());
    pump.start();

  }

  public void example45(HttpClient client) {

    client.getNow("some-uri", response -> {
      // the status code - e.g. 200 or 404
      System.out.println("Status code is " + response.statusCode());

      // the status message e.g. "OK" or "Not Found".
      System.out.println("Status message is " + response.statusMessage());
    });

  }

  public void example46(HttpClientResponse response) {

    String contentType = response.headers().get("content-type");
    String contentLength = response.headers().get("content-lengh");

  }

  public void example47(HttpClient client) {

    client.getNow("some-uri", response -> {

      response.handler(buffer -> {
        System.out.println("Received a part of the response body: " + buffer);
      });
    });
  }

  public void example48(HttpClient client) {

    client.getNow("some-uri", response -> {

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
    });
  }

  public void example49(HttpClient client) {

    client.getNow("some-uri", response -> {

      response.bodyHandler(totalBuffer -> {
        // Now all the body has been read
        System.out.println("Total response body length is " + totalBuffer.length());
      });
    });
  }

  public void example50(HttpClient client) {

    HttpClientRequest request = client.put("some-uri", response -> {
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

  public void example51(HttpServer server) {

    server.websocketHandler(websocket -> {
      System.out.println("Connected!");
    });
  }

  public void example52(HttpServer server) {

    server.websocketHandler(websocket -> {
      if (websocket.path().equals("/myapi")) {
        websocket.reject();
      } else {
        // Do something
      }
    });
  }

  public void example53(HttpServer server) {

    server.requestHandler(request -> {
      if (request.path().equals("/myapi")) {

        ServerWebSocket websocket = request.upgrade();
        // Do something

      } else {
        // Reject
        request.response().setStatusCode(400).end();
      }
    });
  }

  public void example54(HttpClient client) {
    client.websocket("/some-uri", websocket -> {
      System.out.println("Connected!");
    });
  }

  public void example55(WebSocket websocket) {
    // Write a simple message
    Buffer buffer = Buffer.buffer().appendInt(123).appendFloat(1.23f);

    websocket.writeMessage(buffer);
  }

  public void example56(WebSocket websocket, Buffer buffer1, Buffer buffer2, Buffer buffer3) {

    WebSocketFrame frame1 = WebSocketFrame.binaryFrame(buffer1, false);
    websocket.writeFrame(frame1);

    WebSocketFrame frame2 = WebSocketFrame.continuationFrame(buffer2, false);
    websocket.writeFrame(frame2);

    // Write the final frame
    WebSocketFrame frame3 = WebSocketFrame.continuationFrame(buffer2, true);
    websocket.writeFrame(frame3);

  }

  public void example57(WebSocket websocket) {

    websocket.frameHandler(frame -> {
      System.out.println("Received a frame of size!");
    });

  }
}
