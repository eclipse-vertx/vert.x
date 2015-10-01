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

import io.vertx.core.cli.*;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
    CommandLine evaluated = cli.parse( Collections.singletonList("--file=hello.txt"));
    assertThat(evaluated.cli().getOptions()).hasSize(1);
    assertThat((String) evaluated.getOptionValue("file")).isEqualTo("hello.txt");
    assertThat(evaluated.getOptionValues("f")).containsExactly("hello.txt");

    StringBuilder usage = new StringBuilder();
    cli.usage(usage);
    assertThat(usage).startsWith("Usage: test [-f <value>]");
    assertThat(usage).contains("-f,--file <value>");
  }


  @Test
  public void testWithOneLongOptionUsingSpace() throws CLIException {
    Option[] options = new Option[]{
        new Option().setShortName("f").setLongName("file")
            .setSingleValued(true)
    };

    cli.addOptions(Arrays.asList(options));
    CommandLine evaluated = cli.parse( Arrays.asList("--file", "hello.txt"));
    assertThat(evaluated.cli().getOptions()).hasSize(1);
    assertThat((String) evaluated.getOptionValue("file")).isEqualTo("hello.txt");
    assertThat(evaluated.getOptionValues("f")).containsExactly("hello.txt");

    StringBuilder usage = new StringBuilder();
    cli.usage(usage);
    assertThat(usage).startsWith("Usage: test [-f <value>]");
  }

  @Test
  public void testWithOneShortOption() throws CLIException {
    Option[] options = new Option[]{
        new Option().setShortName("f").setLongName("file").setSingleValued(true)
    };

    cli.addOptions(Arrays.asList(options));
    CommandLine evaluated = cli.parse( Collections.singletonList("-f=hello.txt"));
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
    CommandLine evaluated = cli.parse( Arrays.asList("-f", "hello.txt"));
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

    CommandLine evaluated = cli.parse( Arrays.asList("--file", "hello.txt"));
    assertThat((String) evaluated.getOptionValue("file")).isEqualTo("hello.txt");

    evaluated = cli.parse( Collections.singletonList("--file=hello.txt"));
    assertThat((String) evaluated.getOptionValue("file")).isEqualTo("hello.txt");

    evaluated = cli.parse( Collections.singletonList("-filehello.txt"));
    assertThat((String) evaluated.getOptionValue("file")).isEqualTo("hello.txt");

    evaluated = cli.parse( Arrays.asList("--FILE", "hello.txt"));
    assertThat((String) evaluated.getOptionValue("file")).isEqualTo("hello.txt");
  }

  @Test
  public void testTheDifferentFormatForShortOption() throws CLIException {

    Option[] options = new Option[]{
        new Option().setShortName("f").setLongName("file").setSingleValued(true)
    };
    cli.addOptions(Arrays.asList(options));

    CommandLine evaluated = cli.parse( Arrays.asList("-f", "hello.txt"));
    assertThat((String) evaluated.getOptionValue("file")).isEqualTo("hello.txt");

    evaluated = cli.parse( Collections.singletonList("-f=hello.txt"));
    assertThat((String) evaluated.getOptionValue("file")).isEqualTo("hello.txt");

    evaluated = cli.parse( Collections.singletonList("-fhello.txt"));
    assertThat((String) evaluated.getOptionValue("file")).isEqualTo("hello.txt");
  }

  @Test
  public void testWithMultipleValues() throws CLIException {

    Option[] options = new Option[]{
        new Option().setShortName("f").setLongName("file")
            .setMultiValued(true)
    };
    cli.addOptions(Arrays.asList(options));

    CommandLine evaluated = cli.parse( Arrays.asList("-f=hello.txt", "--file=hello2.txt"));
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
    CommandLine evaluated = cli.parse( Collections.singletonList("-f=hello.txt,hello2.txt"));
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
    CommandLine evaluated = cli.parse( Arrays.asList("--flag", "--flag2", "--flag3"));
    assertThat((boolean) evaluated.getOptionValue("flag")).isEqualTo(true);
    assertThat((boolean) evaluated.getOptionValue("flag2")).isEqualTo(true);
    assertThat((boolean) evaluated.getOptionValue("flag3")).isEqualTo(true);

    evaluated = cli.parse( Arrays.asList("--flag=true", "--flag2=false", "--flag3"));
    assertThat((boolean) evaluated.getOptionValue("flag")).isEqualTo(true);
    assertThat((boolean) evaluated.getOptionValue("flag2")).isEqualTo(false);
    assertThat((boolean) evaluated.getOptionValue("flag3")).isEqualTo(true);

    evaluated = cli.parse( Arrays.asList("--flag", "--flag2"));
    assertThat((boolean) evaluated.getOptionValue("flag")).isEqualTo(true);
    assertThat((boolean) evaluated.getOptionValue("flag2")).isEqualTo(true);
    assertThat((boolean) evaluated.getOptionValue("flag3")).isEqualTo(false);

    evaluated = cli.parse( Arrays.asList("--flag", "true", "--flag2", "false", "--flag3"));
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
    CommandLine evaluated = cli.parse( Arrays.asList("org.acme.Foo", "-f=no"));
    assertThat(evaluated.allArguments()).contains("org.acme.Foo");

    evaluated = cli.parse( Arrays.asList("-f=no", "org.acme.Foo"));
    assertThat(evaluated.allArguments()).contains("org.acme.Foo");

    evaluated = cli.parse( Arrays.asList("-f=no", "org.acme.Foo", "bar"));
    assertThat(evaluated.allArguments()).contains("org.acme.Foo", "bar");
  }

  @Test
  public void testUnknownOption() throws CLIException {
    Option[] options = new Option[]{
        new TypedOption<Boolean>().setShortName("f").setLongName("flag")
            .setType(Boolean.class).setRequired(true).setSingleValued(true)
    };

    cli.addOptions(Arrays.asList(options));
    CommandLine evaluated = cli.parse( Arrays.asList("-flag=true", "-unknown=x"));
    assertThat(evaluated.allArguments()).contains("-unknown=x");
  }

  @Test(expected = MissingOptionException.class)
  public void testNotFulfilledRequiredOptions() throws CLIException {
    Option[] options = new Option[]{
        new TypedOption<Boolean>().setShortName("f").setLongName("flag").setType(Boolean.class).setRequired(true).setSingleValued(true)
    };
    cli.addOptions(Arrays.asList(options));
    CommandLine evaluated = cli.parse( Collections.emptyList());
  }

  @Test
  public void testRequiredOptions() throws CLIException {
    Option[] options = new Option[]{
        new TypedOption<Boolean>().setShortName("f").setLongName("flag")
            .setType(Boolean.class).setRequired(true).setFlag(true)
    };

    cli.addOptions(Arrays.asList(options));
    cli.parse( Collections.singletonList("-f"));
  }

  @Test
  public void testQuotedValues() throws CLIException {
    Option[] options = new Option[]{
        new Option().setShortName("f").setLongName("file").setSingleValued(true)
    };

    cli.addOptions(Arrays.asList(options));
    CommandLine evaluated = cli.parse( Arrays.asList("--file", "\"hello.txt\""));
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
    CommandLine evaluated = cli.parse( Arrays.asList("--num", "-1.5"));
    assertThat(evaluated.cli().getOptions()).hasSize(1);
    assertThat(cli.getArguments()).isEmpty();
    assertThat((double) evaluated.getOptionValue("num")).isEqualTo(-1.5d);

    evaluated = cli.parse( Collections.singletonList("--num=-1.5"));
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
    cli.parse( Collections.singletonList("--file"));
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
    CommandLine evaluated = cli.parse( Collections.singletonList("-ha"));
    assertThat((boolean) evaluated.getOptionValue("ha")).isTrue();
    assertThat((String) evaluated.getArgumentValue("verticle")).isNull();
    assertThat((String) evaluated.getArgumentValue(0)).isNull();
    evaluated = cli.parse( Arrays.asList("-ha", "true"));
    assertThat((boolean) evaluated.getOptionValue("ha")).isTrue();
    evaluated = cli.parse( Collections.singletonList("-ha=true"));
    assertThat((boolean) evaluated.getOptionValue("ha")).isTrue();
    evaluated = cli.parse( Collections.singletonList("--ha"));
    assertThat((boolean) evaluated.getOptionValue("ha")).isTrue();
    evaluated = cli.parse( Arrays.asList("--ha", "false"));
    assertThat((boolean) evaluated.getOptionValue("ha")).isFalse();
    evaluated = cli.parse( Collections.singletonList("--ha=no"));
    assertThat((boolean) evaluated.getOptionValue("ha")).isFalse();


    // Verticle deployment
    evaluated = cli.parse( Arrays.asList("org.acme.FooVerticle",
        "-instances=4",
        "-cp", "." + File.pathSeparator + "my.jar"));
    assertThat(evaluated.allArguments()).hasSize(1).containsExactly("org.acme.FooVerticle");
    assertThat((String) evaluated.getArgumentValue("verticle")).isEqualTo("org.acme.FooVerticle");

    int instances = evaluated.getOptionValue("instances");
    List<String> classpath = evaluated.getOptionValues("classpath");
    assertThat(instances).isEqualTo(4);
    assertThat(classpath).containsExactly(".", "my.jar");


    // Cluster environment
    evaluated = cli.parse( Arrays.asList("org.acme.FooVerticle", "-cluster"));
    assertThat(evaluated.allArguments()).hasSize(1).containsExactly("org.acme.FooVerticle");
    assertThat((String) evaluated.getArgumentValue("verticle")).isEqualTo("org.acme.FooVerticle");
    assertThat((String) evaluated.getArgumentValue(0)).isEqualTo("org.acme.FooVerticle");
    assertThat((boolean) evaluated.getOptionValue("cluster")).isTrue();

    evaluated = cli.parse( Arrays.asList("org.acme.FooVerticle", "--cluster"));
    assertThat(evaluated.allArguments()).hasSize(1).containsExactly("org.acme.FooVerticle");
    assertThat((String) evaluated.getArgumentValue("verticle")).isEqualTo("org.acme.FooVerticle");
    assertThat((String) evaluated.getArgumentValue(0)).isEqualTo("org.acme.FooVerticle");
    assertThat((boolean) evaluated.getOptionValue("cluster")).isTrue();

    evaluated = cli.parse( Arrays.asList("org.acme.FooVerticle", "-cluster", "-cluster-host", "127.0.0.1"));
    assertThat(evaluated.allArguments()).hasSize(1).containsExactly("org.acme.FooVerticle");
    assertThat((String) evaluated.getArgumentValue("verticle")).isEqualTo("org.acme.FooVerticle");
    assertThat((String) evaluated.getArgumentValue(0)).isEqualTo("org.acme.FooVerticle");
    assertThat((boolean) evaluated.getOptionValue("cluster")).isTrue();
    assertThat((String) evaluated.getOptionValue("cluster-host"))
        .isEqualTo("127.0.0.1");

    evaluated = cli.parse( Arrays.asList("org.acme.FooVerticle", "-cluster", "--cluster-host", "127.0.0.1"));
    assertThat(evaluated.allArguments()).hasSize(1).containsExactly("org.acme.FooVerticle");
    assertThat((String) evaluated.getArgumentValue("verticle")).isEqualTo("org.acme.FooVerticle");
    assertThat((String) evaluated.getArgumentValue(0)).isEqualTo("org.acme.FooVerticle");
    assertThat((boolean) evaluated.getOptionValue("cluster")).isTrue();
    assertThat((String) evaluated.getOptionValue("cluster-host"))
        .isEqualTo("127.0.0.1");

    evaluated = cli.parse( Arrays.asList("org.acme.FooVerticle", "-cluster", "-cluster-host=127.0.0.1"));
    assertThat(evaluated.allArguments()).hasSize(1).containsExactly("org.acme.FooVerticle");
    assertThat((String) evaluated.getArgumentValue("verticle")).isEqualTo("org.acme.FooVerticle");
    assertThat((String) evaluated.getArgumentValue(0)).isEqualTo("org.acme.FooVerticle");
    assertThat((boolean) evaluated.getOptionValue("cluster")).isTrue();
    assertThat((String) evaluated.getOptionValue("cluster-host"))
        .isEqualTo("127.0.0.1");

    evaluated = cli.parse( Arrays.asList("org.acme.FooVerticle", "-cluster", "-cluster-host", "127.0.0.1",
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
    CommandLine evaluated = cli.parse( Arrays.asList("-Dx=y", "-F"));
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
    CommandLine evaluated = cli.parse( Arrays.asList("-d", "-e", "-f"));
    assertThat((boolean) evaluated.getOptionValue("d")).isTrue();
    assertThat((boolean) evaluated.getOptionValue("e")).isTrue();
    assertThat((boolean) evaluated.getOptionValue("f")).isTrue();

    evaluated = cli.parse( Collections.singletonList("-de"));
    assertThat((boolean) evaluated.getOptionValue("d")).isTrue();
    assertThat((boolean) evaluated.getOptionValue("e")).isTrue();
    assertThat((boolean) evaluated.getOptionValue("f")).isFalse();

    evaluated = cli.parse( Collections.singletonList("-def"));
    assertThat((boolean) evaluated.getOptionValue("d")).isTrue();
    assertThat((boolean) evaluated.getOptionValue("e")).isTrue();
    assertThat((boolean) evaluated.getOptionValue("f")).isTrue();
  }

}