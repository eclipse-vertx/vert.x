package io.vertx.core;

import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Main2 {

  public static void main(String[] args) {

    Vertx vertx = Vertx.vertx();

    int n = 30;

    vertx.eventBus().consumer("the-address", msg -> {
      vertx.setTimer(900, id -> {
        msg.reply("");
      });
    });

    vertx.runOnContext(v -> {
      AtomicInteger count = new AtomicInteger();
      for (int i = 0;i < n;i++) {
        long id = vertx.setTimer(1000, $ -> {

        });
        vertx.eventBus().send("the-address", "", reply -> {
          if (!vertx.cancelTimer(id)) {
            System.out.println("could not cancel timer");
          }
          try {
            Thread.sleep(20);
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
          if (count.incrementAndGet() == n) {
            System.out.println("done");
          }
        });
      }
    });

  }
}
