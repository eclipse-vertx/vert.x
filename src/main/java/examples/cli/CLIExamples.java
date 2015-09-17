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

package examples.cli;

import io.vertx.core.cli.Argument;
import io.vertx.core.cli.CLI;
import io.vertx.core.cli.CommandLine;
import io.vertx.core.cli.Option;

import java.util.List;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class CLIExamples {

  public void example1() {
    CLI cli = CLI.create("copy")
        .setSummary("A command line interface to copy files.")
        .addOption(new Option()
            .setLongName("directory")
            .setShortName("R")
            .setDescription("enables directory support")
            .setFlag(true))
        .addArgument(new Argument()
            .setIndex(0)
            .setDescription("The source")
            .setArgName("source"))
        .addArgument(new Argument()
            .setIndex(0)
            .setDescription("The destination")
            .setArgName("target"));
  }

  public void example2() {
    CLI cli = CLI.create("some-name")
        .setSummary("A command line interface illustrating the options valuation.")
        .addOption(new Option()
            .setLongName("flag").setShortName("f").setFlag(true).setDescription("a flag"))
        .addOption(new Option()
            .setLongName("single").setShortName("s").setDescription("a single-valued option"))
        .addOption(new Option()
            .setLongName("multiple").setShortName("m").setMultiValued(true)
            .setDescription("a multi-valued option"));
  }

  public void example3() {
    CLI cli = CLI.create("some-name")
        .addOption(new Option()
            .setLongName("mandatory")
            .setRequired(true)
            .setDescription("a mandatory option"));
  }

  public void example4() {
    CLI cli = CLI.create("some-name")
        .addOption(new Option()
            .setLongName("optional")
            .setDefaultValue("hello")
            .setDescription("an optional option with a default value"));
  }

  public void example5() {
    CLI cli = CLI.create("some-name")
        .addArgument(new Argument()
            .setIndex(0)
            .setDescription("the first argument")
            .setArgName("arg1"))
        .addArgument(new Argument()
            .setIndex(1)
            .setDescription("the second argument")
            .setArgName("arg2"));
  }

  public void example6() {
    CLI cli = CLI.create("copy")
        .setSummary("A command line interface to copy files.")
        .addOption(new Option()
            .setLongName("directory")
            .setShortName("R")
            .setDescription("enables directory support")
            .setFlag(true))
        .addArgument(new Argument()
            .setIndex(0)
            .setDescription("The source")
            .setArgName("source"))
        .addArgument(new Argument()
            .setIndex(0)
            .setDescription("The destination")
            .setArgName("target"));

    StringBuilder builder = new StringBuilder();
    cli.usage(builder);
  }

  public void example7(CLI cli, List<String> userCommandLineArguments) {
    CommandLine commandLine = cli.parse(userCommandLineArguments);
  }

  public void example8(CLI cli, List<String> userCommandLineArguments) {
    CommandLine commandLine = cli.parse(userCommandLineArguments);
    String opt = commandLine.getOptionValue("my-option");
    boolean flag = commandLine.isFlagEnabled("my-flag");
    String arg0 = commandLine.getArgumentValue(0);
  }
}
