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

package io.vertx.core.cli.impl;

import io.vertx.core.cli.Argument;
import io.vertx.core.cli.CLI;
import io.vertx.core.cli.CommandLine;
import io.vertx.core.cli.Option;
import org.junit.Test;

import java.util.Arrays;

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
    assertThat(builder)
        .contains("test [-f]")
        .contains(" -f   turn on/off");
  }

  @Test
  public void testUsageComputationWhenUsingOnlyFlagLongOption() {
    final CLI cli = CLI.create("test")
        .addOption(new Option().setLongName("flag").setDescription("turn on/off").setFlag(true));

    StringBuilder builder = new StringBuilder();
    cli.usage(builder);

    assertThat(builder)
        .contains("test [--flag]")
        .contains(" --flag   turn on/off");
  }

  @Test
  public void testUsageComputationWhenUsingShortAndLongFlagOption() {
    final CLI cli = CLI.create("test")
        .addOption(new Option().setLongName("flag").setShortName("f").setDescription("turn on/off").setFlag(true));

    StringBuilder builder = new StringBuilder();
    cli.usage(builder);

    assertThat(builder)
        .contains("test [-f]")
        .contains(" -f,--flag   turn on/off");
  }

  @Test
  public void testUsageComputationWhenUsingShortAndLongOption() {
    final CLI cli = CLI.create("test")
        .addOption(new Option().setLongName("file").setShortName("f").setDescription("a file"));

    StringBuilder builder = new StringBuilder();
    cli.usage(builder);

    assertThat(builder)
        .contains("test [-f <value>]")
        .contains(" -f,--file <value>   a file");
  }

  @Test
  public void testUsageComputationWhenUsingOnlyShortOption() {
    final CLI cli = CLI.create("test")
        .addOption(new Option().setShortName("f").setDescription("a file"));

    StringBuilder builder = new StringBuilder();
    cli.usage(builder);

    assertThat(builder)
        .contains("test [-f <value>]")
        .contains(" -f <value>   a file");
  }

  @Test
  public void testUsageComputationWhenUsingOnlyLongOption() {
    final CLI cli = CLI.create("test")
        .addOption(new Option().setLongName("file").setDescription("a file"));

    StringBuilder builder = new StringBuilder();
    cli.usage(builder);

    assertThat(builder)
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

    assertThat(builder)
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

    assertThat(builder)
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

    assertThat(builder)
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

    assertThat(builder)
        .contains("test [foo]");
  }

}