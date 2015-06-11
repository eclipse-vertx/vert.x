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