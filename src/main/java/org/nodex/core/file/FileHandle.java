package org.nodex.core.file;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.nodex.core.Completion;
import org.nodex.core.CompletionWithResult;
import org.nodex.core.Nodex;
import org.nodex.core.NodexInternal;
import org.nodex.core.buffer.Buffer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * User: tim
 * Date: 03/08/11
 * Time: 09:16
 */
public class FileHandle {

  private final AsynchronousFileChannel ch;
  private final String contextID;

  FileHandle(AsynchronousFileChannel ch) {
    this.ch = ch;
    this.contextID = Nodex.instance.getContextID();
  }

  private void doWrite(final ByteBuffer buff, final int position, final Completion completion) {

    ch.write(buff, position, null, new CompletionHandler<Integer, Object>() {

      public void completed(Integer bytesWritten, Object attachment) {

        int pos = position;

        if (buff.hasRemaining()) {
          // partial write
          pos += bytesWritten;
          // resubmit
          doWrite(buff, pos, completion);
        } else {
          // It's been fully written
          NodexInternal.instance.executeOnContext(contextID, new Runnable() {
            public void run() {
              completion.onCompletion();
            }
          });
        }
      }

      public void failed(Throwable exc, Object attachment) {
        if (exc instanceof Exception) {
          final Exception e = (Exception)exc;
          NodexInternal.instance.executeOnContext(contextID, new Runnable() {
            public void run() {
              completion.onException(e);
            }
          });
        } else {
          exc.printStackTrace(System.err);
        }
      }
    });
  }

  private void doRead(final ByteBuffer buff, final int position, final CompletionWithResult<Buffer> completion) {

    ch.read(buff, position, null, new CompletionHandler<Integer, Object>() {

      int pos = position;

      private void done() {
        NodexInternal.instance.executeOnContext(contextID, new Runnable() {
          public void run() {
            Buffer nbuff = Buffer.fromChannelBuffer(ChannelBuffers.wrappedBuffer(buff));
            completion.onCompletion(nbuff);
          }
        });
      }

      public void completed(Integer bytesRead, Object attachment) {
        if (bytesRead == -1) {
          //End of file
          done();
        } else if (buff.hasRemaining()) {
          // partial write
          pos += bytesRead;
          // resubmit
          doRead(buff, pos, completion);
        } else {
          // It's been fully written
          done();
        }
      }

      public void failed(Throwable exc, Object attachment) {
        if (exc instanceof Exception) {
          final Exception e = (Exception) exc;
          NodexInternal.instance.executeOnContext(contextID, new Runnable() {
            public void run() {
              completion.onException(e);
            }
          });
        } else {
          exc.printStackTrace(System.err);
        }
      }
    });
  }

  private void checkContext() {
    if (!Nodex.instance.getContextID().equals(contextID)) {
      throw new IllegalStateException("FileHandle must only be used in the context that created it");
    }
  }

  void write(Buffer buffer, int position, final Completion completion) {
    checkContext();

    ByteBuffer bb = buffer._toChannelBuffer().toByteBuffer();
    doWrite(bb, position, completion);
  }

  void read(int position, int length, final CompletionWithResult<Buffer> completion) {
    checkContext();

    ByteBuffer buff = ByteBuffer.allocate(length);
    doRead(buff, position, completion);
  }

  void close() throws IOException {
    ch.close();
  }

}
