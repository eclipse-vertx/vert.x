/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.file.impl;

import io.netty.buffer.ByteBuf;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.FileSystemException;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.impl.Arguments;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.streams.impl.InboundBuffer;

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
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * This class is optimised for performance when used on the same event loop that is was passed to the handler with.
 * However it can be used safely from other threads.
 *
 * The internal state is protected using the synchronized keyword. If always used on the same event loop, then
 * we benefit from biased locking which makes the overhead of synchronized near zero.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class AsyncFileImpl implements AsyncFile {

  private static final Logger log = LoggerFactory.getLogger(AsyncFile.class);

  public static final int DEFAULT_READ_BUFFER_SIZE = 8192;

  private final VertxInternal vertx;
  private final AsynchronousFileChannel ch;
  private final ContextInternal context;
  private boolean closed;
  private Runnable closedDeferred;
  private long writesOutstanding;
  private boolean overflow;
  private Handler<Throwable> exceptionHandler;
  private Handler<Void> drainHandler;
  private long writePos;
  private int maxWrites = 128 * 1024;    // TODO - we should tune this for best performance
  private int lwm = maxWrites / 2;
  private int readBufferSize = DEFAULT_READ_BUFFER_SIZE;
  private InboundBuffer<Buffer> queue;
  private Handler<Buffer> handler;
  private Handler<Void> endHandler;
  private long readPos;
  private long readLength = Long.MAX_VALUE;

  AsyncFileImpl(VertxInternal vertx, String path, OpenOptions options, ContextInternal context) {
    if (!options.isRead() && !options.isWrite()) {
      throw new FileSystemException("Cannot open file for neither reading nor writing");
    }
    this.vertx = vertx;
    Path file = Paths.get(path);
    HashSet<OpenOption> opts = new HashSet<>();
    if (options.isRead()) opts.add(StandardOpenOption.READ);
    if (options.isWrite()) opts.add(StandardOpenOption.WRITE);
    if (options.isCreate()) opts.add(StandardOpenOption.CREATE);
    if (options.isCreateNew()) opts.add(StandardOpenOption.CREATE_NEW);
    if (options.isSync()) opts.add(StandardOpenOption.SYNC);
    if (options.isDsync()) opts.add(StandardOpenOption.DSYNC);
    if (options.isDeleteOnClose()) opts.add(StandardOpenOption.DELETE_ON_CLOSE);
    if (options.isSparse()) opts.add(StandardOpenOption.SPARSE);
    if (options.isTruncateExisting()) opts.add(StandardOpenOption.TRUNCATE_EXISTING);
    try {
      if (options.getPerms() != null) {
        FileAttribute<?> attrs = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(options.getPerms()));
        ch = AsynchronousFileChannel.open(file, opts, vertx.getWorkerPool(), attrs);
      } else {
        ch = AsynchronousFileChannel.open(file, opts, vertx.getWorkerPool());
      }
      if (options.isAppend()) writePos = ch.size();
    } catch (IOException e) {
      throw new FileSystemException(e);
    }
    this.context = context;
    this.queue = new InboundBuffer<>(context, 0);
    queue.handler(buff -> {
      if (buff.length() > 0) {
        handleBuffer(buff);
      } else {
        handleEnd();
      }
    });
    queue.drainHandler(v -> {
      doRead();
    });
  }

  @Override
  public Future<Void> close() {
    Promise<Void> promise = context.promise();
    closeInternal(promise);
    return promise.future();
  }

  @Override
  public void close(Handler<AsyncResult<Void>> handler) {
    closeInternal(handler);
  }

  @Override
  public Future<Void> end() {
    Promise<Void> promise = context.promise();
    close(promise);
    return promise.future();
  }

  @Override
  public void end(Handler<AsyncResult<Void>> handler) {
    close(handler);
  }

  @Override
  public synchronized AsyncFile read(Buffer buffer, int offset, long position, int length, Handler<AsyncResult<Buffer>> handler) {
    Objects.requireNonNull(handler, "handler");
    read(buffer, offset, position, length).onComplete(handler);
    return this;
  }

  @Override
  public Future<Buffer> read(Buffer buffer, int offset, long position, int length) {
    Promise<Buffer> promise = context.promise();
    Objects.requireNonNull(buffer, "buffer");
    Arguments.require(offset >= 0, "offset must be >= 0");
    Arguments.require(position >= 0, "position must be >= 0");
    Arguments.require(length >= 0, "length must be >= 0");
    check();
    ByteBuffer bb = ByteBuffer.allocate(length);
    doRead(buffer, offset, bb, position, promise);
    return promise.future();
  }

  @Override
  public AsyncFile fetch(long amount) {
    queue.fetch(amount);
    return this;
  }

  @Override
  public void write(Buffer buffer, long position, Handler<AsyncResult<Void>> handler) {
    Objects.requireNonNull(handler, "handler");
    doWrite(buffer, position, handler);
  }

  @Override
  public Future<Void> write(Buffer buffer, long position) {
    Promise<Void> promise = context.promise();
    write(buffer, position, promise);
    return promise.future();
  }

  private synchronized void doWrite(Buffer buffer, long position, Handler<AsyncResult<Void>> handler) {
    Objects.requireNonNull(buffer, "buffer");
    Arguments.require(position >= 0, "position must be >= 0");
    check();
    Handler<AsyncResult<Void>> wrapped = ar -> {
      checkContext();
      Runnable action;
      synchronized (AsyncFileImpl.this) {
        if (writesOutstanding == 0 && closedDeferred != null) {
          action = closedDeferred;
        } else {
          if (overflow && writesOutstanding <= lwm) {
            overflow = false;
            Handler<Void> h = drainHandler;
            if (h != null) {
              action = () -> {
                h.handle(null);
              };
            } else {
              action = null;
            }
          } else {
            action = null;
          }
        }
      }
      if (action != null) {
        action.run();
      }

      if (ar.succeeded()) {
        if (handler != null) {
          handler.handle(ar);
        }
      } else {
        if (handler != null) {
          handler.handle(ar);
        } else {
          handleException(ar.cause());
        }
      }
    };
    ByteBuf buf = buffer.getByteBuf();
    if (buf.nioBufferCount() > 1) {
      doWrite(buf.nioBuffers(), position, wrapped);
    } else {
      ByteBuffer bb = buf.nioBuffer();
      doWrite(bb, position, bb.limit(),  wrapped);
    }
  }

  @Override
  public Future<Void> write(Buffer buffer) {
    Promise<Void> promise = context.promise();
    write(buffer, promise);
    return promise.future();
  }

  @Override
  public synchronized void write(Buffer buffer, Handler<AsyncResult<Void>> handler) {
    int length = buffer.length();
    doWrite(buffer, writePos, handler);
    writePos += length;
  }

  @Override
  public synchronized AsyncFile setWriteQueueMaxSize(int maxSize) {
    Arguments.require(maxSize >= 2, "maxSize must be >= 2");
    check();
    this.maxWrites = maxSize;
    this.lwm = maxWrites / 2;
    return this;
  }

  @Override
  public synchronized AsyncFile setReadBufferSize(int readBufferSize) {
    this.readBufferSize = readBufferSize;
    return this;
  }

  @Override
  public synchronized boolean writeQueueFull() {
    check();
    return overflow;
  }

  @Override
  public synchronized AsyncFile drainHandler(Handler<Void> handler) {
    check();
    this.drainHandler = handler;
    return this;
  }

  @Override
  public synchronized AsyncFile exceptionHandler(Handler<Throwable> handler) {
    check();
    this.exceptionHandler = handler;
    return this;
  }

  @Override
  public synchronized AsyncFile handler(Handler<Buffer> handler) {
    check();
    if (closed) {
      return this;
    }
    this.handler = handler;
    if (handler != null) {
      doRead();
    } else {
      queue.clear();
    }
    return this;
  }

  @Override
  public synchronized AsyncFile endHandler(Handler<Void> handler) {
    check();
    this.endHandler = handler;
    return this;
  }

  @Override
  public synchronized AsyncFile pause() {
    check();
    queue.pause();
    return this;
  }

  @Override
  public synchronized AsyncFile resume() {
    check();
    if (!closed) {
      queue.resume();
    }
    return this;
  }


  @Override
  public Future<Void> flush() {
    Promise<Void> promise = context.promise();
    doFlush(promise);
    return promise.future();
  }

  @Override
  public AsyncFile flush(Handler<AsyncResult<Void>> handler) {
    doFlush(handler);
    return this;
  }

  @Override
  public synchronized AsyncFile setReadPos(long readPos) {
    this.readPos = readPos;
    return this;
  }

  @Override
  public synchronized AsyncFile setReadLength(long readLength) {
    this.readLength = readLength;
    return this;
  }

  @Override
  public synchronized long getReadLength() {
    return readLength;
  }

  @Override
  public synchronized AsyncFile setWritePos(long writePos) {
    this.writePos = writePos;
    return this;
  }

  @Override
  public synchronized long getWritePos() {
    return writePos;
  }

  private void handleException(Throwable t) {
    if (exceptionHandler != null && t instanceof Exception) {
      exceptionHandler.handle(t);
    } else {
      log.error("Unhandled exception", t);

    }
  }

  private synchronized void doWrite(ByteBuffer[] buffers, long position, Handler<AsyncResult<Void>> handler) {
    AtomicInteger cnt = new AtomicInteger();
    AtomicBoolean sentFailure = new AtomicBoolean();
    for (ByteBuffer b: buffers) {
      int limit = b.limit();
      doWrite(b, position, limit, ar -> {
        if (ar.succeeded()) {
          if (cnt.incrementAndGet() == buffers.length) {
            handler.handle(ar);
          }
        } else {
          if (sentFailure.compareAndSet(false, true)) {
            handler.handle(ar);
          }
        }
      });
      position += limit;
    }
  }

  private void doRead() {
    doRead(ByteBuffer.allocate(readBufferSize));
  }

  private synchronized void doRead(ByteBuffer bb) {
    Buffer buff = Buffer.buffer(readBufferSize);
    int readSize = (int) Math.min((long)readBufferSize, readLength);
    bb.limit(readSize);
    Promise<Buffer> promise = context.promise();
    promise.future().onComplete(ar -> {
      if (ar.succeeded()) {
        Buffer buffer = ar.result();
        readPos += buffer.length();
        readLength -= buffer.length();
        // Empty buffer represents end of file
        if (queue.write(buffer) && buffer.length() > 0) {
          doRead(bb);
        }
      } else {
        handleException(ar.cause());
      }
    });
    doRead(buff, 0, bb, readPos, promise);
  }


  private void handleBuffer(Buffer buff) {
    Handler<Buffer> handler;
    synchronized (this) {
      handler = this.handler;
    }
    if (handler != null) {
      checkContext();
      handler.handle(buff);
    }
  }

  private void handleEnd() {
    Handler<Void> endHandler;
    synchronized (this) {
      handler = null;
      endHandler = this.endHandler;
    }
    if (endHandler != null) {
      checkContext();
      endHandler.handle(null);
    }
  }

  private synchronized void doFlush(Handler<AsyncResult<Void>> handler) {
    checkClosed();
    context.executeBlockingInternal((Promise<Void> fut) -> {
      try {
        ch.force(false);
        fut.complete();
      } catch (IOException e) {
        throw new FileSystemException(e);
      }
    }, handler);
  }

  private void doWrite(ByteBuffer buff, long position, long toWrite, Handler<AsyncResult<Void>> handler) {
    if (toWrite > 0) {
      synchronized (this) {
        writesOutstanding += toWrite;
        overflow |= writesOutstanding >= maxWrites;
      }
      writeInternal(buff, position, handler);
    } else {
      handler.handle(Future.succeededFuture());
    }
  }

  private void writeInternal(ByteBuffer buff, long position, Handler<AsyncResult<Void>> handler) {

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
          context.runOnContext((v) -> {
            synchronized (AsyncFileImpl.this) {
              writesOutstanding -= buff.limit();
            }
            handler.handle(Future.succeededFuture());
          });
        }
      }

      public void failed(Throwable exc, Object attachment) {
        if (exc instanceof Exception) {
          context.runOnContext((v) -> {
            synchronized (AsyncFileImpl.this) {
              writesOutstanding -= buff.limit();
            }
            handler.handle(Future.failedFuture(exc));
          });
        } else {
          log.error("Error occurred", exc);
        }
      }
    });
  }

  private void doRead(Buffer writeBuff, int offset, ByteBuffer buff, long position, Promise<Buffer> promise) {

    ch.read(buff, position, null, new java.nio.channels.CompletionHandler<Integer, Object>() {

      long pos = position;

      private void done() {
        buff.flip();
        writeBuff.setBytes(offset, buff);
        buff.compact();
        promise.complete(writeBuff);
      }

      public void completed(Integer bytesRead, Object attachment) {
        if (bytesRead == -1) {
          //End of file
          done();
        } else if (buff.hasRemaining()) {
          // partial read
          pos += bytesRead;
          // resubmit
          doRead(writeBuff, offset, buff, pos, promise);
        } else {
          // It's been fully written
          done();
        }
      }

      public void failed(Throwable t, Object attachment) {
        promise.fail(t);
      }
    });
  }

  private void check() {
    checkClosed();
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
    context.executeBlockingInternal(res -> {
      try {
        ch.close();
        res.complete(null);
      } catch (IOException e) {
        res.fail(e);
      }
    }, handler);
  }

  private synchronized void closeInternal(Handler<AsyncResult<Void>> handler) {
    check();

    closed = true;

    if (writesOutstanding == 0) {
      doClose(handler);
    } else {
      closedDeferred = () -> doClose(handler);
    }
  }
}
