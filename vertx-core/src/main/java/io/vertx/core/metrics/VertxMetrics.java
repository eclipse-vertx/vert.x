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

import com.codahale.metrics.Counter;
import io.vertx.core.VertxOptions;
import io.vertx.core.impl.VertxInternal;

/**
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
public class VertxMetrics extends AbstractMetrics {

  private Counter timers;

  public VertxMetrics(VertxInternal vertx) {
    super(vertx, "io.vertx");
    if (isEnabled()) {
      timers = counter("timers");
    }
  }

  public void newVertx(VertxOptions options) {
    if (!isEnabled()) return;

    gauge(options::getEventLoopPoolSize, "event-loop-size");
    gauge(options::getWorkerPoolSize, "worker-pool-size");
    if (options.isClustered()) {
      gauge(options::getClusterHost, "cluster-host");
      gauge(options::getClusterPort, "cluster-port");
    }
  }

  public void setTimer(long timerID, boolean periodic) {
    if (!isEnabled()) return;

    timers.inc();
  }

  public void cancelTimer(long timerID) {
    if (!isEnabled()) return;

    timers.dec();
  }

  public void finishedTimer(long timerID) {
    if (!isEnabled()) return;

    timers.dec();
  }
}
