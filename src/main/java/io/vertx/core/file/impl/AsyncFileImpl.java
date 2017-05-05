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

package io.vertx.core.file.impl;

import io.netty.buffer.ByteBuf;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.FileSystemException;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.impl.Arguments;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

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
 * This class is optimised for performance when used on the same event loop that is was passed to the handler with.
 * However it can be used safely from other threads.
 * <p>
 * The internal state is protected using the synchronized keyword. If always used on the same event loop, then
 * we benefit from biased locking which makes the overhead of synchronized near zero.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
// TODO: 16/12/26 by zmyer
public class AsyncFileImpl implements AsyncFile {

    private static final Logger log = LoggerFactory.getLogger(AsyncFile.class);
    //默认的读取缓存区大小
    public static final int DEFAULT_READ_BUFFER_SIZE = 8192;
    //vertx节点对象
    private final VertxInternal vertx;
    //异步文件通道对象
    private final AsynchronousFileChannel ch;
    //执行上下文对象
    private final ContextImpl context;
    //是否关闭文件对象
    private boolean closed;
    private Runnable closedDeferred;
    private long writesOutstanding;
    //异常处理对象
    private Handler<Throwable> exceptionHandler;
    private Handler<Void> drainHandler;
    //文件写入位置
    private long writePos;
    //最大的写入大小
    private int maxWrites = 128 * 1024;    // TODO - we should tune this for best performance
    private int lwm = maxWrites / 2;
    //读取缓冲区大小
    private int readBufferSize = DEFAULT_READ_BUFFER_SIZE;
    //是否暂停
    private boolean paused;
    //数据处理对象
    private Handler<Buffer> dataHandler;
    //结束处理对象
    private Handler<Void> endHandler;
    //文件读入位置
    private long readPos;
    private boolean readInProgress;

