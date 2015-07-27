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
package io.vertx.core.cli;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Test based on the commons-cli parser tests.
 */
public class IntensiveParserTest {

  private CommandLineParser parser = new CommandLineParser();

  private CommandLine commandLine;

  @Before
  public void setUp() {
    commandLine = new CommandLine();
    commandLine.addOption(OptionModel.<Boolean>builder().shortName("a").longName("enable-a").acceptValue(false)
        .description("turn [a] on or off").type(Boolean.class).build());
    commandLine.addOption(OptionModel.<String>builder().shortName("b").longName("bfile").acceptValue(true)
        .description("set the value of [b]").type(String.class).build());
    commandLine.addOption(OptionModel.<Boolean>builder().shortName("c").longName("copt").acceptValue(false)
        .description("turn [c] on or off").type(Boolean.class).build());
  }

  @Test
  public void testSimpleShort() throws Exception {
    String[] args = new String[]{
        "-a",
        "-b", "toast",
        "foo", "bar"};

    CommandLine cl = parser.parse(commandLine, args);

    assertThat((boolean) cl.getOptionValue("a")).isTrue();
    assertThat((String) cl.getOptionValue("b")).isEqualTo("toast");
    assertThat((boolean) cl.getOptionValue("c")).isFalse();
    assertThat(cl.getAllArguments()).contains("foo", "bar").hasSize(2);
  }

  @Test
  public void testSimpleLong() throws Exception {
    String[] args = new String[]{"--enable-a",
        "--bfile", "toast",
        "foo", "bar"};

    CommandLine cl = parser.parse(commandLine, args);

    assertThat((boolean) cl.getOptionValue("a")).isTrue();
    assertThat((String) cl.getOptionValue("b")).isEqualTo("toast");
    assertThat((String) cl.getOptionValue("bfile")).isEqualTo("toast");
    assertThat((boolean) cl.getOptionValue("c")).isFalse();
    assertThat(cl.getAllArguments()).contains("foo", "bar").hasSize(2);
  }

  @Test
  public void testArgumentsInTheMiddle() throws Exception {
    String[] args = new String[]{"-c",
        "foobar",
        "-b", "toast"};

    CommandLine cl = parser.parse(commandLine, args);
    assertThat((boolean) cl.getOptionValue("c")).isTrue();
    assertThat((String) cl.getOptionValue("b")).isEqualTo("toast");
    assertThat(cl.getAllArguments()).contains("foobar").hasSize(1);
  }

  @Test
  public void testUnrecognizedOption() throws Exception {
    String[] args = new String[]{"-a", "-d", "-b", "toast", "foo", "bar"};
    CommandLine cl = parser.parse(commandLine, args);
    assertThat(cl.getAllArguments()).contains("-d", "foo", "bar").hasSize(3);
  }

  @Test
  public void testMissingValue() throws Exception {
    String[] args = new String[]{"-b"};

    try {
      parser.parse(commandLine, args);
      fail("Exception expected");
    } catch (MissingValueException e) {
      assertThat(e.getOption().getShortName()).isEqualToIgnoringCase("b");
    }
  }

  @Test
  public void testDoubleDash1() throws Exception {
    String[] args = new String[]{
        "--copt",
        "--",
        "-b", "toast"};

    CommandLine cl = parser.parse(commandLine, args);

    assertThat((boolean) cl.getOptionValue("c")).isTrue();
    assertThat((String) cl.getOptionValue("b")).isNull();
    assertThat(cl.getAllArguments()).hasSize(2).contains("-b", "toast");
  }

  @Test
  public void testMissingValueBecauseOfDoubleDash() throws Exception {

    commandLine.addOption(OptionModel.<String>builder().shortName("n").acceptValue(true).type(String.class).build());
    commandLine.addOption(OptionModel.<String>builder().shortName("m").acceptValue(false).type(String.class).build());

    try {
      parser.parse(commandLine, "-n", "--", "-m");
      fail("Exception expected");
    } catch (MissingValueException e) {
      assertThat(e.getOption().getShortName()).isEqualTo("n");
    }
  }

