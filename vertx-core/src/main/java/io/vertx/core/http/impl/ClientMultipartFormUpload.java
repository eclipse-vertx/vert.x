/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
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
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.multipart.*;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.internal.concurrent.InboundMessageChannel;
import io.vertx.core.internal.http.HttpHeadersInternal;
import io.vertx.core.streams.Pipe;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

/**
 * A stream that sends a multipart form.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ClientMultipartFormUpload implements ReadStream<Buffer> {

  private static final Object END_SENTINEL = new Object();

  private static final UnpooledByteBufAllocator ALLOC = new UnpooledByteBufAllocator(false);

  private DefaultFullHttpRequest request;
  private HttpPostRequestEncoder encoder;
  private Handler<Throwable> exceptionHandler;
  private Handler<Buffer> dataHandler;
  private Handler<Void> endHandler;
  private final InboundMessageChannel<Object> pending;
  private boolean writable;
  private boolean ended;
  private final ContextInternal context;

  public ClientMultipartFormUpload(ContextInternal context,
                                   ClientMultipartFormImpl parts,
                                   boolean multipart,
                                   HttpPostRequestEncoder.EncoderMode encoderMode) throws Exception {
    this.context = context;
    this.writable = true;
    this.pending = new InboundMessageChannel<>(context.executor(), context.executor()) {
      @Override
      protected void handleResume() {
        writable = true;
        pump();
      }
      @Override
      protected void handlePause() {
        writable = false;
      }
      @Override
      protected void handleMessage(Object msg) {
        handleChunk(msg);
      }
    };
    this.request = new DefaultFullHttpRequest(
      HttpVersion.HTTP_1_1,
      HttpMethod.POST,
      "/");
    parts.charset();
    Charset charset = parts.charset() != null ? parts.charset() : HttpConstants.DEFAULT_CHARSET;
    this.encoder = new HttpPostRequestEncoder(
      new DefaultHttpDataFactory(DefaultHttpDataFactory.MINSIZE, charset) {
        @Override
        public Attribute createAttribute(HttpRequest request, String name, String value) {
          try {
            return new MemoryAttribute(name, value, charset);
          } catch (IOException e) {
            throw new IllegalArgumentException(e);
          }
        }

        @Override
        public FileUpload createFileUpload(HttpRequest request, String name, String filename, String contentType, String contentTransferEncoding, Charset _charset, long size) {
          if (_charset == null) {
            _charset = charset;
          }
          return super.createFileUpload(request, name, filename, contentType, contentTransferEncoding, _charset, size);
        }
      },
      request,
      multipart,
      charset,
      encoderMode);
    for (ClientMultipartFormDataPart formDataPart : parts) {
      if (formDataPart.isAttribute()) {
        encoder.addBodyAttribute(formDataPart.name(), formDataPart.value());
      } else {
        String pathname = formDataPart.pathname();
        if (pathname != null) {
          encoder.addBodyFileUpload(formDataPart.name(),
            formDataPart.filename(), new File(formDataPart.pathname()),
            formDataPart.mediaType(), formDataPart.isText());
        } else {
          String contentType = formDataPart.mediaType();
          if (formDataPart.mediaType() == null) {
            if (formDataPart.isText()) {
              contentType = "text/plain";
            } else {
              contentType = "application/octet-stream";
            }
          }
          String transferEncoding = formDataPart.isText() ? null : "binary";
          MemoryFileUpload fileUpload = new MemoryFileUpload(
            formDataPart.name(),
            formDataPart.filename(),
            contentType, transferEncoding, null, formDataPart.content().length());
          fileUpload.setContent(((BufferInternal)formDataPart.content()).getByteBuf());
          encoder.addBodyHttpData(fileUpload);
        }
      }
    }
    encoder.finalizeRequest();
  }

  private void handleChunk(Object item) {
    Handler handler;
    synchronized (ClientMultipartFormUpload.this) {
      if (item instanceof Buffer) {
        handler = dataHandler;
      } else if (item instanceof Throwable) {
        handler = exceptionHandler;
      } else if (item == END_SENTINEL) {
        handler = endHandler;
        item = null;
      } else {
        return;
      }
    }
    handler.handle(item);
  }

  public void pump() {
    if (!context.inThread()) {
      throw new IllegalArgumentException();
    }
    while (!ended) {
      if (encoder.isChunked()) {
        try {
          HttpContent chunk = encoder.readChunk(ALLOC);
          ByteBuf content = chunk.content();
          Buffer buff = BufferInternal.buffer(content);
          pending.write(buff);
          if (encoder.isEndOfInput()) {
            ended = true;
            request = null;
            encoder = null;
            pending.write(END_SENTINEL);
          } else if (!writable) {
            break;
          }
        } catch (Exception e) {
          ended = true;
          request = null;
          encoder = null;
          pending.write(e);
          break;
        }
      } else {
        ByteBuf content = request.content();
        Buffer buffer = BufferInternal.buffer(content);
        request = null;
        encoder = null;
        pending.write(buffer);
        ended = true;
        pending.write(END_SENTINEL);
      }
    }
  }

  public MultiMap headers() {
    return HttpHeadersInternal.headers(request.headers());
  }

  @Override
  public synchronized ClientMultipartFormUpload exceptionHandler(Handler<Throwable> handler) {
    exceptionHandler = handler;
    return this;
  }

  @Override
  public synchronized ClientMultipartFormUpload handler(Handler<Buffer> handler) {
    dataHandler = handler;
    return this;
  }

  @Override
  public synchronized ClientMultipartFormUpload pause() {
    pending.pause();
    return this;
  }

  @Override
  public ReadStream<Buffer> fetch(long amount) {
    pending.fetch(amount);
    return this;
  }

  @Override
  public synchronized ClientMultipartFormUpload resume() {
    pending.fetch(Long.MAX_VALUE);
    return this;
  }

  @Override
  public synchronized ClientMultipartFormUpload endHandler(Handler<Void> handler) {
    endHandler = handler;
    return this;
  }

  @Override
  public Future<Void> pipeTo(WriteStream<Buffer> dst) {
    return pipe().to(dst);
  }

  @Override
  public Pipe<Buffer> pipe() {
    Pipe<Buffer> pipe = ReadStream.super.pipe();
    return new Pipe<>() {
      @Override
      public Pipe<Buffer> endOnFailure(boolean end) {
        pipe.endOnFailure(end);
        return this;
      }
      @Override
      public Pipe<Buffer> endOnSuccess(boolean end) {
        pipe.endOnSuccess(end);
        return this;
      }
      @Override
      public Pipe<Buffer> endOnComplete(boolean end) {
        pipe.endOnComplete(end);
        return this;
      }
      @Override
      public Future<Void> to(WriteStream<Buffer> dst) {
        Future<Void> f = pipe.to(dst);
        pump();
        return f;
      }
      @Override
      public void close() {
        pipe.close();
      }
    };
  }
}
