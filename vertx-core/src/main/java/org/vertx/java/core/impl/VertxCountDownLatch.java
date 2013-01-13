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
package org.vertx.java.core.impl;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Very similar to CountDownLatch, except that await() doesn't return upon a 
 * InterruptedException
 * 
 * @author Juergen Donnerstag
 */
public class VertxCountDownLatch extends CountDownLatch {
	
  public VertxCountDownLatch(int count) {
  	super(count);
  }

  @Override
  public void await() {
    while (true) {
      try {
        super.await();
        break;
      } catch (InterruptedException ignore) {
      }
    }
  }
  
  @Override
  public boolean await(long timeout, TimeUnit unit) {
		long start = System.currentTimeMillis();
		long millis = unit.toMillis(timeout);
    while (millis > 0) {
      try {
        return super.await(millis, TimeUnit.MILLISECONDS);
      } catch (InterruptedException ignore) {
        // can get spurious interrupts
  			millis = millis - (System.currentTimeMillis() - start);
      }
    }
    return false;
  }
}
