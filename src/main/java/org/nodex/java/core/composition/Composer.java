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

package org.nodex.java.core.composition;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class Composer {

  private List<Runnable> runList = new ArrayList();
  private int pos;

  public Composer when(final Runnable handler) {
    Deferred deff = new Deferred(handler);
    return when(deff);
  }

  public Composer when(final Composable composable) {
    return when(new Composable[]{composable});
  }

  public Composer when(final Composable... composables) {
    Runnable run = new Runnable() {
      public void run() {
        final AtomicInteger countDown = new AtomicInteger(composables.length);
        Runnable cb = new Runnable() {
          public void run() {
            if (countDown.decrementAndGet() == 0) {
              next();
            }
          }
        };
        for (Composable c : composables) {
          c.execute();
          c.onComplete(cb);
        }
      }
    };
    runList.add(run);
    return this;
  }

  public Composer afterDelay(long delay, final Composable composable) {
    return this;
  }

  public void end() {
    pos = 0;
    runCurrent();
  }

  private void next() {
    pos++;
    if (pos < runList.size()) {
      runCurrent();
    }
  }

  private void runCurrent() {
    Runnable run = runList.get(pos);
    run.run();
  }

  private static class Deferred extends Composable {

    private final Runnable cb;

    Deferred(Runnable cb) {
      this.cb = cb;
    }

    @Override
    public void execute() {
      cb.run();
      complete();
    }
  }

}
