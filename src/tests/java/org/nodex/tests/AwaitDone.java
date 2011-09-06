/*
 * Copyright 2002-2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.nodex.tests;

import org.nodex.java.core.EventHandler;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class AwaitDone implements EventHandler<Void> {
  private CountDownLatch latch = new CountDownLatch(1);

  public void onEvent(Void v) {
    latch.countDown();
  }

  public boolean awaitDone(long timeout) {
    try {
      return latch.await(timeout, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      return false;
    }
  }
}