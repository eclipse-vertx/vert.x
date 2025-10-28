package io.vertx.tests.http.http3;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.test.core.LinuxOrOsx;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.tls.Cert;
import io.vertx.test.tls.Trust;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(LinuxOrOsx.class)
public class Http3ClientTest extends VertxTestBase {

  private HttpServer server;
  private Http3ClientOptions clientOptions;
  private HttpClientAgent client;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    Http3ServerOptions serverOptions = new Http3ServerOptions();
    serverOptions.getSslOptions().setKeyCertOptions(Cert.SERVER_JKS.get());
//    serverOptions.setClientAddressValidation(QuicClientAddressValidation.NONE);
//    serverOptions.setKeyLogFile("/Users/julien/keylogfile.txt");
    clientOptions = new Http3ClientOptions();
    clientOptions.getSslOptions().setTrustOptions(Trust.SERVER_JKS.get());
    clientOptions.getSslOptions().setHostnameVerificationAlgorithm("");
    server = vertx.createHttpServer(serverOptions);
    client = vertx.createHttpClient(clientOptions);
  }

  @Override
  protected void tearDown() throws Exception {
    server.close().await();
    client.close().await();
    super.tearDown();
  }

  @Test
  public void testGet() {
    server.requestHandler(req -> {
      req.response().end("Hello World");
    });
    server.listen(8443, "localhost").await();

    HttpClientConnection connection = client.connect(new HttpConnectOptions()
      .setHost("localhost")
      .setPort(8443)).await();

    Buffer response = connection.request(HttpMethod.GET, 8443, "localhost", "/")
      .compose(request -> request
        .send()
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(HttpClientResponse::body))
      .await();

    assertEquals("Hello World", response.toString());
  }

  @Test
  public void testPost() {
    server.requestHandler(req -> {
      req.bodyHandler(buff -> {
        req.response().end(buff);
      });
    });
    server.listen(8443, "localhost").await();

    HttpClientConnection connection = client.connect(new HttpConnectOptions()
      .setHost("localhost")
      .setPort(8443)).await();

    Buffer response = connection.request(HttpMethod.POST, 8443, "localhost", "/")
      .compose(request -> request
        .setChunked(true)
        .send("Hello World"))
      .expecting(HttpResponseExpectation.SC_OK)
      .compose(HttpClientResponse::body)
      .await();

    assertEquals("Hello World", response.toString());
  }

  @Test
  public void testResponseTrailers() throws Exception {
    server.requestHandler(req -> {
      req.response()
        .putTrailer("trailer_key", "trailer_value")
        .end();
    });
    server.listen(8443, "localhost").await();

    MultiMap trailers = client.request(HttpMethod.GET, 8443, "localhost", "/")
      .compose(request -> request
        .send()
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(resp -> resp.end().map(v -> resp.trailers())))
      .await();

    assertEquals(1, trailers.size());
    assertEquals("trailer_value", trailers.get("trailer_key"));
  }

  @Test
  public void testClientShutdown() throws Exception{
    testClientConnectionShutdown(false);
  }

  @Test
  public void testClientConnectionShutdown() throws Exception{
    testClientConnectionShutdown(true);
  }

  private void testClientConnectionShutdown(boolean closeConnection) throws Exception{

    disableThreadChecks();

    AtomicReference<HttpServerRequest> requestRef = new AtomicReference<>();

    server.requestHandler(request -> {
      requestRef.compareAndSet(null, request);
//      request.exceptionHandler(err -> {
//      });
    });

    server.listen(8443, "localhost").await();

    HttpClientRequest request = client.request(HttpMethod.GET, 8443, "localhost", "/").await();
    Future<HttpClientResponse> fut = request.send();
    assertWaitUntil(() -> requestRef.get() != null);

    HttpConnection connection = request.connection();
    connection.shutdownHandler(v -> testComplete());

    long now = System.currentTimeMillis();
    Future<Void> future;
    if (closeConnection) {
      future = connection.shutdown(10, TimeUnit.SECONDS);
    } else {
      future = client.shutdown(10, TimeUnit.SECONDS);
    }
    future = future
      .andThen(onSuccess2(v -> {
        assertTrue(System.currentTimeMillis() - now >= 1000);
      }));
    vertx.setTimer(1000, id -> requestRef.get().response().end("Hello World"));

    fut.await();
    future.await();

    await();
  }

  @Test
  public void testClientConnectionShutdownTimeout() throws Exception {

    disableThreadChecks();

    AtomicReference<HttpServerRequest> requestRef = new AtomicReference<>();

    server.requestHandler(request -> {
      requestRef.compareAndSet(null, request);
      request
        .response()
        .exceptionHandler(err -> {
        assertSame(HttpClosedException.class, err.getClass());
        testComplete();
      });
    });

    server.listen(8443, "localhost").await();

    HttpClientRequest request = client.request(HttpMethod.GET, 8443, "localhost", "/").await();
    Future<HttpClientResponse> fut = request.send();
    assertWaitUntil(() -> requestRef.get() != null);

    request.connection().shutdown(1, TimeUnit.SECONDS).await();

    try {
      fut.await();
    } catch (Exception e) {
      assertSame(HttpClosedException.class, e.getClass());
    }

    await();
  }

  @Test
  public void testServerConnectionGoAway() {

    CompletableFuture<Void> shutdown = new CompletableFuture<>();

    server.requestHandler(req -> {
      Future<Void> fut = req.connection().shutdown();
      shutdown.whenComplete((s,  err) -> {
        long now = System.currentTimeMillis();
        fut.onComplete(onSuccess2(v -> {
          assertTrue(System.currentTimeMillis() - now >= 1000);
          testComplete();
        }));
        vertx.setTimer(1000, id -> {
          req.response().end("done");
        });
      });
    });

    server.listen(8443, "localhost").await();

    HttpClientConnection connection = client.connect(new HttpConnectOptions()
      .setHost("localhost")
      .setPort(8443)).await();

    connection.shutdownHandler(v -> {
      shutdown.complete(null);
    });

    Buffer response = connection.request(HttpMethod.GET, 8443, "localhost", "/")
      .compose(request -> request
        .send()
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(HttpClientResponse::body))
      .await();

    assertEquals("done", response.toString());

    await();
  }

  @Test
  public void testClientConnectionGoAway() throws Exception {

    CompletableFuture<Void> shutdown = new CompletableFuture<>();

    server.requestHandler(req -> {
      shutdown.complete(null);
      req.connection().shutdownHandler(v -> {
        vertx.setTimer(1000, id -> {
          req.response().end();
        });
      });
//      Future<Void> fut = req.connection().shutdown();
//      shutdown.whenComplete((s,  err) -> {
//        long now = System.currentTimeMillis();
//        fut.onComplete(onSuccess2(v -> {
//          assertTrue(System.currentTimeMillis() - now >= 1000);
//          testComplete();
//        }));
//        vertx.setTimer(1000, id -> {
//          req.response().end("done");
//        });
//      });
    });

    server.listen(8443, "localhost").await();

    HttpClientConnection connection = client.connect(new HttpConnectOptions()
      .setHost("localhost")
      .setPort(8443)).await();

    HttpClientRequest request = connection.request(HttpMethod.GET, 8443, "localhost", "/")
      .await();

    request.end().await();

    shutdown.get(10, TimeUnit.SECONDS.SECONDS);

    connection.shutdown(10, TimeUnit.SECONDS).await();

//    await();
  }

  @Test
  public void testClientRequestResetUponClientPartialRequestResetByServer() throws Exception {

    server.requestHandler(req -> {
      req.handler(buff -> {
        req.response().reset();
      });
      req.exceptionHandler(err -> {
        if (err instanceof StreamResetException) {
          assertEquals(StreamResetException.class, err.getClass());
          complete();
        }
      });
    });

    server.listen(8443, "localhost").await();

    HttpClientConnection connection = client.connect(new HttpConnectOptions()
      .setHost("localhost")
      .setPort(8443)).await();

    HttpClientRequest request = connection
      .request(HttpMethod.GET, 8443, "localhost", "/")
      .await();

    request.setChunked(true).write("chunk").await();

    try {
      request.response().await();
      fail();
    } catch (StreamResetException expected) {
    }

    await();
  }

  @Test
  public void testServerResponseReset() throws Exception {

    CompletableFuture<Void> continuation = new CompletableFuture<>();

    server.requestHandler(req -> {
      req.endHandler(buff -> {
        HttpServerResponse response = req.response();
        continuation.whenComplete((v,  err) -> {
          response.reset();
        });
        response
          .setChunked(true)
          .write("chunk");
      });
    });

    server.listen(8443, "localhost").await();

    HttpClientConnection connection = client.connect(new HttpConnectOptions()
      .setHost("localhost")
      .setPort(8443)).await();

    HttpClientRequest request = connection
      .request(HttpMethod.GET, 8443, "localhost", "/")
      .await();

    request.end().await();

    HttpClientResponse response = request.response().await();
    Future<Buffer> body = response.body();

    continuation.complete(null);
    try {
      body.await();
      fail();
    } catch (StreamResetException expected) {
    }
  }

  @Test
  public void testConnectionClose() throws Exception {
    waitFor(2);
    server.requestHandler(req -> {
      req.response().setChunked(true).writeHead();
    });
    server.listen(8443, "localhost").await();
    HttpClientConnection connection = client.connect(new HttpConnectOptions()
      .setHost("localhost")
      .setPort(8443)).await();
    HttpClientRequest request = connection
      .request(HttpMethod.GET, 8443, "localhost", "/")
      .await();
    request.exceptionHandler(err -> {
      complete();
    });
    request.setChunked(true).writeHead().await();
    HttpClientResponse response = request.response().await();
    response.exceptionHandler(err -> {
      complete();
    });
    connection.close().await();
    await();
  }
}
