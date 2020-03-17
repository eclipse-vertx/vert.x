/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
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
  public final AtomicInteger discardCount = new AtomicInteger();
  public final AtomicInteger deliveredCount = new AtomicInteger();
  public final AtomicInteger localDeliveredCount = new AtomicInteger();

  public HandlerMetric(String address, String repliedAddress) {
    this.address = address;
    this.repliedAddress = repliedAddress;
  }

  @Override
  public String toString() {
    return "HandlerRegistration[address=" + address + ",repliedAddress=" + repliedAddress +
        ",deliveredCount=" + deliveredCount.get() + ",discardCount="  + discardCount + ",localCount=" + localDeliveredCount.get() + "]";
  }
}
