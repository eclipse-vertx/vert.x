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
import io.vertx.core.cli.Argument;
import io.vertx.core.cli.Option;
import io.vertx.core.cli.annotations.*;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

/**
 * Test the default parser.
 */
public class DefaultParserTest {

  private CLI cli;

  @Before
  public void setUp() {
    cli = new DefaultCLI().setName("test");
  }

  @Test
  public void testWithOneLongOption() throws CLIException {
    Option[] options = new Option[]{
        new Option().setShortName("f").setLongName("file").setSingleValued(true)
    };

    cli.addOptions(Arrays.asList(options));
    CommandLine evaluated = cli.parse(Collections.singletonList("--file=hello.txt"));
    assertThat(evaluated.cli().getOptions()).hasSize(1);
    assertThat((String) evaluated.getOptionValue("file")).isEqualTo("hello.txt");
    assertThat(evaluated.getOptionValues("f")).containsExactly("hello.txt");

    StringBuilder usage = new StringBuilder();
    cli.usage(usage);
    assertThat(usage.toString()).startsWith("Usage: test [-f <value>]");
    assertThat(usage.toString()).contains("-f,--file <value>");
  }


  @Test
  public void testWithOneLongOptionUsingSpace() throws CLIException {
    Option[] options = new Option[]{
        new Option().setShortName("f").setLongName("file")
            .setSingleValued(true)
    };

    cli.addOptions(Arrays.asList(options));
    CommandLine evaluated = cli.parse(Arrays.asList("--file", "hello.txt"));
    assertThat(evaluated.cli().getOptions()).hasSize(1);
    assertThat((String) evaluated.getOptionValue("file")).isEqualTo("hello.txt");
    assertThat(evaluated.getOptionValues("f")).containsExactly("hello.txt");

    StringBuilder usage = new StringBuilder();
    cli.usage(usage);
    assertThat(usage.toString()).startsWith("Usage: test [-f <value>]");
  }

  @Test
  public void testWithOneShortOption() throws CLIException {
    Option[] options = new Option[]{
        new Option().setShortName("f").setLongName("file").setSingleValued(true)
    };

    cli.addOptions(Arrays.asList(options));
    CommandLine evaluated = cli.parse(Collections.singletonList("-f=hello.txt"));
    assertThat(evaluated.cli().getOptions()).hasSize(1);
    assertThat((String) evaluated.getOptionValue("file")).isEqualTo("hello.txt");
    assertThat(evaluated.getOptionValues("f")).containsExactly("hello.txt");
  }

  @Test
  public void testWithOneShortOptionUsingSpace() throws CLIException {
    Option[] options = new Option[]{
        new Option().setShortName("f").setLongName("file").setSingleValued(true)
    };

    cli.addOptions(Arrays.asList(options));
    CommandLine evaluated = cli.parse(Arrays.asList("-f", "hello.txt"));
    assertThat(evaluated.cli().getOptions()).hasSize(1);
    assertThat((String) evaluated.getOptionValue("file")).isEqualTo("hello.txt");
    assertThat(evaluated.getOptionValues("f")).containsExactly("hello.txt");
  }

  @Test
  public void testTheDifferentFormatForLongOption() throws CLIException {

    Option[] options = new Option[]{
        new Option().setShortName("f").setLongName("file").setSingleValued(true)
    };
    cli.addOptions(Arrays.asList(options));

    CommandLine evaluated = cli.parse(Arrays.asList("--file", "hello.txt"));
    assertThat((String) evaluated.getOptionValue("file")).isEqualTo("hello.txt");

    evaluated = cli.parse(Collections.singletonList("--file=hello.txt"));
    assertThat((String) evaluated.getOptionValue("file")).isEqualTo("hello.txt");

    evaluated = cli.parse(Collections.singletonList("-filehello.txt"));
    assertThat((String) evaluated.getOptionValue("file")).isEqualTo("hello.txt");

    evaluated = cli.parse(Arrays.asList("--FILE", "hello.txt"));
    assertThat((String) evaluated.getOptionValue("file")).isEqualTo("hello.txt");
  }

  @Test
  public void testTheDifferentFormatForShortOption() throws CLIException {

    Option[] options = new Option[]{
        new Option().setShortName("f").setLongName("file").setSingleValued(true)
    };
    cli.addOptions(Arrays.asList(options));

    CommandLine evaluated = cli.parse(Arrays.asList("-f", "hello.txt"));
    assertThat((String) evaluated.getOptionValue("file")).isEqualTo("hello.txt");

    evaluated = cli.parse(Collections.singletonList("-f=hello.txt"));
    assertThat((String) evaluated.getOptionValue("file")).isEqualTo("hello.txt");

    evaluated = cli.parse(Collections.singletonList("-fhello.txt"));
    assertThat((String) evaluated.getOptionValue("file")).isEqualTo("hello.txt");
  }

