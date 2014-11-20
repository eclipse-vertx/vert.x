/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */
package io.vertx.core.spi;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;

/**
 * Factory for creating Vertx instances.<p>
 * Use this to create Vertx instances when embedding Vert.x core directly.<p>
 *
 * @author pidster
 *
 */
public interface VertxFactory {

  Vertx vertx();

  Vertx vertx(VertxOptions options);

  void clusteredVertx(VertxOptions options, Handler<AsyncResult<Vertx>> resultHandler);

  Context context();

}
