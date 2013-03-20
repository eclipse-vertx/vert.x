package vertx.tests;/*
 * Copyright 2013 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */

import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.impl.ConcurrentHashSet;
import org.vertx.java.platform.Verticle;
import org.vertx.java.testframework.TestUtils;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class MultiThreadedWorker extends Verticle {

  private Set<Thread> threads = new ConcurrentHashSet<>();

  private TestUtils tu;

  @Override
  public void start() {

    tu = new TestUtils(vertx);

    vertx.eventBus().registerHandler("fooaddress", new Handler<Message>() {

      AtomicInteger cnt = new AtomicInteger();

      @Override
      public void handle(Message event) {
        threads.add(Thread.currentThread());
        if (cnt.incrementAndGet() == 1000) {
          tu.azzert(threads.size() > 1);
          tu.testComplete();
        }
      }
    });

  }

}
