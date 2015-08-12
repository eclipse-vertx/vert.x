/*
 *  Copyright (c) 2011-2013 The original author or authors
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
package io.vertx.core.impl.cli;

import io.vertx.core.impl.cli.CommandLineParser;
import io.vertx.core.impl.cli.CommandManager;
import io.vertx.core.impl.cli.VertxCommandLineInterface;
import io.vertx.core.impl.cli.commands.HelloCommand;
import io.vertx.core.spi.cli.CommandLine;
import io.vertx.core.spi.cli.CommandLineException;
import io.vertx.core.spi.cli.ExecutionContext;
import org.junit.Test;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultCommandTest {

  HelloCommand command = new HelloCommand();


  @Test
  public void testCWD() throws CommandLineException {
    CommandLine line = new CommandLine();
    CommandManager.define(command, line);
    CommandLineParser parser = new CommandLineParser();

    parser.parse(line, "--name=vert.x");
    CommandManager.inject(command, line);
    assertThat(command.getCwd()).isEqualTo(new File("."));

    parser.parse(line, "--cwd=target", "--name=vert.x");
    CommandManager.inject(command, line);
    assertThat(command.getCwd()).isEqualTo(new File("target"));
  }

  @Test
  public void testSystemProperties() throws CommandLineException {
    CommandLine line = new CommandLine();
    ExecutionContext context = new ExecutionContext(line, new VertxCommandLineInterface());
    CommandManager.define(command, line);

    command.initialize(context);
    line.parse("--name=vert.x", "-Dfoo=bar", "--systemProp=x=y");
    CommandManager.inject(command, line);
    command.setup();
    assertThat(System.getProperty("foo")).isEqualToIgnoringCase("bar");
    assertThat(System.getProperty("x")).isEqualToIgnoringCase("y");

    command.tearDown();
    assertThat(System.getProperty("foo")).isNull();
    assertThat(System.getProperty("x")).isNull();
  }

}