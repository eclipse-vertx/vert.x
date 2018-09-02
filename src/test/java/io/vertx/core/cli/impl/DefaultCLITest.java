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
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the {@link DefaultCLI}.
 *
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class DefaultCLITest {


  @Test
  public void testFlag() {
    final CLI cli = CLI.create("test")
        .addOption(new Option().setShortName("f").setFlag(true))
        .addOption(new Option().setShortName("x"));
    final CommandLine evaluated = cli.parse(Arrays.asList("-f", "-x", "foo"));
    assertThat(evaluated.isFlagEnabled("f")).isTrue();
    assertThat((String) evaluated.getOptionValue("x")).isEqualToIgnoringCase("foo");
  }

  @Test
  public void testMissingFlag() {
    final CLI cli = CLI.create("test")
        .addOption(new Option().setShortName("f").setFlag(true))
        .addOption(new Option().setShortName("x"));
    final CommandLine evaluated = cli.parse(Arrays.asList("-x", "foo"));
    assertThat(evaluated.isFlagEnabled("f")).isFalse();
    assertThat((String) evaluated.getOptionValue("x")).isEqualToIgnoringCase("foo");
  }

  @Test
  public void testUsageComputationWhenUsingOnlyFlagShortOption() {
    final CLI cli = CLI.create("test")
        .addOption(new Option().setShortName("f").setDescription("turn on/off").setFlag(true));

    StringBuilder builder = new StringBuilder();
    cli.usage(builder);
    assertThat(builder.toString())
        .contains("test [-f]")
        .contains("Options and Arguments")
        .contains(" -f   turn on/off");
  }

  @Test
  public void testUsageWhenNoArgsAndOptions() {
    final CLI cli = CLI.create("test").setDescription("A simple test command.");

    StringBuilder builder = new StringBuilder();
    cli.usage(builder);
    assertThat(builder.toString())
        .contains("test")
        .doesNotContain("Options").doesNotContain("Arguments");
  }

  @Test
  public void testUsageComputationWhenUsingOnlyFlagLongOption() {
    final CLI cli = CLI.create("test")
        .addOption(new Option().setLongName("flag").setDescription("turn on/off").setFlag(true));

    StringBuilder builder = new StringBuilder();
    cli.usage(builder);

    assertThat(builder.toString())
        .contains("test [--flag]")
        .contains(" --flag   turn on/off");
  }

  @Test
  public void testUsageComputationWhenUsingShortAndLongFlagOption() {
    final CLI cli = CLI.create("test")
        .addOption(new Option().setLongName("flag").setShortName("f").setDescription("turn on/off").setFlag(true));

    StringBuilder builder = new StringBuilder();
    cli.usage(builder);

    assertThat(builder.toString())
        .contains("test [-f]")
        .contains(" -f,--flag   turn on/off");
  }

  @Test
  public void testUsageComputationWhenUsingShortAndLongOption() {
    final CLI cli = CLI.create("test")
        .addOption(new Option().setLongName("file").setShortName("f").setDescription("a file"));

    StringBuilder builder = new StringBuilder();
    cli.usage(builder);

    assertThat(builder.toString())
        .contains("test [-f <value>]")
        .contains(" -f,--file <value>   a file");
  }

  @Test
  public void testUsageComputationWhenUsingOnlyShortOption() {
    final CLI cli = CLI.create("test")
        .addOption(new Option().setShortName("f").setDescription("a file"));

    StringBuilder builder = new StringBuilder();
    cli.usage(builder);

    assertThat(builder.toString())
        .contains("test [-f <value>]")
        .contains(" -f <value>   a file");
  }

  @Test
  public void testUsageComputationWhenUsingOnlyLongOption() {
    final CLI cli = CLI.create("test")
        .addOption(new Option().setLongName("file").setDescription("a file"));

    StringBuilder builder = new StringBuilder();
    cli.usage(builder);

    assertThat(builder.toString())
        .contains("test [--file <value>]")
        .contains(" --file <value>   a file");
  }

  @Test
  public void testUsageComputationWhenUsingRequiredOptionAndArgument() {
    final CLI cli = CLI.create("test")
        .addOption(new Option().setLongName("file").setShortName("f").setDescription("a file").setRequired(true))
        .addArgument(new Argument().setArgName("foo").setDescription("foo").setRequired(true));

    StringBuilder builder = new StringBuilder();
    cli.usage(builder);

    assertThat(builder.toString())
        .contains("test -f <value> foo")
        .contains(" -f,--file <value>   a file")
    .contains("<foo>               foo");
  }

  @Test
  public void testUsageComputationWithSeveralArguments() {
    final CLI cli = CLI.create("test")
        .addOption(new Option().setLongName("file").setShortName("f").setDescription("a file").setRequired(true))
        .addArgument(new Argument().setIndex(0).setArgName("foo").setDescription("foo"))
        .addArgument(new Argument().setIndex(1))
        .addArgument(new Argument().setIndex(2).setArgName("bar").setDescription("bar"));

    StringBuilder builder = new StringBuilder();
    cli.usage(builder);

    assertThat(builder.toString())
        .contains("test -f <value> foo value bar")
        .contains(" -f,--file <value>   a file")
        .contains("<foo>               foo")
        .contains("<value>")
        .contains("<bar>               bar");
  }

  @Test
  public void testUsageComputationWithHiddenArguments() {
    final CLI cli = CLI.create("test")
        .addOption(new Option().setLongName("file").setShortName("f").setDescription("a file").setRequired(true))
        .addArgument(new Argument().setIndex(0).setArgName("foo").setDescription("foo"))
        .addArgument(new Argument().setIndex(1))
        .addArgument(new Argument().setIndex(2).setArgName("bar").setDescription("bar").setHidden(true));

    StringBuilder builder = new StringBuilder();
    cli.usage(builder);

    assertThat(builder.toString())
        .contains("test -f <value> foo value")
        .contains(" -f,--file <value>   a file")
        .contains("<foo>               foo")
        .contains("<value>")
        .doesNotContain("bar");
  }

  @Test
  public void testUsageComputationWhenUsingNotRequiredArgument() {
    final CLI cli = CLI.create("test")
        .addArgument(new Argument().setArgName("foo").setRequired(false));

    StringBuilder builder = new StringBuilder();
    cli.usage(builder);

    assertThat(builder.toString())
        .contains("test [foo]");
  }

  @Test
  public void testCommandLineValidationWhenValid() {
    final CLI cli = CLI.create("test")
        .addArgument(new Argument().setArgName("foo").setRequired(true));

    CommandLine commandLine = cli.parse(Collections.singletonList("foo"));
    assertThat(commandLine.isValid()).isTrue();
  }

  @Test(expected = MissingValueException.class)
  public void testCommandLineValidationWhenInvalid() {
    final CLI cli = CLI.create("test")
        .addArgument(new Argument().setArgName("foo").setRequired(true));

    cli.parse(Collections.<String>emptyList());
  }

  @Test
  public void testCommandLineValidationWhenInvalidWithValidationDisabled() {
    final CLI cli = CLI.create("test")
        .addArgument(new Argument().setArgName("foo").setRequired(true));

    CommandLine commandLine = cli.parse(Collections.<String>emptyList(), false);
    assertThat(commandLine.isValid()).isEqualTo(false);
  }

}
