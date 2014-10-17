/*
 * Copyright (c) 2011-2014 The original author or authors
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

package io.vertx.core;

/**
 * A verticle is the deployment unit of Vert.x
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface Verticle {

  Vertx getVertx();

  void setVertx(Vertx vertx);

  void start(Future<Void> startFuture) throws Exception;

  void stop(Future<Void> stopFuture) throws Exception;
}
