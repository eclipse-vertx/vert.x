/*
 * Copyright (c) 2011-2013 Red Hat Inc.
 * ------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package vertx.tests;

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
