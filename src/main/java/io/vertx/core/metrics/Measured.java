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
   * @return true if the metrics are enabled
   */
  boolean isMetricsEnabled();

}
