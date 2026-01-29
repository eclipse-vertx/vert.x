package io.vertx.tests.http;

import io.vertx.core.Closeable;
import io.vertx.core.Completable;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpResponseExpectation;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.impl.tcp.TcpHttpServer;
import io.vertx.test.http.HttpTestBase;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class HttpServerCloseSequenceTest extends HttpTestBase {

  @Test
  public void testHttpServerImplCloseSequence() throws Exception {

    TestHandler testHandler = new TestHandler();

    HttpServer customServer = vertx.createHttpServer(createBaseServerOptions())
      .requestHandler(testHandler);

    server.requestHandler(testHandler);

    startServer(testAddress, customServer);

    client.request(requestOptions)
      .compose(req -> req
        .send()
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(HttpClientResponse::body))
      .await();

    assertFalse(testHandler.isClosed());
    assertFalse(testHandler.isClosing());

    customServer
      .shutdown(10, TimeUnit.SECONDS)
      .onComplete(onSuccess(v2 -> {
        assertTrue(((TcpHttpServer) customServer).isClosed());
        assertTrue("TestHandler should be closed during shutdown", testHandler.isClosed());
        testComplete();
      }));

    vertx.setTimer(15, v -> {
      assertFalse(testHandler.isClosed());
      assertTrue(testHandler.isClosing());
      testHandler.close();
    });

    await();
  }

  private static class TestHandler implements Handler<HttpServerRequest>, Closeable {

    private volatile boolean closed = false;
    private volatile Completable<Void> closing;

    @Override
    public void handle(HttpServerRequest event) {
      event.response().end("OK");
    }

    void close() {
      closed = true;
      closing.succeed();
    }

    @Override
    public void close(Completable<Void> completion) {
      closing = completion;
    }

    public boolean isClosed() {
      return closed;
    }

    public boolean isClosing() {
      return closing != null;
    }
  }
}
