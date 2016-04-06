/*
 * Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
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