  @Test
  public void testWithMultipleValues() throws CLIException {

    Option[] options = new Option[]{
        new Option().setShortName("f").setLongName("file")
            .setMultiValued(true)
    };
    cli.addOptions(Arrays.asList(options));

    CommandLine evaluated = cli.parse(Arrays.asList("-f=hello.txt", "--file=hello2.txt"));
    assertThat(evaluated.cli().getOptions()).hasSize(1);
    assertThat((String) evaluated.getOptionValue("file")).isEqualTo("hello.txt");
    assertThat(evaluated.getOptionValues("f"))
        .containsExactly("hello.txt", "hello2.txt");
  }

  @Test
  public void testWithList() throws CLIException {
    CLI cli = new DefaultCLI().setName("test");
    Option[] options = new Option[]{
        new TypedOption<String>().setShortName("f").setLongName("file")
            .setParsedAsList(true).setType(String.class)
    };

    cli.addOptions(Arrays.asList(options));
    CommandLine evaluated = cli.parse(Collections.singletonList("-f=hello.txt,hello2.txt"));
    assertThat(evaluated.cli().getOptions()).hasSize(1);
    assertThat((String) evaluated.getOptionValue("file")).isEqualTo("hello.txt,hello2.txt");
    assertThat(evaluated.getOptionValues("f"))
        .containsExactly("hello.txt", "hello2.txt");
  }

  @Test
  public void testWithFlag() throws CLIException {
    CLI cli = new DefaultCLI().setName("test");
    Option[] options = new Option[]{
        new TypedOption<Boolean>().setType(Boolean.TYPE)
            .setShortName("f").setLongName("flag")
            .setFlag(true).setSingleValued(true),
        new TypedOption<Boolean>().setType(Boolean.TYPE)
            .setShortName("f2").setLongName("flag2")
            .setFlag(true).setSingleValued(true),
        new TypedOption<Boolean>().setType(Boolean.TYPE)
            .setShortName("f3").setLongName("flag3")
            .setFlag(true)
    };

    cli.addOptions(Arrays.asList(options));
    CommandLine evaluated = cli.parse(Arrays.asList("--flag", "--flag2", "--flag3"));
    assertThat((boolean) evaluated.getOptionValue("flag")).isEqualTo(true);
    assertThat((boolean) evaluated.getOptionValue("flag2")).isEqualTo(true);
    assertThat((boolean) evaluated.getOptionValue("flag3")).isEqualTo(true);

    evaluated = cli.parse(Arrays.asList("--flag=true", "--flag2=false", "--flag3"));
    assertThat((boolean) evaluated.getOptionValue("flag")).isEqualTo(true);
    assertThat((boolean) evaluated.getOptionValue("flag2")).isEqualTo(false);
    assertThat((boolean) evaluated.getOptionValue("flag3")).isEqualTo(true);

    evaluated = cli.parse(Arrays.asList("--flag", "--flag2"));
    assertThat((boolean) evaluated.getOptionValue("flag")).isEqualTo(true);
    assertThat((boolean) evaluated.getOptionValue("flag2")).isEqualTo(true);
    assertThat((boolean) evaluated.getOptionValue("flag3")).isEqualTo(false);

    evaluated = cli.parse(Arrays.asList("--flag", "true", "--flag2", "false", "--flag3"));
    assertThat((boolean) evaluated.getOptionValue("flag")).isEqualTo(true);
    assertThat((boolean) evaluated.getOptionValue("flag2")).isEqualTo(false);
    assertThat((boolean) evaluated.getOptionValue("flag3")).isEqualTo(true);
  }

  @Test
  public void testArguments() throws CLIException {
    Option[] options = new Option[]{
        new TypedOption<Boolean>().setShortName("f").setLongName("flag").setType(Boolean.class).setSingleValued(true)
    };

    cli.addOptions(Arrays.asList(options));
    CommandLine evaluated = cli.parse(Arrays.asList("org.acme.Foo", "-f=no"));
    assertThat(evaluated.allArguments()).contains("org.acme.Foo");

    evaluated = cli.parse(Arrays.asList("-f=no", "org.acme.Foo"));
    assertThat(evaluated.allArguments()).contains("org.acme.Foo");

    evaluated = cli.parse(Arrays.asList("-f=no", "org.acme.Foo", "bar"));
    assertThat(evaluated.allArguments()).contains("org.acme.Foo", "bar");
  }

