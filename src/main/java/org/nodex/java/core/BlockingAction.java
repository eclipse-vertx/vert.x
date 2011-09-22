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

import org.nodex.java.core.internal.NodexInternal;

/**
 * <p>Sometimes it is necessary to perform operations in node.x which are inherently blocking, e.g. talking to legacy
 * blocking APIs or libraries. This class allows blocking operations to be executed cleanly in an asychronous
 * environment.</p>
 * <p>By subclassing this class, implementing the {@link SynchronousAction#action} method and executing it, node.x will perform the blocking operation on a thread from a
 * background thread pool specially reserved for blocking operations. This means the event loop threads are not
 * blocked and can continue to service other requests.</p>
 * <p>It will rarely be necessary to use this class directly, it is normally used by libraries which wrap legacy
 * blocking APIs into an asynchronous form suitable for a 100% asynchronous framework such as node.x</p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class BlockingAction<T> extends SynchronousAction<T> {

  /**
   * Run the blocking action using a thread from the background pool.
   */
  protected void run() {
    final long contextID = Nodex.instance.getContextID();
    Runnable runner = new Runnable() {
      public void run() {
        try {
          final T result = action();
          NodexInternal.instance.executeOnContext(contextID, new Runnable() {
            public void run() {
              setResult(result);
            }
          });
        } catch (final Exception e) {
          NodexInternal.instance.executeOnContext(contextID, new Runnable() {
            public void run() {
              setException(e);
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
