/*
 * Copyright (c) 2011-2017 The original author or authors
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     https://www.eclipse.org/legal/epl-2.0/
 *
 *     The Apache License v2.0 is available at
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * You may elect to redistribute this code under either of these licenses.
 */
package io.vertx.core.impl.launcher;

import io.vertx.core.cli.CLI;
import io.vertx.core.cli.CLIException;
import io.vertx.core.cli.CommandLine;
import io.vertx.core.cli.annotations.CLIConfigurator;
import io.vertx.core.cli.annotations.Name;
import io.vertx.core.impl.launcher.commands.HelloCommand;
import io.vertx.core.spi.launcher.DefaultCommand;
import io.vertx.core.spi.launcher.ExecutionContext;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultCommandTest {

  private HelloCommand command = new HelloCommand();

  private CommandLine parse(CLI cli, String... args) throws CLIException {
    return cli.parse(Arrays.asList(args));
  }

  @Test
  public void testCWD() throws CLIException {
    CLI cli = CLIConfigurator.define(command.getClass());

    CommandLine evaluatedCLI = parse(cli, "--name=vert.x");
    CLIConfigurator.inject(evaluatedCLI, command);
    assertThat(command.getCwd()).isEqualTo(new File("."));

    evaluatedCLI = parse(cli, "--cwd=target", "--name=vert.x");
    CLIConfigurator.inject(evaluatedCLI, command);
    assertThat(command.getCwd()).isEqualTo(new File("target"));
  }

  @Test
  public void testSystemProperties() throws CLIException {
    CLI cli = CLIConfigurator.define(command.getClass());
    VertxCommandLauncher launcher = new VertxCommandLauncher();
    CommandLine evaluatedCLI = parse(cli, "--name=vert.x", "-Dfoo=bar", "--systemProp=x=y");
    CLIConfigurator.inject(evaluatedCLI, command);
    command.setUp(new ExecutionContext(command, launcher, evaluatedCLI));
    assertThat(System.getProperty("foo")).isEqualToIgnoringCase("bar");
    assertThat(System.getProperty("x")).isEqualToIgnoringCase("y");

    command.tearDown();
    // System properties are not removed by the tearDown.
    assertThat(System.getProperty("foo")).isEqualToIgnoringCase("bar");
    assertThat(System.getProperty("x")).isEqualToIgnoringCase("y");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testThatCLINeedsAName() {
    CLIConfigurator.define(MyCommandWithoutName.class);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testThatCLINeedsANonEmptyName() {
    CLIConfigurator.define(MyCommandWithoutEmptyName.class);
  }

  public static class MyCommandWithoutName extends DefaultCommand {

    @Override
    public void run() throws CLIException {

    }
  }

  @Name(value = "")
  public static class MyCommandWithoutEmptyName extends DefaultCommand {

    @Override
    public void run() throws CLIException {

    }
  }

}
