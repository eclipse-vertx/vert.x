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
package io.vertx.core.externals;

import io.vertx.core.AbstractVerticle;


public class MyVerticle extends AbstractVerticle {

  @Override
  public void start() throws Exception {
    System.out.println("Started...");
    vertx.deployVerticle("io.vertx.core.impl.launcher.commands.HttpTestVerticle");
  }
}
