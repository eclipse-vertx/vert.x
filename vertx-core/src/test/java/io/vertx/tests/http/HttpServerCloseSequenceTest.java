package io.vertx.tests.http;

import io.vertx.core.Closeable;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.HttpServerImpl;
import io.vertx.test.http.HttpTestBase;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class HttpServerCloseSequenceTest extends HttpTestBase {

  private WebSocketClient wsClient;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    WebSocketClientOptions options = new WebSocketClientOptions();
    wsClient = vertx.createWebSocketClient(options);
  }

  @Override
  public void tearDown() throws Exception {
    if (wsClient != null) {
      wsClient.close();
    }
    super.tearDown();
  }

  @Test
  public void testHttpServerImplCloseSequence() throws Exception {
    TestHandler testHandler = new TestHandler();
    TestWebSocketHandler testWebSocketHandler = new TestWebSocketHandler(5, 100);

    HttpServer customServer = vertx.createHttpServer(new HttpServerOptions().setPort(0))
      .requestHandler(testHandler)
      .webSocketHandler(testWebSocketHandler);

    startServer(customServer);

    wsClient.connect(customServer.actualPort(), "localhost", "/")
      .onComplete(onSuccess(ws -> {
        ws.handler(buffer -> {
          String message = buffer.toString();
          if (message.equals("Hello")) {
            ws.writeTextMessage("Goodbye");
          }
        });

        client.request(HttpMethod.GET, customServer.actualPort(), "localhost", "/")
          .compose(req -> req.send().compose(resp -> {
            assertFalse(testHandler.isClosed());
            assertFalse(testWebSocketHandler.isClosed());
            customServer.shutdown(1, TimeUnit.SECONDS).onComplete(onSuccess(v2 -> {
              assertTrue(((HttpServerImpl) customServer).isClosed());
              assertTrue("TestHandler should be closed during shutdown", testHandler.isClosed());
              assertTrue("TestWebSocketHandler should be closed during shutdown", testWebSocketHandler.isClosed());

              assertTrue("Counter should reach target", testWebSocketHandler.getCounter() >= 5);

              testComplete();
            }));

            return resp.body();
          })).onFailure(this::fail);
      })).onFailure(this::fail);

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

    private final AtomicInteger counter = new AtomicInteger(0);
    private final int targetCount;
    private final long checkIntervalMs;

    private boolean closed = false;

    public TestWebSocketHandler(int targetCount, long checkIntervalMs) {
      this.targetCount = targetCount;
      this.checkIntervalMs = checkIntervalMs;
    }

    @Override
    public void handle(ServerWebSocket ws) {
      ws.textMessageHandler(msg -> {
        if(msg.equals("Goodbye")) {
          int current = counter.incrementAndGet();
          if (current < targetCount) {
            vertx.setTimer(50, id -> ws.writeTextMessage("Hello"));
          }
        }
      });

      vertx.setTimer(100, id -> ws.writeTextMessage("Hello"));
    }

    @Override
    public void close(Promise<Void> completion) {
      if (counter.get() >= targetCount) {
        closed = true;
        completion.complete();
        return;
      }

      Handler<Long> checkCounter = new Handler<>() {
        @Override
        public void handle(Long timerId) {
          if (counter.get() >= targetCount) {
            closed = true;
            completion.complete();
          } else {
            vertx.setTimer(checkIntervalMs, this);
          }
        }
      };

      vertx.setTimer(checkIntervalMs, checkCounter);
    }

    public boolean isClosed() {
      return closed;
    }

    public int getCounter() {
      return counter.get();
    }
  }
}
