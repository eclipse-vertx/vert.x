/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package org.vertx.java.core.file.impl;

import io.netty.buffer.ByteBuf;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.file.AsyncFile;
import org.vertx.java.core.file.FileSystemException;
import org.vertx.java.core.impl.BlockingAction;
import org.vertx.java.core.impl.DefaultContext;
import org.vertx.java.core.impl.DefaultFutureResult;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DefaultAsyncFile implements AsyncFile {

  private static final Logger log = LoggerFactory.getLogger(AsyncFile.class);
  public static final int BUFFER_SIZE = 8192;

  private final VertxInternal vertx;
  private final AsynchronousFileChannel ch;
  private final DefaultContext context;
  private boolean closed;
  private Runnable closedDeferred;
  private long writesOutstanding;

  private Handler<Throwable> exceptionHandler;
  private Handler<Void> drainHandler;

  private long writePos;
  private int maxWrites = 128 * 1024;    // TODO - we should tune this for best performance
  private int lwm = maxWrites / 2;

  private boolean paused;
  private Handler<Buffer> dataHandler;
  private Handler<Void> endHandler;
  private long readPos;
  private boolean readInProgress;

  DefaultAsyncFile(final VertxInternal vertx, final String path, String perms, final boolean read, final boolean write, final boolean createNew,
            final boolean flush, final DefaultContext context) {
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
    try {
      if (perms != null) {
        FileAttribute<?> attrs = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(perms));
        ch = AsynchronousFileChannel.open(file, options, vertx.getBackgroundPool(), attrs);
      } else {
        ch = AsynchronousFileChannel.open(file, options, vertx.getBackgroundPool());
      }
    } catch (IOException e) {
      throw new FileSystemException(e);
    }
    this.context = context;
  }

  @Override
  public void close() {
    closeInternal(null);
  }

  @Override
  public void close(Handler<AsyncResult<Void>> handler) {
    closeInternal(handler);
  }

  @Override
  public AsyncFile write(Buffer buffer, long position, final Handler<AsyncResult<Void>> handler) {
    check();
    final ByteBuf buf = buffer.getByteBuf();
    if (buf.nioBufferCount() > 1) {
      final Iterator<ByteBuffer> buffers = Arrays.asList(buf.nioBuffers()).iterator();
      doWrite(buffers, position, handler);
    } else {
      ByteBuffer bb = buf.nioBuffer();
      doWrite(bb, position, bb.limit(),  handler);
    }
    return this;
  }

  private void doWrite(final Iterator<ByteBuffer> buffers, final long position, final Handler<AsyncResult<Void>> handler) {
    final ByteBuffer b = buffers.next();
    final int limit = b.limit();
    doWrite(b, position, limit, new Handler<AsyncResult<Void>>() {
      @Override
      public void handle(AsyncResult<Void> event) {
        if (event.failed()) {
          handler.handle(event);
        } else {
          if (buffers.hasNext()) {
            doWrite(buffers, position + limit, handler);
          } else {
            handler.handle(event);
          }
        }
      }
    });
  }
  @Override
  public AsyncFile read(Buffer buffer, int offset, long position, int length, Handler<AsyncResult<Buffer>> handler) {
    check();
    ByteBuffer bb = ByteBuffer.allocate(length);
    doRead(buffer, offset, bb, position, handler);
    return this;
  }

  @Override
  public AsyncFile write(Buffer buffer) {
    check();
    final int length = buffer.length();
    Handler<AsyncResult<Void>> handler = new Handler<AsyncResult<Void>>() {

      public void handle(AsyncResult<Void> deferred) {
        if (deferred.succeeded()) {
          checkContext();
          checkDrained();
          if (writesOutstanding == 0 && closedDeferred != null) {
            closedDeferred.run();
          }
        } else {
          handleException(deferred.cause());
        }
      }
    };

    ByteBuf buf = buffer.getByteBuf();
    if (buf.nioBufferCount() > 1) {
      final Iterator<ByteBuffer> buffers = Arrays.asList(buf.nioBuffers()).iterator();
      doWrite(buffers, writePos, handler);
    } else {
      ByteBuffer bb = buf.nioBuffer();
      doWrite(bb, writePos, bb.limit(), handler);
    }
    writePos += length;
    return this;
  }

  private void checkDrained() {
    if (drainHandler != null && writesOutstanding <= lwm) {
      Handler<Void> handler = drainHandler;
      drainHandler = null;
      handler.handle(null);
    }
  }

  @Override
  public AsyncFile setWriteQueueMaxSize(int maxSize) {
    check();
    this.maxWrites = maxSize;
    this.lwm = maxWrites / 2;
    return this;
  }

  @Override
  public boolean writeQueueFull() {
    check();
    return writesOutstanding >= maxWrites;
  }

  @Override
  public AsyncFile drainHandler(Handler<Void> handler) {
    check();
    this.drainHandler = handler;
    checkDrained();
    return this;
  }

  @Override
  public AsyncFile exceptionHandler(Handler<Throwable> handler) {
    check();
    this.exceptionHandler = handler;
    return this;
  }

  private void handleException(Throwable t) {
    if (exceptionHandler != null && t instanceof Exception) {
      exceptionHandler.handle(t);
    } else {
      log.error("Unhandled exception", t);

    }
  }

  private void doRead() {
    if (!readInProgress) {
      readInProgress = true;
      Buffer buff = new Buffer(BUFFER_SIZE);
      read(buff, 0, readPos, BUFFER_SIZE, new Handler<AsyncResult<Buffer>>() {

        public void handle(AsyncResult<Buffer> ar) {
          if (ar.succeeded()) {
            readInProgress = false;
            Buffer buffer = ar.result();
            if (buffer.length() == 0) {
              // Empty buffer represents end of file
              handleEnd();
            } else {
              readPos += buffer.length();
              handleData(buffer);
              if (!paused && dataHandler != null) {
                doRead();
              }
            }
          } else {
            handleException(ar.cause());
          }
        }
      });
    }
  }

  @Override
  public AsyncFile dataHandler(Handler<Buffer> handler) {
    check();
    this.dataHandler = handler;
    if (dataHandler != null && !paused && !closed) {
      doRead();
    }
    return this;
  }

  @Override
  public AsyncFile endHandler(Handler<Void> handler) {
    check();
    this.endHandler = handler;
    return this;
  }

  @Override
  public AsyncFile pause() {
    check();
    paused = true;
    return this;
  }

  @Override
  public AsyncFile resume() {
    check();
    if (paused && !closed) {
      paused = false;
      if (dataHandler != null) {
        doRead();
      }
    }
    return this;
  }

  private void handleData(Buffer buffer) {
    if (dataHandler != null) {
      checkContext();
      dataHandler.handle(buffer);
    }
  }

  private void handleEnd() {
    if (endHandler != null) {
      checkContext();
      endHandler.handle(null);
    }
  }

  @Override
  public AsyncFile flush() {
    doFlush(null);
    return this;
  }

  @Override
  public AsyncFile flush(Handler<AsyncResult<Void>> handler) {
    doFlush(handler);
    return this;
  }

  private void doFlush(Handler<AsyncResult<Void>> handler) {
    checkClosed();
    checkContext();
    new BlockingAction<Void>(vertx, handler) {
      public Void action() {
        try {
          ch.force(false);
          return null;
        } catch (IOException e) {
          throw new FileSystemException(e);
        }
      }
    }.run();
  }

  private void doWrite(final ByteBuffer buff, final long position, final long toWrite, final Handler<AsyncResult<Void>> handler) {
    writesOutstanding += toWrite;
    writeInternal(buff, position, handler);
  }

  private void writeInternal(final ByteBuffer buff, final long position, final Handler<AsyncResult<Void>> handler) {

    ch.write(buff, position, null, new java.nio.channels.CompletionHandler<Integer, Object>() {

      public void completed(Integer bytesWritten, Object attachment) {

        long pos = position;

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
              handler.handle(new DefaultFutureResult<Void>().setResult(null));
            }
          });
        }
      }

      public void failed(Throwable exc, Object attachment) {
        if (exc instanceof Exception) {
          final Exception e = (Exception) exc;
          context.execute(new Runnable() {
            public void run() {
              handler.handle(new DefaultFutureResult<Void>().setResult(null));
            }
          });
        } else {
          log.error("Error occurred", exc);
        }
      }
    });
  }

  private void doRead(final Buffer writeBuff, final int offset, final ByteBuffer buff, final long position, final Handler<AsyncResult<Buffer>> handler) {

    ch.read(buff, position, null, new java.nio.channels.CompletionHandler<Integer, Object>() {

      long pos = position;

      final DefaultFutureResult<Buffer> result = new DefaultFutureResult<>();

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

      public void failed(final Throwable t, Object attachment) {
        context.execute(new Runnable() {
          public void run() {
            result.setFailure(t).setHandler(handler);
          }
        });
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

  private void doClose(Handler<AsyncResult<Void>> handler) {
    DefaultFutureResult<Void> res = new DefaultFutureResult<>();
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

  private void closeInternal(final Handler<AsyncResult<Void>> handler) {
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