  @Test
  public void testUnknownOption() throws CLIException {
    Option[] options = new Option[]{
        new TypedOption<Boolean>().setShortName("f").setLongName("flag")
            .setType(Boolean.class).setRequired(true).setSingleValued(true)
    };

    cli.addOptions(Arrays.asList(options));
    CommandLine evaluated = cli.parse(Arrays.asList("-flag=true", "-unknown=x"));
    assertThat(evaluated.allArguments()).contains("-unknown=x");
  }

  @Test(expected = MissingOptionException.class)
  public void testNotFulfilledRequiredOptions() throws CLIException {
    Option[] options = new Option[]{
        new TypedOption<Boolean>().setShortName("f").setLongName("flag").setType(Boolean.class).setRequired(true).setSingleValued(true)
    };
    cli.addOptions(Arrays.asList(options));
    CommandLine evaluated = cli.parse(Collections.emptyList());
  }

  @Test
  public void testRequiredOptions() throws CLIException {
    Option[] options = new Option[]{
        new TypedOption<Boolean>().setShortName("f").setLongName("flag")
            .setType(Boolean.class).setRequired(true).setFlag(true)
    };

    cli.addOptions(Arrays.asList(options));
    cli.parse(Collections.singletonList("-f"));
  }

  @Test
  public void testQuotedValues() throws CLIException {
    Option[] options = new Option[]{
        new Option().setShortName("f").setLongName("file").setSingleValued(true)
    };

    cli.addOptions(Arrays.asList(options));
    CommandLine evaluated = cli.parse(Arrays.asList("--file", "\"hello.txt\""));
    assertThat(evaluated.cli().getOptions()).hasSize(1);
    assertThat((String) evaluated.getOptionValue("file")).isEqualTo("hello.txt");
    assertThat(evaluated.getOptionValues("f")).containsExactly("hello.txt");
  }

  @Test
  public void testNegativeNumbers() throws CLIException {
    CLI cli = new DefaultCLI().setName("test");
    Option[] options = new Option[]{
        new TypedOption<Double>().setLongName("num").setSingleValued(true)
            .setType(Double.class)
    };

    cli.addOptions(Arrays.asList(options));
    CommandLine evaluated = cli.parse(Arrays.asList("--num", "-1.5"));
    assertThat(evaluated.cli().getOptions()).hasSize(1);
    assertThat(cli.getArguments()).isEmpty();
    assertThat((double) evaluated.getOptionValue("num")).isEqualTo(-1.5d);

    evaluated = cli.parse(Collections.singletonList("--num=-1.5"));
    assertThat(evaluated.cli().getOptions()).hasSize(1);
    assertThat(cli.getArguments()).isEmpty();
    assertThat((double) evaluated.getOptionValue("num")).isEqualTo(-1.5d);
  }

  @Test(expected = MissingValueException.class)
  public void testMissingValue() throws CLIException {
    Option[] options = new Option[]{
        new Option().setShortName("f").setLongName("file").setSingleValued(true)
    };

    cli.addOptions(Arrays.asList(options));
    cli.parse(Collections.singletonList("--file"));
  }

