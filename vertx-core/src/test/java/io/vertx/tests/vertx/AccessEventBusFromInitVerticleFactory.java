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

package io.vertx.tests.vertx;

import io.vertx.core.Deployable;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.spi.VerticleFactory;

import java.util.concurrent.Callable;

public class AccessEventBusFromInitVerticleFactory implements VerticleFactory {

  @Override
  public void init(Vertx vertx) {
    // should not be null
    if (vertx.eventBus() == null) {
      throw new IllegalStateException("eventBus was null, while it shouldn't");
    }
    if (vertx.sharedData() == null) {
      throw new IllegalStateException("sharedData was null, while it shouldn't");
    }
  }

  @Override
  public String prefix() {
    return "invalid";
  }

  @Override
  public void createVerticle2(String verticleName, ClassLoader classLoader, Promise<Callable<? extends Deployable>> promise) {
    promise.complete();
  }

  @Override
  public void close() {

  }
}
