package examples;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Expectation;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.FileSystem;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.http.ClientForm;
import io.vertx.core.http.ClientMultipartForm;
import io.vertx.core.http.CompressionConfig;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.Http1ClientConfig;
import io.vertx.core.http.Http1ServerConfig;
import io.vertx.core.http.Http2ClientConfig;
import io.vertx.core.http.Http2ServerConfig;
import io.vertx.core.http.Http2Settings;
import io.vertx.core.http.Http3ClientConfig;
import io.vertx.core.http.Http3Settings;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientAgent;
import io.vertx.core.http.HttpClientConfig;
import io.vertx.core.http.HttpClientConnection;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpConnectOptions;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpResponseExpectation;
import io.vertx.core.http.HttpResponseHead;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerConfig;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.HttpSettings;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.PoolOptions;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.http.StreamResetException;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebSocketClient;
import io.vertx.core.http.WebSocketConnectOptions;
import io.vertx.core.http.WebSocketFrame;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.ClientSSLOptions;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.LogConfig;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.ProxyType;
import io.vertx.core.net.QuicConfig;
import io.vertx.core.net.QuicServerConfig;
import io.vertx.core.net.ServerSSLOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.endpoint.LoadBalancer;
import io.vertx.core.net.endpoint.ServerEndpoint;
import io.vertx.core.streams.Pipe;
import io.vertx.core.streams.ReadStream;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class HttpExamples {

  public void defaultHttpServer(Vertx vertx) {
    HttpServer server = vertx.createHttpServer();
  }

  public void configurationOfAnHttpServer(Vertx vertx) {
    HttpServerConfig config = new HttpServerConfig()
      .setVersions(HttpVersion.HTTP_1_1)
      .setMaxFormFields(512)
      .setHttp1Config(new Http1ServerConfig()
        .setMaxInitialLineLength(1024))
      .setCompression(new CompressionConfig()
        .setCompressionEnabled(true)
        .addGzip());

    HttpServer server = vertx.createHttpServer(config);
  }

  public void configurationOfAnHttpsServer(Vertx vertx) {
    ServerSSLOptions sslOptions = new ServerSSLOptions()
      .setKeyCertOptions(
        new JksOptions().
          setPath("/path/to/your/server-keystore.jks").
          setPassword("password-of-your-keystore")
      );

    HttpServerConfig config = new HttpServerConfig()
      .setSsl(true);

    HttpServer server = vertx.createHttpServer(config, sslOptions);
  }

  public void configurationOfAnHttp1Server(Vertx vertx) {
    HttpServerConfig config = new HttpServerConfig()
      .setSsl(true)
      .setHttp1Config(new Http1ServerConfig()
        .setMaxInitialLineLength(1024));
  }

  public void configurationOfAnH2Server(Vertx vertx) {
    HttpServerConfig config = new HttpServerConfig()
      .setSsl(true)
      .setHttp2Config(new Http2ServerConfig()
        .setInitialSettings(new Http2Settings()
          .setMaxConcurrentStreams(250)));
  }

  public void configurationOfAnH3Server(Vertx vertx) {
    HttpServerConfig config = new HttpServerConfig()
      .setVersions(HttpVersion.HTTP_3);
  }

  public void configurationOfH3ServerConcurrency(Vertx vertx) {
    HttpServerConfig config = new HttpServerConfig()
      .setVersions(HttpVersion.HTTP_3);

    QuicServerConfig quicConfig = config.getQuicConfig();
    quicConfig.setTransportConfig(new QuicConfig()
      .setInitialMaxStreamsBidi(250));
  }

  public void configurationOfAHybridServer(Vertx vertx) {
    HttpServerConfig config = new HttpServerConfig()
      .setVersions(HttpVersion.HTTP_1_1, HttpVersion.HTTP_2, HttpVersion.HTTP_3)
      .setSsl(true);

    ServerSSLOptions sslOptions = new ServerSSLOptions()
      .setKeyCertOptions(new JksOptions().setPath("/path/to/my/keystore"));

    HttpServer server = vertx.createHttpServer(config, sslOptions);
  }

  public void configurationOfAHybridServerPorts(Vertx vertx, int tcpPort, int quicPort) {
    HttpServerConfig config = new HttpServerConfig()
      .setVersions(HttpVersion.HTTP_1_1, HttpVersion.HTTP_2, HttpVersion.HTTP_3)
      .setTcpPort(tcpPort)
      .setQuicPort(quicPort);
  }

  public void configurationOfAHybridServerPort(Vertx vertx, int port, int quicPort) {
    HttpServerConfig config = new HttpServerConfig()
      .setVersions(HttpVersion.HTTP_1_1, HttpVersion.HTTP_2, HttpVersion.HTTP_3)
      .setPort(port);
  }

  public void httpServerBuilder(Vertx vertx, HttpServerConfig config) {
    // Pretty much like vertx.createHttpServer(config)
    HttpServer server = vertx
      .httpServerBuilder()
      .with(config)
      .build();
  }

  public void bindingAnHttpServer(Vertx vertx) {
    HttpServer server = vertx.createHttpServer();
    server.listen();
  }

  public void bindingAnHttpServerToHostAndPort(Vertx vertx) {
    HttpServer server = vertx.createHttpServer();
    server.listen(8080, "myhost.com");
  }

  public void bindinAnHttpServerAndGettingNotified(Vertx vertx) {
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

  public void bindingAnHttpServerToAnUnixDomainSocket(Vertx vertx) {
    HttpServer httpServer = vertx.createHttpServer();

    // Only available when running on JDK16+, or using a native transport
    SocketAddress address = SocketAddress.domainSocketAddress("/var/tmp/myservice.sock");

    httpServer
      .requestHandler(req -> {
        // Handle application
      })
      .listen(address)
      .onComplete(ar -> {
        if (ar.succeeded()) {
          // Bound to socket
        } else {
          // Handle failure
        }
      });
  }

  public void serverRequestHandler(Vertx vertx) {

    HttpServer server = vertx.createHttpServer();
    server.requestHandler(request -> {
      // Handle the request in here
    });
  }

  public void serverHelloWorldRequestHandler(Vertx vertx) {
    vertx
      .createHttpServer()
      .requestHandler(request -> {
        request.response().end("Hello world");
      })
      .listen(8080);
  }

  public void serverRequestHeaders(HttpServerRequest request) {

    MultiMap headers = request.headers();

    // Get the User-Agent:
    System.out.println("User agent is " + headers.get("user-agent"));

    // You can also do this and get the same result:
    System.out.println("User agent is " + headers.get("User-Agent"));
  }

  public void serverRequestHandleBuffer(HttpServerRequest request) {

    request.handler(buffer -> {
      System.out.println("I have received a chunk of the body of length " + buffer.length());
    });
  }

  public void serverRequestChunkAggregation(HttpServerRequest request) {

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

  public void serverRequestHandleBody(HttpServerRequest request) {

    request.bodyHandler(totalBuffer -> {
      System.out.println("Full body received, length = " + totalBuffer.length());
    });
  }

  public void serverRequestMultipartForm(HttpServer server) {

    server.requestHandler(request -> {
      request.setExpectMultipart(true);
      request.endHandler(v -> {
        // The body has now been fully read, so retrieve the form attributes
        MultiMap formAttributes = request.formAttributes();
      });
    });
  }

  public void serverRequestHandleMultipartUpload(HttpServer server) {

    server.requestHandler(request -> {
      request.setExpectMultipart(true);
      request.uploadHandler(upload -> {
        System.out.println("Processing a file upload " + upload.name());
      });
    });
  }

  public void serverRequestHandleMultipartUploadBuffer(HttpServerRequest request) {

    request.uploadHandler(upload -> {
      upload.handler(chunk -> {
        System.out.println("Received a chunk of the upload of length " + chunk.length());
      });
    });
  }

  public void serverRequestMultipartStreamToFileSystem(HttpServerRequest request) {

    request.uploadHandler(upload -> {
      upload.streamToFileSystem("myuploads_directory/" + upload.filename());
    });
  }

  public void serverRequestCookies(HttpServerRequest request) {
    Cookie someCookie = request.getCookie("mycookie");
    String cookieValue = someCookie.getValue();

    // Do something with cookie...

    // Add a cookie - this will get written back in the response automatically
    request.response().addCookie(Cookie.cookie("othercookie", "somevalue"));
  }

  public void serverRequestHandleCustomFrame(HttpServerRequest request) {

    request.customFrameHandler(frame -> {

      System.out.println("Received a frame type=" + frame.type() +
        " payload" + frame.payload().toString());
    });
  }

  public void serverResponseWriteBuffer(HttpServerRequest request, Buffer buffer) {
    HttpServerResponse response = request.response();
    response.write(buffer);
  }

  public void serverResponseWriteString(HttpServerRequest request) {
    HttpServerResponse response = request.response();
    response.write("hello world!");
  }

  public void serverResponseWriteStringWithEncoding(HttpServerRequest request) {
    HttpServerResponse response = request.response();
    response.write("hello world!", "UTF-16");
  }

  public void serverResponseEnd(HttpServerRequest request) {
    HttpServerResponse response = request.response();
    response.write("hello world!");
    response.end();
  }

  public void serverResponseEndWithString(HttpServerRequest request) {
    HttpServerResponse response = request.response();
    response.end("hello world!");
  }

  public void serverResponseHeaders(HttpServerRequest request) {
    HttpServerResponse response = request.response();
    MultiMap headers = response.headers();
    headers.set("content-type", "text/html");
    headers.set("other-header", "wibble");
  }

  public void serverResponsePutHeader(HttpServerRequest request) {
    HttpServerResponse response = request.response();
    response.putHeader("content-type", "text/html").putHeader("other-header", "wibble");
  }

  public void serverResponseSetChunked(HttpServerRequest request) {
    HttpServerResponse response = request.response();
    response.setChunked(true);
  }

  public void serverResponseTrailers(HttpServerRequest request) {
    HttpServerResponse response = request.response();
    response.setChunked(true);
    MultiMap trailers = response.trailers();
    trailers.set("X-wibble", "woobble").set("X-quux", "flooble");
  }

  public void serverResponsePutTrailer(HttpServerRequest request) {
    HttpServerResponse response = request.response();
    response.setChunked(true);
    response
      .putTrailer("X-wibble", "woobble")
      .putTrailer("X-quux", "flooble");
  }

  public void serverResponseSendFile(Vertx vertx) {
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

  public void serverResponseSendFileRegion(Vertx vertx) {
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

  public void serverResponseSendFileFromOffset(Vertx vertx) {
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

  public void serverResponsePiping(Vertx vertx) {
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

  public void serverResponseSend(Vertx vertx) {
    vertx.createHttpServer().requestHandler(request -> {
      HttpServerResponse response = request.response();
      if (request.method() == HttpMethod.PUT) {
        response.send(request);
      } else {
        response.setStatusCode(400).end();
      }
    }).listen(8080);
  }

  public void serverResponseWriteCustomFrame(HttpServerResponse response) {

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

  public static void serverResponseByPassCompression(HttpServerRequest request) {
    // Disable compression and send an image
    request.response()
      .putHeader(HttpHeaders.CONTENT_ENCODING, HttpHeaders.IDENTITY)
      .sendFile("/path/to/image.jpg");
  }

  public static void serverConfigureCompressors() {
    new HttpServerConfig()
      .setCompression(new CompressionConfig()
        .setCompressionEnabled(true)
        .addGzip()
        .addDeflate()
      );
  }

  public static void serverConfigureCompressor() {
    new HttpServerConfig()
      .setCompression(new CompressionConfig()
        .addGzip(6, 15, 8));
  }

  public void defaultHttpClient(Vertx vertx) {
    HttpClientAgent client = vertx.createHttpClient();
  }

  public void configurationOfAnHttpClient(Vertx vertx) {

    HttpClientConfig config = new HttpClientConfig()
      .setVersions(HttpVersion.HTTP_1_1)
      .setMaxRedirects(5)
      .setHttp1Config(new Http1ClientConfig()
        .setMaxInitialLineLength(1024));

    HttpClientAgent client = vertx.createHttpClient(config);
  }

  public void configurationOfAnHttpsClient(Vertx vertx) {
    ClientSSLOptions sslOptions = new ClientSSLOptions()
      .setTrustOptions(
        new JksOptions().
          setPath("/path/to/your/truststore.jks").
          setPassword("password-of-your-truststore")
      );

    HttpClientConfig config = new HttpClientConfig()
      .setSsl(true);

    HttpClientAgent client = vertx.createHttpClient(config);
  }

  public void configurationOfAnHttp1Client(Vertx vertx) {
    HttpClientConfig config = new HttpClientConfig()
      .setVersions(HttpVersion.HTTP_1_1)
      .setSsl(true)
      .setHttp1Config(new Http1ClientConfig()
        .setMaxInitialLineLength(1024));
  }

  public void configurationOfAnHttp2Client(Vertx vertx) {
    HttpClientConfig config = new HttpClientConfig()
      .setVersions(HttpVersion.HTTP_2)
      .setSsl(true)
      .setHttp2Config(new Http2ClientConfig()
        .setKeepAliveTimeout(Duration.ofSeconds(10)));
  }

  public void configurationOfAnHttp3Client(Vertx vertx) {
    HttpClientConfig config = new HttpClientConfig()
      .setVersions(HttpVersion.HTTP_3)
      .setSsl(true)
      .setHttp3Config(new Http3ClientConfig()
        .setKeepAliveTimeout(Duration.ofSeconds(10)));
  }

  public void configurationOfAHybridClient(Vertx vertx) {
    HttpClientConfig config = new HttpClientConfig()
      .setVersions(HttpVersion.HTTP_1_1, HttpVersion.HTTP_2, HttpVersion.HTTP_3)
      .setSsl(true)
      .setFollowAlternativeServices(true);

    ClientSSLOptions sslOptions = new ClientSSLOptions()
      .setKeyCertOptions(new JksOptions().setPath("/path/to/my/keystore"));

    HttpClientAgent client = vertx.createHttpClient(config, sslOptions);
  }

  public void clientRequestUnixDomainSocketServer(Vertx vertx) {
    HttpClient httpClient = vertx.createHttpClient();

    // Only available when running on JDK16+, or using a native transport
    SocketAddress addr = SocketAddress.domainSocketAddress("/var/tmp/myservice.sock");

    // Send request to the server
    httpClient.request(new RequestOptions()
        .setServer(addr)
        .setHost("localhost")
        .setPort(8080)
        .setURI("/"))
      .compose(request -> request.send().compose(HttpClientResponse::body))
      .onComplete(ar -> {
        if (ar.succeeded()) {
          // Process response
        } else {
          // Handle failure
        }
      });
  }

  public void configureOfClientPool(Vertx vertx) {
    PoolOptions options = new PoolOptions().setHttp1MaxSize(10);
    HttpClientAgent client = vertx.createHttpClient(options);
  }

  public void httpClientBuilder(Vertx vertx, HttpClientConfig config) {
    // Pretty much like vertx.createHttpClient(config)
    HttpClientAgent client = vertx
      .httpClientBuilder()
      .with(config)
      .build();
  }

  public void clientRequest(HttpClient client) {
    client
      .request(HttpMethod.GET, 8080, "myserver.mycompany.com", "/some-uri")
      .onSuccess(ar1 -> {
        // Connected to the server
      });
  }

  public void clientRequestDefaultHostAndPort(Vertx vertx) {

    // Set the default host
    HttpClientConfig config = new HttpClientConfig()
      .setDefaultHost("wibble.com");

    // Can also set default port if you want...
    HttpClientAgent client = vertx.createHttpClient(config);
    client
      .request(HttpMethod.GET, "/some-uri")
      .compose(request -> request.send())
      .onSuccess(response -> {
        System.out.println("Received response with status code " + response.statusCode());
      });
  }

  public void clientRequestHeaders(Vertx vertx) {

    HttpClientAgent client = vertx.createHttpClient();

    // Write some headers using the headers multi-map
    MultiMap headers = HttpHeaders.set("content-type", "application/json").set("other-header", "foo");

    client
      .request(HttpMethod.GET, "some-uri")
      .compose(request -> {
        request.headers().addAll(headers);
        return request.send();
      })
      .onSuccess(response -> {
        System.out.println("Received response with status code " + response.statusCode());
      });
  }

  public void clientRequestPutHeader(HttpClientRequest request) {

    // Write some headers using the putHeader method

    request.putHeader("content-type", "application/json")
      .putHeader("other-header", "foo");
  }

  public void clientRequestSend(HttpClient client) {
    client
      .request(HttpMethod.GET, 8080, "myserver.mycompany.com", "/some-uri")
      // Send the request
      .compose(request -> request.send())
      // And process the response
      .onSuccess(response -> {
        System.out.println("Received response with status code " + response.statusCode());
      });
  }

  public void clientRequestSendString(HttpClient client) {
    client
      .request(HttpMethod.GET, 8080, "myserver.mycompany.com", "/some-uri")
      // Send the request
      .compose(request -> request.send("Hello World"))
      // And process the response
      .onComplete(ar -> {
        if (ar.succeeded()) {
          HttpClientResponse response = ar.result();
          System.out.println("Received response with status code " + response.statusCode());
        } else {
          System.out.println("Something went wrong " + ar.cause().getMessage());
        }
      });
  }

  public void clientRequestSendBuffer(HttpClientRequest request) {

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

  public void clientRequestSendStream(HttpClientRequest request, ReadStream<Buffer> stream) {

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

  public void clientRequestWriteAndEnd(Vertx vertx, String body) {
    HttpClientAgent client = vertx.createHttpClient();

    client.request(HttpMethod.POST, "some-uri")
      .onSuccess(request -> {
        request
          .response()
          .onSuccess(response -> {
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

  public void clientRequestWrite(HttpClientRequest request) {

    // Write string encoded in UTF-8
    request.write("some data");

    // Write string encoded in specific encoding
    request.write("some other data", "UTF-16");

    // Write a buffer
    request.write(Buffer.buffer()
      .appendInt(123)
      .appendLong(245L)
    );
  }

  public void clientRequestEnd(HttpClientRequest request) {
    request.end();
  }

  public void clientRequestEndWithBuffer(HttpClientRequest request) {
    // End the request with a string
    request.end("some-data");

    // End it with a buffer
    Buffer buffer = Buffer.buffer().appendFloat(12.3f).appendInt(321);
    request.end(buffer);
  }

  public void clientRequestPiping(HttpClientRequest request, AsyncFile file) {

    request.setChunked(true);
    file.pipeTo(request);
  }

  public void clientRequestSetChunked(HttpClientRequest request) {

    request.setChunked(true);

    // Write some chunks
    for (int i = 0; i < 10; i++) {
      request.write("this-is-chunk-" + i);
    }

    request.end();
  }

  public void clientRequestSendForm(HttpClientRequest request) {
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

  public void clientRequestSendMultipart(HttpClientRequest request) {
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

  public void clientRequestSendMultipartWithFileUpload(HttpClientRequest request) {
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

  public void clientRequestDefaultSSL(Vertx vertx, ClientSSLOptions sslOptions) {
    HttpClientAgent client = vertx.createHttpClient(new HttpClientConfig()
      .setSsl(true), sslOptions);

    // Use the global default configuration with TLS enabled
    client
      .request(new RequestOptions()
        .setHost("localhost")
        .setPort(8080)
        .setURI("/"))
      .compose(request -> request.send())
      .onSuccess(response -> {
        System.out.println("Received response with status code " + response.statusCode());
      });
  }

  public void clientRequestOverrideSSL(Vertx vertx, ClientSSLOptions sslOptions) {
    HttpClientAgent client = vertx.createHttpClient(new HttpClientConfig(), sslOptions);

    // Override the default configuration and use TLS
    client
      .request(new RequestOptions()
        .setHost("localhost")
        .setPort(8080)
        .setURI("/")
        .setSsl(true))
      .compose(request -> request.send())
      .onSuccess(response -> {
        System.out.println("Received response with status code " + response.statusCode());
      });
  }

  public void clientRequestOverrideVersion(HttpClient client) {
    client
      .request(new RequestOptions()
        .setProtocolVersion(HttpVersion.HTTP_2)
        .setHost("localhost")
        .setPort(8080)
        .setURI("/"))
      .compose(request -> request.send())
      .onSuccess(response -> {
        System.out.println("Received response with status code " + response.statusCode());
      });
  }

  public void clientRequestHttp3(HttpClient client) {
    client
      .request(new RequestOptions()
        .setProtocolVersion(HttpVersion.HTTP_3)
        .setAbsoluteURI("https://google.com"))
      .compose(request -> request.send())
      .onSuccess(response -> {
        System.out.println("Received response with HTTP version " + response.request().version());
      });
  }

  public void configurationOfHttpClientAltSvc(Vertx vertx, ClientSSLOptions sslOptions) {
    HttpClientConfig config = new HttpClientConfig()
      .setFollowAlternativeServices(true)
      .setVersions(HttpVersion.HTTP_1_1, HttpVersion.HTTP_3)
      .setSsl(true);
    HttpClientAgent client = vertx.createHttpClient(config, sslOptions);
  }

  public void clientRequestAltSvc(HttpClient client) {
    client
      .request(new RequestOptions().setAbsoluteURI("https://google.com"))
      .compose(request -> request.send())
      .onSuccess(response -> {
        System.out.println("Received response with HTTP version " + response.request().version());
      });
  }

  public void clientRequestIdleTimeout(HttpClient client, int port, String host, String uri, int timeoutMS) {
    Future<Buffer> fut = client
      .request(new RequestOptions()
        .setHost(host)
        .setPort(port)
        .setURI(uri)
        .setIdleTimeout(timeoutMS))
      .compose(request -> request
        .send()
        .compose(HttpClientResponse::body));
  }

  public void clientRequestTimeout(HttpClient client, int port, String host, String uri, int timeoutMS) {
    Future<Buffer> fut = client
      .request(new RequestOptions()
        .setHost(host)
        .setPort(port)
        .setURI(uri)
        .setTimeout(timeoutMS))
      .compose(request -> request
        .send()
        .compose(HttpClientResponse::body));
  }

  public void clientRequestWriteCustomFrame(HttpClientRequest request) {

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

  public void clientRequestHandleResponse(HttpClientRequest request) {

    // Send the request
    request
      .send()
      .onSuccess(response -> {

        // the status code - e.g. 200 or 404
        System.out.println("Status code is " + response.statusCode());

        // the status message e.g. "OK" or "Not Found".
        System.out.println("Status message is " + response.statusMessage());
      });
  }

  public void clientResponseHeaders(HttpClientResponse response) {

    String contentType = response.headers().get("content-type");
    String contentLength = response.headers().get("content-lengh");
  }

  public void clientResponseHandleBuffer(HttpClient client) {

    client
      .request(HttpMethod.GET, "some-uri")
      .compose(request -> request.send())
      .onSuccess(response -> {
        response.handler(buffer -> {
          System.out.println("Received a part of the response body: " + buffer);
        });
      });
  }

  public void clientResponseChunkAggregation(HttpClientRequest request) {

    request
      .send()
      .onSuccess(response -> {

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

  public void clientResponseBody(HttpClientRequest request) {

    request
      .send()
      .compose(response -> response.body())
      .onSuccess(body -> {

        // Now all the body has been read
        System.out.println("Total response body length is " + body.length());
      });
  }

  private interface HttpClient2 {
    Future<HttpClientResponse> get(String requestURI);
  }

  public void clientRequestCompositionFromNonVertxThread(HttpClient2 client) throws Exception {
    Future<HttpClientResponse> get = client.get("some-uri");

    // Assuming we have a client that returns a future response
    // assuming this is *not* on the event-loop
    // introduce a potential data race for the sake of this example
    Thread.sleep(100);

    get.onSuccess(response -> {

      // Response events might have happened already
      response
        .body()
        .onComplete(ar -> {

        });
    });
  }

  public void clientRequestCompositionConfineThread(HttpClient client) throws Exception {

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
    future.onComplete(ar -> {
      if (ar.succeeded()) {
        System.out.println("Received json result " + ar.result());
      } else {
        System.out.println("Something went wrong " + ar.cause().getMessage());
      }
    });
  }

  public void clientRequestCompositionWithExpectation(HttpClient client) throws Exception {

    Future<JsonObject> future = client
      .request(HttpMethod.GET, "some-uri")
      .compose(request -> request
        .send()
        .expecting(HttpResponseExpectation.SC_OK.and(HttpResponseExpectation.JSON))
        .compose(response -> response
          .body()
          .map(buffer -> buffer.toJsonObject())));

    // Listen to the composed final json result
    future.onComplete(ar -> {
      if (ar.succeeded()) {
        System.out.println("Received json result " + ar.result());
      } else {
        System.out.println("Something went wrong " + ar.cause().getMessage());
      }
    });
  }

  public void clientRequestCompositionStreaming(HttpClient client, FileSystem fileSystem) throws Exception {

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

  public void clientResponseExpectingPredefinedExpectations(HttpClient client, RequestOptions options) {
    Future<Buffer> fut = client
      .request(options)
      .compose(request -> request
        .send()
        .expecting(HttpResponseExpectation.SC_SUCCESS)
        .compose(response -> response.body()));
  }

  public void clientResponseExpectingCustomExpectation(HttpClient client) {

    // Check CORS header allowing to do POST
    HttpResponseExpectation methodsExpectation =
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
        .expecting(methodsExpectation))
      .onComplete(res -> {
        if (res.succeeded()) {
          // Process the POST request now
        } else {
          System.out.println("Something went wrong " + res.cause().getMessage());
        }
      });
  }

  public void clientResponseExpectingStatus(HttpClient client, RequestOptions options) {
    client
      .request(options)
      .compose(request -> request
        .send()
        .expecting(HttpResponseExpectation.status(200, 202)))
      .onSuccess(res -> {
        // ....
      });
  }

  public void clientResponseExpectingContentType(HttpClient client, RequestOptions options) {
    client
      .request(options)
      .compose(request -> request
        .send()
        .expecting(HttpResponseExpectation.contentType("some/content-type")))
      .onSuccess(res -> {
        // ....
      });
  }

  public void clientResponseCustomErrorExpectation() {
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

  public void clientRequestFollowRedirects(HttpClient client) {
    client
      .request(HttpMethod.GET, "some-uri")
      .compose(request -> request
        .setFollowRedirects(true)
        .send())
      .onSuccess(response -> {
        System.out.println("Received response with status code " + response.statusCode());
      });
  }

  public void configurationOfAnHttpClientRedirects(Vertx vertx) {

    HttpClientAgent client = vertx.createHttpClient(
      new HttpClientConfig()
        .setMaxRedirects(32));

    client
      .request(HttpMethod.GET, "some-uri")
      .compose(request -> request.setFollowRedirects(true).send())
      .onSuccess(response -> {
        System.out.println("Received response with status code " + response.statusCode());
      });
  }

  public void configurationOfAnHttpClientRedirectHandler(Vertx vertx) {
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

  private String resolveURI(String base, String uriRef) {
    throw new UnsupportedOperationException();
  }

  public void clientRequestTunnel(HttpClient client) {

    client.request(HttpMethod.CONNECT, "some-uri")
      // Connect to the server
      .compose(request -> request
        .connect()
        .expecting(HttpResponseExpectation.SC_OK))
      .onSuccess(response -> {
        // Tunnel created, raw buffers are transmitted on the wire
        NetSocket socket = response.netSocket();
      });
  }

  public void clientResponseHandleCustomFrame(HttpClientResponse response) {
    response.customFrameHandler(frame -> {

      System.out.println("Received a frame type=" + frame.type() +
        " payload" + frame.payload().toString());
    });
  }

  public static void configurationOfAnHttpClientRoundRobinLoadBalancing(Vertx vertx) {
    HttpClientAgent client = vertx
      .httpClientBuilder()
      .withLoadBalancer(LoadBalancer.ROUND_ROBIN)
      .build();
  }

  public static void configurationOfAnHttpClientConsistentHashingLoadBalancing(Vertx vertx, int servicePort) {
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

  public static void configurationOfConsistentHashing() {
    // Use 10 virtual nodes per server and Power of two choices policy in the absence of a routing key
    LoadBalancer loadBalancer = LoadBalancer.consistentHashing(10, LoadBalancer.POWER_OF_TWO_CHOICES);
  }

  private static int indexOfEndpoint(List<? extends ServerEndpoint> endpoints) {
    return 0;
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

  public void configurationOfClientPoolMultiplexedProtocols(Vertx vertx) {

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

  public static void clientConnection(HttpClientAgent client) {
    HttpConnectOptions connectOptions = new HttpConnectOptions()
      .setHost("example.com")
      .setPort(80);

    Future<HttpClientConnection> fut = client.connect(connectOptions);
  }

  public static void clientConnectionRequest(HttpClientConnection connection) {
    connection
      .request()
      .onSuccess(request -> {
        request.setMethod(HttpMethod.GET);
        request.setURI("/some-uri");
        Future<HttpClientResponse> response = request.send();
      });
  }

  public void expectContinueClientHandleContinue(HttpClient client) {

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

  public void expectContinueServerWriteContinue(HttpServer httpServer) {

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

  public void expectContinueServerRejectContinue(HttpServer httpServer) {

    httpServer.requestHandler(request -> {
      if (request.getHeader("Expect").equalsIgnoreCase("100-Continue")) {

        //
        boolean reject = true;
        if (reject) {

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

  public void serverRequestConnection(HttpServerRequest request) {
    HttpConnection connection = request.connection();
  }

  public void serverConnectHandler(Vertx vertx, HttpServerConfig httpConfig) {
    HttpServer server = vertx.httpServerBuilder()
      .with(httpConfig)
      .withConnectHandler(connection -> {
        System.out.println("A client connected");
      })
      .build();
  }

  public void clientRequestConnection(HttpClientRequest request) {
    HttpConnection connection = request.connection();
  }

  public void clientConnectHandler(Vertx vertx, HttpClientConfig config) {
    vertx
      .httpClientBuilder()
      .with(config)
      .withConnectHandler(connection -> {
        System.out.println("Connected to the server");
      })
      .build();
  }

  public void connectionSettings(HttpConnection connection) {
    HttpSettings settings = connection.remoteSettings();

    // HTTP/2
    Integer http2MaxFrameSize = settings.get(Http2Settings.MAX_FRAME_SIZE);

    // HTTP/3
    Long http3MaxFieldSectionSize = settings.get(Http3Settings.MAX_FIELD_SECTION_SIZE);
  }

  public void serverShutdown(HttpServer server) {
    server
      .shutdown()
      .onSuccess(res -> {
        System.out.println("Server is now closed");
      });
  }

  public void connectionHandleShutdown(HttpServer server) {
    server.connectionHandler(conn -> {
      conn.shutdownHandler(v -> {
        // Perform clean-up
      });
    });
  }

  public void serverShutdownWithAmountOfTime(HttpServer server) {
    server
      .shutdown(60, TimeUnit.SECONDS)
      .onSuccess(res -> {
        System.out.println("Server is now closed");
      });
  }

  public static void clientSharing1(Vertx vertx) {
    HttpClientConfig config = new HttpClientConfig()
      .setShared(true);

    HttpClientAgent client = vertx.createHttpClient(config);
    vertx.deployVerticle(() -> new AbstractVerticle() {
      @Override
      public void start() throws Exception {
        // Use the client
      }
    }, new DeploymentOptions().setInstances(4));
  }

  public static void clientSharing2(Vertx vertx) {
    vertx.deployVerticle(() -> new AbstractVerticle() {
      HttpClientAgent client;
      @Override
      public void start() {
        // Get or create a shared client
        // this actually creates a lease to the client
        // when the verticle is undeployed, the lease will be released automaticaly
        client = vertx.createHttpClient(new HttpClientConfig()
          .setShared(true)
          .setName("my-client"));
      }
    }, new DeploymentOptions().setInstances(4));
  }

  public static void clientSharing3(Vertx vertx) {
    vertx.deployVerticle(() -> new AbstractVerticle() {
      HttpClientAgent client;
      @Override
      public void start() {
        // The client creates and use two event-loops for 4 instances
        client = vertx.createHttpClient(
          new HttpClientConfig()
            .setShared(true)
            .setName("my-client"),
          new PoolOptions().setEventLoopSize(2));
      }
    }, new DeploymentOptions().setInstances(4));
  }

  public void serverSharing(Vertx vertx) {
    vertx.createHttpServer().requestHandler(request -> {
      request.response().end("Hello from server " + this);
    }).listen(8080);
  }

  public void serverSharingRandomPort(Vertx vertx) {
    vertx.createHttpServer().requestHandler(request -> {
      request.response().end("Hello from server " + this);
    }).listen(-1);
  }

  public void configurationOfAnHttpServerLogging(Vertx vertx) {

    HttpServerConfig config = new HttpServerConfig()
      .setLogConfig(new LogConfig()
        .setEnabled(true));

    HttpServer server = vertx.createHttpServer(config);
  }

  public void configurationOfAnHttpClientLogging(Vertx vertx) {
    HttpClientConfig config = new HttpClientConfig()
      .setLogConfig(new LogConfig()
        .setEnabled(true));

    HttpClientAgent client = vertx.createHttpClient(config);
  }

  public void configurationOfAnHttpClientHaProxy(Vertx vertx) {

    HttpServerConfig config = new HttpServerConfig();
    config
      .getTcpConfig()
      .setUseProxyProtocol(true);

    HttpServer server = vertx.createHttpServer(config);
    server.requestHandler(request -> {
      // Print the actual client address provided by the HA proxy protocol instead of the proxy address
      System.out.println(request.remoteAddress());

      // Print the address of the proxy
      System.out.println(request.localAddress());
    });
  }

  public void connectionWritePing(HttpConnection connection) {
    Buffer data = Buffer.buffer();
    for (byte i = 0;i < 8;i++) {
      data.appendByte(i);
    }
    connection
      .ping(data)
      .onSuccess(pong -> System.out.println("Remote side replied"));
  }

  public void connectionHandlePing(HttpConnection connection) {
    connection.pingHandler(ping -> {
      System.out.println("Got pinged by remote side");
    });
  }

  public void connectionUpdateSettings(HttpConnection connection) {
    connection.updateSettings(new Http2Settings().setMaxConcurrentStreams(100));
  }

  public void connectionUpdateSettingsWithAck(HttpConnection connection) {
    connection
      .updateSettings(new Http2Settings().setMaxConcurrentStreams(100))
      .onSuccess(v -> System.out.println("The settings update has been acknowledged "));
  }

  public void connectionHandleSettings(HttpConnection connection) {
    connection.remoteSettingsHandler(settings -> {
      System.out.println("Received new settings");
    });
  }

  public void serverWebSocketHandler(HttpServer server) {

    server.webSocketHandler(webSocket -> {
      System.out.println("Connected!");
    });
  }

  public void serverWebSocketHandshakeHandler(HttpServer server) {
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

  public void serverWebSocketUpgrade(HttpServer server) {

    server.requestHandler(request -> {
      switch (request.path()) {
        case "/myapi":

          Future<ServerWebSocket> fut = request.toWebSocket();
          fut.onSuccess(ws -> {
            // Do something
          });
          break;

        default:

          // Reject
          request.response().setStatusCode(400).end();
          break;
      }
    });
  }

  public void clientWebSocketConnect(Vertx vertx) {
    WebSocketClient client = vertx.createWebSocketClient();

    client
      .connect(80, "example.com", "/some-uri")
      .onSuccess(ws -> {
        ws.textMessageHandler(msg -> {
          // Handle msg
        });
        System.out.println("Connected!");
      });
  }

  public void clientWebSocketConfigurationAndConnect(Vertx vertx) {
    WebSocketClient client = vertx.createWebSocketClient();

    client
      .webSocket()
      .textMessageHandler(msg -> {
        // Handle msg
      })
      .connect(80, "example.com", "/some-uri")
      .onSuccess(ws -> {
        System.out.println("Connected!");
      });
  }

  public void clientWebSocketDisableOriginHeader(WebSocketClient client, String host, int port, String requestUri) {
    WebSocketConnectOptions options = new WebSocketConnectOptions()
      .setHost(host)
      .setPort(port)
      .setURI(requestUri)
      .setAllowOriginHeader(false);
    client
      .connect(options)
      .onSuccess(ws -> {
        System.out.println("Connected!");
      });
  }

  public void clientWebSocketSetOriginHeader(WebSocketClient client, String host, int port, String requestUri, String origin) {
    WebSocketConnectOptions options = new WebSocketConnectOptions()
      .setHost(host)
      .setPort(port)
      .setURI(requestUri)
      .addHeader(HttpHeaders.ORIGIN, origin);
    client
      .connect(options)
      .onSuccess(ws -> {
        System.out.println("Connected!");
      });
  }

  public void webSocketWriteMessage(WebSocket webSocket) {
    // Write a simple binary message
    Buffer buffer = Buffer.buffer().appendInt(123).appendFloat(1.23f);
    webSocket.writeBinaryMessage(buffer);

    // Write a simple text message
    String message = "hello";
    webSocket.writeTextMessage(message);
  }

  public void webSocketWriteFrame(WebSocket webSocket, Buffer buffer1, Buffer buffer2, Buffer buffer3) {

    WebSocketFrame frame1 = WebSocketFrame.binaryFrame(buffer1, false);
    webSocket.writeFrame(frame1);

    WebSocketFrame frame2 = WebSocketFrame.continuationFrame(buffer2, false);
    webSocket.writeFrame(frame2);

    // Write the final frame
    WebSocketFrame frame3 = WebSocketFrame.continuationFrame(buffer2, true);
    webSocket.writeFrame(frame3);
  }

  public void webSocketWriteFinalFrame(WebSocket webSocket) {

    // Send a WebSocket message consisting of a single final text frame:

    webSocket.writeFinalTextFrame("Geronimo!");

    // Send a WebSocket message consisting of a single final binary frame:

    Buffer buff = Buffer.buffer().appendInt(12).appendString("foo");

    webSocket.writeFinalBinaryFrame(buff);
  }

  public void webSocketHandleFrame(WebSocket webSocket) {

    webSocket.frameHandler(frame -> {
      System.out.println("Received a frame of size!");
    });
  }

  public void configurationOfAnHttpClientHTTPProxy(Vertx vertx) {

    HttpClientConfig config = new HttpClientConfig();
    config.getTcpConfig()
      .setProxyOptions(new ProxyOptions()
        .setType(ProxyType.HTTP)
        .setHost("localhost")
        .setPort(3128)
        .setUsername("username")
        .setPassword("secret"));
    HttpClientAgent client = vertx.createHttpClient(config);

  }

  public void configurationOfAnHttpClientSOCKS5Proxy(Vertx vertx) {
    HttpClientConfig config = new HttpClientConfig();
    config.getTcpConfig()
      .setProxyOptions(new ProxyOptions()
        .setType(ProxyType.SOCKS5)
        .setHost("localhost")
        .setPort(1080)
        .setUsername("username")
        .setPassword("secret"));
    HttpClientAgent client = vertx.createHttpClient(config);

  }

  public void clientRequestOverrideProxy(HttpClient client, ProxyOptions proxyOptions) {

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

  public void configurationOfAnHttpClientNonProxyHosts(Vertx vertx) {
    HttpClientConfig config = new HttpClientConfig();
    config.getTcpConfig()
      .setProxyOptions(new ProxyOptions()
        .setType(ProxyType.SOCKS5)
        .setHost("localhost").setPort(1080)
        .setUsername("username")
        .setPassword("secret"))
      .addNonProxyHost("*.foo.com")
      .addNonProxyHost("localhost");
    HttpClientAgent client = vertx.createHttpClient(config);
  }

  public void configurationOfAnHttpClientProxyConnectTimeout(ProxyOptions proxyOptions) {
    proxyOptions.setConnectTimeout(Duration.ofSeconds(60));
  }

  public void clientRequestFtpScheme(Vertx vertx) {

    HttpClientConfig config = new HttpClientConfig();
    config.getTcpConfig()
      .setProxyOptions(new ProxyOptions()
        .setType(ProxyType.HTTP));
    HttpClientAgent client = vertx.createHttpClient(config);
    client
      .request(HttpMethod.GET, "ftp://ftp.gnu.org/gnu/")
      .compose(request -> request.send())
      .onSuccess(response -> {
        System.out.println("Received response with status code " + response.statusCode());
      });
  }
}