  @Test
  public void testVertxRun() throws CLIException {
    CLI cli = new DefaultCLI().setName("test");
    Option[] options = new Option[]{
        new TypedOption<String>().setLongName("conf").setType(String.class)
            .setSingleValued(true),
        new TypedOption<Integer>().setLongName("instances").setType(Integer.class)
            .setSingleValued(true).setDefaultValue("1"),
        new TypedOption<Boolean>().setLongName("worker").setType(Boolean.class)
            .setFlag(true),
        new TypedOption<String>().setLongName("classpath").setShortName("cp")
            .setListSeparator(File.pathSeparator)
            .setType(String.class).setSingleValued(true),
        new TypedOption<Boolean>().setLongName("cluster").setType(Boolean.class)
            .setFlag(true),
        new TypedOption<Integer>().setLongName("cluster-port").setType(Integer.class)
            .setSingleValued(true),
        new TypedOption<String>().setLongName("cluster-host").setType(String.class)
            .setSingleValued(true),
        new TypedOption<Boolean>().setLongName("ha").setType(Boolean.class)
            .setFlag(true).setSingleValued(true),
        new TypedOption<Integer>().setLongName("quorum").setType(Integer.class)
            .setSingleValued(true),
        new TypedOption<String>().setLongName("ha-group").setType(String.class)
            .setDefaultValue("__DEFAULT__").setSingleValued(true)
    };
    cli.addOptions(Arrays.asList(options));
    cli.addArgument(new TypedArgument<String>().setType(String.class)
        .setArgName("verticle").setIndex(0).setRequired(false));

    // Bare
    CommandLine evaluated = cli.parse(Collections.singletonList("-ha"));
    assertThat((boolean) evaluated.getOptionValue("ha")).isTrue();
    assertThat((String) evaluated.getArgumentValue("verticle")).isNull();
    assertThat((String) evaluated.getArgumentValue(0)).isNull();
    evaluated = cli.parse(Arrays.asList("-ha", "true"));
    assertThat((boolean) evaluated.getOptionValue("ha")).isTrue();
    evaluated = cli.parse(Collections.singletonList("-ha=true"));
    assertThat((boolean) evaluated.getOptionValue("ha")).isTrue();
    evaluated = cli.parse(Collections.singletonList("--ha"));
    assertThat((boolean) evaluated.getOptionValue("ha")).isTrue();
    evaluated = cli.parse(Arrays.asList("--ha", "false"));
    assertThat((boolean) evaluated.getOptionValue("ha")).isFalse();
    evaluated = cli.parse(Collections.singletonList("--ha=no"));
    assertThat((boolean) evaluated.getOptionValue("ha")).isFalse();


    // Verticle deployment
    evaluated = cli.parse(Arrays.asList("org.acme.FooVerticle",
        "-instances=4",
        "-cp", "." + File.pathSeparator + "my.jar"));
    assertThat(evaluated.allArguments()).hasSize(1).containsExactly("org.acme.FooVerticle");
    assertThat((String) evaluated.getArgumentValue("verticle")).isEqualTo("org.acme.FooVerticle");

    int instances = evaluated.getOptionValue("instances");
    List<String> classpath = evaluated.getOptionValues("classpath");
    assertThat(instances).isEqualTo(4);
    assertThat(classpath).containsExactly(".", "my.jar");


    // Cluster environment
    evaluated = cli.parse(Arrays.asList("org.acme.FooVerticle", "-cluster"));
    assertThat(evaluated.allArguments()).hasSize(1).containsExactly("org.acme.FooVerticle");
    assertThat((String) evaluated.getArgumentValue("verticle")).isEqualTo("org.acme.FooVerticle");
    assertThat((String) evaluated.getArgumentValue(0)).isEqualTo("org.acme.FooVerticle");
    assertThat((boolean) evaluated.getOptionValue("cluster")).isTrue();

    evaluated = cli.parse(Arrays.asList("org.acme.FooVerticle", "--cluster"));
    assertThat(evaluated.allArguments()).hasSize(1).containsExactly("org.acme.FooVerticle");
    assertThat((String) evaluated.getArgumentValue("verticle")).isEqualTo("org.acme.FooVerticle");
    assertThat((String) evaluated.getArgumentValue(0)).isEqualTo("org.acme.FooVerticle");
    assertThat((boolean) evaluated.getOptionValue("cluster")).isTrue();

    evaluated = cli.parse(Arrays.asList("org.acme.FooVerticle", "-cluster", "-cluster-host", "127.0.0.1"));
    assertThat(evaluated.allArguments()).hasSize(1).containsExactly("org.acme.FooVerticle");
    assertThat((String) evaluated.getArgumentValue("verticle")).isEqualTo("org.acme.FooVerticle");
    assertThat((String) evaluated.getArgumentValue(0)).isEqualTo("org.acme.FooVerticle");
    assertThat((boolean) evaluated.getOptionValue("cluster")).isTrue();
    assertThat((String) evaluated.getOptionValue("cluster-host"))
        .isEqualTo("127.0.0.1");

    evaluated = cli.parse(Arrays.asList("org.acme.FooVerticle", "-cluster", "--cluster-host", "127.0.0.1"));
    assertThat(evaluated.allArguments()).hasSize(1).containsExactly("org.acme.FooVerticle");
    assertThat((String) evaluated.getArgumentValue("verticle")).isEqualTo("org.acme.FooVerticle");
    assertThat((String) evaluated.getArgumentValue(0)).isEqualTo("org.acme.FooVerticle");
    assertThat((boolean) evaluated.getOptionValue("cluster")).isTrue();
    assertThat((String) evaluated.getOptionValue("cluster-host"))
        .isEqualTo("127.0.0.1");

    evaluated = cli.parse(Arrays.asList("org.acme.FooVerticle", "-cluster", "-cluster-host=127.0.0.1"));
    assertThat(evaluated.allArguments()).hasSize(1).containsExactly("org.acme.FooVerticle");
    assertThat((String) evaluated.getArgumentValue("verticle")).isEqualTo("org.acme.FooVerticle");
    assertThat((String) evaluated.getArgumentValue(0)).isEqualTo("org.acme.FooVerticle");
    assertThat((boolean) evaluated.getOptionValue("cluster")).isTrue();
    assertThat((String) evaluated.getOptionValue("cluster-host"))
        .isEqualTo("127.0.0.1");

    evaluated = cli.parse(Arrays.asList("org.acme.FooVerticle", "-cluster", "-cluster-host", "127.0.0.1",
        "-cluster-port", "1234"));
    assertThat(evaluated.allArguments()).hasSize(1).containsExactly("org.acme.FooVerticle");
    assertThat((String) evaluated.getArgumentValue("verticle")).isEqualTo("org.acme.FooVerticle");
    assertThat((String) evaluated.getArgumentValue(0)).isEqualTo("org.acme.FooVerticle");
    assertThat((boolean) evaluated.getOptionValue("cluster")).isTrue();
    assertThat((String) evaluated.getOptionValue("cluster-host"))
        .isEqualTo("127.0.0.1");
    assertThat((int) evaluated.getOptionValue("cluster-port")).isEqualTo(1234);

  }

