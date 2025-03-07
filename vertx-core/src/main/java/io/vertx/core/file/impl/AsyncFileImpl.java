/*
 * Copyright (c) 2011-2021 Contributors to the Eclipse Foundation
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
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.AsyncFileLock;
import io.vertx.core.file.FileSystemException;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.impl.Arguments;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.streams.impl.InboundBuffer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.channels.FileLock;
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
        ch = AsynchronousFileChannel.open(file, opts, vertx.workerPool().executor(), attrs);
      } else {
        ch = AsynchronousFileChannel.open(file, opts, vertx.workerPool().executor());
      }
      if (options.isAppend()) writePos = ch.size();
    } catch (IOException e) {
      throw new FileSystemException(FileSystemImpl.getFileAccessErrorMessage("open", path), e);
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
  public Future<Void> end() {
    return close();
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
  public Future<Void> write(Buffer buffer, long position) {
    Promise<Void> promise = context.promise();
    doWrite(buffer, position, promise::handle);
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
    ByteBuf buf = ((BufferInternal)buffer).getByteBuf();
    if (buf.nioBufferCount() > 1) {
      doWrite(buf.nioBuffers(), position, wrapped);
    } else {
      ByteBuffer bb = buf.nioBuffer();
      doWrite(bb, position, bb.limit(),  wrapped);
    }
  }

  @Override
  public synchronized Future<Void> write(Buffer buffer) {
    Promise<Void> promise = context.promise();
    int length = buffer.length();
    doWrite(buffer, writePos, promise::handle);
    writePos += length;
    return promise.future();
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
    doFlush(promise::handle);
    return promise.future();
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
      context.reportException(t);
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
    if (handler == null) {
      return;
    }
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
    context.<Void>executeBlockingInternal(() -> {
      try {
        ch.force(false);
        return null;
      } catch (IOException e) {
        throw new FileSystemException(e);
      }
    }).onComplete(handler);
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

  private void doClose(Promise<Void> handler) {
    context.<Void>executeBlockingInternal(() -> {
      ch.close();
      return null;
    }).onComplete(handler);
  }

  private synchronized void closeInternal(Promise<Void> handler) {
    check();

    closed = true;

    if (writesOutstanding == 0) {
      doClose(handler);
    } else {
      closedDeferred = () -> doClose(handler);
    }
  }

  @Override
  public long sizeBlocking() {
    try {
      return ch.size();
    } catch (IOException e) {
      throw new FileSystemException(e);
    }
  }

  @Override
  public Future<Long> size() {
    return vertx.executeBlockingInternal(this::sizeBlocking);
  }

  @Override
  public AsyncFileLock tryLock(long position, long size, boolean shared) {
    try {
      return new AsyncFileLockImpl(vertx, ch.tryLock(position, size, shared));
    } catch (IOException e) {
      throw new FileSystemException(e);
    }
  }

  private static CompletionHandler<FileLock, PromiseInternal<AsyncFileLock>> LOCK_COMPLETION = new CompletionHandler<FileLock, PromiseInternal<AsyncFileLock>>() {
    @Override
    public void completed(FileLock result, PromiseInternal<AsyncFileLock> p) {
      p.complete(new AsyncFileLockImpl(p.context().owner(), result));
    }

    @Override
    public void failed(Throwable t, PromiseInternal<AsyncFileLock> p) {
      p.fail(new FileSystemException(t));
    }
  };

  @Override
  public Future<AsyncFileLock> lock(long position, long size, boolean shared) {
    PromiseInternal<AsyncFileLock> promise = vertx.promise();
    vertx.executeBlockingInternal(() -> {
      ch.lock(position, size, shared, promise, LOCK_COMPLETION);
      return null;
    }).onComplete(ar -> {
      if (ar.failed()) {
        // Happens only if ch.lock throws a RuntimeException
        promise.fail(new FileSystemException(ar.cause()));
      }
    });
    return promise.future();
  }
}
