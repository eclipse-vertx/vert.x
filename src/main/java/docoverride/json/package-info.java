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
 * == JSON
 * :toc: left
 * <p>
 * Unlike some other languages, Java does not have first class support for http://json.org/[JSON] so we provide
 * two classes to make handling JSON in your Vert.x applications a bit easier.
 * <p>
 * === JSON objects
 * <p>
 * The {@link io.vertx.core.json.JsonObject} class represents JSON objects.
 * <p>
 * A JSON object is basically just a map which has string keys and values can be of one of the JSON supported types
 * (string, number, boolean).
 * <p>
 * JSON objects also support null values.
 * <p>
 * ==== Creating JSON objects
 * <p>
 * Empty JSON objects can be created with the default constructor.
 * <p>
 * You can create a JSON object from a string JSON representation as follows:
 * <p>
 * [source,java]
 * ----
 * {@link docoverride.json.Examples#example0_1}
 * ----
 * <p>
 * ==== Putting entries into a JSON object
 * <p>
 * Use the {@link io.vertx.core.json.JsonObject#put} methods to put values into the JSON object.
 * <p>
 * The method invocations can be chained because of the fluent API:
 * <p>
 * [source,java]
 * ----
 * {@link docoverride.json.Examples#example1}
 * ----
 * <p>
 * ==== Getting values from a JSON object
 * <p>
 * You get values from a JSON object using the {@code getXXX} methods, for example:
 * <p>
 * [source,java]
 * ----
 * {@link docoverride.json.Examples#example2}
 * ----
 * <p>
 * ==== Encoding the JSON object to a String
 * <p>
 * You use {@link io.vertx.core.json.JsonObject#encode} to encode the object to a String form.
 * <p>
 * === JSON arrays
 * <p>
 * The {@link io.vertx.core.json.JsonArray} class represents JSON arrays.
 * <p>
 * A JSON array is a sequence of values (string, number, boolean).
 * <p>
 * JSON arrays can also contain null values.
 * <p>
 * ==== Creating JSON arrays
 * <p>
 * Empty JSON arrays can be created with the default constructor.
 * <p>
 * You can create a JSON array from a string JSON representation as follows:
 * <p>
 * [source,java]
 * ----
 * {@link docoverride.json.Examples#example0_2}
 * ----
 * <p>
 * ==== Adding entries into a JSON array
 * <p>
 * You add entries to a JSON array using the {@link io.vertx.core.json.JsonArray#add} methods.
 * <p>
 * [source,java]
 * ----
 * {@link docoverride.json.Examples#example3}
 * ----
 * <p>
 * ==== Getting values from a JSON array
 * <p>
 * You get values from a JSON array using the {@code getXXX} methods, for example:
 * <p>
 * [source,java]
 * ----
 * {@link docoverride.json.Examples#example4}
 * ----
 * <p>
 * ==== Encoding the JSON array to a String
 * <p>
 * You use {@link io.vertx.core.json.JsonArray#encode} to encode the array to a String form.
 */
@Document(fileName = "override/json.adoc")
package docoverride.json;

import io.vertx.docgen.Document;

