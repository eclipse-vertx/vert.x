package io.vertx.core.cli;


import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.assertj.core.api.Assertions.assertThat;

public class VertxCommandLineInterfaceTest {

  VertxCommandLineInterface itf = new VertxCommandLineInterface();
  private PrintStream out;

  @Before
  public void setUp() {
    out = System.out;
  }

  @After
  public void tearDown() {
    System.setOut(out);
  }

  @Test
  public void testUsage() {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream stream = new PrintStream(baos);
    System.setOut(stream);

    itf.execute("--help");

    assertThat(baos.toString()).contains("hello").contains("bye")
        .doesNotContain("hidden").contains("A command saying hello");
  }

  @Test
  public void testCommandUsageForHello() {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream stream = new PrintStream(baos);
    System.setOut(stream);

    itf.execute("hello", "--help");

    assertThat(baos.toString())
        .contains("A command saying hello.") // Summary
        .contains("A simple command to wish you a good day.") // Description
        .contains("--cwd <dir>") // Inherited option
        .contains("-n,--name <name>") // Option
        .contains("your name") // Option description
        .contains("-D,--systemProperty <key>=<value>"); // Inherited option with short and long names
  }

  @Test
  public void testCommandUsageForGoodbye() {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream stream = new PrintStream(baos);
    System.setOut(stream);

    itf.execute("bye", "--help");

    assertThat(baos.toString())
        .contains("A command saying goodbye.") // Summary
        .contains("--cwd <dir>") // Inherited option
        .contains("-n <value>") // Option
        .contains("-D,--systemProperty <key>=<value>"); // Inherited option with short and long names
  }

  @Test
  public void testCommandNotFound() {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream stream = new PrintStream(baos);
    System.setOut(stream);
    itf.execute("missing");
    assertThat(baos.toString()).contains("The command", "missing", "See", "--help");

    baos.reset();
    itf.execute("missing", "--help");
    assertThat(baos.toString()).contains("The command", "missing", "See", "--help");
  }

  @Test
  public void testMissingValue() {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream stream = new PrintStream(baos);
    System.setOut(stream);

    itf.execute("hello", "-n");

    assertThat(baos.toString())
        .contains("The option 'name' requires a value");
  }

  @Test
  public void testMissingOption() {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream stream = new PrintStream(baos);
    System.setOut(stream);

    itf.execute("hello");

    assertThat(baos.toString())
        .contains("The option", "name", "is required");
  }

  @Test
  public void testInvalidValue() {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream stream = new PrintStream(baos);
    System.setOut(stream);

    itf.execute("hidden", "-n", "vert.x", "-c", "hello");

    assertThat(baos.toString())
        .contains("The value 'hello' is not accepted by 'count'");
  }

  @Test
  public void testComplexCommandUsage() {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream stream = new PrintStream(baos);
    System.setOut(stream);

    itf.execute("complex", "-h");

    assertThat(baos.toString())
        .contains("-o1 <opt> [-o2]")
        .contains("arg1 [arg2]")
        .contains("A command with options and arguments.") // Summary
        .contains("This is a complex command.") // Description
        .contains("--cwd <dir>") // Inherited option
        .contains("-o1,--option1 <opt>") // Option
        .contains("-o2,--option2") // Option
        .contains("-D,--systemProperty <key>=<value>"); // Inherited option with short and long names
  }

  @Test
  public void testComplexCommandExecutions() {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream stream = new PrintStream(baos);
    System.setOut(stream);

    itf.execute("complex", "-option1=vertx", "-o2", "this is arg 1", "25");

    assertThat(baos.toString())
        .contains("Option 1 : vertx")
        .contains("Option 2 : true")
        .contains("Arg 1 : this is arg 1")
        .contains("Arg 2 : 25");

    baos.reset();
    itf = new VertxCommandLineInterface();

    itf.execute("complex", "-option1=vertx", "this is arg 1");
    assertThat(baos.toString())
        .contains("Option 1 : vertx")
        .contains("Option 2 : false")
        .contains("Arg 1 : this is arg 1")
        .contains("Arg 2 : 0");

    baos.reset();
    itf = new VertxCommandLineInterface();
    itf.execute("complex", "-option1=vertx", "this is arg 1", "24", "xxx", "yyy");
    assertThat(baos.toString())
        .contains("Option 1 : vertx")
        .contains("Option 2 : false")
        .contains("Arg 1 : this is arg 1")
        .contains("Arg 2 : 24")
        .contains("xxx", "yyy");

    baos.reset();
    itf = new VertxCommandLineInterface();
    itf.execute("complex", "this is arg 1", "24");
    assertThat(baos.toString())
        .doesNotContain("Option 1 : vertx")
        .doesNotContain("Arg 1 : this is arg 1")
        .contains("Usage")
        .contains("The option", "is required");

    baos.reset();
    itf = new VertxCommandLineInterface();
    itf.execute("complex", "-option1=vertx");
    assertThat(baos.toString())
        .doesNotContain("Option 1 : vertx")
        .contains("Usage")
        .contains("The argument 'arg1' is required");
  }

}