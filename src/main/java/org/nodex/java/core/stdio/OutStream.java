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

package org.nodex.java.core.stdio;

import org.nodex.java.core.Handler;
import org.nodex.java.core.Nodex;
import org.nodex.java.core.buffer.Buffer;
import org.nodex.java.core.internal.NodexInternal;
import org.nodex.java.core.streams.WriteStream;

import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * <p>An asynchronous wrapper around a {@link PrintStream}</p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class OutStream extends StreamBase implements WriteStream {

  private Handler<Void> drainHandler;
  private int writeQueueMaxSize = 8 * 1024;
  private final PrintStream out;
  private final AtomicInteger pendingWrites = new AtomicInteger(0);

  /**
   * Create a new {@code OutStream} wrapping a {@link PrintStream}
   * @param out
   */
  public OutStream(PrintStream out) {
    super();
    this.out = out;
  }

  /**
   * {@inheritDoc}
   */
  public void writeBuffer(Buffer data) {
    checkThread();
    final byte[] bytes = data.getBytes();
    pendingWrites.addAndGet(bytes.length);
    NodexInternal.instance.executeInBackground(new Runnable() {
      public void run() {
        out.write(bytes, 0, bytes.length);
        int queueSize = pendingWrites.addAndGet(-bytes.length);
        checkDrain(queueSize);
      }
    });
  }

  /**
   * {@inheritDoc}
   */
  public void setWriteQueueMaxSize(int maxSize) {
    checkThread();
    this.writeQueueMaxSize = maxSize;
  }

  /**
   * {@inheritDoc}
   */
  public boolean writeQueueFull() {
    checkThread();
    return pendingWrites.get() >= writeQueueMaxSize;
  }

  /**
   * {@inheritDoc}
   */
  public void drainHandler(Handler<Void> handler) {
    checkThread();
    this.drainHandler = handler;
    if (pendingWrites.get() <= writeQueueMaxSize / 2 && drainHandler != null) {
      this.drainHandler = null;
      handler.handle(null);
    }
  }

  private void checkDrain(int queueSize) {
    if (queueSize <= writeQueueMaxSize / 2 && drainHandler != null) {
      NodexInternal.instance.executeOnContext(contextID, new Runnable() {
        public void run() {
          Handler<Void> dh = drainHandler;
          if (dh != null) {
            NodexInternal.instance.setContextID(contextID);
            drainHandler = null;
            dh.handle(null);
          }
        }
      });
    }
  }

}
