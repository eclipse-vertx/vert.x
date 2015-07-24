package io.vertx.core.cli;

import io.vertx.core.cli.commands.HelloCommand;
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