  @Test
  public void testWithDashD() throws CLIException {
    CLI cli = new DefaultCLI().setName("test");
    Option[] options = new Option[]{
        new TypedOption<String>().setShortName("D").setLongName("systemProperty")
            .setMultiValued(true).setType(String.class),
        new TypedOption<Boolean>().setShortName("F").setLongName("flag")
            .setFlag(true)
            .setType(Boolean.class)
    };

    cli.addOptions(Arrays.asList(options));
    CommandLine evaluated = cli.parse(Arrays.asList("-Dx=y", "-F"));
    assertThat(evaluated.cli().getOptions()).hasSize(2);
    assertThat(evaluated.getRawValueForOption(evaluated.cli().getOption("systemProperty")))
        .isEqualTo("x=y");
    assertThat((boolean) evaluated.getOptionValue("flag")).isTrue();
  }

  @Test
  public void testConcatenatedOptions() throws CLIException {
    CLI cli = new DefaultCLI().setName("test");
    Option[] options = new Option[]{
        new TypedOption<Boolean>().setShortName("d").setFlag(true)
            .setType(Boolean.class),
        new TypedOption<Boolean>().setShortName("e").setFlag(true)
            .setType(Boolean.class),
        new TypedOption<Boolean>().setShortName("f").setFlag(true)
            .setType(Boolean.class)
    };

    cli.addOptions(Arrays.asList(options));
    CommandLine evaluated = cli.parse(Arrays.asList("-d", "-e", "-f"));
    assertThat((boolean) evaluated.getOptionValue("d")).isTrue();
    assertThat((boolean) evaluated.getOptionValue("e")).isTrue();
    assertThat((boolean) evaluated.getOptionValue("f")).isTrue();

    evaluated = cli.parse(Collections.singletonList("-de"));
    assertThat((boolean) evaluated.getOptionValue("d")).isTrue();
    assertThat((boolean) evaluated.getOptionValue("e")).isTrue();
    assertThat((boolean) evaluated.getOptionValue("f")).isFalse();

    evaluated = cli.parse(Collections.singletonList("-def"));
    assertThat((boolean) evaluated.getOptionValue("d")).isTrue();
    assertThat((boolean) evaluated.getOptionValue("e")).isTrue();
    assertThat((boolean) evaluated.getOptionValue("f")).isTrue();
  }

  @Test(expected = CLIException.class)
  public void testThatNonUniqueArgumentIndexAreDetected() {
    CLI cli = new DefaultCLI().setName("test");
    cli.addArgument(new Argument().setIndex(0));
    cli.addArgument(new Argument().setIndex(1));
    cli.addArgument(new Argument().setIndex(1)); // conflict

    cli.parse(Arrays.asList("a", "b", "c"));
  }

