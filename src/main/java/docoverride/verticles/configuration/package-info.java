/*
 * Copyright (c) 2014 Red Hat, Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

/**
 * === Passing configuration to a verticle
 *
 * Configuration in the form of JSON can be passed to a verticle at deployment time:
 *
 * [source,$lang]
 * ----
 * {@link examples.CoreExamples#example13}
 * ----
 *
 * This configuration is then available via the {@link io.vertx.core.Context} object or directly using the
 * {@link io.vertx.core.AbstractVerticle#config()} method. The configuration is returned as a JSON object so you
 * can retrieve data as follows:
 *
 * [source,$lang]
 * ----
 * {@link examples.ConfigurableVerticleExamples#start()}
 * ----
 *
 * === Accessing environment variables in a Verticle
 *
 * Environment variables and system properties are accessible using the Java API:
 *
 * [source,$lang]
 * ----
 * {@link examples.CoreExamples#systemAndEnvProperties()}
 * ----
 *
 */
@Document(fileName = "override/verticle-configuration.adoc")
package docoverride.verticles.configuration;

import io.vertx.docgen.Document;
