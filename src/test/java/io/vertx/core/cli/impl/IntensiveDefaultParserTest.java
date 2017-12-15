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

package io.vertx.core.cli.impl;


import io.vertx.core.cli.*;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Test based on the commons-cli parser tests.
 */
public class IntensiveDefaultParserTest {

  private DefaultCLI cli;

  @Before
  public void setUp() {
    cli = new DefaultCLI();
    cli.setName("test").setHidden(false).setDescription("A test command");

    cli.addOption(new TypedOption<Boolean>().setType(Boolean.class).setShortName("a").setLongName("enable-a")
        .setFlag(true).setDescription("turn [a] on or off"));
    cli.addOption(new TypedOption<String>().setType(String.class).setShortName("b").setLongName("bfile").setSingleValued(true)
        .setDescription("set the value of [b]"));
    cli.addOption(new TypedOption<Boolean>().setType(Boolean.class).setShortName("c").setLongName("copt").setSingleValued(false)
        .setDescription("turn [c] on or off"));
  }

  private boolean getBooleanOption(CommandLine evaluatedCLI, String name) {
    return evaluatedCLI.getOptionValue(name);
  }

  private String getStringOption(CommandLine evaluatedCLI, String name) {
    return evaluatedCLI.getOptionValue(name);
  }

  @Test
  public void testSimpleShort() throws Exception {
    String[] args = new String[]{
        "-a",
        "-b", "toast",
        "foo", "bar"};
    CommandLine evaluated = cli.parse(Arrays.asList(args));

    assertThat(getBooleanOption(evaluated, "a")).isTrue();
    assertThat(getStringOption(evaluated, "b")).isEqualTo("toast");
    assertThat(getBooleanOption(evaluated, "c")).isFalse();
    assertThat(evaluated.allArguments()).contains("foo", "bar").hasSize(2);
  }

  @Test
  public void testSimpleLong() throws Exception {
    String[] args = new String[]{"--enable-a",
        "--bfile", "toast",
        "foo", "bar"};

    CommandLine evaluated = cli.parse(Arrays.asList(args));

    assertThat(getBooleanOption(evaluated, "a")).isTrue();
    assertThat(getStringOption(evaluated, "b")).isEqualTo("toast");
    assertThat(getStringOption(evaluated, "bfile")).isEqualTo("toast");
    assertThat(getBooleanOption(evaluated, "c")).isFalse();
    assertThat(evaluated.allArguments()).contains("foo", "bar").hasSize(2);
  }

  @Test
  public void testArgumentsInTheMiddle() throws Exception {
    String[] args = new String[]{"-c",
        "foobar",
        "-b", "toast"};

    CommandLine evaluated = cli.parse(Arrays.asList(args));
    assertThat(getBooleanOption(evaluated, "c")).isTrue();
    assertThat(getStringOption(evaluated, "b")).isEqualTo("toast");
    assertThat(evaluated.allArguments()).contains("foobar").hasSize(1);
  }

  @Test
  public void testUnrecognizedOption() throws Exception {
    String[] args = new String[]{"-a", "-d", "-b", "toast", "foo", "bar"};
    CommandLine evaluated = cli.parse(Arrays.asList(args));
    assertThat(evaluated.allArguments()).contains("-d", "foo", "bar").hasSize(3);
  }

