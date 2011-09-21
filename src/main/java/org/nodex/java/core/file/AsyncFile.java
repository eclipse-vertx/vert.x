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

package org.nodex.java.core.file;

import org.nodex.java.core.BlockingTask;
import org.nodex.java.core.CompletionHandler;
import org.nodex.java.core.Deferred;
import org.nodex.java.core.Future;
import org.nodex.java.core.Handler;
import org.nodex.java.core.Nodex;
import org.nodex.java.core.SimpleDeferred;
import org.nodex.java.core.buffer.Buffer;
import org.nodex.java.core.internal.NodexInternal;
import org.nodex.java.core.streams.ReadStream;
import org.nodex.java.core.streams.WriteStream;

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
 * <p>Represents a file on the file-system which can be read from, or written to asynchronously.</p>
 * <p>Methods also exist to get a {@link org.nodex.java.core.streams.ReadStream} or a
 *  {@link org.nodex.java.core.streams.WriteStream} on the file. This allows the data to be pumped to and from
 *  other streams, e.g. an {@link org.nodex.java.core.http.HttpClientRequest} instance, using the {@link org.nodex.java.core.streams.Pump} class</p>
 * @author <a href="http://tfox.org">Tim Fox</a>
  */
public class AsyncFile {

  public static final int BUFFER_SIZE = 8192;

  private final AsynchronousFileChannel ch;
  private final Thread th;
  private final long contextID;
  private boolean closed;
  private ReadStream readStream;
  private WriteStream writeStream;
  private Deferred<Void> closedDeferred;
  private long writesOutstanding;

