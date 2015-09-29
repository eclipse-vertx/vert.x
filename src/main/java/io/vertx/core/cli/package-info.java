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
 * Vert.x Core provides an API for parsing command line arguments passed to programs. It's also able to print help
 * messages detailing the options available for a command line tool. Even if such features are far from
 * the Vert.x core topics, this API is used in the {@link io.vertx.core.Launcher} class that you can use in _fat-jar_
 * and in the `vertx` command line tools. In addition, it's polyglot (can be used from any supported language) and is
 * used in Vert.x Shell.
 *
 * Vert.x CLI provides a model to describe your command line interface, but also a parser. This parser supports
 * different types of syntax:
 *
 * * POSIX like options (ie. `tar -zxvf foo.tar.gz`)
 * * GNU like long options (ie. `du --human-readable --max-depth=1`)
 * * Java like properties (ie. `java -Djava.awt.headless=true -Djava.net.useSystemProxies=true Foo`)
 * * Short options with value attached (ie. `gcc -O2 foo.c`)
 * * Long options with single hyphen (ie. `ant -projecthelp`)
 *
 * Using the CLI api is a 3-steps process:
 *
 * 1. The definition of the command line interface
 * 2. The parsing of the user command line
 * 3. The query / interrogation
 *
 * === Definition Stage
 *
 * Each command line interface must define the set of options and arguments that will be used. It also requires a
 * name. The CLI API uses the {@link io.vertx.core.cli.Option} and {@link io.vertx.core.cli.Argument} classes to
 * describe options and arguments:
 *
 * [source,$lang]
 * ----
 * {@link examples.cli.CLIExamples#example1}
 * ----
 *
 * As you can see, you can create a new {@link io.vertx.core.cli.CLI} using
 * {@link io.vertx.core.cli.CLI#create(java.lang.String)}. The passed string is the name of the CLI. Once created you
 * can set the summary and description. The summary is intended to be short (one line), while the description can
 * contain more details. Each option and argument are also added on the {@code CLI} object using the
 * {@link io.vertx.core.cli.CLI#addArgument(io.vertx.core.cli.Argument)} and
 * {@link io.vertx.core.cli.CLI#addOption(io.vertx.core.cli.Option)} methods.
 *
 * ==== Options
 *
 * An {@link io.vertx.core.cli.Option} is a command line parameter identified by a _key_ present in the user command
 * line. Options must have at least a long name or a short name. Long name are generally used using a `--` prefix,
 * while short names are used with a single `-`. Options can get a description displayed in the usage (see below).
 * Options can receive 0, 1 or several values. An option receiving 0 values is a `flag`, and must be declared using
 * {@link io.vertx.core.cli.Option#setFlag(boolean)}. By default, options receive a single value, however, you can
 * configure the option to receive several values using {@link io.vertx.core.cli.Option#setMultiValued(boolean)}:
 *
 * [source,$lang]
 * ----
 * {@link examples.cli.CLIExamples#example2}
 * ----
 *
 * Options can be marked as mandatory. A mandatory option not set in the user command line throws an exception during
 * the parsing:
 *
 * [source,$lang]
 * ----
 * {@link examples.cli.CLIExamples#example3}
 * ----
 *
 * Non-mandatory options can have a _default value_. This value would be used if the user does not set the option in
 * the command line:
 *
 * [source,$lang]
 * ----
 * {@link examples.cli.CLIExamples#example4}
 * ----
 *
 * An option can be _hidden_ using the {@link io.vertx.core.cli.Option#setHidden(boolean)} method. Hidden option are
 * not listed in the usage, but can still be used in the user command line (for power-users).
 *
 * Options can also be instantiated from their JSON form.
 *
 * ==== Arguments
 *
 * Unlike options, arguments do not have a _key_ and are identified by their _index_. For example, in
 * `java com.acme.Foo`, `com.acme.Foo` is an argument.
 *
 * Arguments do not have a name, there are identified using a mandatory 0-based index. The first parameter has the
 * index `0`:
 *
 * [source,$lang]
 * ----
 * {@link examples.cli.CLIExamples#example5}
 * ----
 *
 * The `argName` is optional and used in the usage message.
 *
 * As options, {@link io.vertx.core.cli.Argument} can:
 *
 * * be hidden using {@link io.vertx.core.cli.Argument#setHidden(boolean)}
 * * be mandatory using {@link io.vertx.core.cli.Argument#setRequired(boolean)}
 * * have a default value using {@link io.vertx.core.cli.Argument#setDefaultValue(java.lang.String)}
 *
 * Arguments can also be instantiated from their JSON form.
 *
 * ==== Usage generation
 *
 * Once your {@link io.vertx.core.cli.CLI} instance is configured, you can generate the _usage_ message:
 *
 * [source,$lang]
 * ----
 * {@link examples.cli.CLIExamples#example6}
 * ----
 *
 * It generates an usage message like this one:
 *
 *[source]
 * ----
 * Usage: copy [-R] source target
 *
 * A command line interface to copy files.
 *
 *   -R,--directory   enables directory support
 * ----
 *
 * If you need to tune the usage message, check the {@link io.vertx.core.cli.UsageMessageFormatter} class.
 *
 * === Parsing Stage
 *
 * Once your {@link io.vertx.core.cli.CLI} instance is configured, you can parse the user command line to evaluate
 * each option and argument:
 *
 * [source,$lang]
 * ----
 * {@link examples.cli.CLIExamples#example7}
 * ----
 *
 * The {@link io.vertx.core.cli.CLI#parse(java.util.List)} method returns a {@link io.vertx.core.cli.CommandLine}
 * object containing the values. By default, it validates the user command line and checks that each mandatory options
 * and arguments have been set as well as the number of values received by each option. You can disable the
 * validation by passing `false` as second parameter of {@link io.vertx.core.cli.CLI#parse(java.util.List, boolean)}.
 * This is useful if you want to check an argument or option is present even if the parsed command line is invalid.
 *
 * === Query / Interrogation Stage
 *
 * Once parsed, you can retrieve the values of the options and arguments from the
 * {@link io.vertx.core.cli.CommandLine} object returned by the {@link io.vertx.core.cli.CLI#parse(java.util.List)}
 * method:
 *
 * [source,$lang]
 * ----
 * {@link examples.cli.CLIExamples#example8}
 * ----
 *
 * [language,java]
 * ----
 * include::cli-for-java.adoc[]
 * ----
 *
 */
@Document(fileName = "cli.adoc")
package io.vertx.core.cli;

import io.vertx.docgen.Document;