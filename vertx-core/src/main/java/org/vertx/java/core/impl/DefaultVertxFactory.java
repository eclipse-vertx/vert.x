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
package org.vertx.java.core.impl;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.VertxFactory;

/**
 * @author pidster
 *
 */
public class DefaultVertxFactory extends VertxFactory {

  @Override
  public Vertx createVertx() {
    return new DefaultVertx();
  }

  @Override
  public Vertx createVertx(String hostname) {
    return new DefaultVertx(hostname);
  }

  @Override
  public Vertx createVertx(int port, String hostname) {
    return new DefaultVertx(port, hostname, null);
  }

  @Override
  public void createVertx(int port, String hostname, final Handler<AsyncResult<Vertx>> resultHandler) {
    new DefaultVertx(port, hostname, resultHandler);
  }

}
