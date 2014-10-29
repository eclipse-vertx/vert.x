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

package io.vertx.core.http;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Handler;
import io.vertx.core.streams.ReadStream;

/**
 * A {@link io.vertx.core.streams.ReadStream} of {@link io.vertx.core.http.HttpServerRequest}, used for
 * notifying http request to a {@link io.vertx.core.http.HttpServer}.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface HttpServerRequestStream extends ReadStream<HttpServerRequest> {

  @Override
  HttpServerRequestStream exceptionHandler(Handler<Throwable> handler);

  @Override
  HttpServerRequestStream handler(Handler<HttpServerRequest> handler);

  @Override
  HttpServerRequestStream pause();

  @Override
  HttpServerRequestStream resume();

  @Override
  HttpServerRequestStream endHandler(Handler<Void> endHandler);
}
