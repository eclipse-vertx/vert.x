package io.vertx.tests.http;

import io.vertx.core.Closeable;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.HttpServerImpl;
import io.vertx.test.http.HttpTestBase;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class HttpServerCloseSequenceTest extends HttpTestBase {

  @Test
  public void testHttpServerImplCloseSequence() throws Exception {
    TestHandler testHandler = new TestHandler();
    TestWebSocketHandler testWebSocketHandler = new TestWebSocketHandler();

    HttpServer customServer = vertx.createHttpServer(new HttpServerOptions().setPort(0))
      .requestHandler(testHandler)
      .webSocketHandler(testWebSocketHandler);

    customServer.listen().onComplete(onSuccess(v -> client.request(HttpMethod.GET, customServer.actualPort(), "localhost", "/")
      .compose(req -> req.send().compose(resp -> {
        assertFalse(testHandler.isClosed());
        assertFalse(testWebSocketHandler.isClosed());

        long startTime = System.currentTimeMillis();
        customServer.shutdown(1, TimeUnit.SECONDS).onComplete(onSuccess(v2 -> {
          long endTime = System.currentTimeMillis();
          assertTrue(((HttpServerImpl) customServer).isClosed());
          assertTrue(endTime - startTime >= 0);
          assertTrue("TestHandler should be closed during shutdown", testHandler.isClosed());
          assertTrue("TestWebSocketHandler should be closed during shutdown", testWebSocketHandler.isClosed());
          testComplete();
        }));

        return resp.body();
      })).onFailure(this::fail)));

    await();
  }

  private class TestHandler implements Handler<HttpServerRequest>, Closeable {
    private boolean closed = false;

    @Override
    public void handle(HttpServerRequest event) {
      event.response().end("OK");
    }

    @Override
    public void close(Promise<Void> completion) {
      closed = true;
      completion.complete();
    }

    public boolean isClosed() {
      return closed;
    }
  }

  private class TestWebSocketHandler implements Handler<ServerWebSocket>, Closeable {
    private boolean closed = false;

    @Override
    public void handle(ServerWebSocket ws) {
      ws.closeHandler(v -> {
        // Connection closed
      });

      vertx.setTimer(100, id -> ws.writeTextMessage("Hello"));
    }

    @Override
    public void close(Promise<Void> completion) {
      closed = true;
      completion.complete();
    }

    public boolean isClosed() {
      return closed;
    }
  }
}