  @Test(expected = CLIException.class)
  public void testThatOnlyTheLastArgumentCanBeMultivalued() {
    CLI cli = new DefaultCLI().setName("test");
    cli.addArgument(new Argument().setIndex(0));
    cli.addArgument(new Argument().setIndex(1).setMultiValued(true));
    cli.addArgument(new Argument().setIndex(2));

    cli.parse(Arrays.asList("a", "b", "c", "d"));
  }

  @Test(expected = CLIException.class)
  public void testThatOnlyOneArgumentCanBeMultivalued() {
    CLI cli = new DefaultCLI().setName("test");
    cli.addArgument(new Argument().setIndex(0));
    cli.addArgument(new Argument().setIndex(1).setMultiValued(true));
    cli.addArgument(new Argument().setIndex(2).setMultiValued(true));

    cli.parse(Arrays.asList("a", "b", "c", "d"));
  }

  @Test
  public void testWhenThereAreNoDeclaredArguments() {
    CLI cli = new DefaultCLI().setName("test");
    CommandLine cl = cli.parse(Arrays.asList("a", "b", "c"));
    assertThat(cl.allArguments()).containsExactly("a", "b", "c");
  }

  @Test
  public void testWithArgumentReceivingMultipleValues() {
    CLI cli = new DefaultCLI().setName("test");
    cli.addArgument(new Argument().setIndex(0).setArgName("arg").setDescription("argument1"));
    cli.addArgument(new Argument().setIndex(1).setMultiValued(true).setArgName("m").setDescription("multiple arg"));
    CommandLine cl = cli.parse(Arrays.asList("a", "b", "c"));
    assertThat((String) cl.getArgumentValue(0)).isEqualTo("a");
    assertThat(cl.getArgumentValues(1)).containsExactly("b", "c");
    assertThat((String) cl.getArgumentValue(1)).isEqualTo("b");

    StringBuilder builder = new StringBuilder();
    cli.usage(builder);

    assertThat(builder.toString()).contains("test arg m...");
  }

  @Test
  public void testAnnotatedClassWithArgumentReceivingMultipleValues() {
    CLI cli = CLI.create(CLIUsingMultipleArgument.class);

    CLIUsingMultipleArgument instance = new CLIUsingMultipleArgument();

    CommandLine cl = cli.parse(Arrays.asList("a", "b", "-s=1", "-s=2"));
    CLIConfigurator.inject(cl, instance);
  }

  @Test
  public void testWithMultipleArgumentReceivingSingleValues() {
    CLI cli = new DefaultCLI().setName("test");
    cli.addArgument(new Argument().setIndex(0));
    cli.addArgument(new Argument().setIndex(1).setMultiValued(true));
    CommandLine cl = cli.parse(Arrays.asList("a", "b"));
    assertThat((String) cl.getArgumentValue(0)).isEqualTo("a");
    assertThat(cl.getArgumentValues(1)).containsExactly("b");
    assertThat((String) cl.getArgumentValue(1)).isEqualTo("b");
  }

  @Test
  public void testWithMultipleRequiredArgument() {
    CLI cli = new DefaultCLI().setName("test");
    cli.addArgument(new Argument().setIndex(0));
    cli.addArgument(new Argument().setIndex(1).setMultiValued(true).setRequired(true));
    CommandLine cl = cli.parse(Arrays.asList("a", "b", "c"));
    assertThat((String) cl.getArgumentValue(0)).isEqualTo("a");
    assertThat(cl.getArgumentValues(1)).containsExactly("b", "c");
    assertThat((String) cl.getArgumentValue(1)).isEqualTo("b");

    cl = cli.parse(Arrays.asList("a", "b"));
    assertThat((String) cl.getArgumentValue(0)).isEqualTo("a");
    assertThat(cl.getArgumentValues(1)).containsExactly("b");
    assertThat((String) cl.getArgumentValue(1)).isEqualTo("b");

    try {
      cli.parse(Collections.singletonList("a"));
      fail("required argument not fultilled");
    } catch (MissingValueException e) {
      // OK.
    }
  }

  @Test
  public void testThatArgumentIndexCanBeGenerated() {
    CLI cli = new DefaultCLI().setName("test");
    cli.addArgument(new Argument());
    cli.addArgument(new Argument());
    cli.addArgument(new Argument().setMultiValued(true));

    CommandLine line = cli.parse(Arrays.asList("a", "b", "c", "d"));
    assertThat((String) line.getArgumentValue(0)).isEqualToIgnoringCase("a");
    assertThat((String) line.getArgumentValue(1)).isEqualToIgnoringCase("b");
    assertThat(line.getArgumentValues(2)).containsExactly("c", "d");
  }

