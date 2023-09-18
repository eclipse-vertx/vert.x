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
package io.vertx.core;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.Unstable;

import java.util.concurrent.ExecutorService;

/**
 * Generic worker options defining the characteristics of a worker service.
 */
@Unstable
@DataObject
public interface WorkerOptions {

  /**
   * Create the executor service that implements this worker options.
   *
   * @param vertx the vertx instance
   * @return the executor service implementing these worker options
   */
  ExecutorService createExecutor(Vertx vertx);

  /**
   * @return a copy of this object
   */
  WorkerOptions copy();

}
