package org.nodex.core.file;

import org.jboss.netty.buffer.ChannelBuffers;
import org.nodex.core.Completion;
import org.nodex.core.CompletionWithResult;
import org.nodex.core.ExceptionHandler;
import org.nodex.core.Nodex;
import org.nodex.core.NodexInternal;
import org.nodex.core.buffer.Buffer;
import org.nodex.core.buffer.DataHandler;
import org.nodex.core.streams.ReadStream;
import org.nodex.core.streams.WriteStream;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * User: tim
 * Date: 03/08/11
 * Time: 09:16
 */
public class FileHandle {

  private static final int BUFFER_SIZE = 8192;

  private final AsynchronousFileChannel ch;
  private final Thread th;
  private final String contextID;
  private boolean closed;

  FileHandle(AsynchronousFileChannel ch, String contextID) {
    this.ch = ch;
    this.contextID = contextID;
    this.th = Thread.currentThread();
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
            setContextID();
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
          // partial read
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
              setContextID();
              completion.onException(e);
            }
          });
        } else {
          exc.printStackTrace(System.err);
        }
      }
    });
  }

  void write(Buffer buffer, int position, final Completion completion) {
    checkClosed();
    ByteBuffer bb = buffer._toChannelBuffer().toByteBuffer();
    doWrite(bb, position, completion);
  }

  void read(int position, int length, final CompletionWithResult<Buffer> completion) {
    checkClosed();

    ByteBuffer buff = ByteBuffer.allocate(length);
    doRead(buff, position, completion);
  }

  void close() throws IOException {
    checkClosed();
    ch.close();
  }

  void sync(boolean meta) throws IOException {
    ch.force(meta);
  }

  void checkContext() {
    if (!Nodex.instance.getContextID().equals(contextID)) {
      throw new IllegalStateException("FileHandle must only be used in the context that created it");
    }
  }

  WriteStream getWriteStream() {
    return new WriteStream() {

      ExceptionHandler exceptionHandler;
      Runnable drainHandler;
      AtomicInteger writesOutstanding = new AtomicInteger(0);
      int pos;
      int maxWrites = 64 * 1024;    // TODO - we should tune this for best performance
      int lwm = maxWrites / 2;

      public void writeBuffer(Buffer buffer) {
        checkClosed();
        final int length = buffer.length();
        writesOutstanding.addAndGet(length);
        pos += length;
        ByteBuffer bb = buffer._toChannelBuffer().toByteBuffer();

        doWrite(bb, pos, new Completion() {

          public void onCompletion() {
            int size = writesOutstanding.addAndGet(-length);
            //Low water mark
            if (drainHandler != null && size <= lwm) {
              drainHandler.run();
            }
          }

          public void onException(Exception e) {
            handleException(e);
          }
        });
      }

      public void setWriteQueueMaxSize(int maxSize) {
        checkClosed();
        this.maxWrites = maxSize;
        this.lwm = maxWrites / 2;
      }

      public boolean writeQueueFull() {
        checkClosed();
        return writesOutstanding.get() >= maxWrites;
      }

      public void drainHandler(Runnable handler) {
        checkClosed();
        this.drainHandler = handler;
      }

      public void exceptionHandler(ExceptionHandler handler) {
        checkClosed();
        this.exceptionHandler = handler;
      }

      void handleException(Exception e) {
        if (exceptionHandler != null) {
          exceptionHandler.onException(e);
        } else {
          e.printStackTrace(System.err);
        }
      }
    };
  }

  ReadStream getReadStream() {

    return new ReadStream() {

      boolean paused;
      DataHandler dataHandler;
      ExceptionHandler exceptionHandler;
      Runnable endHandler;
      int pos;
      boolean closed;

      void doRead() {
        read(pos, BUFFER_SIZE, new CompletionWithResult<Buffer>() {
          public void onCompletion(Buffer buffer) {
            if (buffer.length() == 0) {
              // Empty buffer represents endHandler of file
              try {
                close();
              } catch (IOException e) {
                handleException(e);
              }
              handleEnd();
            } else {
              pos += buffer.length();

              handleData(buffer);

              if (!paused) {
                doRead();
              }
            }
          }

          public void onException(Exception e) {
            handleException(e);
          }
        });
      }

      public void dataHandler(DataHandler handler) {
        checkClosed();
        this.dataHandler = handler;
        if (dataHandler != null && !paused && !closed) {
          doRead();
        }
      }

      public void exceptionHandler(ExceptionHandler handler) {
        checkClosed();
        this.exceptionHandler = handler;
      }

      public void endHandler(Runnable handler) {
        checkClosed();
        this.endHandler = handler;
      }

      public void pause() {
        checkClosed();
        paused = true;
      }

      public void resume() {
        checkClosed();
        paused = false;
        if (dataHandler != null && !closed) {
          doRead();
        }
      }

      void handleException(Exception e) {
        if (exceptionHandler != null) {
          exceptionHandler.onException(e);
        } else {
          e.printStackTrace(System.err);
        }
      }

      void handleData(Buffer buffer) {
        if (dataHandler != null) {
          dataHandler.onData(buffer);
        }
      }

      void handleEnd() {
        if (endHandler != null) {
          endHandler.run();
        }
      }
    };
  }

  private void checkClosed() {
    if (closed) {
      throw new IllegalStateException("File handle is closedHandler");
    }
  }

  protected void setContextID() {
    // Sanity checkClosed
    // All ops should always be invoked on same thread
    if (Thread.currentThread() != th) {
      throw new IllegalStateException("Invoked with wrong thread");
    }
    NodexInternal.instance.setContextID(contextID);
  }
}
