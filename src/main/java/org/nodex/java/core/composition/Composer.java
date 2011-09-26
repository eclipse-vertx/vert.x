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
import org.nodex.java.core.DeferredAction;
import org.nodex.java.core.Future;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>Composer allows asynchronous control flows to be defined</p>
 *
 * <p>This is useful when you have some asynchronous actions to be performed <i>after</i> other actions have completed.
 * In a asynchronous framework such as node.x you cannot just block on the result of one action before executing
 * the next one, since an event loop thread must never block. By using Composer you can describe the execution order
 * of a sequence of actions in a quasi-direct way, even though when they execute it will all be asynchronous.</p>
 * <p>Each action to execute is represented by an instance of {@link Deferred}. A Deferred represents an action
 * which is yet to be executed. Instances of Deferred can represent any asynchronous action, e.g. copying a file from A to B,
 * or getting a key from a Redis server.</p>
 * <p>An example of using this class is as follows:</p>
 * <pre>
 *
 * // d1..dn are instances of Deferred, returned from other node.x modules, e.g. {@link org.nodex.java.core.file.FileSystem}
 *
 * Composer comp = new Composer();
 * comp.parallel(d1);
 * comp.parallel(d2);
 * comp.parellel(d3);
 * comp.series(d4);
 * comp.parallel(d5);
 * comp.series(d6);
 * comp.series(d7);
 * comp.parallel(d8);
 * comp.parallel(d9);
 * comp.execute();
 * </pre>
 *
 * <p>In the above example, when {@code execute} is invoked, d1, d2, and d3 will be executed. When d1, d2, and d3
 * have all completed, then d4 and d5 will be executed. When d4 and d5 have completed d6 will be executed.
 * When d6 has completed d7, d8 and d9 will be executed. All this will occur asynchronous with no thread blocking for
 * anything to complete.</p>
 *
 * <p>Here is another example which uses the return values from actions in subsequent actions:</p>
 *
 * <pre>
 * Composer comp = new Composer();
 * final Future&lt;Integer&gt; f = comp.parallel(d1)
 * comp.series(new SimpleAction() {
 *   public void act() {
 *     System.out.println("Result of d1 is " + f.result);
 *   }
 * }
 * comp.execute();
 * </pre>
 *
 * <p>In the above example, when execute is invoked d1 will be executed. When d1 completes the result of the action
 * will be available in the {@link Future} f, and the second action will be executed which simply displays the result.</p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class Composer {

  private List<WaitingBatch> batches = new ArrayList<>();
  private WaitingBatch currentBatch;
  private boolean executed;

  /**
   * Add a Deferred to be executed in parallel with any other Deferred instances added using this method since the
   * last time (if any) the {@link #series} method was invoked. All such Deferred instances will be executed only
   * when there are no more Deferred instances that have not yet completed and were added before the last call to {@code series}.
   * @return A Future representing the future result of the Deferred.
   */
  public <T> Future<T> parallel(Deferred<T> deferred) {
    checkExecuted();
    //We can't return the original Deferred since we want the user to be able to set their own handler on the returned
    //value, but we use the handler on the Deferred
    final DeferredAction<T> ret = new DeferredAction<T>() {
      public void run() {
      }
    };
    if (!deferred.complete()) {
      if (currentBatch == null) {
        currentBatch = new WaitingBatch();
        batches.add(currentBatch);
      }
      currentBatch.addFuture(deferred);
      final WaitingBatch batch = currentBatch;
      deferred.handler(new CompletionHandler<T>() {
        public void handle(Future<T> f) {
          if (f.succeeded()) {
            ret.setResult(f.result());
          } else {
            ret.setException(f.exception());
          }
          batch.complete();
        }
      });
      checkAll();
    }
    return ret;
  }

  /**
   * Adds a Deferred that will be executed after any other Deferred instances that were added to this Composer have
   * completed.
   * @return A Future representing the future result of the Deferred.
   */
  public <T> Future<T> series(Deferred<T> deferred) {
    checkExecuted();
    currentBatch = null;
    return parallel(deferred);
  }

  /**
   * Start executing any Deferred instances added to this composer. Any instances added with {@link #parallel} will
   * be executed immediately until the first instance added with {@link #series} is encountered. Once all of those
   * instances have completed then, the next "batch" of Deferred instances starting from instance added with {@link #series}
   * up until the next instance added with {@link #series} will be executed. Once all of those have completed the next batch
   * up until the next {@link #series} will be executed. This process will complete until all Deferred instances have been
   * executed.
   */
  public void execute() {
    if (!executed) {
      executed = true;
      if (!batches.isEmpty()) {
        WaitingBatch batch = batches.get(0);
        batch.execute();
      }
    }
  }

  private class WaitingBatch {

    List<Deferred<?>> futures = new ArrayList<>();
    int pending;
    boolean batchExecuted;

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
      if (!batchExecuted) {
        batchExecuted = true;
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
