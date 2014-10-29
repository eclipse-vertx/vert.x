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

package io.vertx.core.net;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Handler;
import io.vertx.core.streams.ReadStream;

/**
 * A {@link io.vertx.core.streams.ReadStream} of {@link io.vertx.core.net.NetSocket}, used for notifying
 * socket connections to a {@link io.vertx.core.net.NetServer}.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface NetSocketStream extends ReadStream<NetSocket> {

  @Override
  NetSocketStream exceptionHandler(Handler<Throwable> handler);

  @Override
  NetSocketStream handler(Handler<NetSocket> handler);

  @Override
  NetSocketStream pause();

  @Override
  NetSocketStream resume();

  @Override
  NetSocketStream endHandler(Handler<Void> endHandler);
}
