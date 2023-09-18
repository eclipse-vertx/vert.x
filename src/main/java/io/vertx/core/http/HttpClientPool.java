/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.http;

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.net.SSLOptions;

import java.util.function.Function;

/**
 * An asynchronous HTTP client.
 * <p>
 * It allows you to make requests to HTTP servers, and a single client can make requests to any server.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public interface HttpClientPool extends HttpClient {

  @SuppressWarnings("deprecation")
  @Override
  Future<Void> updateSSLOptions(SSLOptions options);

  @SuppressWarnings("deprecation")
  @Override
  default void updateSSLOptions(SSLOptions options, Handler<AsyncResult<Void>> handler) {
    HttpClient.super.updateSSLOptions(options, handler);
  }

  @SuppressWarnings("deprecation")
  @Override
  HttpClientPool connectionHandler(Handler<HttpConnection> handler);

  @SuppressWarnings("deprecation")
  @Override
  HttpClientPool redirectHandler(Function<HttpClientResponse, Future<RequestOptions>> handler);

  @SuppressWarnings("deprecation")
  @GenIgnore
  @Override
  Function<HttpClientResponse, Future<RequestOptions>> redirectHandler();

}
