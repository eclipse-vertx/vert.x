/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.core.file.impl;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.file.AsyncFile;
import org.vertx.java.core.file.FileSystemException;
import org.vertx.java.core.impl.BlockingAction;
import org.vertx.java.core.impl.Context;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.streams.ReadStream;
import org.vertx.java.core.streams.WriteStream;

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
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DefaultAsyncFile implements AsyncFile {

  private static final Logger log = LoggerFactory.getLogger(AsyncFile.class);

  private final VertxInternal vertx;
  private final AsynchronousFileChannel ch;
  private final Context context;
  private boolean closed;
  private ReadStream readStream;
  private WriteStream writeStream;
  private Runnable closedDeferred;
  private long writesOutstanding;

  DefaultAsyncFile(final VertxInternal vertx, final String path, String perms, final boolean read, final boolean write, final boolean createNew,
            final boolean flush, final Context context) throws Exception {
    if (!read && !write) {
      throw new FileSystemException("Cannot open file for neither reading nor writing");
    }
    this.vertx = vertx;
    Path file = Paths.get(path);
    HashSet<OpenOption> options = new HashSet<>();
    if (read) options.add(StandardOpenOption.READ);
    if (write) options.add(StandardOpenOption.WRITE);
    if (createNew) options.add(StandardOpenOption.CREATE);
    if (flush) options.add(StandardOpenOption.DSYNC);
    if (perms != null) {
      FileAttribute<?> attrs = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(perms));
      ch = AsynchronousFileChannel.open(file, options, vertx.getBackgroundPool(), attrs);
    } else {
      ch = AsynchronousFileChannel.open(file, options, vertx.getBackgroundPool());
    }
    this.context = context;
  }

  public void close() {
    closeInternal(null);
  }

  public void close(AsyncResultHandler<Void> handler) {
    closeInternal(handler);
  }

  public void write(Buffer buffer, int position, AsyncResultHandler<Void> handler) {
    check();
    ByteBuffer bb = buffer.getChannelBuffer().toByteBuffer();
    doWrite(bb, position, handler);
  }

  public void read(Buffer buffer, int offset, int position, int length, AsyncResultHandler<Buffer> handler) {
    check();
    ByteBuffer bb = ByteBuffer.allocate(length);
    doRead(buffer, offset, bb, position, handler);
  }

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

          doWrite(bb, pos, new AsyncResultHandler<Void>() {

            public void handle(AsyncResult<Void> deferred) {
              if (deferred.succeeded()) {
                checkContext();
                checkDrained();
                if (writesOutstanding == 0 && closedDeferred != null) {
                  closedDeferred.run();
                }
              } else {
                handleException(deferred.exception);
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
            log.error("Unhandled exception", e);
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
        Handler<Buffer> dataHandler;
        Handler<Exception> exceptionHandler;
        Handler<Void> endHandler;
        int pos;
        boolean readInProgress;

        void doRead() {
          if (!readInProgress) {
            readInProgress = true;
            Buffer buff = new Buffer(BUFFER_SIZE);
            read(buff, 0, pos, BUFFER_SIZE, new AsyncResultHandler<Buffer>() {

              public void handle(AsyncResult<Buffer> ar) {
                if (ar.succeeded()) {
                  readInProgress = false;
                  Buffer buffer = ar.result;
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
                  handleException(ar.exception);
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
            log.error("Unhandled exception", e);
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

  public void flush() {
    doFlush(null);
  }

  public void flush(AsyncResultHandler<Void> handler) {
    doFlush(handler);
  }

  private void doFlush(AsyncResultHandler<Void> handler) {
    checkClosed();
    checkContext();
    new BlockingAction<Void>(vertx, handler) {
      public Void action() throws Exception {
        ch.force(false);
        return null;
      }
    }.run();
  }

  private void doWrite(final ByteBuffer buff, final int position, final AsyncResultHandler<Void> handler) {
    writesOutstanding += buff.limit();
    writeInternal(buff, position, handler);
  }

  private void writeInternal(final ByteBuffer buff, final int position, final AsyncResultHandler<Void> handler) {

    ch.write(buff, position, null, new java.nio.channels.CompletionHandler<Integer, Object>() {

      public void completed(Integer bytesWritten, Object attachment) {

        int pos = position;

        if (buff.hasRemaining()) {
          // partial write
          pos += bytesWritten;
          // resubmit
          writeInternal(buff, pos, handler);
        } else {
          // It's been fully written
          context.execute(new Runnable() {
            public void run() {
              writesOutstanding -= buff.limit();
              new AsyncResult<Void>().setResult(null).setHandler(handler);
            }
          });
        }
      }

      public void failed(Throwable exc, Object attachment) {
        if (exc instanceof Exception) {
          final Exception e = (Exception) exc;
          context.execute(new Runnable() {
            public void run() {
              new AsyncResult<Void>().setFailure(e).setHandler(handler);
            }
          });
        } else {
          log.error("Error occurred", exc);
        }
      }
    });
  }

  private void doRead(final Buffer writeBuff, final int offset, final ByteBuffer buff, final int position, final AsyncResultHandler<Buffer> handler) {

    ch.read(buff, position, null, new java.nio.channels.CompletionHandler<Integer, Object>() {

      int pos = position;

      final AsyncResult<Buffer> result = new AsyncResult<>();

      private void done() {
        context.execute(new Runnable() {
          public void run() {
            buff.flip();
            writeBuff.setBytes(offset, buff);
            result.setResult(writeBuff).setHandler(handler);
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
          doRead(writeBuff, offset, buff, pos, handler);
        } else {
          // It's been fully written
          done();
        }
      }

      public void failed(Throwable exc, Object attachment) {
        if (exc instanceof Exception) {
          final Exception e = (Exception) exc;
          context.execute(new Runnable() {
            public void run() {
              result.setFailure(e).setHandler(handler);
            }
          });
        } else {
          vertx.reportException(exc);
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

  private void checkContext() {
    if (!vertx.getContext().equals(context)) {
      throw new IllegalStateException("AsyncFile must only be used in the context that created it, expected: "
          + context + " actual " + vertx.getContext());
    }
  }

  private void doClose(AsyncResultHandler<Void> handler) {
    AsyncResult<Void> res = new AsyncResult<>();
    try {
      ch.close();
      res.setResult(null);
    } catch (IOException e) {
      res.setFailure(e);
    }
    if (handler != null) {
      handler.handle(res);
    }
  }

  private void closeInternal(final AsyncResultHandler<Void> handler) {
    check();

    closed = true;

    if (writesOutstanding == 0) {
      doClose(handler);
    } else {
      closedDeferred = new Runnable() {
        public void run() {
          doClose(handler);
        }
      };
    }
  }

}
