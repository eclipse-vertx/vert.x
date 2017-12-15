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

package io.vertx.core.impl.launcher;


import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.assertj.core.api.Assertions.assertThat;

public class VertxCommandLineInterfaceTest {

  VertxCommandLauncher itf = new VertxCommandLauncher();
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
  public void testUsageOnDifferentStream() {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream stream = new PrintStream(baos);
    itf = new VertxCommandLauncher() {
      /**
       * @return the printer used to write the messages. Defaults to {@link System#out}.
       */
      @Override
      public PrintStream getPrintStream() {
        return stream;
      }
    };

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
        .contains("Usage: ")
        .contains("A command saying hello.") // Summary
        .contains("A simple command to wish you a good day.") // Description
        .contains("-n,--name <name>") // Option
        .contains("your name"); // Option description
  }

  @Test
  public void testCommandUsageForGoodbye() {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream stream = new PrintStream(baos);
    System.setOut(stream);

    itf.execute("bye", "--help");

    assertThat(baos.toString())
        .contains("A command saying goodbye.") // Summary
        .contains("-n <value>"); // Option
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
        .contains("-o1 <opt>", " [-o2] ")
        .contains("arg1", "[arg2]")
        .contains("A command with options and arguments.") // Summary
        .contains("This is a complex command.") // Description
        .contains("-o1,--option1 <opt>") // Option
        .contains("-o2,--option2"); // Option
  }

  @Test
  public void testHiddenCommandUsage() {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream stream = new PrintStream(baos);
    System.setOut(stream);

    itf.execute("hidden", "-h");

    assertThat(baos.toString())
        .contains("-n <value>")
        .doesNotContain("-c ")
        .doesNotContain("count");
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
    itf = new VertxCommandLauncher();

    itf.execute("complex", "-option1=vertx", "this is arg 1");
    assertThat(baos.toString())
        .contains("Option 1 : vertx")
        .contains("Option 2 : false")
        .contains("Arg 1 : this is arg 1")
        .contains("Arg 2 : 0");

    baos.reset();
    itf = new VertxCommandLauncher();
    itf.execute("complex", "-option1=vertx", "this is arg 1", "24", "xxx", "yyy");
    assertThat(baos.toString())
        .contains("Option 1 : vertx")
        .contains("Option 2 : false")
        .contains("Arg 1 : this is arg 1")
        .contains("Arg 2 : 24")
        .contains("xxx", "yyy");

    baos.reset();
    itf = new VertxCommandLauncher();
    itf.execute("complex", "this is arg 1", "24");
    assertThat(baos.toString())
        .doesNotContain("Option 1 : vertx")
        .doesNotContain("Arg 1 : this is arg 1")
        .contains("Usage")
        .contains("The option", "is required");

    baos.reset();
    itf = new VertxCommandLauncher();
    itf.execute("complex", "-option1=vertx");
    assertThat(baos.toString())
        .doesNotContain("Option 1 : vertx")
        .contains("Usage")
        .contains("The argument 'arg1' is required");
  }

  @Test
  public void testUsingDifferentPrinter() {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream stream = new PrintStream(baos);
    itf = new VertxCommandLauncher() {
      @Override
      public PrintStream getPrintStream() {
        return stream;
      }
    };

    itf.execute("complex", "-option1=vertx", "-o2", "this is arg 1", "25");

    assertThat(baos.toString())
        .contains("Option 1 : vertx")
        .contains("Option 2 : true")
        .contains("Arg 1 : this is arg 1")
        .contains("Arg 2 : 25");

    baos.reset();
    itf = new VertxCommandLauncher() {
      @Override
      public PrintStream getPrintStream() {
        return stream;
      }
    };

    itf.execute("complex", "-option1=vertx", "this is arg 1");
    assertThat(baos.toString())
        .contains("Option 1 : vertx")
        .contains("Option 2 : false")
        .contains("Arg 1 : this is arg 1")
        .contains("Arg 2 : 0");

    baos.reset();
    itf = new VertxCommandLauncher() {
      @Override
      public PrintStream getPrintStream() {
        return stream;
      }
    };
    itf.execute("complex", "-option1=vertx", "this is arg 1", "24", "xxx", "yyy");
    assertThat(baos.toString())
        .contains("Option 1 : vertx")
        .contains("Option 2 : false")
        .contains("Arg 1 : this is arg 1")
        .contains("Arg 2 : 24")
        .contains("xxx", "yyy");

    baos.reset();
    itf = new VertxCommandLauncher() {
      @Override
      public PrintStream getPrintStream() {
        return stream;
      }
    };
    itf.execute("complex", "this is arg 1", "24");
    assertThat(baos.toString())
        .doesNotContain("Option 1 : vertx")
        .doesNotContain("Arg 1 : this is arg 1")
        .contains("Usage")
        .contains("The option", "is required");

    baos.reset();
    itf = new VertxCommandLauncher() {
      @Override
      public PrintStream getPrintStream() {
        return stream;
      }
    };
    itf.execute("complex", "-option1=vertx");
    assertThat(baos.toString())
        .doesNotContain("Option 1 : vertx")
        .contains("Usage")
        .contains("The argument 'arg1' is required");
  }

}
