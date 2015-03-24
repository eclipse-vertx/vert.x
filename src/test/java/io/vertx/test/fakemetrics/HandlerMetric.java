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

package io.vertx.test.fakemetrics;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class HandlerMetric {

  public final String address;
  public final boolean replyHandler;
  public final AtomicInteger beginCount = new AtomicInteger();
  public final AtomicInteger endCount = new AtomicInteger();
  public final AtomicInteger failureCount = new AtomicInteger();
  public final AtomicInteger localCount = new AtomicInteger();

  public HandlerMetric(String address, boolean replyHandler) {
    this.address = address;
    this.replyHandler = replyHandler;
  }

  @Override
  public String toString() {
    return "HandlerRegistration[address=" + address + ",replyHandler=" + replyHandler + ",beginCount=" + beginCount.get() +
        ",endCount=" + endCount.get() + ",failureCount=" + failureCount + ",localCount=" + localCount.get() + "]";
  }
}
