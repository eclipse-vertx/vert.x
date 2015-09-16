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
import io.vertx.core.cli.converters.Person4;
import io.vertx.core.cli.converters.Person4Converter;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;


public class TypedArgumentTest {

  private CLI cli;
  private CommandLine evaluated;

  @Before
  public void setUp() {
    cli = new DefaultCLI().setName("test");
  }

  @Test(expected = CLIException.class)
  public void testThatArgumentWithTheSameIndexAreDetected() throws CLIException {
    cli.addArgument(new TypedArgument<String>().setIndex(0).setType(String.class));
    cli.addArgument(new TypedArgument<String>().setIndex(0).setType(String.class));
    evaluated = cli.parse(Arrays.asList("a", "b"));
  }

  @Test
  public void testThatMissingArgumentsAreDetected() throws CLIException {
    cli.addArgument(new TypedArgument<String>().setIndex(0).setType(String.class)
        .setRequired(true));
    cli.addArgument(new TypedArgument<String>().setIndex(1).setType(String.class)
        .setRequired(true));

    try {
      evaluated = cli.parse(Collections.emptyList());
      fail("Missing Value Exception expected");
    } catch (MissingValueException e) {
      // OK
    }

    try {
      evaluated = cli.parse(Collections.singletonList("a"));
      fail("Missing Value Exception expected");
    } catch (MissingValueException e) {
      // OK
    }

    evaluated = cli.parse(Arrays.asList("a", "b"));
  }

  @Test
  public void testMixedOfRequiredAnOptionalArguments() throws CLIException {
    cli.addArgument(new TypedArgument<String>().setIndex(0).setType(String.class)
        .setRequired(true));
    cli.addArgument(new TypedArgument<String>().setIndex(1).setType(String.class)
        .setRequired(false));

    try {
      evaluated = cli.parse(Collections.emptyList());
      fail("Missing Value Exception expected");
    } catch (MissingValueException e) {
      // OK
    }

    evaluated = cli.parse(Collections.singletonList("a"));
    assertThat((String) evaluated.getArgumentValue(0)).isEqualTo("a");
    assertThat((String) evaluated.getArgumentValue(1)).isNull();

    evaluated = cli.parse(Arrays.asList("a", "b"));
    assertThat((String) evaluated.getArgumentValue(0)).isEqualTo("a");
    assertThat((String) evaluated.getArgumentValue(1)).isEqualTo("b");
  }

  @Test
  public void testThatArgumentsAreOrdered() throws CLIException {
    cli.addArgument(new TypedArgument<String>().setIndex(1).setArgName("1").setType(String.class));
    cli.addArgument(new TypedArgument<String>().setIndex(0).setArgName("2").setType(String.class));
    cli.addArgument(new TypedArgument<String>().setIndex(2).setArgName("3").setType(String.class));

    assertThat(cli.getArguments()).hasSize(3);
    Iterator<Argument> iterator = cli.getArguments().iterator();
    assertThat(iterator.next().getArgName()).isEqualTo("2");
    assertThat(iterator.next().getArgName()).isEqualTo("1");
    assertThat(iterator.next().getArgName()).isEqualTo("3");

    evaluated = cli.parse(Arrays.asList("a", "b", "c"));

    assertThat((String) evaluated.getArgumentValue("2")).isEqualTo("a");
    assertThat((String) evaluated.getArgumentValue("1")).isEqualTo("b");
    assertThat((String) evaluated.getArgumentValue("3")).isEqualTo("c");
  }

  @Test
  public void testThatDefaultValuesAreHandled() throws CLIException {
    cli.addArgument(new TypedArgument<String>().setIndex(0).setArgName("1").setType(String.class)
        .setDefaultValue("hello").setRequired(false));

    evaluated = cli.parse(Collections.singletonList("a"));
    assertThat((String) evaluated.getArgumentValue(0)).isEqualTo("a");
    evaluated = cli.parse(Collections.emptyList());
    assertThat((String) evaluated.getArgumentValue(0)).isEqualTo("hello");
  }

  @Test
  public void testThatInvalidValuesAreReported() throws CLIException {
    cli.addArgument(new TypedArgument<Integer>()
        .setIndex(0).setArgName("1").setType(Integer.class));

    try {
      evaluated = cli.parse(Collections.singletonList("a"));
      evaluated.getArgumentValue(0);
      fail("Exception expected");
    } catch (CLIException e) {
      assertThat(e).isInstanceOf(InvalidValueException.class);
      InvalidValueException cause = (InvalidValueException) e;
      assertThat(cause.getArgument().getIndex()).isEqualTo(0);
      assertThat(cause.getArgument().getArgName()).isEqualTo("1");
      assertThat(cause.getValue()).isEqualTo("a");
    }

  }

  @Test
  public void testThatInvalidValuesAsDefaultValueAreReported() throws CLIException {
    try {
      cli.addArgument(new TypedArgument<Integer>()
          .setIndex(0).setArgName("1").setType(Integer.class).setDefaultValue("a"));
    } catch (IllegalArgumentException e) {
      assertThat(e.getCause()).isInstanceOf(InvalidValueException.class);
      InvalidValueException cause = (InvalidValueException) e.getCause();
      assertThat(cause.getArgument().getIndex()).isEqualTo(0);
      assertThat(cause.getArgument().getArgName()).isEqualTo("1");
      assertThat(cause.getValue()).isEqualTo("a");
    }
  }

  @Test
  public void testThatConvertersAreHandled() throws CLIException {
    final TypedArgument<Person4> arg = new TypedArgument<Person4>()
        .setIndex(0).setArgName("person").setType(Person4.class)
        .setConverter(ReflectionUtils.newInstance(Person4Converter.class))
        .setDefaultValue("Bill,Ballantine")
        .setRequired(false);
    cli.addArgument(arg);

    evaluated = cli.parse(Collections.singletonList("Bob,Morane"));
    Person4 person = evaluated.getArgumentValue("person");
    assertThat(person.first).isEqualTo("Bob");
    assertThat(person.last).isEqualTo("Morane");


    evaluated = cli.parse(Collections.emptyList());
    person = evaluated.getArgumentValue("person");
    assertThat(person.first).isEqualTo("Bill");
    assertThat(person.last).isEqualTo("Ballantine");
  }

}