  @Test
  public void testSingleDash() throws Exception {
    String[] args = new String[]{"--copt",
        "-b", "-",
        "-a",
        "-"};

    CommandLine cl = parser.parse(commandLine, args);

    assertThat((boolean) cl.getOptionValue("a")).isTrue();
    assertThat((String) cl.getOptionValue("b")).isEqualTo("-");
    assertThat((boolean) cl.getOptionValue("c")).isTrue();
    assertThat(cl.getAllArguments()).contains("-").hasSize(1);
  }

  @Test
  public void testNegativeArgument() throws Exception {
    String[] args = new String[]{"-b", "-1"};

    CommandLine cl = parser.parse(commandLine, args);
    assertThat((String) cl.getOptionValue("b")).isEqualTo("-1");
  }

  @Test
  public void testNegativeOption() throws Exception {
    String[] args = new String[]{"-b", "-1"};
    commandLine.addOption(new OptionModel.Builder<Boolean>().type(Boolean.class).acceptValue(false).shortName("1").build());

    CommandLine cl = parser.parse(commandLine, args);
    assertThat((String) cl.getOptionValue("b")).isEqualTo("-1");
    assertThat((boolean) cl.getOptionValue("1")).isFalse();

    cl = parser.parse(commandLine, "-1");
    assertThat((boolean) cl.getOptionValue("1")).isTrue();
    assertThat((String) cl.getOptionValue("b")).isNull();
  }

  @Test
  public void testArgumentStartingWithHyphen() throws Exception {
    String[] args = new String[]{"-b", "-foo"};

    CommandLine cl = parser.parse(commandLine, args);
    assertThat((String) cl.getOptionValue("b")).isEqualTo("-foo");
  }

  @Test
  public void testShortWithEqual() throws Exception {
    String[] args = new String[]{"-f=bar"};
    commandLine.addOption(new OptionModel.Builder<String>().type(String.class).acceptValue(true)
        .longName("foo").shortName("f").build());

    CommandLine cl = parser.parse(commandLine, args);
    assertThat((String) cl.getOptionValue("foo")).isEqualTo("bar");
  }

  @Test
  public void testShortWithoutEqual() throws Exception {
    String[] args = new String[]{"-fbar"};
    commandLine.addOption(new OptionModel.Builder<String>().type(String.class).acceptValue(true)
        .longName("foo").shortName("f").build());
    CommandLine cl = parser.parse(commandLine, args);
    assertThat((String) cl.getOptionValue("foo")).isEqualTo("bar");
  }

  @Test
  public void testLongWithEqualDoubleDash() throws Exception {
    String[] args = new String[]{"--foo=bar"};
    commandLine.addOption(new OptionModel.Builder<String>().type(String.class).acceptValue(true)
        .longName("foo").shortName("f").build());
    CommandLine cl = parser.parse(commandLine, args);
    assertThat((String) cl.getOptionValue("foo")).isEqualTo("bar");
  }

  @Test
  public void testLongWithEqualSingleDash() throws Exception {
    String[] args = new String[]{"-foo=bar"};
    commandLine.addOption(new OptionModel.Builder<String>().type(String.class).acceptValue(true)
        .longName("foo").shortName("f").build());
    CommandLine cl = parser.parse(commandLine, args);
    assertThat((String) cl.getOptionValue("foo")).isEqualTo("bar");
  }

  @Test
  public void testLongWithoutEqualSingleDash() throws Exception {
    String[] args = new String[]{"-foobar"};
    commandLine.addOption(new OptionModel.Builder<String>().type(String.class).acceptValue(true)
        .longName("foo").shortName("f").build());
    CommandLine cl = parser.parse(commandLine, args);
    assertThat((String) cl.getOptionValue("foo")).isEqualTo("bar");
  }

  @Test
  public void testAmbiguousLongWithoutEqualSingleDash() throws Exception {
    String[] args = new String[]{"-b", "-foobar"};

    OptionModel<String> f = OptionModel.<String>builder().longName("foo").shortName("f").type(String.class).acceptValue().build();
    OptionModel<Boolean> b = OptionModel.<Boolean>builder().longName("bar").shortName("b").type(Boolean.class).build();

    commandLine.removeOption("b").addOption(f).addOption(b);

    parser.parse(commandLine, args);
    assertThat(b.getValue()).isTrue();
    assertThat(f.getValue()).isEqualTo("bar");
  }

