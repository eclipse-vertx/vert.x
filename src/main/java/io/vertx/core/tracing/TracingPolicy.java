/*
 * Copyright (c) 2011-2020 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.tracing;

/**
 * Policy controlling the behavior across boundaries.
 *
 * This control is applied for clients or servers reporting traces.
 */
public enum TracingPolicy {

  /**
   * Do not propagate a trace, this is equivalent of disabling tracing.
   */
  IGNORE,

  /**
   * Propagate an existing trace.
   */
  PROPAGATE,

  /**
   * Reuse an existing trace or create a new trace when no one exist.
   */
  ALWAYS

}
