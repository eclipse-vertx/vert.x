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

package io.vertx.core.spi.metrics;

/**
 * The metrics interface is implemented by metrics providers that wants to provide monitoring of
 * Vert.x core.
 *
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
public interface Metrics {

  /**
   * Whether the metrics are enabled.
   *
   * @return true if the metrics are enabled.
   */
  boolean isEnabled();

  /**
   * Used to close out the metrics, for example when an http server/client has been closed.<p/>
   *
   * No specific thread and context can be expected when this method is called.
   */
  void close();
}
