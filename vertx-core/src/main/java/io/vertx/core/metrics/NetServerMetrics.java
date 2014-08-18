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

import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.NetServerOptions;

/**
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
public class NetServerMetrics extends NetworkMetrics {
  public NetServerMetrics(VertxInternal vertx, String host, int port) {
    super(vertx, addressName("io.vertx.net.servers", host, port));
  }
}
