package org.nodex.core.stdio;

import org.nodex.core.Nodex;
import org.nodex.core.NodexInternal;
import org.nodex.core.buffer.Buffer;
import org.nodex.core.buffer.DataHandler;

import java.io.IOException;
import java.io.InputStream;

/**
 * User: tim
 * Date: 17/08/11
 * Time: 08:44
 */
public class InStream {
  public InStream(InputStream in) {
    this.in = in;
  }
  private final InputStream in;

  public void read(int bytes, DataHandler handler) {
    Long contextID = Nodex.instance.getContextID();
    if (contextID == null) {
      throw new IllegalStateException("Stdio can only be used inside an event loop");
    }
    byte[] read = new byte[bytes];
    doRead(read, 0, handler, contextID);
  }

  private void doRead(final byte[] read, final int offset, final DataHandler handler, final long contextID) {
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
                handler.onData(Buffer.create(read));
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