    // TODO: 16/12/26 by zmyer
    AsyncFileImpl(VertxInternal vertx, String path, OpenOptions options, ContextImpl context) {
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
    public void end() {
        close();
    }

    // TODO: 16/12/26 by zmyer
    @Override
    public synchronized AsyncFile read(Buffer buffer, int offset, long position, int length, Handler<AsyncResult<Buffer>> handler) {
        Objects.requireNonNull(buffer, "buffer");
        Objects.requireNonNull(handler, "handler");
        Arguments.require(offset >= 0, "offset must be >= 0");
        Arguments.require(position >= 0, "position must be >= 0");
        Arguments.require(length >= 0, "length must be >= 0");
        check();
        //首先分配读取缓冲区对象
        ByteBuffer bb = ByteBuffer.allocate(length);
        //开始读取
        doRead(buffer, offset, bb, position, handler);
        return this;
    }

    // TODO: 16/12/26 by zmyer
    @Override
    public AsyncFile write(Buffer buffer, long position, Handler<AsyncResult<Void>> handler) {
        Objects.requireNonNull(handler, "handler");
        //开始写入文件
        return doWrite(buffer, position, handler);
    }

    // TODO: 16/12/26 by zmyer
    private synchronized AsyncFile doWrite(Buffer buffer, long position, Handler<AsyncResult<Void>> handler) {
        Objects.requireNonNull(buffer, "buffer");
        Arguments.require(position >= 0, "position must be >= 0");
        check();
        //异步写入处理对象
        Handler<AsyncResult<Void>> wrapped = ar -> {
            if (ar.succeeded()) {
                checkContext();
                checkDrained();
                if (writesOutstanding == 0 && closedDeferred != null) {
                    closedDeferred.run();
                }
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
            //开始写入消息
            doWrite(buf.nioBuffers(), position, wrapped);
        } else {
            ByteBuffer bb = buf.nioBuffer();
            //开始写入消息
            doWrite(bb, position, bb.limit(), wrapped);
        }
        return this;
    }

    // TODO: 16/12/26 by zmyer
    @Override
    public synchronized AsyncFile write(Buffer buffer) {
        int length = buffer.length();
        //开始写入消息
        doWrite(buffer, writePos, null);
        writePos += length;
        return this;
    }

    // TODO: 16/12/26 by zmyer
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
        return writesOutstanding >= maxWrites;
    }

    @Override
    public synchronized AsyncFile drainHandler(Handler<Void> handler) {
        check();
        this.drainHandler = handler;
        checkDrained();
        return this;
    }

    @Override
    public synchronized AsyncFile exceptionHandler(Handler<Throwable> handler) {
        check();
        this.exceptionHandler = handler;
        return this;
    }

    // TODO: 16/12/26 by zmyer
    @Override
    public synchronized AsyncFile handler(Handler<Buffer> handler) {
        check();
        //设置数据处理对象
        this.dataHandler = handler;
        if (dataHandler != null && !paused && !closed) {
            //开始读取消息
            doRead();
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
        paused = true;
        return this;
    }

    // TODO: 16/12/26 by zmyer
    @Override
    public synchronized AsyncFile resume() {
        check();
        if (paused && !closed) {
            paused = false;
            if (dataHandler != null) {
                doRead();
            }
        }
        return this;
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

    @Override
    public synchronized AsyncFile setReadPos(long readPos) {
        this.readPos = readPos;
        return this;
    }

    @Override
    public synchronized AsyncFile setWritePos(long writePos) {
        this.writePos = writePos;
        return this;
    }

    private synchronized void checkDrained() {
        if (drainHandler != null && writesOutstanding <= lwm) {
            Handler<Void> handler = drainHandler;
            drainHandler = null;
            handler.handle(null);
        }
    }

    private void handleException(Throwable t) {
        if (exceptionHandler != null && t instanceof Exception) {
            exceptionHandler.handle(t);
        } else {
            log.error("Unhandled exception", t);

        }
    }

    // TODO: 16/12/26 by zmyer
    private synchronized void doWrite(ByteBuffer[] buffers, long position, Handler<AsyncResult<Void>> handler) {
        AtomicInteger cnt = new AtomicInteger();
        AtomicBoolean sentFailure = new AtomicBoolean();
        for (ByteBuffer b : buffers) {
            int limit = b.limit();
            //开始写入缓冲区
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

    // TODO: 16/12/26 by zmyer
    private synchronized void doRead() {
        if (!readInProgress) {
            readInProgress = true;
            Buffer buff = Buffer.buffer(readBufferSize);
            //开始读取文件
            read(buff, 0, readPos, readBufferSize, ar -> {
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
            });
        }
    }

    // TODO: 16/12/26 by zmyer
    private synchronized void handleData(Buffer buffer) {
        if (dataHandler != null) {
            checkContext();
            dataHandler.handle(buffer);
        }
    }

    private synchronized void handleEnd() {
        if (endHandler != null) {
            checkContext();
            endHandler.handle(null);
        }
    }

    // TODO: 16/12/26 by zmyer
    private synchronized void doFlush(Handler<AsyncResult<Void>> handler) {
        checkClosed();
        context.executeBlocking(() -> {
            try {
                //开始强制刷新文件通道
                ch.force(false);
                return null;
            } catch (IOException e) {
                throw new FileSystemException(e);
            }
        }, handler);
    }

    // TODO: 16/12/26 by zmyer
    private void doWrite(ByteBuffer buff, long position, long toWrite, Handler<AsyncResult<Void>> handler) {
        if (toWrite == 0) {
            throw new IllegalStateException("Cannot save zero bytes");
        }
        writesOutstanding += toWrite;
        //开始写入缓冲区
        writeInternal(buff, position, handler);
    }

    // TODO: 16/12/26 by zmyer
    private void writeInternal(ByteBuffer buff, long position, Handler<AsyncResult<Void>> handler) {
        //向文件通道对象写入缓冲区
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
                        writesOutstanding -= buff.limit();
                        handler.handle(Future.succeededFuture());
                    });
                }
            }

            public void failed(Throwable exc, Object attachment) {
                if (exc instanceof Exception) {
                    context.runOnContext((v) -> handler.handle(Future.succeededFuture()));
                } else {
                    log.error("Error occurred", exc);
                }
            }
        });
    }

    // TODO: 16/12/26 by zmyer
    private void doRead(Buffer writeBuff, int offset, ByteBuffer buff, long position, Handler<AsyncResult<Buffer>> handler) {
        //开始从文件通道对象中读取内容
        ch.read(buff, position, null, new java.nio.channels.CompletionHandler<Integer, Object>() {
            long pos = position;

            private void done() {
                context.runOnContext((v) -> {
                    buff.flip();
                    writeBuff.setBytes(offset, buff);
                    handler.handle(Future.succeededFuture(writeBuff));
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

            public void failed(Throwable t, Object attachment) {
                context.runOnContext((v) -> handler.handle(Future.failedFuture(t)));
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

    // TODO: 16/12/26 by zmyer
    private void doClose(Handler<AsyncResult<Void>> handler) {
        ContextImpl handlerContext = vertx.getOrCreateContext();
        handlerContext.executeBlocking(res -> {
            try {
                //关闭文件通道对象
                ch.close();
                res.complete(null);
            } catch (IOException e) {
                res.fail(e);
            }
        }, handler);
    }

    // TODO: 16/12/26 by zmyer
    private synchronized void closeInternal(Handler<AsyncResult<Void>> handler) {
        check();

        closed = true;

        if (writesOutstanding == 0) {
            //关闭通道对象
            doClose(handler);
        } else {
            closedDeferred = () -> doClose(handler);
        }
    }

}
