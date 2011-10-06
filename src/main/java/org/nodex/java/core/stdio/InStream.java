/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.nodex.java.core.stdio;

import org.nodex.java.core.Handler;
import org.nodex.java.core.buffer.Buffer;
import org.nodex.java.core.internal.NodexInternal;
import org.nodex.java.core.streams.ReadStream;

import java.io.IOException;
import java.io.InputStream;

/**
 * <p>An asynchronous wrapper around a {@link InputStream}</p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class InStream extends StreamBase implements ReadStream {

  private Handler<Buffer> dataHandler;
  private Handler<Void> endHandler;
  private boolean paused;
  private final InputStream in;

  private static final int BUFFER_SIZE = 1024;

  /**
   * Create a new {@code Instream} wrapping the {@link InputStream} in
   */
  public InStream(InputStream in) {
    super();
    this.in = in;
  }

  /**
   * {@inheritDoc}
   */
  public void dataHandler(Handler<Buffer> handler) {
    checkThread();
    Handler<Buffer> oldHandler = this.dataHandler;
    this.dataHandler = handler;
    if (!paused && oldHandler == null && handler != null) {
      doRead();
    }
  }

  /**
   * {@inheritDoc}
   */
  public void pause() {
    checkThread();
    paused = true;
  }

  /**
   * {@inheritDoc}
   */
  public void resume() {
    checkThread();
    paused = false;
    if (dataHandler != null) {
      doRead();
    }
  }

  /**
   * {@inheritDoc}
   */
  public void endHandler(Handler<Void> endHandler) {
    checkThread();
    this.endHandler = endHandler;
  }

  private void doRead() {
    final NodexInternal nodex = NodexInternal.instance;
    nodex.executeInBackground(new Runnable() {
      public void run() {
        try {
          byte[] buff = new byte[BUFFER_SIZE];
          int bytesRead = in.read(buff);
          if (bytesRead != -1) {
            if (bytesRead < BUFFER_SIZE) {
              byte[] buff2 = new byte[bytesRead];
              System.arraycopy(buff, 0, buff2, 0, bytesRead);
              buff = buff2;
            }
            final Buffer ret = Buffer.create(buff);
            nodex.executeOnContext(contextID, new Runnable() {
              public void run() {
                nodex.setContextID(contextID);
                if (!paused && dataHandler != null) {
                  dataHandler.handle(ret);
                  if (!paused && dataHandler != null) {
                    doRead();
                  }
                }
              }
            });
          } else {
            // Will this ever happen?
            if (endHandler != null) {
              endHandler.handle(null);
            }
          }
        } catch (IOException e) {
          if (exceptionHandler != null) {
            exceptionHandler.handle(e);
          }
        }
      }
    });
  }
}
