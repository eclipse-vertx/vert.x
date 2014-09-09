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
import com.codahale.metrics.Metered;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;
import io.vertx.core.Handler;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.datagram.DatagramSocket;
import io.vertx.core.datagram.DatagramSocketOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.metrics.spi.DatagramMetrics;
import io.vertx.core.metrics.spi.EventBusMetrics;
import io.vertx.core.metrics.spi.HttpClientMetrics;
import io.vertx.core.metrics.spi.HttpServerMetrics;
import io.vertx.core.metrics.spi.Metrics;
import io.vertx.core.metrics.spi.NetMetrics;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.codahale.metrics.MetricRegistry.*;

/**
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
class MetricsImpl extends AbstractMetrics implements Metrics {

  static final String BASE_NAME = "vertx";

  private Counter timers;
  private Counter verticles;
  private Handler<Void> doneHandler;

  public MetricsImpl(Vertx vertx, VertxOptions vertxOptions) {
    super(new Registry(vertxOptions), BASE_NAME);
    initialize(vertxOptions);
  }

  public void initialize(VertxOptions options) {
    if (!isEnabled()) return;

    timers = counter("timers");

    gauge(options::getEventLoopPoolSize, "event-loop-size");
    gauge(options::getWorkerPoolSize, "worker-pool-size");
    if (options.isClustered()) {
      gauge(options::getClusterHost, "cluster-host");
      gauge(options::getClusterPort, "cluster-port");
    }

    verticles = counter("verticles");
  }

  @Override
  public void verticleDeployed(Verticle verticle) {
    if (!isEnabled()) return;

    verticles.inc();
    counter("verticles", verticleName(verticle)).inc();
  }

  @Override
  public void verticleUndeployed(Verticle verticle) {
    if (!isEnabled()) return;

    verticles.dec();
    counter("verticles", verticleName(verticle)).dec();
  }

  @Override
  public void timerCreated(long id) {
    if (!isEnabled()) return;

    timers.inc();
  }

  @Override
  public void timerEnded(long id, boolean cancelled) {
    if (!isEnabled()) return;

    timers.dec();
  }

  @Override
  public EventBusMetrics register(EventBus eventBus) {
    return new EventBusMetricsImpl(this, name(baseName(), "eventbus"));
  }

  @Override
  public HttpServerMetrics register(HttpServer server, HttpServerOptions options) {
    return new HttpServerMetricsImpl(this, name(baseName(), "http.servers"));
  }

  @Override
  public HttpClientMetrics register(HttpClient client, HttpClientOptions options) {
    return new HttpClientMetricsImpl(this, instanceName(name(baseName(), "http.clients"), client), options);
  }

  @Override
  public NetMetrics register(NetServer server, NetServerOptions options) {
    return new NetMetricsImpl(this, name(baseName(), "net.servers"), false);
  }

  @Override
  public NetMetrics register(NetClient client, NetClientOptions options) {
    return new NetMetricsImpl(this, instanceName(name(baseName(), "net.clients"), client), true);
  }

  @Override
  public DatagramMetrics register(DatagramSocket socket, DatagramSocketOptions options) {
    return new DatagramMetricsImpl(this, name(baseName(), "datagram"));
  }

  @Override
  public void stop() {
    registry().shutdown();
    if (doneHandler != null) {
      doneHandler.handle(null);
    }
  }

  @Override
  public String metricBaseName() {
    return baseName();
  }

  @Override
  public Map<String, JsonObject> metrics(TimeUnit rateUnit, TimeUnit durationUnit) {
    Objects.requireNonNull(rateUnit);
    Objects.requireNonNull(durationUnit);

    Map<String, JsonObject> metrics = new HashMap<>();
    registry().getMetrics().forEach((name, metric) -> {
      JsonObject data = convertMetric(metric, rateUnit, durationUnit);
      metrics.put(name, data);
    });

    return metrics;
  }

  void setDoneHandler(Handler<Void> handler) {
    this.doneHandler = handler;
  }

  private static String verticleName(Verticle verticle) {
    return verticle.getClass().getName();
  }

  private static MetricRegistry createRegistry(VertxOptions options) {
    return options.isMetricsEnabled() ? new MetricRegistry() : null;
  }

  //----------------------- Convert metrics to JsonObject

  private JsonObject convertMetric(Metric metric, TimeUnit rateUnit, TimeUnit durationUnit) {
    if (metric instanceof Gauge) {
      return toJson((Gauge) metric);
    } else if (metric instanceof Counter) {
      return toJson((Counter) metric);
    } else if (metric instanceof Histogram) {
      return toJson((Histogram) metric);
    } else if (metric instanceof Meter) {
      return toJson((Meter) metric, rateUnit);
    } else if (metric instanceof Timer) {
      return toJson((Timer) metric, rateUnit, durationUnit);
    } else {
      throw new IllegalArgumentException("Unknown metric " + metric);
    }
  }

  private static JsonObject toJson(Gauge gauge) {
    return new JsonObject().putValue("value", gauge.getValue());
  }

  private static JsonObject toJson(Counter counter) {
    return new JsonObject().putValue("count", counter.getCount());
  }

  private static JsonObject toJson(Histogram histogram) {
    Snapshot snapshot = histogram.getSnapshot();
    JsonObject json = new JsonObject();
    json.putNumber("count", histogram.getCount());

    // Snapshot
    populateSnapshot(json, snapshot, 1);

    return json;
  }

  private JsonObject toJson(Meter meter, TimeUnit rateUnit) {
    JsonObject json = new JsonObject();

    // Meter
    populateMetered(json, meter, rateUnit);

    return json;
  }

  private JsonObject toJson(Timer timer, TimeUnit rateUnit, TimeUnit durationUnit) {
    Snapshot snapshot = timer.getSnapshot();
    JsonObject json = new JsonObject();

    // Meter
    populateMetered(json, timer, rateUnit);

    // Snapshot
    double factor = 1.0 / durationUnit.toNanos(1);
    populateSnapshot(json, snapshot, factor);

    // Duration rate
    String duration = durationUnit.toString().toLowerCase();
    json.putString("durationRate", duration);

    return json;
  }

  private static void populateMetered(JsonObject json, Metered meter, TimeUnit rateUnit) {
    double factor = rateUnit.toSeconds(1);
    json.putNumber("count", meter.getCount());
    json.putNumber("meanRate", meter.getMeanRate() * factor);
    json.putNumber("oneMinuteRate", meter.getOneMinuteRate() * factor);
    json.putNumber("fiveMinuteRate", meter.getFiveMinuteRate() * factor);
    json.putNumber("fifteenMinuteRate", meter.getFifteenMinuteRate() * factor);
    String rate = "events/" + rateUnit.toString().toLowerCase();
    json.putString("rate", rate);
  }

  private static void populateSnapshot(JsonObject json, Snapshot snapshot, double factor) {
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
