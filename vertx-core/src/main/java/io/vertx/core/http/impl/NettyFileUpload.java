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

package io.vertx.core.http.impl;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.impl.InboundBuffer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public final class NettyFileUpload implements FileUpload, ReadStream<Buffer> {

  private final String name;
  private String contentType;
  private String filename;
  private String contentTransferEncoding;
  private Charset charset;
  private boolean completed;
  private long maxSize = -1;
  private final HttpServerRequest request;
  private final InboundBuffer<Object> pending;
  private Handler<Void> endHandler;
  private Handler<Throwable> exceptionHandler;
  private Handler<Buffer> dataHandler;
  private final long size;

  NettyFileUpload(Context context, HttpServerRequest request, String name, String filename, String contentType, String contentTransferEncoding, Charset charset, long size) {
    this.name = name;
    this.filename = filename;
    this.contentType = contentType;
    this.contentTransferEncoding = contentTransferEncoding;
    this.charset = charset;
    this.request = request;
    this.size = size;
    this.pending = new InboundBuffer<>(context)
      .drainHandler(v -> request.resume())
      .handler(buff -> {
        if (buff == InboundBuffer.END_SENTINEL) {
          Handler<Void> handler = endHandler();
          if (handler != null) {
            handler.handle(null);
          }
        } else {
          Handler<Buffer> handler = handler();
          if (handler != null) {
            handler.handle((Buffer) buff);
          }
        }
      });
  }

  @Override
  public synchronized NettyFileUpload exceptionHandler(Handler<Throwable> handler) {
    exceptionHandler = handler;
    return this;
  }

  private Handler<Buffer> handler() {
    return dataHandler;
  }

  @Override
  public synchronized NettyFileUpload handler(Handler<Buffer> handler) {
    dataHandler = handler;
    return this;
  }

  @Override
  public NettyFileUpload pause() {
    pending.pause();
    return this;
  }

  @Override
  public NettyFileUpload resume() {
    return fetch(Long.MAX_VALUE);
  }

  @Override
  public NettyFileUpload fetch(long amount) {
    pending.fetch(amount);
    return this;
  }

  private synchronized Handler<Void> endHandler() {
    return endHandler;
  }

  @Override
  public synchronized NettyFileUpload endHandler(Handler<Void> handler) {
    endHandler = handler;
    return this;
  }

  private void receiveData(Buffer data) {
    if (data.length() != 0) {
      if (!pending.write(data)) {
        request.pause();
      }
    }
  }

  private void end() {
    pending.write(InboundBuffer.END_SENTINEL);
  }

  public void handleException(Throwable err) {
    Handler<Throwable> handler;
    synchronized (this) {
      handler = exceptionHandler;
    }
    if (handler != null) {
      handler.handle(err);
    }
  }

  @Override
  public void setContent(ByteBuf channelBuffer) throws IOException {
    completed = true;
    receiveData(BufferInternal.buffer(channelBuffer));
    end();
  }

  @Override
  public void addContent(ByteBuf channelBuffer, boolean last) throws IOException {
    receiveData(BufferInternal.buffer(channelBuffer));
    if (last) {
      completed = true;
      end();
    }
  }

  @Override
  public void setContent(File file) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setContent(InputStream inputStream) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isCompleted() {
    return completed;
  }

  @Override
  public long length() {
    return size;
  }

  @Override
  public void delete() {
    throw new UnsupportedOperationException();
  }

  @Override
  public long definedLength() {
    return size;
  }

  @Override
  public void checkSize(long newSize) throws IOException {
    if (maxSize >= 0 && newSize > maxSize) {
      throw new IOException("Size exceed allowed maximum capacity");
    }
  }

  @Override
  public long getMaxSize() {
    return maxSize;
  }

  @Override
  public void setMaxSize(long maxSize) {
    this.maxSize = maxSize;
  }

  @Override
  public byte[] get() throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public ByteBuf getChunk(int i) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getString() throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getString(Charset charset) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setCharset(Charset charset) {
    this.charset = charset;
  }

  @Override
  public Charset getCharset() {
    return charset;
  }

  @Override
  public boolean renameTo(File file) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isInMemory() {
    return false;
  }

  @Override
  public File getFile() throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public HttpDataType getHttpDataType() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int compareTo(InterfaceHttpData o) {
    return 0;
  }

  @Override
  public String getFilename() {
    return filename;
  }

  @Override
  public void setFilename(String filename) {
    this.filename = filename;
  }

  @Override
  public void setContentType(String contentType) {
    this.contentType = contentType;
  }

  @Override
  public String getContentType() {
    return contentType;
  }

  @Override
  public void setContentTransferEncoding(String contentTransferEncoding) {
    this.contentTransferEncoding = contentTransferEncoding;
  }

  @Override
  public String getContentTransferEncoding() {
    return contentTransferEncoding;
  }

  @Override
  public ByteBuf getByteBuf() throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public FileUpload copy() {
    throw new UnsupportedOperationException();
  }

  //@Override
  public FileUpload duplicate() {
    throw new UnsupportedOperationException();
  }

  @Override
  public FileUpload retainedDuplicate() {
    throw new UnsupportedOperationException();
  }

  @Override
  public FileUpload replace(ByteBuf content) {
    throw new UnsupportedOperationException();
  }

  @Override
  public FileUpload retain() {
    return this;
  }

  @Override
  public FileUpload retain(int increment) {
    return this;
  }

  @Override
  public FileUpload touch(Object hint) {
    return this;
  }

  @Override
  public FileUpload touch() {
    return this;
  }

  @Override
  public ByteBuf content() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int refCnt() {
    return 1;
  }

  @Override
  public boolean release() {
    return false;
  }

  @Override
  public boolean release(int decrement) {
    return false;
  }
}
