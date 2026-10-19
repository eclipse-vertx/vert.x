/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.http.impl.form;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpConstants;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerFileUpload;
import io.vertx.core.http.impl.HttpUtils;
import io.vertx.core.impl.buffer.VertxByteBufAllocator;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.internal.http.ContentDecoder;
import io.vertx.core.internal.http.FlowController;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * Decodes a form (url encoded or multipart) request body, the content is forwarded to a chunk handler and an end handler.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class FormDecoder implements ContentDecoder<Void> {

  private final MultiMap attributes;
  private final Handler<Throwable> exceptionHandler;
  private final Handler<Buffer> chunkHandler;
  private final Handler<Void> endHandler;
  private final NettyFileUploadDataFactory factory;
  private HttpPostRequestDecoder decoder;

  public FormDecoder(ContextInternal context,
                     HttpRequest request,
                     int maxAttributeSize,
                     int maxFields,
                     int maxBufferedBytes,
                     Supplier<Handler<HttpServerFileUpload>> uploadHandler,
                     MultiMap attributes,
                     Handler<Throwable> exceptionHandler,
                     Handler<Buffer> chunkHandler,
                     Handler<Void> endHandler) {
    String contentType = request.headers().get(HttpHeaderNames.CONTENT_TYPE);
    if (contentType == null) {
      throw new IllegalStateException("Request must have a content-type header to decode a multipart request");
    }
    if (!HttpUtils.isValidMultipartContentType(contentType)) {
      throw new IllegalStateException("Request must have a valid content-type header to decode a multipart request");
    }
    if (!HttpUtils.isValidMultipartMethod(request.method())) {
      throw new IllegalStateException("Request method must be one of POST, PUT, PATCH or DELETE to decode a multipart request");
    }
    request = normalizeMediaType(request, contentType);
    NettyFileUploadDataFactory factory = new NettyFileUploadDataFactory(context, uploadHandler);
    factory.setMaxLimit(maxAttributeSize);
    HttpPostRequestDecoder decoder;
    try {
      decoder = new HttpPostRequestDecoder(factory, request, HttpConstants.DEFAULT_CHARSET, maxFields, maxBufferedBytes);
    } catch (HttpPostRequestDecoder.ErrorDataDecoderException e) {
      throw new IllegalStateException("Request must have a valid content-type header to decode a multipart request", e);
    }
    this.attributes = attributes;
    this.exceptionHandler = exceptionHandler;
    this.chunkHandler = chunkHandler;
    this.endHandler = endHandler;
    this.factory = factory;
    this.decoder = decoder;
  }

  @Override
  public void init(FlowController flowController) {
    factory.flowController(flowController);
  }

  @Override
  public void handle(ByteBuf chunk) {
    ByteBuf copy = VertxByteBufAllocator.DEFAULT.heapBuffer(chunk.readableBytes());
    copy.writeBytes(chunk, chunk.readerIndex(), chunk.readableBytes());
    if (decoder != null) {
      try {
        decoder.offer(new DefaultHttpContent(copy.duplicate()));
      } catch (Exception e) {
        dispose(e);
        exceptionHandler.handle(e);
      }
    }
    chunkHandler.handle(BufferInternal.buffer(copy));
  }

  @Override
  public Void handleEnd() {
    if (decoder != null) {
      MultiMap decoded = MultiMap.caseInsensitiveMultiMap();
      Throwable failure = null;
      try {
        decoder.offer(LastHttpContent.EMPTY_LAST_CONTENT);
        while (decoder.hasNext()) {
          InterfaceHttpData data = decoder.next();
          if (data instanceof Attribute) {
            Attribute attr = (Attribute) data;
            try {
              decoded.add(attr.getName(), attr.getValue());
            } finally {
              attr.release();
            }
          }
        }
        if (decoder.currentPartialHttpData() != null) {
          failure = new HttpPostRequestDecoder.ErrorDataDecoderException("Multipart body ended before the end of a part");
        }
      } catch (HttpPostRequestDecoder.EndOfDataDecoderException e) {
        // ignore this as it is expected
      } catch (Exception e) {
        failure = e;
      } finally {
        dispose(failure);
      }
      if (failure != null) {
        exceptionHandler.handle(failure);
      } else {
        attributes.addAll(decoded);
      }
    }
    endHandler.handle(null);
    return null;
  }

  @Override
  public void handleClose(Throwable cause) {
    if (cause != null && decoder != null) {
      dispose(cause);
    }
  }

  private void dispose(Throwable failure) {
    InterfaceHttpData partial = decoder.currentPartialHttpData();
    if (partial instanceof Attribute && partial.refCnt() > 0) {
      partial.release();
    }
    decoder.destroy();
    decoder = null;
    if (failure != null && partial instanceof NettyFileUpload) {
      ((NettyFileUpload) partial).handleException(failure);
    }
  }

  private static HttpRequest normalizeMediaType(HttpRequest request, String contentType) {
    int index = contentType.indexOf(';');
    String mediaType = index == -1 ? contentType : contentType.substring(0, index);
    String normalized = mediaType.toLowerCase(Locale.ROOT);
    if (normalized.equals(mediaType)) {
      return request;
    }
    HttpRequest copy = new DefaultHttpRequest(request.protocolVersion(), request.method(), request.uri());
    copy.headers().set(request.headers());
    copy.headers().set(HttpHeaderNames.CONTENT_TYPE, normalized + contentType.substring(mediaType.length()));
    return copy;
  }
}
