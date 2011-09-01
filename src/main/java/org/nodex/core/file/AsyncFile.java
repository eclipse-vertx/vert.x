package org.nodex.core.file;

import org.jboss.netty.buffer.ChannelBuffers;
import org.nodex.core.BlockingTask;
import org.nodex.core.CompletionHandler;
import org.nodex.core.EventHandler;
import org.nodex.core.Nodex;
import org.nodex.core.NodexInternal;
import org.nodex.core.buffer.Buffer;
import org.nodex.core.streams.ReadStream;
import org.nodex.core.streams.WriteStream;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashSet;

/**
 * User: tim
 * Date: 03/08/11
 * Time: 09:16
 */
public class AsyncFile {

  public static final int BUFFER_SIZE = 8192;

  private final AsynchronousFileChannel ch;
  private final Thread th;
  private final long contextID;
  private boolean closed;
  private ReadStream readStream;
  private WriteStream writeStream;
  private CompletionHandler closedCompletionHandler;
  private long writesOutstanding;

  AsyncFile(final String path, String perms, final boolean read, final boolean write, final boolean createNew,
            final boolean sync, final boolean syncMeta, final long contextID, final Thread th) throws Exception {
    if (!read && !write) {
      throw new FileSystemException("Cannot open file for neither reading nor writing");
    }
    Path file = Paths.get(path);
    HashSet<OpenOption> options = new HashSet<>();
    if (read) options.add(StandardOpenOption.READ);
    if (write) options.add(StandardOpenOption.WRITE);
    if (createNew) options.add(StandardOpenOption.CREATE);
    if (sync) options.add(StandardOpenOption.DSYNC);
    if (syncMeta) options.add(StandardOpenOption.SYNC);
    if (perms != null) {
      FileAttribute<?> attrs = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(perms));
      ch = AsynchronousFileChannel.open(file, options, NodexInternal.instance.getBackgroundPool(), attrs);
    } else {
      ch = AsynchronousFileChannel.open(file, options, NodexInternal.instance.getBackgroundPool());
    }
    this.contextID = contextID;
    this.th = th;
  }

  public void close(final CompletionHandler<Void> completionHandler) {
    check();

    closed = true;

    CompletionHandler comp = new CompletionHandler<Void>() {
      public void onCompletion(Void v) {
        try {
          ch.close();
          completionHandler.onCompletion(null);
        } catch (IOException e) {
          completionHandler.onException(e);
        }
      }

      public void onException(Exception e) {
        completionHandler.onException(e);
      }
    };

    if (writesOutstanding > 0) {
      //Need to wait for all writes to complete before firing the completionHandler
      closedCompletionHandler = comp;
    } else {
      comp.onCompletion(null);
    }
  }

  public void write(Buffer buffer, int position, final CompletionHandler completionHandler) {
    check();
    ByteBuffer bb = buffer._getChannelBuffer().toByteBuffer();
    doWrite(bb, position, completionHandler, true);
  }

  public void read(int position, int length, final CompletionHandler<Buffer> completionHandler) {
    check();
    ByteBuffer buff = ByteBuffer.allocate(length);
    doRead(buff, position, completionHandler);
  }

  public WriteStream getWriteStream() {
    check();
    if (writeStream == null) {
      writeStream = new WriteStream() {
        EventHandler<Exception> exceptionHandler;
        EventHandler<Void> drainHandler;

        int pos;
        int maxWrites = 64 * 1024;    // TODO - we should tune this for best performance
        int lwm = maxWrites / 2;

        public void writeBuffer(Buffer buffer) {
          check();
          final int length = buffer.length();
          ByteBuffer bb = buffer._getChannelBuffer().toByteBuffer();

          doWrite(bb, pos, new CompletionHandler<Void>() {

            public void onCompletion(Void v) {
              checkContext();

              checkDrained();
              if (writesOutstanding == 0 && closedCompletionHandler != null) {
                closedCompletionHandler.onCompletion(null);
              }
            }

            public void onException(Exception e) {
              handleException(e);
            }
          }, true);
          pos += length;
        }

        private void checkDrained() {
          if (drainHandler != null && writesOutstanding <= lwm) {
            EventHandler<Void> handler = drainHandler;
            drainHandler = null;
            handler.onEvent(null);
          }
        }

        public void setWriteQueueMaxSize(int maxSize) {
          check();
          this.maxWrites = maxSize;
          this.lwm = maxWrites / 2;
        }

        public boolean writeQueueFull() {
          check();
          return writesOutstanding >= maxWrites;
        }

        public void drainHandler(EventHandler<Void> handler) {
          check();
          this.drainHandler = handler;
          checkDrained();
        }

        public void exceptionHandler(EventHandler<Exception> handler) {
          check();
          this.exceptionHandler = handler;
        }

        void handleException(Exception e) {
          if (exceptionHandler != null) {
            exceptionHandler.onEvent(e);
          } else {
            e.printStackTrace(System.err);
          }
        }
      };
    }
    return writeStream;
  }

  public ReadStream getReadStream() {
    check();
    if (readStream == null) {
      readStream = new ReadStream() {

        boolean paused;
        EventHandler<Buffer> dataHandler;
        EventHandler<Exception> exceptionHandler;
        EventHandler<Void> endHandler;
        int pos;
        boolean readInProgress;

        void doRead() {
          if (!readInProgress) {
            readInProgress = true;
            read(pos, BUFFER_SIZE, new CompletionHandler<Buffer>() {
              public void onCompletion(Buffer buffer) {
                readInProgress = false;

                if (buffer.length() == 0) {
                  // Empty buffer represents end of file
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
        }

        public void dataHandler(EventHandler<Buffer> handler) {
          check();
          this.dataHandler = handler;
          if (dataHandler != null && !paused && !closed) {
            doRead();
          }
        }

        public void exceptionHandler(EventHandler<Exception> handler) {
          check();
          this.exceptionHandler = handler;
        }

        public void endHandler(EventHandler<Void> handler) {
          check();
          this.endHandler = handler;
        }

        public void pause() {
          check();
          paused = true;
        }

        public void resume() {
          check();
          if (paused && !closed) {
            paused = false;
            if (dataHandler != null) {
              doRead();
            }
          }
        }

        void handleException(Exception e) {
          if (exceptionHandler != null) {
            checkContext();
            exceptionHandler.onEvent(e);
          } else {
            e.printStackTrace(System.err);
          }
        }

        void handleData(Buffer buffer) {
          if (dataHandler != null) {
            checkContext();
            dataHandler.onEvent(buffer);
          }
        }

        void handleEnd() {
          if (endHandler != null) {
            checkContext();
            endHandler.onEvent(null);
          }
        }
      };
    }
    return readStream;
  }

  public void sync(final boolean metaData, CompletionHandler completionHandler) {
    checkClosed();
    checkContext();
    new BlockingTask(completionHandler) {
      public Object execute() throws Exception {
        ch.force(metaData);
        return null;
      }
    }.run();
  }

  private void doWrite(final ByteBuffer buff, final int position, final CompletionHandler<Void> completionHandler, final boolean add) {
    if (add) {
      writesOutstanding += buff.limit();
    }

    ch.write(buff, position, null, new java.nio.channels.CompletionHandler<Integer, Object>() {

      public void completed(Integer bytesWritten, Object attachment) {

        //writesOutstanding.addAndGet(-bytesWritten);

        int pos = position;

        if (buff.hasRemaining()) {
          // partial write
          pos += bytesWritten;
          // resubmit
          doWrite(buff, pos, completionHandler, false);
        } else {
          // It's been fully written
          NodexInternal.instance.executeOnContext(contextID, new Runnable() {
            public void run() {
              writesOutstanding -= buff.limit();
              completionHandler.onCompletion(null);
            }
          });
        }
      }

      public void failed(Throwable exc, Object attachment) {
        if (exc instanceof Exception) {
          final Exception e = (Exception) exc;
          NodexInternal.instance.executeOnContext(contextID, new Runnable() {
            public void run() {
              completionHandler.onException(e);
            }
          });
        } else {
          exc.printStackTrace(System.err);
        }
      }
    });
  }

  private void doRead(final ByteBuffer buff, final int position, final CompletionHandler<Buffer> completionHandler) {

    ch.read(buff, position, null, new java.nio.channels.CompletionHandler<Integer, Object>() {

      int pos = position;

      private void done() {
        NodexInternal.instance.executeOnContext(contextID, new Runnable() {
          public void run() {
            setContextID();
            buff.flip();
            Buffer nbuff = new Buffer(ChannelBuffers.wrappedBuffer(buff));
            completionHandler.onCompletion(nbuff);
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
          doRead(buff, pos, completionHandler);
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
              completionHandler.onException(e);
            }
          });
        } else {
          exc.printStackTrace(System.err);
        }
      }
    });
  }

  private void check() {
    checkClosed();
    checkContext();
  }

  private void checkClosed() {
    if (closed) {
      throw new IllegalStateException("File handle is closed");
    }
  }

  private void setContextID() {
    // Sanity checkClosed
    // All ops should always be invoked on same thread
    if (Thread.currentThread() != th) {
      throw new IllegalStateException("Invoked with wrong thread");
    }
    NodexInternal.instance.setContextID(contextID);
  }

  private void checkContext() {
    if (!Nodex.instance.getContextID().equals(contextID)) {
      throw new IllegalStateException("AsyncFile must only be used in the context that created it");
    }
  }

}
