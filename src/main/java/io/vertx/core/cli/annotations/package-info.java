/*
 *  Copyright (c) 2011-2015 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *       The Eclipse Public License is available at
 *       http://www.eclipse.org/legal/epl-v10.html
 *
 *       The Apache License v2.0 is available at
 *       http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

/**
 * === Typed options and arguments
 *
 * The described {@link io.vertx.core.cli.Option} and {@link io.vertx.core.cli.Argument} classes are _untyped_,
 * meaning that the only get String values.
 * {@link io.vertx.core.cli.TypedOption} and {@link io.vertx.core.cli.TypedArgument} let you specify a _type_, so the
 * (String) raw value is converted to the specified type.
 *
 * Instead of
 * {@link io.vertx.core.cli.Option} and {@link io.vertx.core.cli.Argument}, use {@link io.vertx.core.cli.TypedOption}
 * and {@link io.vertx.core.cli.TypedArgument} in the {@link io.vertx.core.cli.CLI} definition:
 *
 * [source,java]
 * ----
 * {@link examples.cli.TypedCLIExamples#example1}
 * ----
 *
 * Then you can retrieve the converted values as follows:
 *
 * [source,java]
 * ----
 * {@link examples.cli.TypedCLIExamples#example2}
 * ----
 *
 * The vert.x CLI is able to convert to classes:
 *
 * * having a constructor with a single
 * {@link java.lang.String} argument, such as {@link java.io.File} or {@link io.vertx.core.json.JsonObject}
 * * with a static `from` or `fromString` method
 * * with a static `valueOf` method, such as primitive types and enumeration
 *
 * In addition, you can implement your own {@link io.vertx.core.cli.converters.Converter} and instruct the CLI to use
 * this converter:
 *
 * [source,java]
 * ----
 * {@link examples.cli.TypedCLIExamples#example3}
 * ----
 *
 * For booleans, the boolean values are evaluated to {@code true}: `on`, `yes`, `1`, `true`.
 *
 * === Using annotations
 *
 * You can also define your CLI using annotations. Definition is done using annotation on the class and on _setter_
 * methods:
 *
 * [source, java]
 * ----
 * &#64;Name("some-name")
 * &#64;Summary("some short summary.")
 * &#64;Description("some long description")
 * public class AnnotatedCli {
 *
 *   private boolean flag;
 *   private String name;
 *   private String arg;
 *
 *  &#64;Option(shortName = "f", flag = true)
 *  public void setFlag(boolean flag) {
 *    this.flag = flag;
 *  }
 *
 *  &#64;Option(longName = "name")
 *  public void setName(String name) {
 *    this.name = name;
 *  }
 *
 *  &#64;Argument(index = 0)
 *  public void setArg(String arg) {
 *   this.arg = arg;
 *  }
 * }
 * ----
 *
 * Once annotated, you can define the {@link io.vertx.core.cli.CLI} and inject the values using:
 *
 * [source,java]
 * ----
 * {@link examples.cli.TypedCLIExamples#example4}
 * ----
 */
@Document(fileName = "cli-for-java.adoc")
package io.vertx.core.cli.annotations;

import io.vertx.docgen.Document;