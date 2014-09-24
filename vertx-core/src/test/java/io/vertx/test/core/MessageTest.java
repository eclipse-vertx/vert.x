package io.vertx.test.core;

import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;

import org.junit.Test;

public class MessageTest extends VertxTestBase {

  @Test
  public void testForward() throws Exception{

    vertx.eventBus().registerHandler("first", new Handler<Message<String>>(){

      @Override
      public void handle(Message<String> event) {
        event.forward("second");
      }

    });

    vertx.eventBus().registerHandler("second", new Handler<Message<String>>(){

      @Override
      public void handle(Message<String> event) {
        event.forward("first");
      }
    });

    vertx.eventBus().send("first", "first");
  }
}
