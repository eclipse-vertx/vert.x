package io.vertx.core;

import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class Main extends AbstractVerticle {
  private HttpClient httpClient;
  private AtomicInteger successCount = new AtomicInteger(0);
  private AtomicInteger failureCount = new AtomicInteger(0);

  @Override
  public void start() throws Exception {
    HttpClientOptions options = new HttpClientOptions();
    options.setMaxWaitQueueSize(1000);
    options.setKeepAlive(false);
    options.setPipelining(false);
    options.setMaxPoolSize(500);
    httpClient = vertx.createHttpClient(options);
    vertx.eventBus().consumer("request", (Message<Integer> message) -> {
      sendData(response -> {
//                System.out.println(Thread.currentThread().getName());
        if (response.succeeded()) {
          successCount.incrementAndGet();
        } else {
          failureCount.incrementAndGet();
        }
        if ((successCount.intValue() + failureCount.intValue()) % 20 == 0) {
          int totalResponses = successCount.intValue() + failureCount.intValue();
          System.out.println("total = " + totalResponses + " (success = " + successCount.intValue() + ", failure = " + failureCount.intValue() + ")");

          if (totalResponses == 200) {
            System.out.println("done");
            vertx.eventBus().send("shutdown", "");
          }
        }
        delay(20);
      });
    });
  }

  private void sendData(Handler<AsyncResult<String>> handler) {
    AtomicBoolean done = new AtomicBoolean();
    HttpClientRequest request = httpClient.postAbs("http://localhost:7777", response -> {
      response.bodyHandler(buffer -> {
        handler.handle(Future.succeededFuture(buffer.toString()));
        done.set(true);
      });
    });
    request.exceptionHandler(e -> {
      if (done.get()) {
        System.out.println("dépassé");
      }
//      System.out.println(e.getMessage());
      handler.handle(Future.failedFuture(e));
    });
    request.setTimeout(2000);

    String data = "data";
    request.putHeader(HttpHeaders.CONTENT_LENGTH, Integer.toString(data.length()));
    request.putHeader(HttpHeaders.CONTENT_TYPE, "text/plain");
    request.write(data).end();
  }

  private void delay(long time) {
    try {
      Thread.sleep(time);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  private static Vertx vertx;
  private static HttpServer httpServer;

  static {
  }

  public static void main(String[] args) throws Exception {
    vertx = Vertx.vertx();
    startWebServer();
    vertx.deployVerticle(Main.class.getName(), new DeploymentOptions().setWorker(true), future -> {
      if (future.succeeded()) {
        for (int i = 0; i < 200; i++) {
          vertx.eventBus().send("request", 1);
        }
      }
    });

    vertx.eventBus().consumer("shutdown", e -> {
      httpServer.close();
      vertx.close();
    });
  }

  private static void startWebServer() {
    String webServiceResponse = "<response><hello></hello></response>";
    HttpServerOptions options = new HttpServerOptions();
    Timer timer = new Timer();
    httpServer = vertx.createHttpServer(options).requestHandler(request -> {
      timer.schedule(new TimerTask() {
        @Override
        public void run() {
          request.response().headers().set(HttpHeaders.CONTENT_LENGTH, String.valueOf(webServiceResponse.length()));
          request.response().headers().set(HttpHeaders.CONTENT_TYPE, "application/xml");
          request.response().end(webServiceResponse);
        }
      }, 1000);
    }).listen(7777);
  }
}
