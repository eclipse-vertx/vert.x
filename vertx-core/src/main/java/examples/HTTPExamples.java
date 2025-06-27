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

import io.netty.handler.codec.compression.GzipOptions;
import io.netty.handler.codec.compression.StandardCompressionOptions;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.FileSystem;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.http.*;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.endpoint.LoadBalancer;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.ProxyType;
import io.vertx.core.net.endpoint.ServerEndpoint;
import io.vertx.core.streams.Pipe;
import io.vertx.core.streams.ReadStream;

import java.util.List;
import java.util.concurrent.TimeUnit;

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
    server
      .listen(8080, "myhost.com")
      .onComplete(res -> {
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

  public void sendHttpServerResponse(Vertx vertx) {
    vertx.createHttpServer().requestHandler(request -> {
      HttpServerResponse response = request.response();
      if (request.method() == HttpMethod.PUT) {
        response.send(request);
      } else {
        response.setStatusCode(400).end();
      }
    }).listen(8080);
  }

  public void example28(Vertx vertx) {
    HttpClientAgent client = vertx.createHttpClient();
  }

  public void example29(Vertx vertx) {
    HttpClientOptions options = new HttpClientOptions().setKeepAlive(false);
    HttpClientAgent client = vertx.createHttpClient(options);
  }

  public void examplePoolConfiguration(Vertx vertx) {
    PoolOptions options = new PoolOptions().setHttp1MaxSize(10);
    HttpClientAgent client = vertx.createHttpClient(options);
  }

  public void exampleClientLogging(Vertx vertx) {
    HttpClientOptions options = new HttpClientOptions().setLogActivity(true);
    HttpClientAgent client = vertx.createHttpClient(options);
  }

  public void exampleClientBuilder01(Vertx vertx, HttpClientOptions options) {
    // Pretty much like vertx.createHttpClient(options)
    HttpClientAgent build = vertx
      .httpClientBuilder()
      .with(options)
      .build();
  }

  public void example30(HttpClient client) {
    client
      .request(HttpMethod.GET, 8080, "myserver.mycompany.com", "/some-uri")
      .onComplete(ar1 -> {
        if (ar1.succeeded()) {
          // Connected to the server
        }
      });
  }

  public void example31(Vertx vertx) {

    // Set the default host
    HttpClientOptions options = new HttpClientOptions().setDefaultHost("wibble.com");

    // Can also set default port if you want...
    HttpClientAgent client = vertx.createHttpClient(options);
    client
      .request(HttpMethod.GET, "/some-uri")
      .onComplete(ar1 -> {
        if (ar1.succeeded()) {
          HttpClientRequest request = ar1.result();
          request
            .send()
            .onComplete(ar2 -> {
              if (ar2.succeeded()) {
                HttpClientResponse response = ar2.result();
                System.out.println("Received response with status code " + response.statusCode());
              }
            });
        }
      });
  }

  public void example32(Vertx vertx) {

    HttpClientAgent client = vertx.createHttpClient();

    // Write some headers using the headers multi-map
    MultiMap headers = HttpHeaders.set("content-type", "application/json").set("other-header", "foo");

    client
      .request(HttpMethod.GET, "some-uri")
      .onComplete(ar1 -> {
        if (ar1.succeeded()) {
          if (ar1.succeeded()) {
            HttpClientRequest request = ar1.result();
            request.headers().addAll(headers);
            request
              .send()
              .onComplete(ar2 -> {
                HttpClientResponse response = ar2.result();
                System.out.println("Received response with status code " + response.statusCode());
              });
          }
        }
      });
  }

  public void example33(HttpClientRequest request) {

    // Write some headers using the putHeader method

    request.putHeader("content-type", "application/json")
           .putHeader("other-header", "foo");
  }

  public void sendRequest01(HttpClient client) {
    client
      .request(HttpMethod.GET, 8080, "myserver.mycompany.com", "/some-uri")
      .onComplete(ar1 -> {
        if (ar1.succeeded()) {
          HttpClientRequest request = ar1.result();

          // Send the request and process the response
          request
            .send()
            .onComplete(ar -> {
              if (ar.succeeded()) {
                HttpClientResponse response = ar.result();
                System.out.println("Received response with status code " + response.statusCode());
              } else {
                System.out.println("Something went wrong " + ar.cause().getMessage());
              }
            });
        }
      });
  }

  public void sendRequest02(HttpClient client) {
    client
      .request(HttpMethod.GET, 8080, "myserver.mycompany.com", "/some-uri")
      .onComplete(ar1 -> {
        if (ar1.succeeded()) {
          HttpClientRequest request = ar1.result();

          // Send the request and process the response
          request
            .send("Hello World")
            .onComplete(ar -> {
              if (ar.succeeded()) {
                HttpClientResponse response = ar.result();
                System.out.println("Received response with status code " + response.statusCode());
              } else {
                System.out.println("Something went wrong " + ar.cause().getMessage());
              }
            });
        }
      });
  }

  public void sendRequest03(HttpClientRequest request) {

    // Send the request and process the response
    request
      .send(Buffer.buffer("Hello World"))
      .onComplete(ar -> {
        if (ar.succeeded()) {
          HttpClientResponse response = ar.result();
          System.out.println("Received response with status code " + response.statusCode());
        } else {
          System.out.println("Something went wrong " + ar.cause().getMessage());
        }
      });
  }

  public void sendRequest04(HttpClientRequest request, ReadStream<Buffer> stream) {

    // Send the request and process the response
    request
      .putHeader(HttpHeaders.CONTENT_LENGTH, "1000")
      .send(stream)
      .onComplete(ar -> {
        if (ar.succeeded()) {
          HttpClientResponse response = ar.result();
          System.out.println("Received response with status code " + response.statusCode());
        } else {
          System.out.println("Something went wrong " + ar.cause().getMessage());
        }
      });
  }
  public void example34(Vertx vertx, String body) {
    HttpClientAgent client = vertx.createHttpClient();

    client.request(HttpMethod.POST, "some-uri")
      .onSuccess(request -> {
        request.response().onSuccess(response -> {
          System.out.println("Received response with status code " + response.statusCode());
        });

        // Now do stuff with the request
        request.putHeader("content-length", "1000");
        request.putHeader("content-type", "text/plain");
        request.write(body);

        // Make sure the request is ended when you're done with it
        request.end();
    });
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

  public void sendForm(HttpClientRequest request) {
    ClientForm form = ClientForm.form();
    form.attribute("firstName", "Dale");
    form.attribute("lastName", "Cooper");

    // Submit the form as a form URL encoded body
    request
      .send(form)
      .onSuccess(res -> {
        // OK
      });
  }

  public void sendMultipart(HttpClientRequest request) {
    ClientForm form = ClientForm.form();
    form.attribute("firstName", "Dale");
    form.attribute("lastName", "Cooper");

    // Submit the form as a multipart form body
    request
      .putHeader("content-type", "multipart/form-data")
      .send(form)
      .onSuccess(res -> {
        // OK
      });
  }

  public void sendMultipartWithFileUpload(HttpClientRequest request) {
    ClientMultipartForm form = ClientMultipartForm.multipartForm()
      .attribute("imageDescription", "a very nice image")
      .binaryFileUpload(
        "imageFile",
        "image.jpg",
        "/path/to/image",
        "image/jpeg");

    // Submit the form as a multipart form body
    request
      .send(form)
      .onSuccess(res -> {
        // OK
      });
  }

  public void clientIdleTimeout(HttpClient client, int port, String host, String uri, int timeoutMS) {
    Future<Buffer> fut = client
      .request(new RequestOptions()
        .setHost(host)
        .setPort(port)
        .setURI(uri)
        .setIdleTimeout(timeoutMS))
      .compose(request -> request.send().compose(HttpClientResponse::body));
  }

  public void clientConnectTimeout(HttpClient client, int port, String host, String uri, int timeoutMS) {
    Future<Buffer> fut = client
      .request(new RequestOptions()
        .setHost(host)
        .setPort(port)
        .setURI(uri)
        .setConnectTimeout(timeoutMS))
      .compose(request -> request.send().compose(HttpClientResponse::body));
  }

  public void clientTimeout(HttpClient client, int port, String host, String uri, int timeoutMS) {
    Future<Buffer> fut = client
      .request(new RequestOptions()
        .setHost(host)
        .setPort(port)
        .setURI(uri)
        .setTimeout(timeoutMS))
      .compose(request -> request.send().compose(HttpClientResponse::body));
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

  public void example45(HttpClientRequest request) {

    // Send the request
    request
      .send()
      .onComplete(ar2 -> {
        if (ar2.succeeded()) {

          HttpClientResponse response = ar2.result();

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

    client
      .request(HttpMethod.GET, "some-uri")
      .onComplete(ar1 -> {

        if (ar1.succeeded()) {
          HttpClientRequest request = ar1.result();
          request
            .send()
            .onComplete(ar2 -> {
              HttpClientResponse response = ar2.result();
              response.handler(buffer -> {
                System.out.println("Received a part of the response body: " + buffer);
              });
            });
        }
      });
  }

  public void example48(HttpClientRequest request) {

    request
      .send()
      .onComplete(ar2 -> {

        if (ar2.succeeded()) {

          HttpClientResponse response = ar2.result();

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

  public void example49(HttpClientRequest request) {

    request
      .send()
      .onComplete(ar1 -> {

        if (ar1.succeeded()) {
          HttpClientResponse response = ar1.result();
          response
            .body()
            .onComplete(ar2 -> {

              if (ar2.succeeded()) {
                Buffer body = ar2.result();
                // Now all the body has been read
                System.out.println("Total response body length is " + body.length());
              }
            });
        }
      });
  }

  private interface HttpClient2 {
    Future<HttpClientResponse> get(String requestURI);
  }

  public void exampleClientComposition01(HttpClient2 client) throws Exception {
    Future<HttpClientResponse> get = client.get("some-uri");

    // Assuming we have a client that returns a future response
    // assuming this is *not* on the event-loop
    // introduce a potential data race for the sake of this example
    Thread.sleep(100);

    get.onSuccess(response -> {

      // Response events might have happen already
      response
        .body()
        .onComplete(ar -> {

        });
    });
  }

  // Seems to fail javac in CI
//  public void exampleClientComposition02(Vertx vertx, HttpClient client) throws Exception {
//
//    vertx.deployVerticle(() -> new AbstractVerticle() {
//      @Override
//      public void start() {
//
//        HttpClient client = vertx.createHttpClient();
//
//        Future<HttpClientRequest> future = client.request(HttpMethod.GET, "some-uri");
//      }
//    }, new DeploymentOptions());
//  }

  public void exampleClientComposition03(HttpClient client) throws Exception {

    Future<JsonObject> future = client
      .request(HttpMethod.GET, "some-uri")
      .compose(request -> request
        .send()
        .compose(response -> {
          // Process the response on the event-loop which guarantees no races
          if (response.statusCode() == 200 &&
              response.getHeader(HttpHeaders.CONTENT_TYPE).equals("application/json")) {
            return response
              .body()
              .map(buffer -> buffer.toJsonObject());
          } else {
            return Future.failedFuture("Incorrect HTTP response");
          }
        }));

    // Listen to the composed final json result
    future.onSuccess(json -> {
      System.out.println("Received json result " + json);
    }).onFailure(err -> {
      System.out.println("Something went wrong " + err.getMessage());
    });
  }

  public void exampleClientComposition03_(HttpClient client) throws Exception {

    Future<JsonObject> future = client
      .request(HttpMethod.GET, "some-uri")
      .compose(request -> request
        .send()
        .expecting(HttpResponseExpectation.SC_OK.and(HttpResponseExpectation.JSON))
        .compose(response -> response
          .body()
          .map(buffer -> buffer.toJsonObject())));
    // Listen to the composed final json result
    future.onSuccess(json -> {
      System.out.println("Received json result " + json);
    }).onFailure(err -> {
      System.out.println("Something went wrong " + err.getMessage());
    });
  }

  public void exampleClientComposition04(HttpClient client, FileSystem fileSystem) throws Exception {

    Future<Void> future = client
      .request(HttpMethod.GET, "some-uri")
      .compose(request -> request
        .send()
        .compose(response -> {
          // Process the response on the event-loop which guarantees no races
          if (response.statusCode() == 200) {

            // Create a pipe, this pauses the response
            Pipe<Buffer> pipe = response.pipe();

            // Write the file on the disk
            return fileSystem
              .open("/some/large/file", new OpenOptions().setWrite(true))
              .onFailure(err -> pipe.close())
              .compose(file -> pipe.to(file));
          } else {
            return Future.failedFuture("Incorrect HTTP response");
          }
        }));
  }

  public void usingPredefinedExpectations(HttpClient client, RequestOptions options) {
    Future<Buffer> fut = client
      .request(options)
      .compose(request -> request
        .send()
        .expecting(HttpResponseExpectation.SC_SUCCESS)
        .compose(response -> response.body()));
  }

  public void usingPredicates(HttpClient client) {

    // Check CORS header allowing to do POST
    HttpResponseExpectation methodsPredicate =
      resp -> {
        String methods = resp.getHeader("Access-Control-Allow-Methods");
        return methods != null && methods.contains("POST");
      };

    // Send pre-flight CORS request
    client
      .request(new RequestOptions()
        .setMethod(HttpMethod.OPTIONS)
        .setPort(8080)
        .setHost("myserver.mycompany.com")
        .setURI("/some-uri")
        .putHeader("Origin", "Server-b.com")
        .putHeader("Access-Control-Request-Method", "POST"))
      .compose(request -> request
        .send()
        .expecting(methodsPredicate))
      .onSuccess(res -> {
        // Process the POST request now
      })
      .onFailure(err ->
        System.out.println("Something went wrong " + err.getMessage()));
  }

  public void usingSpecificStatus(HttpClient client, RequestOptions options) {
    client
      .request(options)
      .compose(request -> request
        .send()
        .expecting(HttpResponseExpectation.status(200, 202)))
      .onSuccess(res -> {
        // ....
      });
  }

  public void usingSpecificContentType(HttpClient client, RequestOptions options) {
    client
      .request(options)
      .compose(request -> request
        .send()
        .expecting(HttpResponseExpectation.contentType("some/content-type")))
      .onSuccess(res -> {
        // ....
      });
  }

  public void expectationCustomError() {
    Expectation<HttpResponseHead> expectation = HttpResponseExpectation.SC_SUCCESS
      .wrappingFailure((resp, err) -> new MyCustomException(resp.statusCode(), err.getMessage()));
  }

  private static class MyCustomException extends Exception {

    private final int code;

    public MyCustomException(int code, String message) {
      super(message);
      this.code = code;
    }
  }

  public void exampleFollowRedirect01(HttpClient client) {
    client
      .request(HttpMethod.GET, "some-uri")
      .onComplete(ar1 -> {
        if (ar1.succeeded()) {

          HttpClientRequest request = ar1.result();
          request.setFollowRedirects(true);
          request
            .send()
            .onComplete(ar2 -> {
              if (ar2.succeeded()) {

                HttpClientResponse response = ar2.result();
                System.out.println("Received response with status code " + response.statusCode());
              }
            });
        }
      });
  }

  public void exampleFollowRedirect02(Vertx vertx) {

    HttpClientAgent client = vertx.createHttpClient(
        new HttpClientOptions()
            .setMaxRedirects(32));

    client
      .request(HttpMethod.GET, "some-uri")
      .onComplete(ar1 -> {
        if (ar1.succeeded()) {

          HttpClientRequest request = ar1.result();
          request.setFollowRedirects(true);
          request
            .send()
            .onComplete(ar2 -> {
              if (ar2.succeeded()) {

                HttpClientResponse response = ar2.result();
                System.out.println("Received response with status code " + response.statusCode());
              }
            });
        }
      });
  }

  private String resolveURI(String base, String uriRef) {
    throw new UnsupportedOperationException();
  }

  public void exampleFollowRedirect03(Vertx vertx) {
    HttpClientAgent client = vertx.httpClientBuilder()
      .withRedirectHandler(response -> {

        // Only follow 301 code
        if (response.statusCode() == 301 && response.getHeader("Location") != null) {

          // Compute the redirect URI
          String absoluteURI = resolveURI(response.request().absoluteURI(), response.getHeader("Location"));

          // Create a new ready to use request that the client will use
          return Future.succeededFuture(new RequestOptions().setAbsoluteURI(absoluteURI));
        }

        // We don't redirect
        return null;
      })
      .build();
  }

  public void example50(HttpClient client) {

    client.request(HttpMethod.PUT, "some-uri")
      .onSuccess(request -> {
        request.response().onSuccess(response -> {
          System.out.println("Received response with status code " + response.statusCode());
        });

        request.putHeader("Expect", "100-Continue");

        request.continueHandler(v -> {
          // OK to send rest of body
          request.write("Some data");
          request.write("Some more data");
          request.end();
        });

        request.writeHead();
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

    client.request(HttpMethod.CONNECT, "some-uri")
      .onSuccess(request -> {

        // Connect to the server
        request
          .connect()
          .onComplete(ar -> {
            if (ar.succeeded()) {
              HttpClientResponse response = ar.result();

              if (response.statusCode() != 200) {
                // Connect failed for some reason
              } else {
                // Tunnel created, raw buffers are transmitted on the wire
                NetSocket socket = response.netSocket();
              }
            }
          });
    });
  }

  public void example51(HttpServer server) {

    server.webSocketHandler(webSocket -> {
      System.out.println("Connected!");
    });
  }

  public void exampleAsynchronousHandshake(HttpServer server) {
    server.webSocketHandshakeHandler(handshake -> {
      authenticate(handshake.headers(), ar -> {
        if (ar.succeeded()) {
          if (ar.result()) {
            // Terminate the handshake with the status code 101 (Switching Protocol)
            handshake.accept();
          } else {
            // Reject the handshake with 401 (Unauthorized)
            handshake.reject(401);
          }
        } else {
          // Will send a 500 error
          handshake.reject(500);
        }
      });
    });
  }

  private static void authenticate(MultiMap headers, Handler<AsyncResult<Boolean>> handler) {

  }

  public void example53(HttpServer server) {

    server.requestHandler(request -> {
      if (request.path().equals("/myapi")) {

        Future<ServerWebSocket> fut = request.toWebSocket();
        fut.onSuccess(ws -> {
          // Do something
        });

      } else {
        // Reject
        request.response().setStatusCode(400).end();
      }
    });
  }


  public void example54(Vertx vertx) {
    WebSocketClient client = vertx.createWebSocketClient();

    client
      .connect(80, "example.com", "/some-uri")
      .onComplete(res -> {
        if (res.succeeded()) {
          WebSocket ws = res.result();
          ws.textMessageHandler(msg -> {
            // Handle msg
          });
          System.out.println("Connected!");
        }
      });
  }

  public void example54_bis(Vertx vertx) {
    WebSocketClient client = vertx.createWebSocketClient();

    client
      .webSocket()
      .textMessageHandler(msg -> {
        // Handle msg
      })
      .connect(80, "example.com", "/some-uri")
      .onComplete(res -> {
        if (res.succeeded()) {
          WebSocket ws = res.result();
        }
      });
  }

  public void exampleWebSocketDisableOriginHeader(WebSocketClient client, String host, int port, String requestUri) {
    WebSocketConnectOptions options = new WebSocketConnectOptions()
      .setHost(host)
      .setPort(port)
      .setURI(requestUri)
      .setAllowOriginHeader(false);
    client
      .connect(options)
      .onComplete(res -> {
        if (res.succeeded()) {
          WebSocket ws = res.result();
          System.out.println("Connected!");
        }
      });
  }

  public void exampleWebSocketSetOriginHeader(WebSocketClient client, String host, int port, String requestUri, String origin) {
    WebSocketConnectOptions options = new WebSocketConnectOptions()
      .setHost(host)
      .setPort(port)
      .setURI(requestUri)
      .addHeader(HttpHeaders.ORIGIN, origin);
    client
      .connect(options)
      .onComplete(res -> {
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
    HttpClientAgent client = vertx.createHttpClient(options);

  }

  public void example59(Vertx vertx) {

    HttpClientOptions options = new HttpClientOptions()
        .setProxyOptions(new ProxyOptions().setType(ProxyType.SOCKS5)
            .setHost("localhost").setPort(1080)
            .setUsername("username").setPassword("secret"));
    HttpClientAgent client = vertx.createHttpClient(options);

  }

  public void nonProxyHosts(Vertx vertx) {

    HttpClientOptions options = new HttpClientOptions()
      .setProxyOptions(new ProxyOptions().setType(ProxyType.SOCKS5)
        .setHost("localhost").setPort(1080)
        .setUsername("username").setPassword("secret"))
      .addNonProxyHost("*.foo.com")
      .addNonProxyHost("localhost");
    HttpClientAgent client = vertx.createHttpClient(options);

  }

  public void perRequestProxyOptions(HttpClient client, ProxyOptions proxyOptions) {

    client.request(new RequestOptions()
      .setHost("example.com")
      .setProxyOptions(proxyOptions))
      .compose(request -> request
        .send()
        .compose(HttpClientResponse::body))
      .onSuccess(body -> {
        System.out.println("Received response");
      });
  }

  public void example60(Vertx vertx) {

    HttpClientOptions options = new HttpClientOptions()
        .setProxyOptions(new ProxyOptions().setType(ProxyType.HTTP));
    HttpClientAgent client = vertx.createHttpClient(options);
    client
      .request(HttpMethod.GET, "ftp://ftp.gnu.org/gnu/")
      .onComplete(ar -> {
        if (ar.succeeded()) {
          HttpClientRequest request = ar.result();
          request
            .send()
            .onComplete(ar2 -> {
              if (ar2.succeeded()) {
                HttpClientResponse response = ar2.result();
                System.out.println("Received response with status code " + response.statusCode());
              }
            });
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
      vertx
        .createHttpClient()
        .request(HttpMethod.GET, 8080, "localhost", "/")
        .onComplete(ar1 -> {
          if (ar1.succeeded()) {
            HttpClientRequest request = ar1.result();
            request
              .send()
              .onComplete(ar2 -> {
                if (ar2.succeeded()) {
                  HttpClientResponse resp = ar2.result();
                  resp.bodyHandler(body -> {
                    System.out.println(body.toString("ISO-8859-1"));
                  });
                }
              });
          }
        });
    });
  }

  public void randomServersharing(Vertx vertx) {
    vertx.createHttpServer().requestHandler(request -> {
      request.response().end("Hello from server " + this);
    }).listen(-1);
  }

  public void setSSLPerRequest(HttpClient client) {
    client
      .request(new RequestOptions()
        .setHost("localhost")
        .setPort(8080)
        .setURI("/")
        .setSsl(true))
      .onComplete(ar1 -> {
        if (ar1.succeeded()) {
          HttpClientRequest request = ar1.result();
          request
            .send()
            .onComplete(ar2 -> {
              if (ar2.succeeded()) {
                HttpClientResponse response = ar2.result();
                System.out.println("Received response with status code " + response.statusCode());
              }
            });
        }
      });
  }

  public static void setIdentityContentEncodingHeader(HttpServerRequest request) {
    // Disable compression and send an image
    request.response()
      .putHeader(HttpHeaders.CONTENT_ENCODING, HttpHeaders.IDENTITY)
      .sendFile("/path/to/image.jpg");
  }

  public static void setCompressors() {
    new HttpServerOptions()
      .addCompressor(io.netty.handler.codec.compression.StandardCompressionOptions.gzip())
      .addCompressor(io.netty.handler.codec.compression.StandardCompressionOptions.deflate())
      .addCompressor(io.netty.handler.codec.compression.StandardCompressionOptions.brotli())
      .addCompressor(io.netty.handler.codec.compression.StandardCompressionOptions.zstd());
  }

  public static void compressorConfig() {
    GzipOptions gzip = StandardCompressionOptions.gzip(6, 15, 8);
  }

  public void serverShutdown(HttpServer server) {
    server
      .shutdown()
      .onSuccess(res -> {
        System.out.println("Server is now closed");
      });
  }

  public void serverShutdownWithAmountOfTime(HttpServer server) {
    server
      .shutdown(60, TimeUnit.SECONDS)
      .onSuccess(res -> {
        System.out.println("Server is now closed");
      });
  }

  public void example9(HttpServer server) {

    server
      .close()
      .onSuccess(res -> {
        System.out.println("Server is now closed");
      });
  }

  public void shutdownHandler(HttpServer server) {
    server.connectionHandler(conn -> {
      conn.shutdownHandler(v -> {
        // Perform clean-up
      });
    });
  }

  public static void httpClientSharing1(Vertx vertx) {
    HttpClientAgent client = vertx.createHttpClient(new HttpClientOptions().setShared(true));
    vertx.deployVerticle(() -> new AbstractVerticle() {
      @Override
      public void start() throws Exception {
        // Use the client
      }
    }, new DeploymentOptions().setInstances(4));
  }

  public static void httpClientSharing2(Vertx vertx) {
    vertx.deployVerticle(() -> new AbstractVerticle() {
      HttpClientAgent client;
      @Override
      public void start() {
        // Get or create a shared client
        // this actually creates a lease to the client
        // when the verticle is undeployed, the lease will be released automaticaly
        client = vertx.createHttpClient(new HttpClientOptions().setShared(true).setName("my-client"));
      }
    }, new DeploymentOptions().setInstances(4));
  }

  public static void httpClientSharing3(Vertx vertx) {
    vertx.deployVerticle(() -> new AbstractVerticle() {
      HttpClientAgent client;
      @Override
      public void start() {
        // The client creates and use two event-loops for 4 instances
        client = vertx.createHttpClient(new HttpClientOptions().setShared(true).setName("my-client"), new PoolOptions().setEventLoopSize(2));
      }
    }, new DeploymentOptions().setInstances(4));
  }

  public static void httpClientSideLoadBalancing(Vertx vertx) {
    HttpClientAgent client = vertx
      .httpClientBuilder()
      .withLoadBalancer(LoadBalancer.ROUND_ROBIN)
      .build();
  }

  public static void httpClientSideConsistentHashing(Vertx vertx, int servicePort) {
    HttpClientAgent client = vertx
      .httpClientBuilder()
      .withLoadBalancer(LoadBalancer.CONSISTENT_HASHING)
      .build();

    HttpServer server = vertx.createHttpServer()
      .requestHandler(inboundReq -> {

        // Get a routing key, in this example we will hash the incoming request host/ip
        // it could be anything else, e.g. user id, request id, ...
        String routingKey = inboundReq.remoteAddress().hostAddress();

        client.request(new RequestOptions()
            .setHost("example.com")
            .setURI("/test")
            .setRoutingKey(routingKey))
          .compose(outboundReq -> outboundReq.send()
            .expecting(HttpResponseExpectation.SC_OK)
            .compose(HttpClientResponse::body))
          .onComplete(ar -> {
            if (ar.succeeded()) {
              Buffer response = ar.result();
            }
          });
      });

    server.listen(servicePort);
  }

  public static void consistentHashingConfiguration() {
    // Use 10 virtual nodes per server and Power of two choices policy in the absence of a routing key
    LoadBalancer loadBalancer = LoadBalancer.consistentHashing(10, LoadBalancer.POWER_OF_TWO_CHOICES);
  }

  public static void customLoadBalancingPolicy(Vertx vertx) {
    LoadBalancer loadBalancer = endpoints -> {
      // Returns an endpoint selector for the given endpoints
      // a selector is a stateful view of the provided immutable list of endpoints
      return () -> indexOfEndpoint(endpoints);
    };

    HttpClientAgent client = vertx
      .httpClientBuilder()
      .withLoadBalancer(loadBalancer)
      .build();
  }

  private static int indexOfEndpoint(List<? extends ServerEndpoint> endpoints) {
    return 0;
  }

  private static String getRoutingKey() {
    return null;
  }

  public static void connect(HttpClientAgent client) {
    HttpConnectOptions connectOptions = new HttpConnectOptions()
      .setHost("example.com")
      .setPort(80);

    Future<HttpClientConnection> fut = client.connect(connectOptions);
  }

  public static void connectAndGet(HttpClientConnection connection) {
    connection
      .request()
      .onSuccess(request -> {
        request.setMethod(HttpMethod.GET);
        request.setURI("/some-uri");
        Future<HttpClientResponse> response = request.send();
      });
  }
}
