package perf;

/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.platform.Verticle;

public class RateCounter extends Verticle implements Handler<Message<Integer>> {

  private long last;

  private long count;

  private long start;

  private long totCount;

  public void handle(Message<Integer> msg) {
    if (last == 0) {
      last = start = System.currentTimeMillis();
    }
    count += msg.body;
    totCount += msg.body;
  }

  public void start() {
    vertx.eventBus().registerHandler("rate-counter", this);
    vertx.setPeriodic(3000, new Handler<Long>() {
      public void handle(Long id) {
        if (last != 0) {
          long now = System.currentTimeMillis();
          double rate = 1000 * (double)count / (now - last);
          double avRate = 1000 * (double)totCount / (now - start);
          count = 0;
          System.out.println((now - start) + " Rate: count/sec: " + rate + " Average rate: " + avRate);
          last = now;
        }
      }
    });
  }
}
