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

package io.vertx.core.spi.metrics;

import io.vertx.core.impl.SysProps;

/**
 * The metrics interface is implemented by metrics providers that wants to provide monitoring of
 * Vert.x core.
 *
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
public interface Metrics {

  boolean METRICS_ENABLED = !SysProps.DISABLE_METRICS.getBoolean();

  /**
   * Used to close out the metrics, for example when an http server/client has been closed.<p/>
   *
   * No specific thread and context can be expected when this method is called.
   */
  default void close() {
  }
}
