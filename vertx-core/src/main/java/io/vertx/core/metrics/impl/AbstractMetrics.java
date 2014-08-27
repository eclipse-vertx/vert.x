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

package io.vertx.core.metrics.impl;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Reservoir;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;
import io.vertx.core.metrics.spi.BaseMetrics;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static com.codahale.metrics.MetricRegistry.*;

/**
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
abstract class AbstractMetrics implements BaseMetrics {
  private final MetricRegistry registry;
  private String baseName;
  private boolean enabled;

  public AbstractMetrics(MetricRegistry registry, String baseName) {
    this.registry = registry;
    this.baseName = baseName;
    this.enabled = registry != null;
  }

  protected MetricRegistry registry() {
    return registry;
  }

  protected void setBaseName(String baseName) {
    this.baseName = baseName;
  }

  public String baseName() {
    return baseName;
  }

  public boolean isEnabled() {
    return enabled;
  }

  protected <T> Gauge<T> gauge(Gauge<T> gauge, String... names) {
    if (!enabled) return gauge;

    return registry.register(name(baseName, names), gauge);
  }

  protected Counter counter(String... names) {
    if (!enabled) return noOpCounter;

    return registry.counter(name(baseName, names));
  }

  protected Histogram histogram(String... names) {
    if (!enabled) return noOpHistogram;

    return registry.histogram(name(baseName, names));
  }

  protected Meter meter(String... names) {
    if (!enabled) return noOpMeter;

    return registry.meter(name(baseName, names));
  }

  protected Timer timer(String... names) {
    if (!enabled) return noOpTimer;

    return registry.timer(name(baseName, names));
  }

  protected static String instanceName(String baseName, Object instance) {
    return name(baseName, "@" + Integer.toHexString(instance.hashCode()));
  }


  // The following no-op metrics give us the option to not always check for a null metric registry
  // in our code, and proceed as normal with minimal overhead

  private static final Counter noOpCounter = new NoOpCounter();

  private static final class NoOpCounter extends Counter {
    @Override
    public void inc() {
    }

    @Override
    public void inc(long n) {
    }

    @Override
    public void dec() {
    }

    @Override
    public void dec(long n) {
    }

    @Override
    public long getCount() {
      return 0;
    }
  }

  private static final Histogram noOpHistogram = new NoOpHistogram();

  private static final class NoOpHistogram extends Histogram {

    private NoOpHistogram() {
      super(noOpReservoir);
    }

    @Override
    public void update(int value) {
      super.update(value);
    }

    @Override
    public void update(long value) {
      super.update(value);
    }

    @Override
    public long getCount() {
      return super.getCount();
    }

    @Override
    public Snapshot getSnapshot() {
      return emptySnapshot;
    }
  }

  private static final NoOpReservoir noOpReservoir = new NoOpReservoir();
  private static final Snapshot emptySnapshot = new Snapshot(new long[0]);

  private static class NoOpReservoir implements Reservoir {
    @Override
    public int size() {
      return 0;
    }

    @Override
    public void update(long value) {
    }

    @Override
    public Snapshot getSnapshot() {
      return emptySnapshot;
    }
  }

  private static final NoOpMeter noOpMeter = new NoOpMeter();

  private static class NoOpMeter extends Meter {

    @Override
    public void mark() {
    }

    @Override
    public void mark(long n) {
    }

    @Override
    public long getCount() {
      return 0;
    }

    @Override
    public double getFifteenMinuteRate() {
      return 0;
    }

    @Override
    public double getFiveMinuteRate() {
      return 0;
    }

    @Override
    public double getMeanRate() {
      return 0;
    }

    @Override
    public double getOneMinuteRate() {
      return 0;
    }
  }

  private static final NoOpTimer noOpTimer = new NoOpTimer();

  private static class NoOpTimer extends Timer {
    public NoOpTimer() {
      super(noOpReservoir);
    }

    @Override
    public void update(long duration, TimeUnit unit) {
    }

    @Override
    public <T> T time(Callable<T> event) throws Exception {
      return event.call();
    }

    @Override
    public long getCount() {
      return 0;
    }

    @Override
    public double getFifteenMinuteRate() {
      return 0;
    }

    @Override
    public double getFiveMinuteRate() {
      return 0;
    }

    @Override
    public double getMeanRate() {
      return 0;
    }

    @Override
    public double getOneMinuteRate() {
      return 0;
    }

    @Override
    public Snapshot getSnapshot() {
      return emptySnapshot;
    }
  }
}
