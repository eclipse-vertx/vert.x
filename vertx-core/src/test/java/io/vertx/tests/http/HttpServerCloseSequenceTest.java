package io.vertx.tests.http;

import io.vertx.core.Closeable;
import io.vertx.core.Completable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpResponseExpectation;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.internal.http.HttpServerInternal;
import io.vertx.test.http.HttpTestBase2;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static io.vertx.test.core.TestUtils.assertWaitUntil;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HttpServerCloseSequenceTest extends HttpTestBase2 {

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

    Future<Void> fut = customServer
      .shutdown(10, TimeUnit.SECONDS);

    assertWaitUntil(testHandler::isClosing);
    assertFalse(testHandler.isClosed());

    testHandler.close();
    fut.await();

    assertTrue(((HttpServerInternal) customServer).isClosed());
    assertTrue("TestHandler should be closed during shutdown", testHandler.isClosed());
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
