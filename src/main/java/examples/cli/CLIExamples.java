/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package examples.cli;

import io.vertx.core.cli.Argument;
import io.vertx.core.cli.CLI;
import io.vertx.core.cli.CommandLine;
import io.vertx.core.cli.Option;

import java.io.PrintStream;
import java.util.Collections;
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
            .setIndex(1)
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

  public void example41() {
    CLI cli = CLI.create("some-name")
        .addOption(new Option()
            .setLongName("color")
            .setDefaultValue("green")
            .addChoice("blue").addChoice("red").addChoice("green")
            .setDescription("a color"));
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

  public void example51() {
    CLI cli = CLI.create("some-name")
        // will have the index 0
        .addArgument(new Argument()
            .setDescription("the first argument")
            .setArgName("arg1"))
        // will have the index 1
        .addArgument(new Argument()
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

  public void example9(PrintStream stream) {
    CLI cli = CLI.create("test")
        .addOption(
            new Option().setLongName("help").setShortName("h").setFlag(true).setHelp(true))
        .addOption(
            new Option().setLongName("mandatory").setRequired(true));

    CommandLine line = cli.parse(Collections.singletonList("-h"));

    // The parsing does not fail and let you do:
    if (!line.isValid() && line.isAskingForHelp()) {
      StringBuilder builder = new StringBuilder();
      cli.usage(builder);
      stream.print(builder.toString());
    }
  }
}
