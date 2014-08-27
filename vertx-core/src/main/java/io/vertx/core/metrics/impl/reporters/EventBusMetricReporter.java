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

package io.vertx.core.metrics.impl.reporters;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metered;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

import java.util.Locale;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
public class EventBusMetricReporter {
  private final Vertx vertx;

  private Long timerId;
  private TimeUnit rateUnit = TimeUnit.SECONDS;
  private long rateFactor;
  private TimeUnit durationUnit = TimeUnit.MILLISECONDS;
  private double durationFactor;
  private final MetricRegistry registry;

  public EventBusMetricReporter(Vertx vertx, MetricRegistry registry) {
    this.vertx = vertx;
    this.registry = registry;
    this.rateFactor = rateUnit.toSeconds(1);
    this.durationFactor = 1.0 / durationUnit.toNanos(1);
  }

  public void convertRatesTo(TimeUnit rateUnit) {
    this.rateUnit = rateUnit;
    this.rateFactor = rateUnit.toSeconds(1);
  }

  public void convertDurationsTo(TimeUnit durationUnit) {
    this.durationUnit = durationUnit;
    this.durationFactor = 1.0 / durationUnit.toNanos(1);
  }

  public EventBusMetricReporter start(long duration, TimeUnit unit) {
    if (registry != null) {
      long delay = unit.toMillis(duration);
      String addressPrefix = "io.vertx.metrics/";
      timerId = vertx.setPeriodic(delay, id -> {
        publish(addressPrefix, registry.getGauges());
        publish(addressPrefix, registry.getCounters());
        publish(addressPrefix, registry.getHistograms());
        publish(addressPrefix, registry.getMeters());
        publish(addressPrefix, registry.getTimers());
      });
    }

    return this;
  }

  public void stop() {
    if (timerId != null) {
      vertx.cancelTimer(timerId);
    }
  }

  private void publish(String addressPrefix, SortedMap<String, ? extends Metric> map) {
    if (map.isEmpty()) return;

    map.forEach((name, metric) -> {
      vertx.eventBus().publish(addressPrefix + name, toJson(metric));
    });
  }

  private JsonObject toJson(Metric metric) {
    if (metric instanceof Gauge) {
      return json((Gauge) metric);
    } else if (metric instanceof Counter) {
      return json((Counter) metric);
    } else if (metric instanceof Histogram) {
      return json((Histogram) metric);
    } else if (metric instanceof Meter) {
      return json((Meter) metric);
    } else if (metric instanceof Timer) {
      return json((Timer) metric);
    } else {
      throw new IllegalArgumentException("Unknown metric " + metric);
    }
  }

  private JsonObject json(Gauge gauge) {
    return new JsonObject().putValue("value", gauge.getValue());
  }

  private JsonObject json(Counter counter) {
    return new JsonObject().putValue("count", counter.getCount());
  }

  private JsonObject json(Histogram histogram) {
    Snapshot snapshot = histogram.getSnapshot();
    JsonObject json = new JsonObject();
    json.putNumber("count", histogram.getCount());

    // Snapshot
    populateSnapshot(json, snapshot, 1);

    return json;
  }

  private JsonObject json(Meter meter) {
    JsonObject json = new JsonObject();

    // Meter
    populateMetered(json, meter, 1);

    return json;
  }

  private JsonObject json(Timer timer) {
    Snapshot snapshot = timer.getSnapshot();
    JsonObject json = new JsonObject();

    // Meter
    populateMetered(json, timer, rateFactor);

    // Snapshot
    populateSnapshot(json, snapshot, durationFactor);

    // Duration rate
    String duration = durationUnit.toString().toLowerCase(Locale.US);
    json.putString("durationRate", duration);

    return json;
  }

  private void populateMetered(JsonObject json, Metered meter, double factor) {
    json.putNumber("count", meter.getCount());
    json.putNumber("meanRate", meter.getMeanRate() * factor);
    json.putNumber("oneMinuteRate", meter.getOneMinuteRate() * factor);
    json.putNumber("fiveMinuteRate", meter.getFiveMinuteRate() * factor);
    json.putNumber("fifteenMinuteRate", meter.getFifteenMinuteRate() * factor);
    String rate = "events/" + rateUnit.toString().toLowerCase(Locale.US);
    json.putString("rate", rate);
  }

  private void populateSnapshot(JsonObject json, Snapshot snapshot, double factor) {
    json.putNumber("min", snapshot.getMin() * factor);
    json.putNumber("max", snapshot.getMax() * factor);
    json.putNumber("mean", snapshot.getMean() * factor);
    json.putNumber("stddev", snapshot.getStdDev() * factor);
    json.putNumber("median", snapshot.getMedian() * factor);
    json.putNumber("75%", snapshot.get75thPercentile() * factor);
    json.putNumber("95%", snapshot.get95thPercentile() * factor);
    json.putNumber("98%", snapshot.get98thPercentile() * factor);
    json.putNumber("99%", snapshot.get99thPercentile() * factor);
    json.putNumber("99.9%", snapshot.get999thPercentile() * factor);
  }
}
