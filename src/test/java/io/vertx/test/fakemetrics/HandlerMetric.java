/*
 * Copyright (c) 2011-2017 The original author or authors
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     https://www.eclipse.org/legal/epl-2.0/
 *
 *     The Apache License v2.0 is available at
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.test.fakemetrics;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class HandlerMetric {

  public final String address;
  public final String repliedAddress;
  public final AtomicInteger scheduleCount = new AtomicInteger();
  public final AtomicInteger localScheduleCount = new AtomicInteger();
  public final AtomicInteger beginCount = new AtomicInteger();
  public final AtomicInteger endCount = new AtomicInteger();
  public final AtomicInteger failureCount = new AtomicInteger();
  public final AtomicInteger localBeginCount = new AtomicInteger();

  public HandlerMetric(String address, String repliedAddress) {
    this.address = address;
    this.repliedAddress = repliedAddress;
  }

  @Override
  public String toString() {
    return "HandlerRegistration[address=" + address + ",repliedAddress=" + repliedAddress + ",beginCount=" + beginCount.get() +
        ",endCount=" + endCount.get() + ",failureCount=" + failureCount + ",localCount=" + localBeginCount.get() + "]";
  }
}
