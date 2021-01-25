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

import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.MemoryAttribute;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerFileUpload;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.impl.ContextInternal;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.function.Supplier;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class NettyFileUploadDataFactory extends DefaultHttpDataFactory {

  private final ContextInternal context;
  private final HttpServerRequest request;
  private final Supplier<Handler<HttpServerFileUpload>> lazyUploadHandler;

  NettyFileUploadDataFactory(ContextInternal context, HttpServerRequest request, Supplier<Handler<HttpServerFileUpload>> lazyUploadHandler) {
    super(false);
    this.context = context;
    this.request = request;
    this.lazyUploadHandler = lazyUploadHandler;
  }

  @Override
  public Attribute createAttribute(HttpRequest request, String name) {
    return createAttribute(request, name, 0L);
  }

  @Override
  public Attribute createAttribute(HttpRequest request, String name, long definedSize) {
    return new VertxAttribute(name, definedSize);
  }

  @Override
  public Attribute createAttribute(HttpRequest request, String name, String value) {
    Attribute attr;
    try {
      attr = createAttribute(request, name);
      attr.setValue(value);
    } catch (IOException e) {
      throw new IllegalArgumentException(e);
    }
    return attr;
  }

  @Override
  public FileUpload createFileUpload(HttpRequest httpRequest, String name, String filename, String contentType, String contentTransferEncoding, Charset charset, long size) {
    NettyFileUpload nettyUpload = new NettyFileUpload(context, request, name, filename, contentType,
        contentTransferEncoding, charset);
    HttpServerFileUploadImpl upload = new HttpServerFileUploadImpl(context, nettyUpload, name, filename, contentType, contentTransferEncoding, charset,
      size);
    Handler<HttpServerFileUpload> uploadHandler = lazyUploadHandler.get();
    if (uploadHandler != null) {
      context.dispatch(upload, uploadHandler);
    }
    return nettyUpload;
  }

  private static class VertxAttribute extends MemoryAttribute {
    public VertxAttribute(String name, long definedSize) {
      super(name, definedSize);
      setMaxSize(1024);
    }
    String value;
    @Override
    protected void setCompleted() {
      super.setCompleted();
      // Capture value before it gets corrupted
      // this can be called multiple times (e.g "vert+x" then "vert x"
      value = super.getValue();
    }
    @Override
    public String getValue() {
      return value;
    }
  }
}
