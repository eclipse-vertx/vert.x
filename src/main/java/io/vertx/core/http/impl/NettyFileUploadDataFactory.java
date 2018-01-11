/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
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
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerFileUpload;
import io.vertx.core.http.HttpServerRequest;

import java.nio.charset.Charset;
import java.util.function.Supplier;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class NettyFileUploadDataFactory extends DefaultHttpDataFactory {

  final Vertx vertx;
  final HttpServerRequest request;
  final Supplier<Handler<HttpServerFileUpload>> lazyUploadHandler;

  NettyFileUploadDataFactory(Vertx vertx, HttpServerRequest request, Supplier<Handler<HttpServerFileUpload>> lazyUploadHandler) {
    super(false);
    this.vertx = vertx;
    this.request = request;
    this.lazyUploadHandler = lazyUploadHandler;
  }

  @Override
  public FileUpload createFileUpload(HttpRequest httpRequest, String name, String filename, String contentType, String contentTransferEncoding, Charset charset, long size) {
    HttpServerFileUploadImpl upload = new HttpServerFileUploadImpl(vertx, request, name, filename, contentType, contentTransferEncoding, charset,
        size);
    NettyFileUpload nettyUpload = new NettyFileUpload(upload, name, filename, contentType,
        contentTransferEncoding, charset);
    Handler<HttpServerFileUpload> uploadHandler = lazyUploadHandler.get();
    if (uploadHandler != null) {
      uploadHandler.handle(upload);
    }
    return nettyUpload;
  }
}