  @Test
  public void testLongWithoutEqualDoubleDash() throws Exception {
    String[] args = new String[]{"--foobar"};

    OptionModel<String> f = OptionModel.<String>builder().longName("foo").shortName("f").type(String.class).acceptValue().build();
    commandLine.addOption(f);

    parser.parse(commandLine, args);

    assertThat(f.getValue()).isNull(); // foo isn't expected to be recognized with a double dash
  }

  @Test
  public void testLongWithUnexpectedArgument1() throws Exception {
    String[] args = new String[]{"--foo=bar"};

    OptionModel<Boolean> f = OptionModel.<Boolean>builder().longName("foo").shortName("f").type(Boolean.class)
        .acceptValue(false) // No value accepted here.
        .build();
    commandLine.addOption(f);

    try {
      parser.parse(commandLine, args);
      fail("Exception expected");
    } catch (InvalidValueException e) {
      assertThat(e.getOption().getShortName()).isEqualToIgnoringCase("f");
      assertThat(e.getValue()).isEqualToIgnoringCase("bar");
    }
  }

  @Test
  public void testLongWithUnexpectedArgument2() throws Exception {
    String[] args = new String[]{"-foobar"};

    OptionModel<Boolean> f = OptionModel.<Boolean>builder().longName("foo").shortName("f").type(Boolean.class)
        .acceptValue(false) // No value accepted here.
        .build();
    commandLine.addOption(f);
    try {
      parser.parse(commandLine, args);
      fail("Exception expected");
    } catch (InvalidValueException e) {
      assertThat(e.getOption().getShortName()).isEqualToIgnoringCase("f");
      assertThat(e.getValue()).isEqualToIgnoringCase("bar");
    }
  }

  @Test
  public void testShortWithUnexpectedArgument() throws Exception {
    String[] args = new String[]{"-f=bar"};

    OptionModel<Boolean> f = OptionModel.<Boolean>builder().longName("foo").shortName("f").type(Boolean.class)
        .acceptValue(false) // No value accepted here.
        .build();

    commandLine.addOption(f);
    try {
      parser.parse(commandLine, args);
      fail("Exception expected");
    } catch (InvalidValueException e) {
      assertThat(e.getOption().getShortName()).isEqualToIgnoringCase("f");
      assertThat(e.getValue()).isEqualToIgnoringCase("bar");
    }
  }

  @Test
  public void testPropertiesOption1() throws Exception {
    String[] args = new String[]{"-Jsource=1.5", "-J", "target", "1.5", "foo"};

    OptionModel<String> f = OptionModel.<String>builder().shortName("J").type(String.class)
        .acceptMultipleValues(true)
        .build();
    commandLine.addOption(f);

    CommandLine cl = parser.parse(commandLine, args);

    List<String> values = cl.getOptionValues("J");
    assertThat(values).hasSize(4).containsExactly("source=1.5", "target", "1.5", "foo");
  }

  @Test
  public void testUnambiguousPartialLongOption1() throws Exception {
    String[] args = new String[]{"--ver"};

    OptionModel<Boolean> v = OptionModel.<Boolean>builder().longName("version").type(Boolean.class)
        .acceptValue(false).build();
    OptionModel<Boolean> h = OptionModel.<Boolean>builder().longName("help").type(Boolean.class)
        .acceptValue(false).build();
    commandLine.addOption(v).addOption(h);

    parser.parse(commandLine, args);
    assertThat(v.getValue()).isTrue();
  }

  @Test
  public void testUnambiguousPartialLongOption2() throws Exception {
    String[] args = new String[]{"-ver"};

    OptionModel<Boolean> v = OptionModel.<Boolean>builder().longName("version").type(Boolean.class)
        .acceptValue(false).build();
    OptionModel<Boolean> h = OptionModel.<Boolean>builder().longName("help").type(Boolean.class)
        .acceptValue(false).build();
    commandLine.addOption(v).addOption(h);

    parser.parse(commandLine, args);
    assertThat(v.getValue()).isTrue();
  }

  @Test
  public void testUnambiguousPartialLongOption3() throws Exception {
    String[] args = new String[]{"--ver=1"};

    OptionModel<Integer> v = OptionModel.<Integer>builder().longName("verbose").acceptValue()
        .type(Integer.class).build();
    OptionModel<Boolean> h = OptionModel.<Boolean>builder().longName("help").type(Boolean.class)
        .acceptValue(false).build();

    commandLine.addOption(v).addOption(h);
    parser.parse(commandLine, args);
    assertThat(v.getValue()).isEqualTo(1);
  }

