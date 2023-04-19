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


import io.vertx.codegen.annotations.VertxGen;

/**
 * Policy controlling the behavior of HTTP client-side load balancing
 */
@VertxGen
public enum LoadBalancePolicy {

  /**
   * Disable load balancing
   */
  NONE,

  /**
   * Distributes traffic randomly among available endpoints
   */
  RANDOM,
}