  @Test
  public void testThatArgumentIndexCanBeGeneratedWithPartiallyNumberedArguments() {
    CLI cli = new DefaultCLI().setName("test");
    cli.addArgument(new Argument());
    cli.addArgument(new Argument().setIndex(1));
    cli.addArgument(new Argument().setMultiValued(true));

    CommandLine line = cli.parse(Arrays.asList("a", "b", "c", "d"));
    assertThat((String) line.getArgumentValue(0)).isEqualToIgnoringCase("a");
    assertThat((String) line.getArgumentValue(1)).isEqualToIgnoringCase("b");
    assertThat(line.getArgumentValues(2)).containsExactly("c", "d");
  }

  @Test
  public void testHelpOption() {
    CLI cli = new DefaultCLI().setName("test");
    cli.addOption(new Option().setLongName("foo").setRequired(true));
    cli.addOption(new Option().setLongName("help").setShortName("h").setHelp(true).setFlag(true));

    CommandLine line = cli.parse(Collections.singletonList("--foo=bar"));
    assertThat(line.isValid()).isTrue();
    assertThat((String) line.getOptionValue("foo")).isEqualTo("bar");
    assertThat(line.isAskingForHelp()).isFalse();

    line = cli.parse(Arrays.asList("--foo=bar", "-h"));
    assertThat(line.isValid()).isTrue();
    assertThat((String) line.getOptionValue("foo")).isEqualTo("bar");
    assertThat(line.isFlagEnabled("help")).isTrue();
    assertThat(line.isAskingForHelp()).isTrue();

    line = cli.parse(Collections.singletonList("-h"));
    assertThat(line.isValid()).isFalse();
    assertThat(line.isFlagEnabled("help")).isTrue();
    assertThat(line.isAskingForHelp()).isTrue();

    line = cli.parse(Collections.singletonList("-h"), false);
    assertThat(line.isValid()).isFalse();
    assertThat(line.isFlagEnabled("help")).isTrue();
    assertThat(line.isAskingForHelp()).isTrue();

    line = cli.parse(Arrays.asList("--foo=bar", "-h"), false);
    assertThat(line.isValid()).isTrue();
    assertThat((String) line.getOptionValue("foo")).isEqualTo("bar");
    assertThat(line.isFlagEnabled("help")).isTrue();
    assertThat(line.isAskingForHelp()).isTrue();


    try {
      cli.parse(Collections.<String>emptyList());
      fail("Exception expected");
    } catch (MissingOptionException e) {
      // OK
    }
  }

  @Test
  public void testHelpOptionUsingAnnotation() {

    CLI cli = CLIConfigurator.define(CLIUsingAHelpOption.class);

    CommandLine line = cli.parse(Collections.singletonList("--foo=bar"));
    assertThat(line.isValid()).isTrue();
    assertThat((String) line.getOptionValue("foo")).isEqualTo("bar");
    assertThat(line.isAskingForHelp()).isFalse();

    line = cli.parse(Arrays.asList("--foo=bar", "-h"));
    assertThat(line.isValid()).isTrue();
    assertThat((String) line.getOptionValue("foo")).isEqualTo("bar");
    assertThat(line.isFlagEnabled("help")).isTrue();
    assertThat(line.isAskingForHelp()).isTrue();

    line = cli.parse(Collections.singletonList("-h"));
    assertThat(line.isValid()).isFalse();
    assertThat(line.isFlagEnabled("help")).isTrue();
    assertThat(line.isAskingForHelp()).isTrue();

    line = cli.parse(Collections.singletonList("-h"), false);
    assertThat(line.isValid()).isFalse();
    assertThat(line.isFlagEnabled("help")).isTrue();
    assertThat(line.isAskingForHelp()).isTrue();

    line = cli.parse(Arrays.asList("--foo=bar", "-h"), false);
    assertThat(line.isValid()).isTrue();
    assertThat((String) line.getOptionValue("foo")).isEqualTo("bar");
    assertThat(line.isFlagEnabled("help")).isTrue();
    assertThat(line.isAskingForHelp()).isTrue();


    try {
      cli.parse(Collections.<String>emptyList());
      fail("Exception expected");
    } catch (MissingOptionException e) {
      // OK
    }
  }

