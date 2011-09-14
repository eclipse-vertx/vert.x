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

import org.nodex.java.core.Completion;
import org.nodex.java.core.CompletionHandler;
import org.nodex.java.core.Nodex;
import org.nodex.java.core.buffer.Buffer;
import org.nodex.java.core.internal.NodexInternal;

import java.io.IOException;
import java.io.InputStream;

/**
 * <p>An asynchronous wrapper around a {@link InputStream}</p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class InStream {

  /**
   * Create a new {@code Instream} wrapping the {@link InputStream} in
   */
  public InStream(InputStream in) {
    this.in = in;
  }

  private final InputStream in;

  /**
   * Read {@code bytes} from the {@code InputStream}. When all the bytes have been read, {@code handler} will called
   * with a {@link Buffer} containing the bytes.
   */
  public void read(int bytes, CompletionHandler<Buffer> handler) {
    Long contextID = Nodex.instance.getContextID();
    if (contextID == null) {
      throw new IllegalStateException("Stdio can only be used inside an event loop");
    }
    byte[] read = new byte[bytes];
    doRead(read, 0, handler, contextID);
  }

  private void doRead(final byte[] read, final int offset, final CompletionHandler<Buffer> handler,
                      final long contextID) {
    final NodexInternal nodex = NodexInternal.instance;
    nodex.executeInBackground(new Runnable() {
      public void run() {
        try {
          int bytesRead = in.read(read, offset, read.length - offset);
          int newOffset = offset + bytesRead;
          if (newOffset == read.length) {
            //Done
            nodex.executeOnContext(contextID, new Runnable() {
              public void run() {
                nodex.setContextID(contextID);
                handler.handle(new Completion<>(Buffer.create(read)));
              }
            });
          } else {
            doRead(read, newOffset, handler, contextID);
          }
        } catch (IOException e) {
          handler.handle(new Completion(e));
        }
      }
    });
  }
}
