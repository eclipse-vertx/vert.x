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

package io.vertx.core.metrics;

import io.vertx.codegen.annotations.VertxGen;

/**
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
@VertxGen(concrete = false)
public interface Measured {

  /**
   * Whether the metrics are enabled for this measured object
   *
   * @implSpec
   * The default implementation returns {@code false}
   *
   * @return {@code true} if metrics are enabled
   */
  default boolean isMetricsEnabled() {
    return false;
  }

}
