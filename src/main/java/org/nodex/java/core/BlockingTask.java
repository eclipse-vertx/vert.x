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

package org.nodex.java.core;

/**
 * User: tim
 * Date: 02/08/11
 * Time: 11:20
 */
public abstract class BlockingTask<T> {

  private final CompletionHandler<T> completionHandler;

  public BlockingTask(CompletionHandler<T> completionHandler) {
    this.completionHandler = completionHandler;
  }

  public abstract T execute() throws Exception;

  public final void run() {
    final long contextID = Nodex.instance.getContextID();
    Runnable runner = new Runnable() {
      public void run() {
        try {
          final T result = execute();
          NodexInternal.instance.executeOnContext(contextID, new Runnable() {
            public void run() {
              completionHandler.onEvent(new Completion(result));
            }
          });
        } catch (final Exception e) {
          NodexInternal.instance.executeOnContext(contextID, new Runnable() {
            public void run() {
              completionHandler.onEvent(new Completion(e));
            }
          });
        } catch (Throwable t) {
          //Not much we can do, just log it
          t.printStackTrace(System.err);
        }
      }
    };

    NodexInternal.instance.executeInBackground(runner);
  }
}