  @Test
  public void testUnambiguousPartialLongOption4() throws Exception {
    String[] args = new String[]{"-ver=1"};


    OptionModel<Integer> v = OptionModel.<Integer>builder().longName("verbose").acceptValue()
        .type(Integer.class).build();
    OptionModel<Boolean> h = OptionModel.<Boolean>builder().longName("help").type(Boolean.class)
        .acceptValue(false).build();

    commandLine.addOption(v).addOption(h);
    parser.parse(commandLine, args);
    assertThat(v.getValue()).isEqualTo(1);
  }

  @Test
  public void testAmbiguousPartialLongOption1() throws Exception {
    String[] args = new String[]{"--ver"};

    OptionModel<Integer> v1 = OptionModel.<Integer>builder().longName("verbose").acceptValue()
        .type(Integer.class).build();
    OptionModel<Boolean> v2 = OptionModel.<Boolean>builder().longName("version").type(Boolean.class)
        .acceptValue(false).build();

    commandLine.addOption(v1).addOption(v2);
    try {
      parser.parse(commandLine, args);
      fail("Exception expected");
    } catch (AmbiguousOptionException e) {
      assertThat(e.getToken()).isEqualTo("--ver");
      assertThat(e.getOptions()).contains(v1, v2).hasSize(2);
    }
  }

  @Test
  public void testAmbiguousPartialLongOption2() throws Exception {
    String[] args = new String[]{"-ver"};

    OptionModel<Integer> v1 = OptionModel.<Integer>builder().longName("verbose").acceptValue()
        .type(Integer.class).build();
    OptionModel<Boolean> v2 = OptionModel.<Boolean>builder().longName("version").type(Boolean.class)
        .acceptValue(false).build();

    commandLine.addOption(v1).addOption(v2);
    try {
      parser.parse(commandLine, args);
      fail("Exception expected");
    } catch (AmbiguousOptionException e) {
      assertThat(e.getToken()).isEqualTo("-ver");
      assertThat(e.getOptions()).contains(v1, v2).hasSize(2);
    }
  }

  @Test
  public void testAmbiguousPartialLongOption3() throws Exception {
    String[] args = new String[]{"--ver=1"};

    OptionModel<Integer> v1 = OptionModel.<Integer>builder().longName("verbose").acceptValue()
        .type(Integer.class).build();
    OptionModel<Boolean> v2 = OptionModel.<Boolean>builder().longName("version").type(Boolean.class)
        .acceptValue(false).build();

    commandLine.addOption(v1).addOption(v2);
    try {
      parser.parse(commandLine, args);
      fail("Exception expected");
    } catch (AmbiguousOptionException e) {
      assertThat(e.getToken()).isEqualTo("--ver");
      assertThat(e.getOptions()).contains(v1, v2).hasSize(2);
    }
  }

  @Test
  public void testAmbiguousPartialLongOption4() throws Exception {
    String[] args = new String[]{"-ver=1"};

    OptionModel<Integer> v1 = OptionModel.<Integer>builder().longName("verbose").acceptValue()
        .type(Integer.class).build();
    OptionModel<Boolean> v2 = OptionModel.<Boolean>builder().longName("version").type(Boolean.class)
        .acceptValue(false).build();

    commandLine.addOption(v1).addOption(v2);
    try {
      parser.parse(commandLine, args);
      fail("Exception expected");
    } catch (AmbiguousOptionException e) {
      assertThat(e.getToken()).isEqualTo("-ver");
      assertThat(e.getOptions()).contains(v1, v2).hasSize(2);
    }
  }

  @Test
  public void testPartialLongOptionSingleDash() throws Exception {
    String[] args = new String[]{"-ver"};

    OptionModel<Boolean> v2 = OptionModel.<Boolean>builder().longName("version").type(Boolean.class)
        .acceptValue(false).build();
    OptionModel<Integer> v1 = OptionModel.<Integer>builder().acceptValue().shortName("v")
        .type(Integer.class).build();

    commandLine.addOption(v1).addOption(v2);
    parser.parse(commandLine, args);
    assertThat(v1.getValue()).isNull();
    assertThat(v2.getValue()).isTrue();
  }

