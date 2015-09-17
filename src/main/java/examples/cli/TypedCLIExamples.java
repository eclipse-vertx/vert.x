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

import io.vertx.core.cli.CLI;
import io.vertx.core.cli.CommandLine;
import io.vertx.core.cli.TypedArgument;
import io.vertx.core.cli.TypedOption;
import io.vertx.core.cli.annotations.CLIConfigurator;
import io.vertx.core.cli.converters.Converter;
import io.vertx.docgen.Source;

import java.io.File;
import java.util.List;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
@Source(translate = false)
public class TypedCLIExamples {

  public void example1() {
    CLI cli = CLI.create("copy")
        .setSummary("A command line interface to copy files.")
        .addOption(new TypedOption<Boolean>()
            .setType(Boolean.class)
            .setLongName("directory")
            .setShortName("R")
            .setDescription("enables directory support")
            .setFlag(true))
        .addArgument(new TypedArgument<File>()
            .setType(File.class)
            .setIndex(0)
            .setDescription("The source")
            .setArgName("source"))
        .addArgument(new TypedArgument<File>()
            .setType(File.class)
            .setIndex(0)
            .setDescription("The destination")
            .setArgName("target"));
  }

  public void example2(CLI cli, List<String> userCommandLineArguments) {
    CommandLine commandLine = cli.parse(userCommandLineArguments);
    boolean flag = commandLine.getOptionValue("R");
    File source = commandLine.getArgumentValue("source");
    File target = commandLine.getArgumentValue("target");
  }

  public void example3() {
    CLI cli = CLI.create("some-name")
        .addOption(new TypedOption<Person>()
            .setType(Person.class)
            .setConverter(new PersonConverter())
            .setLongName("person"));
  }

  public void example4(List<String> userCommandLineArguments) {
    CLI cli = CLI.create(AnnotatedCli.class);
    CommandLine commandLine = cli.parse(userCommandLineArguments);
    AnnotatedCli instance = new AnnotatedCli();
    CLIConfigurator.inject(commandLine, instance);
  }

  private class Person {

  }

  private class PersonConverter implements Converter<Person> {

    @Override
    public Person fromString(String s) {
      return null;
    }
  }


}