  @Test
  public void testMissingValue() throws Exception {
    String[] args = new String[]{"-b"};

    try {
      CommandLine evaluated = cli.parse(Arrays.asList(args));
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

    CommandLine evaluated = cli.parse(Arrays.asList(args));

    assertThat(getBooleanOption(evaluated, "c")).isTrue();
    assertThat(getStringOption(evaluated, "b")).isNull();
    assertThat(evaluated.allArguments()).hasSize(2).contains("-b", "toast");
  }

  @Test
  public void testMissingValueBecauseOfDoubleDash() throws Exception {

    cli.addOption(new TypedOption<String>().setType(String.class).setShortName("n").setSingleValued(true));
    cli.addOption(new TypedOption<String>().setType(String.class).setShortName("m").setSingleValued(false));

    try {
      cli.parse(Arrays.asList("-n", "--", "-m"));
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

    CommandLine evaluated = cli.parse(Arrays.asList(args));

    assertThat(getBooleanOption(evaluated, "a")).isTrue();
    assertThat(getStringOption(evaluated, "b")).isEqualTo("-");
    assertThat(getBooleanOption(evaluated, "c")).isTrue();
    assertThat(evaluated.allArguments()).contains("-").hasSize(1);
  }

  @Test
  public void testNegativeArgument() throws Exception {
    String[] args = new String[]{"-b", "-1"};

    CommandLine evaluated = cli.parse(Arrays.asList(args));
    assertThat(getStringOption(evaluated, "b")).isEqualTo("-1");
  }

  @Test
  public void testNegativeOption() throws Exception {
    String[] args = new String[]{"-b", "-1"};
    cli.addOption(new TypedOption<Boolean>().setType(Boolean.class).setSingleValued(false).setShortName("1"));

    CommandLine evaluated = cli.parse(Arrays.asList(args));
    assertThat(getStringOption(evaluated, "b")).isEqualTo("-1");
    assertThat(getBooleanOption(evaluated, "1")).isFalse();

    evaluated = cli.parse(Collections.singletonList("-1"));
    assertThat(getBooleanOption(evaluated, "1")).isTrue();
    assertThat(getStringOption(evaluated, "b")).isNull();
  }

  @Test
  public void testArgumentStartingWithHyphen() throws Exception {
    String[] args = new String[]{"-b", "-foo"};

    CommandLine evaluated = cli.parse(Arrays.asList(args));
    assertThat(getStringOption(evaluated, "b")).isEqualTo("-foo");
  }

  @Test
  public void testShortWithEqual() throws Exception {
    String[] args = new String[]{"-f=bar"};
    cli.addOption(new TypedOption<String>().setType(String.class).setSingleValued(true)
        .setLongName("foo").setShortName("f"));

    CommandLine evaluated = cli.parse(Arrays.asList(args));
    assertThat(getStringOption(evaluated, "foo")).isEqualTo("bar");
  }

  @Test
  public void testShortWithoutEqual() throws Exception {
    String[] args = new String[]{"-fbar"};
    cli.addOption(new TypedOption<String>().setType(String.class).setSingleValued(true)
        .setLongName("foo").setShortName("f"));
    CommandLine evaluated = cli.parse(Arrays.asList(args));
    assertThat(getStringOption(evaluated, "foo")).isEqualTo("bar");
  }

  @Test
  public void testLongWithEqualDoubleDash() throws Exception {
    String[] args = new String[]{"--foo=bar"};
    cli.addOption(new TypedOption<String>().setType(String.class).setSingleValued(true)
        .setLongName("foo").setShortName("f"));
    CommandLine evaluated = cli.parse(Arrays.asList(args));
    assertThat(getStringOption(evaluated, "foo")).isEqualTo("bar");
  }

  @Test
  public void testLongWithEqualSingleDash() throws Exception {
    String[] args = new String[]{"-foo=bar"};
    cli.addOption(new TypedOption<String>().setType(String.class).setSingleValued(true)
        .setLongName("foo").setShortName("f"));
    CommandLine evaluated = cli.parse(Arrays.asList(args));
    assertThat(getStringOption(evaluated, "foo")).isEqualTo("bar");
  }

  @Test
  public void testLongWithoutEqualSingleDash() throws Exception {
    String[] args = new String[]{"-foobar"};
    cli.addOption(new TypedOption<String>().setType(String.class).setSingleValued(true)
        .setLongName("foo").setShortName("f"));
    CommandLine evaluated = cli.parse(Arrays.asList(args));
    assertThat(getStringOption(evaluated, "foo")).isEqualTo("bar");
  }

  @Test
  public void testAmbiguousLongWithoutEqualSingleDash() throws Exception {
    String[] args = new String[]{"-b", "-foobar"};

    TypedOption<String> f =
        new TypedOption<String>().setType(String.class).setLongName("foo").setShortName("f").setSingleValued(true);
    TypedOption<Boolean> b =
        new TypedOption<Boolean>().setType(Boolean.class).setLongName("bar").setShortName("b")
            .setFlag(true);

    cli.removeOption("b").addOption(f).addOption(b);

    CommandLine evaluated = cli.parse(Arrays.asList(args));
    assertThat((boolean) evaluated.getOptionValue("bar")).isTrue();
    assertThat((String) evaluated.getOptionValue("foo")).isEqualTo("bar");
  }

  @Test
  public void testLongWithoutEqualDoubleDash() throws Exception {
    String[] args = new String[]{"--foobar"};

    TypedOption<String> f = new TypedOption<String>()
        .setType(String.class).setLongName("foo").setShortName("f").setSingleValued(true);
    cli.addOption(f);

    CommandLine evaluated = cli.parse(Arrays.asList(args));

    // foo isn't expected to be recognized with a double dash
    assertThat((String) evaluated.getOptionValue("foo")).isNull();
  }

  @Test
  public void testLongWithUnexpectedArgument1() throws Exception {
    String[] args = new String[]{"--foo=bar"};

    TypedOption<Boolean> f = new TypedOption<Boolean>().setLongName("foo").setShortName("f")
        .setType(Boolean.class)
        .setSingleValued(false) // No value accepted here.
        .setFlag(true);
    cli.addOption(f);

    try {
      CommandLine evaluated = cli.parse(Arrays.asList(args));
      fail("Exception expected");
    } catch (InvalidValueException e) {
      assertThat(e.getOption().getShortName()).isEqualToIgnoringCase("f");
      assertThat(e.getValue()).isEqualToIgnoringCase("bar");
    }
  }

  @Test
  public void testLongWithUnexpectedArgument2() throws Exception {
    String[] args = new String[]{"-foobar"};

    TypedOption<Boolean> f = new TypedOption<Boolean>().setLongName("foo").setShortName("f").setType(Boolean.class)
        .setSingleValued(false) // No value accepted here.
        ;
    cli.addOption(f);
    try {
      CommandLine evaluated = cli.parse(Arrays.asList(args));
      fail("Exception expected");
    } catch (InvalidValueException e) {
      assertThat(e.getOption().getShortName()).isEqualToIgnoringCase("f");
      assertThat(e.getValue()).isEqualToIgnoringCase("bar");
    }
  }

  @Test
  public void testShortWithUnexpectedArgument() throws Exception {
    String[] args = new String[]{"-f=bar"};

    TypedOption<Boolean> f = new TypedOption<Boolean>().setLongName("foo").setShortName("f").setType(Boolean.class)
        .setSingleValued(false) // No value accepted here.
        ;

    cli.addOption(f);
    try {
      CommandLine evaluated = cli.parse(Arrays.asList(args));
      fail("Exception expected");
    } catch (InvalidValueException e) {
      assertThat(e.getOption().getShortName()).isEqualToIgnoringCase("f");
      assertThat(e.getValue()).isEqualToIgnoringCase("bar");
    }
  }

  @Test
  public void testPropertiesOption1() throws Exception {
    String[] args = new String[]{"-Jsource=1.5", "-J", "target", "1.5", "foo"};

    TypedOption<String> f = new TypedOption<String>().setShortName("J")
        .setType(String.class)
        .setMultiValued(true);
    cli.addOption(f);

    CommandLine evaluated = cli.parse(Arrays.asList(args));

    List<String> values = evaluated.getOptionValues("J");
    assertThat(values).hasSize(4).containsExactly("source=1.5", "target", "1.5", "foo");
  }

  @Test
  public void testUnambiguousPartialLongOption1() throws Exception {
    String[] args = new String[]{"--ver"};

    TypedOption<Boolean> v = new TypedOption<Boolean>().setLongName("version").setType(Boolean.class)
        .setSingleValued(false);
    TypedOption<Boolean> h = new TypedOption<Boolean>().setLongName("help").setType(Boolean.class)
        .setSingleValued(false);
    cli.addOption(v).addOption(h);

    CommandLine evaluated = cli.parse(Arrays.asList(args));
    assertThat(getBooleanOption(evaluated, "version")).isTrue();
  }

  @Test
  public void testUnambiguousPartialLongOption2() throws Exception {
    String[] args = new String[]{"-ver"};

    TypedOption<Boolean> v = new TypedOption<Boolean>().setLongName("version").setType(Boolean.class)
        .setSingleValued(false);
    TypedOption<Boolean> h = new TypedOption<Boolean>().setLongName("help").setType(Boolean.class)
        .setSingleValued(false);
    cli.addOption(v).addOption(h);

    CommandLine evaluated = cli.parse(Arrays.asList(args));
    assertThat(getBooleanOption(evaluated, "version")).isTrue();
  }

  @Test
  public void testUnambiguousPartialLongOption3() throws Exception {
    String[] args = new String[]{"--ver=1"};

    TypedOption<Integer> v = new TypedOption<Integer>().setLongName("verbose")
        .setSingleValued(true)
        .setType(Integer.class);
    TypedOption<Boolean> h = new TypedOption<Boolean>().setLongName("help").setType(Boolean.class)
        .setSingleValued(false);

    cli.addOption(v).addOption(h);
    CommandLine evaluated = cli.parse(Arrays.asList(args));
    assertThat((int) evaluated.getOptionValue("verbose")).isEqualTo(1);
  }

  @Test
  public void testUnambiguousPartialLongOption4() throws Exception {
    String[] args = new String[]{"-ver=1"};

    TypedOption<Integer> v = new TypedOption<Integer>().setLongName("verbose")
        .setSingleValued(true)
        .setType(Integer.class);
    TypedOption<Boolean> h = new TypedOption<Boolean>().setLongName("help")
        .setType(Boolean.class)
        .setSingleValued(false);

    cli.addOption(v).addOption(h);
    CommandLine evaluated = cli.parse(Arrays.asList(args));
    assertThat((int) evaluated.getOptionValue("verbose")).isEqualTo(1);
  }

  @Test
  public void testAmbiguousPartialLongOption1() throws Exception {
    String[] args = new String[]{"--ver"};

    TypedOption<Integer> v1 = new TypedOption<Integer>().setLongName("verbose")
        .setSingleValued(true)
        .setType(Integer.class);
    TypedOption<Boolean> v2 = new TypedOption<Boolean>().setLongName("version")
        .setType(Boolean.class)
        .setSingleValued(false);

    cli.addOption(v1).addOption(v2);
    try {
      cli.parse(Arrays.asList(args));
      fail("Exception expected");
    } catch (AmbiguousOptionException e) {
      assertThat(e.getToken()).isEqualTo("--ver");
      assertThat(e.getOptions()).hasSize(2);
    }
  }

  @Test
  public void testAmbiguousPartialLongOption2() throws Exception {
    String[] args = new String[]{"-ver"};

    TypedOption<Integer> v1 = new TypedOption<Integer>().setLongName("verbose")
        .setSingleValued(true)
        .setType(Integer.class);
    TypedOption<Boolean> v2 = new TypedOption<Boolean>().setLongName("version").setType(Boolean.class)
        .setSingleValued(false);

    cli.addOption(v1).addOption(v2);
    try {
      cli.parse(Arrays.asList(args));
      fail("Exception expected");
    } catch (AmbiguousOptionException e) {
      assertThat(e.getToken()).isEqualTo("-ver");
      assertThat(e.getOptions()).hasSize(2);
    }
  }

  @Test
  public void testAmbiguousPartialLongOption3() throws Exception {
    String[] args = new String[]{"--ver=1"};

    TypedOption<Integer> v1 = new TypedOption<Integer>().setLongName("verbose")
        .setSingleValued(true)
        .setType(Integer.class);
    TypedOption<Boolean> v2 = new TypedOption<Boolean>().setLongName("version").setType(Boolean.class)
        .setSingleValued(false);

    cli.addOption(v1).addOption(v2);
    try {
      cli.parse(Arrays.asList(args));
      fail("Exception expected");
    } catch (AmbiguousOptionException e) {
      assertThat(e.getToken()).isEqualTo("--ver");
      assertThat(e.getOptions()).hasSize(2);
    }
  }

  @Test
  public void testAmbiguousPartialLongOption4() throws Exception {
    String[] args = new String[]{"-ver=1"};

    TypedOption<Integer> v1 = new TypedOption<Integer>().setLongName("verbose")
        .setSingleValued(true)
        .setType(Integer.class);
    TypedOption<Boolean> v2 = new TypedOption<Boolean>().setLongName("version").setType(Boolean.class)
        .setSingleValued(false);

    cli.addOption(v1).addOption(v2);
    try {
      cli.parse(Arrays.asList(args));
      fail("Exception expected");
    } catch (AmbiguousOptionException e) {
      assertThat(e.getToken()).isEqualTo("-ver");
      assertThat(e.getOptions()).hasSize(2);
    }
  }

  @Test
  public void testPartialLongOptionSingleDash() throws Exception {
    String[] args = new String[]{"-ver"};

    TypedOption<Boolean> v2 = new TypedOption<Boolean>().setLongName("version").setType(Boolean.class)
        .setSingleValued(false);
    TypedOption<Integer> v1 = new TypedOption<Integer>().setSingleValued(true)
        .setShortName("v")
        .setType(Integer.class);

    cli.addOption(v1).addOption(v2);
    CommandLine evaluated = cli.parse(Arrays.asList(args));
    assertThat((Object) evaluated.getOptionValue("v")).isNull();
    assertThat((boolean) evaluated.getOptionValue("version")).isTrue();
  }

  @Test
  public void testWithRequiredOption() throws Exception {
    String[] args = new String[]{"-b", "file"};


    TypedOption<String> b = new TypedOption<String>().setShortName("b").setLongName("bfile").setSingleValued(true)
        .setDescription("set the value of [b]").setType(String.class).setRequired(true);
    cli.removeOption("b").addOption(b);

    CommandLine evaluated = cli.parse(Arrays.asList(args));

    assertThat(getBooleanOption(evaluated, "a")).isFalse();
    assertThat((String) evaluated.getOptionValue("b")).isEqualTo("file");
    assertThat(evaluated.allArguments()).isEmpty();
  }

  @Test
  public void testOptionAndRequiredOption() throws Exception {
    String[] args = new String[]{"-a", "-b", "file"};

    TypedOption<String> b = new TypedOption<String>().setShortName("b").setLongName("bfile").setSingleValued(true)
        .setDescription("set the value of [b]").setType(String.class).setRequired(true);
    cli.removeOption("b").addOption(b);

    CommandLine evaluated = cli.parse(Arrays.asList(args));

    assertThat(getBooleanOption(evaluated, "a")).isTrue();
    assertThat(getStringOption(evaluated, "b")).isEqualTo("file");
    assertThat(evaluated.allArguments()).isEmpty();
  }

  @Test
  public void testMissingRequiredOption() throws Exception {
    String[] args = new String[]{"-a"};

    TypedOption<String> b = new TypedOption<String>().setShortName("b").setLongName("bfile")
        .setSingleValued(true)
        .setDescription("set the value of [b]").setType(String.class).setRequired(true);
    cli.removeOption("b").addOption(b);


    try {
      cli.parse(Arrays.asList(args));
      fail("exception expected");
    } catch (MissingOptionException e) {
      assertThat(e.getExpected()).hasSize(1);
    }
  }

  @Test
  public void testMissingRequiredOptions() throws CLIException {
    String[] args = new String[]{"-a"};

    TypedOption<String> b = new TypedOption<String>().setShortName("b").setLongName("bfile").setSingleValued(true)
        .setDescription("set the value of [b]").setType(String.class).setRequired(true);
    TypedOption<Boolean> c = new TypedOption<Boolean>().setShortName("c").setLongName("copt").setSingleValued(false)
        .setDescription("turn [c] on or off").setType(Boolean.class).setRequired(true);
    cli.removeOption("b").addOption(b).removeOption("c").addOption(c);

    try {
      CommandLine evaluated = cli.parse(Arrays.asList(args));
      fail("exception expected");
    } catch (MissingOptionException e) {
      assertThat(e.getExpected()).hasSize(2);
    }
  }

  @Test
  public void testBursting() throws Exception {
    String[] args = new String[]{"-acbtoast", "foo", "bar"};

    CommandLine evaluated = cli.parse(Arrays.asList(args));

    assertThat(getBooleanOption(evaluated, "a")).isTrue();
    assertThat(getBooleanOption(evaluated, "c")).isTrue();
    assertThat(getStringOption(evaluated, "b")).isEqualTo("toast");
    assertThat(evaluated.allArguments()).hasSize(2).contains("foo", "bar");
  }

  @Test
  public void testUnrecognizedOptionWithBursting() throws Exception {
    String[] args = new String[]{"-adbtoast", "foo", "bar"};

    CommandLine evaluated = cli.parse(Arrays.asList(args));
    assertThat(evaluated.allArguments()).contains("-adbtoast", "foo", "bar").hasSize(3);
  }

  @Test
  public void testMissingArgWithBursting() throws Exception {
    String[] args = new String[]{"-acb"};

    try {
      CommandLine evaluated = cli.parse(Arrays.asList(args));
      fail("exception expected");
    } catch (MissingValueException e) {
      assertThat(e.getOption().getShortName()).isEqualTo("b");
    }
  }

  @Test
  public void testMultiValues() throws Exception {
    String[] args = new String[]{"-e", "one", "two", "-f", "1"};

    TypedOption<String> e = new TypedOption<String>().setShortName("e")
        .setMultiValued(true).setType(String.class);
    TypedOption<Integer> f = new TypedOption<Integer>().setShortName("f")
        .setMultiValued(true).setType(Integer.class);

    cli.addOption(e).addOption(f);
    CommandLine evaluated = cli.parse(Arrays.asList(args));

    assertThat(evaluated.getOptionValues("e")).contains("one", "two").hasSize(2);
    assertThat(evaluated.getOptionValues("f")).contains(1).hasSize(1);
  }

}
