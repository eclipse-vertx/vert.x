/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.examples.eventbus;

import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.deploy.Verticle;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class Sender implements Verticle, Handler<Message<String>> {

  String address = "example.address";
  String creditsAddress = "example.credits";

  int credits = 1000;
  int count = 0;

  EventBus eb = EventBus.instance;

  @Override
  public void start() throws Exception {
    eb.registerHandler(creditsAddress, this);
    Vertx.instance.setTimer(5000, new Handler<Long>() {
      public void handle(Long timerID) {
        sendMessage();
      }
    });

  }

  @Override
  public void stop() throws Exception {
    eb.unregisterHandler(creditsAddress, this);
  }

  @Override
  public void handle(Message<String> event) {
    credits += 1000;
    sendMessage();
  }

  private void sendMessage() {
    for (int i = 0; i < 200; i++) {
      if (credits > 0) {
        credits--;
        eb.send(address, "some message");
        count++;
       // System.out.println("Sent message " + count);
        Vertx.instance.nextTick(new SimpleHandler() {
          public void handle() {
            sendMessage();
          }
        });
      } else {
        break;
      }
    }
  }
}
