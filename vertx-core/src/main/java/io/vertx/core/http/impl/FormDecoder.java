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
package io.vertx.core.http.impl;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.HttpConstants;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerFileUpload;
import io.vertx.core.impl.buffer.VertxByteBufAllocator;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.http.ContentDecoder;
import io.vertx.core.internal.http.FlowController;

import java.util.function.Supplier;

/**
 * Decodes a form (url encoded or multipart) request body, the content is forwarded to a delegate decoder.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class FormDecoder implements ContentDecoder<Void> {

  private final ContextInternal context;
  private final HttpRequest request;
  private final int maxAttributeSize;
  private final int maxFields;
  private final int maxBufferedBytes;
  private final Supplier<Handler<HttpServerFileUpload>> uploadHandler;
  private final MultiMap attributes;
  private final Handler<Throwable> exceptionHandler;
  private final ContentDecoder<?> delegate;
  private HttpPostRequestDecoder decoder;

  public FormDecoder(ContextInternal context,
                     HttpRequest request,
                     int maxAttributeSize,
                     int maxFields,
                     int maxBufferedBytes,
                     Supplier<Handler<HttpServerFileUpload>> uploadHandler,
                     MultiMap attributes,
                     Handler<Throwable> exceptionHandler,
                     ContentDecoder<?> delegate) {
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
    this.context = context;
    this.request = request;
    this.maxAttributeSize = maxAttributeSize;
    this.maxFields = maxFields;
    this.maxBufferedBytes = maxBufferedBytes;
    this.uploadHandler = uploadHandler;
    this.attributes = attributes;
    this.exceptionHandler = exceptionHandler;
    this.delegate = delegate;
  }

  @Override
  public void init(FlowController flowController) {
    NettyFileUploadDataFactory factory = new NettyFileUploadDataFactory(context, flowController, uploadHandler);
    factory.setMaxLimit(maxAttributeSize);
    decoder = new HttpPostRequestDecoder(factory, request, HttpConstants.DEFAULT_CHARSET, maxFields, maxBufferedBytes);
    delegate.init(flowController);
  }

  @Override
  public void handle(ByteBuf chunk) {
    if (decoder != null) {
      try {
        ByteBuf copy = VertxByteBufAllocator.DEFAULT.heapBuffer(chunk.readableBytes());
        copy.writeBytes(chunk, chunk.readerIndex(), chunk.readableBytes());
        decoder.offer(new DefaultHttpContent(copy));
      } catch (HttpPostRequestDecoder.ErrorDataDecoderException |
               HttpPostRequestDecoder.TooLongFormFieldException |
               HttpPostRequestDecoder.TooManyFormFieldsException e) {
        destroy();
        decoder = null;
        exceptionHandler.handle(e);
      }
    }
    delegate.handle(chunk);
  }

  @Override
  public Void handleEnd() {
    if (decoder != null) {
      try {
        decoder.offer(LastHttpContent.EMPTY_LAST_CONTENT);
        while (decoder.hasNext()) {
          InterfaceHttpData data = decoder.next();
          if (data instanceof Attribute) {
            Attribute attr = (Attribute) data;
            try {
              attributes.add(attr.getName(), attr.getValue());
            } catch (Exception e) {
              // Will never happen, anyway handle it somehow just in case
              exceptionHandler.handle(e);
            } finally {
              attr.release();
            }
          }
        }
      } catch (HttpPostRequestDecoder.EndOfDataDecoderException e) {
        // ignore this as it is expected
      } catch (Exception e) {
        exceptionHandler.handle(e);
      } finally {
        destroy();
        decoder = null;
      }
    }
    delegate.handleEnd();
    return null;
  }

  @Override
  public void handleClose(Throwable cause) {
    if (cause != null && decoder != null) {
      destroy();
    }
    delegate.handleClose(cause);
  }

  private void destroy() {
    InterfaceHttpData partial = decoder.currentPartialHttpData();
    if (partial instanceof Attribute && partial.refCnt() > 0) {
      partial.release();
    }
    decoder.destroy();
  }

  /**
   * Fail the file upload in progress, if any.
   *
   * @param failure the failure
   */
  public void failUpload(Throwable failure) {
    if (decoder != null) {
      InterfaceHttpData upload = decoder.currentPartialHttpData();
      if (upload instanceof NettyFileUpload) {
        ((NettyFileUpload) upload).handleException(failure);
      }
    }
  }
}
