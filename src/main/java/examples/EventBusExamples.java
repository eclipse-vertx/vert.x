/*
 * Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package examples;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class EventBusExamples {

  Vertx vertx = Vertx.vertx();

  public void theExample() {
    EventBus eb = vertx.eventBus();
    eb.consumer("test.address", message -> {
      System.out.println("I received a message " + message.body());
    });
  }

  public void theExample2() {
    EventBus eb = vertx.eventBus();
    eb.<String>consumer("test.address", message -> {
      System.out.println("I received a message " + message.body());
    });
  }
}
