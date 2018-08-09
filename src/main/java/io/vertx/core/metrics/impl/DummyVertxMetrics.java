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

package io.vertx.core.metrics.impl;

import io.vertx.core.spi.metrics.*;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DummyVertxMetrics implements VertxMetrics {

  public static final DummyVertxMetrics INSTANCE = new DummyVertxMetrics();

  public static class DummyEventBusMetrics implements EventBusMetrics<Void> {

    public static final DummyEventBusMetrics INSTANCE = new DummyEventBusMetrics();

  }

  public static class DummyHttpServerMetrics implements HttpServerMetrics<Void, Void, Void> {

    public static final DummyHttpServerMetrics INSTANCE = new DummyHttpServerMetrics();

  }

  public static class DummyHttpClientMetrics implements HttpClientMetrics<Void, Void, Void, Void, Void> {

    public static final DummyHttpClientMetrics INSTANCE = new DummyHttpClientMetrics();

  }

  public static class DummyTCPMetrics implements TCPMetrics<Void> {

    public static final DummyTCPMetrics INSTANCE = new DummyTCPMetrics();

  }

  public static class DummyDatagramMetrics implements DatagramSocketMetrics {

    public static final DummyDatagramMetrics INSTANCE = new DummyDatagramMetrics();

  }

  public static class DummyWorkerPoolMetrics implements PoolMetrics<Void> {

    public static final DummyWorkerPoolMetrics INSTANCE = new DummyWorkerPoolMetrics();

  }
}
