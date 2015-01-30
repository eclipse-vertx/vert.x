/*
 * Copyright 2014 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

/**
 * == Metrics
 *
 * Vert.x provides a fairly simple API to retrieve metrics via the {@link io.vertx.core.metrics.Measured Measured} interface
 * which is implemented by various Vert.x components like {@link io.vertx.core.http.HttpServer HttpServer}, {@link io.vertx.core.net.NetServer},
 * and even {@link io.vertx.core.Vertx Vertx} itself.
 *
 * By default Vert.x does not record any metrics. Instead it provides an SPI for others to implement like https://github.com/vert-x3/vertx-metrics[vertx-metrics]
 * which can be added to the classpath. Once added, you can enable metrics by doing the following:
 * [source,java]
 * ----
 * {@link examples.MetricsExamples#example2}
 * ----
 *
 * Once enabled, you can retrieve metrics from any {@link io.vertx.core.metrics.Measured Measured} object which provides
 * a map of the metric name to the data, represented by a {@link io.vertx.core.json.JsonObject}. So for example if we were to print
 * out all metrics for a particular Vert.x instance:
 * [source,java]
 * ----
 * {@link examples.MetricsExamples#example1}
 * ----
 *
 * NOTE: For details on the actual contents of the data (the actual metric) represented by the {@link io.vertx.core.json.JsonObject}
 * consult the implementation documentation like https://github.com/vert-x3/vertx-metrics[vertx-metrics]
 *
 * Often it is desired that you only want to capture specific metrics for a particular component, like an http server
 * without having to know the details of the naming scheme of every metric (something which is left to the implementers of the SPI).
 *
 * Since {@link io.vertx.core.http.HttpServer HttpServer} implements {@link io.vertx.core.metrics.Measured}, you can easily grab all metrics
 * that are specific for that particular http server.
 * [source,java]
 * ----
 * {@link examples.MetricsExamples#example3}
 * ----
 *
 * === Metrics SPI
 *
 * The metrics SPI is an advanced feature which allows implementers to capture events from Vert.x in order to gather metrics. For
 * more information on this, please consult the {@link io.vertx.core.spi.metrics.VertxMetrics API Documentation}.
 */
@Document(fileName = "metrics.adoc")
package io.vertx.core.metrics;

import io.vertx.docgen.Document;