  @Test
  public void testWithRequiredOption() throws Exception {
    String[] args = new String[]{"-b", "file"};


    OptionModel<String> b = OptionModel.<String>builder().shortName("b").longName("bfile").acceptValue(true)
        .description("set the value of [b]").type(String.class).isRequired().build();
    commandLine.removeOption("b").addOption(b);

    CommandLine cl = parser.parse(commandLine, args);

    assertThat((boolean) cl.getOptionValue("a")).isFalse();
    assertThat(b.getValue()).isEqualTo("file");
    assertThat(cl.getAllArguments()).isEmpty();
  }

  @Test
  public void testOptionAndRequiredOption() throws Exception {
    String[] args = new String[]{"-a", "-b", "file"};

    OptionModel<String> b = OptionModel.<String>builder().shortName("b").longName("bfile").acceptValue(true)
        .description("set the value of [b]").type(String.class).isRequired().build();
    commandLine.removeOption("b").addOption(b);

    CommandLine cl = parser.parse(commandLine, args);

    assertThat((boolean) cl.getOptionValue("a")).isTrue();
    assertThat(b.getValue()).isEqualTo("file");
    assertThat(cl.getAllArguments()).isEmpty();
  }

  @Test
  public void testMissingRequiredOption() throws Exception {
    String[] args = new String[]{"-a"};

    OptionModel<String> b = OptionModel.<String>builder().shortName("b").longName("bfile").acceptValue(true)
        .description("set the value of [b]").type(String.class).isRequired().build();
    commandLine.removeOption("b").addOption(b);


    try {
      CommandLine cl = parser.parse(commandLine, args);
      fail("exception expected");
    } catch (MissingOptionException e) {
      assertThat(e.getExpected()).contains(b).hasSize(1);
    }
  }

  @Test
  public void testMissingRequiredOptions() throws CommandLineException {
    String[] args = new String[]{"-a"};

    OptionModel<String> b = OptionModel.<String>builder().shortName("b").longName("bfile").acceptValue(true)
        .description("set the value of [b]").type(String.class).isRequired().build();
    OptionModel<Boolean> c = OptionModel.<Boolean>builder().shortName("c").longName("copt").acceptValue(false)
        .description("turn [c] on or off").type(Boolean.class).isRequired().build();
    commandLine.removeOption("b").addOption(b).removeOption("c").addOption(c);

    try {
      CommandLine cl = parser.parse(commandLine, args);
      fail("exception expected");
    } catch (MissingOptionException e) {
      assertThat(e.getExpected()).contains(b, c).hasSize(2);
    }
  }

  @Test
  public void testBursting() throws Exception {
    String[] args = new String[]{"-acbtoast", "foo", "bar"};

    CommandLine cl = parser.parse(commandLine, args);

    assertThat((boolean) cl.getOptionValue("a")).isTrue();
    assertThat((boolean) cl.getOptionValue("c")).isTrue();
    assertThat((String) cl.getOptionValue("b")).isEqualTo("toast");
    assertThat(cl.getAllArguments()).hasSize(2).contains("foo", "bar");
  }

  @Test
  public void testUnrecognizedOptionWithBursting() throws Exception {
    String[] args = new String[]{"-adbtoast", "foo", "bar"};

    CommandLine cl = parser.parse(commandLine, args);
    assertThat(cl.getAllArguments()).contains("-adbtoast", "foo", "bar").hasSize(3);
  }

  @Test
  public void testMissingArgWithBursting() throws Exception {
    String[] args = new String[]{"-acb"};

    try {
      parser.parse(commandLine, args);
      fail("exception expected");
    } catch (MissingValueException e) {
      assertThat(e.getOption().getShortName()).isEqualTo("b");
    }
  }

  @Test
  public void testMultiValues() throws Exception {
    String[] args = new String[]{"-e", "one", "two", "-f", "1"};

    OptionModel<String> e = OptionModel.<String>builder().shortName("e").acceptMultipleValues().type(String.class).build();
    OptionModel<Integer> f = OptionModel.<Integer>builder().shortName("f").acceptMultipleValues().type(Integer.class).build();

    commandLine.addOption(e).addOption(f);
    parser.parse(commandLine, args);

    assertThat(e.getValues()).contains("one", "two").hasSize(2);
    assertThat(f.getValues()).contains(1).hasSize(1);
  }

}