  @Test
  public void testOptionsWithChoices() {
    CLI cli = new DefaultCLI().setName("test");
    cli.addOption(new Option().setLongName("color").addChoice("red").addChoice("blue").addChoice("green"));

    StringBuilder builder = new StringBuilder();
    cli.usage(builder);
    assertThat(builder.toString())
        .contains("[--color {blue, green, red}]") // Usage line
        .contains("  --color {blue, green, red}"); // options

    CommandLine line = cli.parse(Arrays.asList("--color", "blue"));
    assertThat((String) line.getOptionValue("color")).isEqualTo("blue");

    try {
      cli.parse(Collections.singletonList("--color=black"));
      fail("Invalid value expected");
    } catch (InvalidValueException e) {
      // OK
    }
  }

  @Test
  public void testOptionsWithChoicesAndDefault() {
    CLI cli = new DefaultCLI().setName("test");
    cli.addOption(new Option().setLongName("color").addChoice("red").addChoice("blue").addChoice("green")
        .setDefaultValue("green"));

    StringBuilder builder = new StringBuilder();
    cli.usage(builder);
    assertThat(builder.toString())
        .contains("[--color {blue, green, red}]") // Usage line
        .contains("  --color {blue, green, red}"); // options

    CommandLine line = cli.parse(Arrays.asList("--color", "blue"));
    assertThat((String) line.getOptionValue("color")).isEqualTo("blue");

    try {
      cli.parse(Collections.singletonList("--color=black"));
      fail("Invalid value expected");
    } catch (InvalidValueException e) {
      // OK
    }

    line = cli.parse(Collections.emptyList());
    assertThat((String) line.getOptionValue("color")).isEqualTo("green");
  }

  @Test
  public void testOptionsWithChoicesUsingEnum() {
    CLI cli = new DefaultCLI().setName("test");
    cli.addOption(new TypedOption<RetentionPolicy>().setLongName("retention").setType(RetentionPolicy.class));

    StringBuilder builder = new StringBuilder();
    cli.usage(builder);
    assertThat(builder.toString())
        .contains("[--retention {CLASS, RUNTIME, SOURCE}]") // Usage line
        .contains("  --retention {CLASS, RUNTIME, SOURCE}"); // options

    CommandLine line = cli.parse(Arrays.asList("--retention", "CLASS"));
    assertThat((RetentionPolicy) line.getOptionValue("retention")).isEqualTo(RetentionPolicy.CLASS);

    try {
      cli.parse(Collections.singletonList("--retention=nope"));
      fail("Invalid value expected");
    } catch (InvalidValueException e) {
      // OK
    }
  }

  @Test
  public void testOptionsWithChoicesUsingAnnotations() {
    CLI cli = CLIConfigurator.define(CLIUsingAEnumOption.class);

    StringBuilder builder = new StringBuilder();
    cli.usage(builder);
    assertThat(builder.toString())
        .contains("[--retention {CLASS, RUNTIME, SOURCE}]") // Usage line
        .contains("  --retention {CLASS, RUNTIME, SOURCE}"); // options

    CommandLine line = cli.parse(Arrays.asList("--retention", "CLASS", "--foo", "bar"));
    assertThat((RetentionPolicy) line.getOptionValue("retention")).isEqualTo(RetentionPolicy.CLASS);

    try {
      cli.parse(Collections.singletonList("--retention=nope"));
      fail("Invalid value expected");
    } catch (InvalidValueException e) {
      // OK
    }
  }


  @Name("test")
  private class CLIUsingAHelpOption {
    @io.vertx.core.cli.annotations.Option(help = true, flag = true, shortName = "h", longName = "help")
    public void setHelp(boolean b) {

    }

    @io.vertx.core.cli.annotations.Option(longName = "foo", required = true)
    public void setFoo(String f) {

    }
  }

  @Name("test")
  private class CLIUsingAEnumOption {
    @io.vertx.core.cli.annotations.Option(longName = "retention")
    public void setRetention(RetentionPolicy retention) {

    }

    @io.vertx.core.cli.annotations.Option(longName = "foo", required = true)
    public void setFoo(String f) {

    }
  }

  @Name("test")
  private class CLIUsingMultipleArgument {

    @io.vertx.core.cli.annotations.Argument(index = 0)
    public void setList(List<String> s) {
      if (s.size() != 2) {
        throw new IllegalArgumentException("2 arguments expected");
      }
    }

    @io.vertx.core.cli.annotations.Option(shortName = "s")
    public void setOpts(List<String> s) {
      if (s.size() != 2) {
        throw new IllegalArgumentException("2 values expected");
      }
    }

  }

}
