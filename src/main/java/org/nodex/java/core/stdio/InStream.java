package org.nodex.java.core.stdio;

import org.nodex.java.core.EventHandler;
import org.nodex.java.core.Nodex;
import org.nodex.java.core.NodexInternal;
import org.nodex.java.core.buffer.Buffer;

import java.io.IOException;
import java.io.InputStream;

/**
 *
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class InStream {
  public InStream(InputStream in) {
    this.in = in;
  }

  private final InputStream in;

  public void read(int bytes, EventHandler<Buffer> handler) {
    Long contextID = Nodex.instance.getContextID();
    if (contextID == null) {
      throw new IllegalStateException("Stdio can only be used inside an event loop");
    }
    byte[] read = new byte[bytes];
    doRead(read, 0, handler, contextID);
  }

  private void doRead(final byte[] read, final int offset, final EventHandler<Buffer> handler, final long contextID) {
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
                handler.onEvent(Buffer.create(read));
              }
            });
          } else {
            doRead(read, newOffset, handler, contextID);
          }
        } catch (IOException e) {
          //TODO better error handling
          e.printStackTrace(System.err);
        }
      }
    });
  }
}
