/*
 * Copyright 2011 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.nodex.java.core.composition;

import org.nodex.java.core.CompletionHandler;
import org.nodex.java.core.Deferred;
import org.nodex.java.core.Future;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class Composer {

  private List<WaitingBatch> batches = new ArrayList<>();
  private WaitingBatch currentBatch;
  private boolean executed;

  public <T> Future<T> parallel(Deferred<T> future) {
    checkExecuted();
    if (!future.complete()) {
      if (currentBatch == null) {
        currentBatch = new WaitingBatch();
        batches.add(currentBatch);
      }
      currentBatch.addFuture(future);
      final WaitingBatch batch = currentBatch;
      future.handler(new CompletionHandler<T>() {
        public void handle(Deferred<T> f) {
          batch.complete();
        }
      });
      checkAll();
    }
    return future;
  }

  public <T> Future<T> series(Deferred<T> future) {
    checkExecuted();
    currentBatch = null;
    return parallel(future);
  }

  public void execute() {
    if (!executed) {
      if (!batches.isEmpty()) {
        WaitingBatch batch = batches.get(0);
        batch.execute();
      }
      executed = true;
    }
  }

  private class WaitingBatch {

    Set<Deferred<?>> futures = new HashSet<>();
    int pending;
    boolean executed;

    void addFuture(Deferred<?> future) {
      if (!future.complete()) {
        pending++;
      }
      futures.add(future);
    }

    void complete() {
      pending--;
      if (pending == 0) {
        checkAll();
      }
    }

    void execute() {
      if (!executed) {
        executed = true;
        for (Deferred<?> future: futures) {
          future.execute();
        }
      }
    }
  }

  private void checkExecuted() {
    if (executed) {
      throw new IllegalStateException("Composer has already been executed");
    }
  }

  private void checkAll() {
    if (executed) {
      boolean executeNext = false;
      for (WaitingBatch batch: batches) {
        if (executeNext) {
          batch.execute();
          executeNext = false;
        }
        if (batch.pending == 0) {
          executeNext = true;
        } else {
          break;
        }
      }
    }
  }
}