  AsyncFile(final String path, String perms, final boolean read, final boolean write, final boolean createNew,
            final boolean flush, final long contextID, final Thread th) throws Exception {
    if (!read && !write) {
      throw new FileSystemException("Cannot open file for neither reading nor writing");
    }
    Path file = Paths.get(path);
    HashSet<OpenOption> options = new HashSet<>();
    if (read) options.add(StandardOpenOption.READ);
    if (write) options.add(StandardOpenOption.WRITE);
    if (createNew) options.add(StandardOpenOption.CREATE);
    if (flush) options.add(StandardOpenOption.DSYNC);
    if (perms != null) {
      FileAttribute<?> attrs = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(perms));
      ch = AsynchronousFileChannel.open(file, options, NodexInternal.instance.getBackgroundPool(), attrs);
    } else {
      ch = AsynchronousFileChannel.open(file, options, NodexInternal.instance.getBackgroundPool());
    }
    this.contextID = contextID;
    this.th = th;
  }

  public Deferred<Void> closeDeferred() {
    check();

    closed = true;

    Deferred<Void> deferred = new SimpleDeferred<Void>() {

      @Override
      public Deferred<Void> execute() {
        if (!complete) {
          if (writesOutstanding == 0) {
            run();
            complete = true;
          } else {
            closedDeferred = this;
          }
        }
        return this;
      }

      public void run() {
        try {
          ch.close();
          setResult(null);
        } catch (IOException e) {
          setException(e);
        }
      }
    };

    return deferred;
  }

  /**
   * Close the file.<p>
   * The actual close will happen asynchronously. This method must be called using the same event loop the file was opened from.
   */
  public Future<Void> close() {
    return closeDeferred().execute();
  }

  public Deferred<Void> writeDeferred(Buffer buffer, int position) {
    check();
    ByteBuffer bb = buffer.getChannelBuffer().toByteBuffer();
    return doWrite(bb, position);
  }

  /**
   * Write a {@link Buffer} to the file at position {@code position} in the file. If {@code position} lies outside of the current size
   * of the file, the file will be enlarged to encompass it.<p>
   * The actual write will happen asynchronously, when multiple writes are invoked on the same file
   * there are no guarantees as to order in which those writes actually occur.<p>
   * This method must be called using the same event loop the file was opened from.
   */
  public Future<Void> write(Buffer buffer, int position) {
    return writeDeferred(buffer, position).execute();
  }

  public Deferred<Buffer> readDeferred(Buffer buffer, int offset, int position, int length) {
    check();
    ByteBuffer bb = ByteBuffer.allocate(length);
    return doRead(buffer, offset, bb, position);
  }

  /**
   * Reads {@code length} bytes of data from the file at position {@code position} in the file. The read data will be written into the
   * specified {@code Buffer buffer} at position {@code offset}.<p>
   * {@code position + length} must lie within the confines of the file.<p>
   * The actual read will happen asynchronously. When multiple reads are invoked on the same file
   * there are no guarantees as to order in which those reads actually occur.<p>
   * This method must be called using the same event loop the file was opened from.
   */
  public Deferred<Buffer> read(Buffer buffer, int offset, int position, int length) {
    return readDeferred(buffer, offset, position, length).execute();
  }

  /**
   * Return a {@code WriteStream} instance operating on this {@code AsyncFile}.
   */
  public WriteStream getWriteStream() {
    check();
    if (writeStream == null) {
      writeStream = new WriteStream() {
        Handler<Exception> exceptionHandler;
        Handler<Void> drainHandler;

        int pos;
        int maxWrites = 128 * 1024;    // TODO - we should tune this for best performance
        int lwm = maxWrites / 2;

        public void writeBuffer(Buffer buffer) {
          check();
          final int length = buffer.length();
          ByteBuffer bb = buffer.getChannelBuffer().toByteBuffer();

          Deferred<Void> deferred = doWrite(bb, pos);
          deferred.execute();
          deferred.handler(new CompletionHandler<Void>() {

            public void handle(Deferred<Void> deferred) {
              if (deferred.succeeded()) {
                checkContext();
                checkDrained();
                if (writesOutstanding == 0 && closedDeferred != null) {
                  closedDeferred.execute();
                }
              } else {
                handleException(deferred.exception());
              }
            }
          });
          pos += length;
        }

        private void checkDrained() {
          if (drainHandler != null && writesOutstanding <= lwm) {
            Handler<Void> handler = drainHandler;
            drainHandler = null;
            handler.handle(null);
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

        public void drainHandler(Handler<Void> handler) {
          check();
          this.drainHandler = handler;
          checkDrained();
        }

        public void exceptionHandler(Handler<Exception> handler) {
          check();
          this.exceptionHandler = handler;
        }

        void handleException(Exception e) {
          if (exceptionHandler != null) {
            exceptionHandler.handle(e);
          } else {
            e.printStackTrace(System.err);
          }
        }
      };
    }
    return writeStream;
  }

  /**
   * Return a {@code ReadStream} instance operating on this {@code AsyncFile}.
   */
  public ReadStream getReadStream() {
    check();
    if (readStream == null) {
      readStream = new ReadStream() {

        boolean paused;
        Handler<Buffer> dataHandler;
        Handler<Exception> exceptionHandler;
        Handler<Void> endHandler;
        int pos;
        boolean readInProgress;

        void doRead() {
          if (!readInProgress) {
            readInProgress = true;
            Buffer buff = Buffer.create(BUFFER_SIZE);
            Deferred<Buffer> deferred = read(buff, 0, pos, BUFFER_SIZE);

            deferred.handler(new CompletionHandler<Buffer>() {

              public void handle(Deferred<Buffer> deferred) {
                if (deferred.succeeded()) {
                  readInProgress = false;
                  Buffer buffer = deferred.result();
                  if (buffer.length() == 0) {
                    // Empty buffer represents end of file
                    handleEnd();
                  } else {
                    pos += buffer.length();
                    handleData(buffer);
                    if (!paused && dataHandler != null) {
                      doRead();
                    }
                  }
                } else {
                  handleException(deferred.exception());
                }
              }
            });
          }
        }

        public void dataHandler(Handler<Buffer> handler) {
          check();
          this.dataHandler = handler;
          if (dataHandler != null && !paused && !closed) {
            doRead();
          }
        }

        public void exceptionHandler(Handler<Exception> handler) {
          check();
          this.exceptionHandler = handler;
        }

        public void endHandler(Handler<Void> handler) {
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
            exceptionHandler.handle(e);
          } else {
            e.printStackTrace(System.err);
          }
        }

        void handleData(Buffer buffer) {
          if (dataHandler != null) {
            checkContext();
            dataHandler.handle(buffer);
          }
        }

        void handleEnd() {
          if (endHandler != null) {
            checkContext();
            endHandler.handle(null);
          }
        }
      };
    }
    return readStream;
  }

  public Deferred<Void> flushDeferred() {
    checkClosed();
    checkContext();
    return new BlockingTask<Void>() {
      public Void action() throws Exception {
        ch.force(false);
        return null;
      }
    };
  }

  /**
   * Flush any writes made to this file to underlying persistent storage.<p>
   * If the file was opened with {@code flush} set to {@code true} then calling this method will have no effect.<p>
   * The actual flush will happen asynchronously.<p>
   */
  public Future<Void> flush() {
    return flushDeferred().execute();
  }

  private Deferred<Void> doWrite(final ByteBuffer buff, final int position) {
    writesOutstanding += buff.limit();

    SimpleDeferred<Void> sd = new MyDeferred<Void>() {
      public void run() {
        doWrite(buff, position, this);
      }
    };

    return sd;
  }

  private static abstract class MyDeferred<Void> extends SimpleDeferred<Void> {

    public void setResult(Void result) {
      super.setResult(result);
    }

    public void setException(Exception e) {
      super.setException(e);
    }
  }

  private void doWrite(final ByteBuffer buff, final int position, final MyDeferred<Void> deferred) {

    ch.write(buff, position, null, new java.nio.channels.CompletionHandler<Integer, Object>() {

      public void completed(Integer bytesWritten, Object attachment) {

        int pos = position;

        if (buff.hasRemaining()) {
          // partial write
          pos += bytesWritten;
          // resubmit
          doWrite(buff, pos, deferred);
        } else {
          // It's been fully written
          NodexInternal.instance.executeOnContext(contextID, new Runnable() {
            public void run() {
              writesOutstanding -= buff.limit();
              deferred.setResult(null);
            }
          });
        }
      }

      public void failed(Throwable exc, Object attachment) {
        if (exc instanceof Exception) {
          final Exception e = (Exception) exc;
          NodexInternal.instance.executeOnContext(contextID, new Runnable() {
            public void run() {
              deferred.setException(e);
            }
          });
        } else {
          exc.printStackTrace(System.err);
        }
      }
    });
  }

  private Deferred<Buffer> doRead(final Buffer writeBuff, final int offset, final ByteBuffer buff, final int position) {
    SimpleDeferred<Buffer> sd = new MyDeferred<Buffer>() {
      public void run() {
        doRead(writeBuff, offset, buff, position, this);
      }
    };

    return sd;
  }

  private void doRead(final Buffer writeBuff, final int offset, final ByteBuffer buff, final int position, final MyDeferred<Buffer> deferred) {



    ch.read(buff, position, null, new java.nio.channels.CompletionHandler<Integer, Object>() {

      int pos = position;

      private void done() {
        NodexInternal.instance.executeOnContext(contextID, new Runnable() {
          public void run() {
            setContextID();
            buff.flip();
            writeBuff.setBytes(offset, buff);
            deferred.setResult(writeBuff);
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
          doRead(writeBuff, offset, buff, pos, deferred);
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
              deferred.setException(e);
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
