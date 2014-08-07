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

import com.codahale.metrics.Timer;
import io.vertx.core.impl.VertxInternal;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
public abstract class HttpMetrics extends NetworkMetrics {

  private Timer requests;

  public HttpMetrics(VertxInternal vertx, String baseName) {
    super(vertx, baseName);
    if (isEnabled()) {
      requests = timer("requests");
    }
  }

  /**
   * Provides a timed context to measure http request latency.
   *
   * @param method the http method or null if it's not to be recorded
   * @param uri the request uri or path of the request, or null if it's not to be recorded
   * @return the TimedContext to be measured
   */
  protected TimedContext time(String method, String uri) {
    return new TimedContext(method, uri);
  }

  protected class TimedContext {
    private final String method;
    private final String uri;
    private final long start;

    private TimedContext(String method, String uri) {
      this.method = (method == null) ? null : method.toLowerCase();
      this.uri = uri;
      start = System.nanoTime();
    }

    protected void stop() {
      long duration = System.nanoTime() - start;
      requests.update(duration, TimeUnit.NANOSECONDS);

      // Having named metrics based on URI could get ugly
      if (uri != null) {
        timer("requests", "uri", uri).update(duration, TimeUnit.NANOSECONDS);
      }

      if (method != null) {
        timer(method + "-requests").update(duration, TimeUnit.NANOSECONDS);
        if (uri != null) {
          timer(method + "-requests", uri).update(duration, TimeUnit.NANOSECONDS);
        }
      }
    }
  }
}
