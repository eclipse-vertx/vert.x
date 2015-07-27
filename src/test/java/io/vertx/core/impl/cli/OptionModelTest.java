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
package io.vertx.core.impl.cli;

import io.vertx.core.spi.cli.CommandLine;
import io.vertx.core.spi.cli.CommandLineException;
import io.vertx.core.spi.cli.InvalidValueException;
import io.vertx.core.spi.cli.OptionModel;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;

public class OptionModelTest {

  @Test
  public void testToString() {
    OptionModel<String> option = OptionModel.<String>builder().shortName("f").type(String.class).build();
    assertThat(option.toString()).isEqualToIgnoringCase("'f'");

    option = OptionModel.<String>builder().shortName("f").type(String.class).longName("file").build();
    assertThat(option.toString()).isEqualToIgnoringCase("'file'");

    option = OptionModel.<String>builder().longName("file").type(String.class).build();
    assertThat(option.toString()).isEqualToIgnoringCase("'file'");
  }

  @Test
  public void testClear() throws CommandLineException {
    OptionModel<String> option = OptionModel.<String>builder().shortName("x").type(String.class).acceptValue().build();
    assertThat(option.getValues()).hasSize(0);
    option.process("a");
    assertThat(option.getValues()).hasSize(1);
    option.clear();
    assertThat(option.getValues()).hasSize(0);
  }

  @Test
  public void testProcessing() throws CommandLineException {
    OptionModel<String> option = OptionModel.<String>builder().shortName("f").type(String.class).acceptValue().build();
    assertThat(option.hasValue()).isFalse();
    option.process("file.txt");
    assertThat(option.hasValue()).isTrue();
    assertThat(option.getValue()).isEqualTo("file.txt");

    OptionModel<Boolean> option2 = OptionModel.<Boolean>builder().shortName("f").acceptValue().type(Boolean.TYPE)
        .build();
    assertThat(option2.hasValue()).isFalse();
    option2.process("true");
    assertThat(option.acceptMultipleValues()).isFalse();
    assertThat(option.acceptSingleValue()).isTrue();
    assertThat(option.acceptValue()).isTrue();
    assertThat(option2.hasValue()).isTrue();
    assertThat(option2.getValue()).isTrue();

    option2 = OptionModel.<Boolean>builder().shortName("f").acceptValue(false).type(Boolean.TYPE)
        .build();
    assertThat(option2.hasValue()).isFalse();
    option2.process("");
    assertThat(option2.acceptMultipleValues()).isFalse();
    assertThat(option2.acceptSingleValue()).isFalse();
    assertThat(option2.acceptValue()).isFalse();
    assertThat(option2.hasValue()).isTrue();
    assertThat(option2.getValue()).isTrue();

    // Multiple values
    option = OptionModel.<String>builder().shortName("f").type(String.class).acceptMultipleValues().build();
    assertThat(option.hasValue()).isFalse();
    option.process("file.txt");
    option.process("file2.txt");
    option.process("file3.txt");
    assertThat(option.hasValue()).isTrue();
    assertThat(option.acceptMultipleValues()).isTrue();
    assertThat(option.acceptSingleValue()).isFalse();
    assertThat(option.acceptValue()).isTrue();
    assertThat(option.getValues()).containsExactly("file.txt", "file2.txt", "file3.txt");

    // List
    option = OptionModel.<String>builder().shortName("f").type(String.class).acceptMultipleValues()
        .listSeparator(",").build();
    option.process("file.txt, file2.txt,file3.txt");
    assertThat(option.hasValue()).isTrue();
    assertThat(option.acceptMultipleValues()).isTrue();
    assertThat(option.acceptSingleValue()).isFalse();
    assertThat(option.acceptValue()).isTrue();
    assertThat(option.getValues()).containsExactly("file.txt", "file2.txt", "file3.txt");

    // Default value
    option = OptionModel.<String>builder().shortName("f").type(String.class)
        .acceptValue()
        .defaultValue("hello.txt")
        .build();
    assertThat(option.hasValue()).isFalse();
    assertThat(option.getDefaultValue()).isEqualTo("hello.txt");
    assertThat(option.getValue()).isEqualToIgnoringCase("hello.txt");
    option.setDefaultValue("hello2.txt");
    assertThat(option.getValue()).isEqualToIgnoringCase("hello2.txt");
  }

  @Test
  public void testThatInvalidValuesAreReported() throws CommandLineException {
    CommandLine line = new CommandLine();
    line.addOption(OptionModel.<Integer>builder().shortName("s").type(Integer.class).build());
    try {
      line.parse("-s=a");
      fail("Exception expected");
    } catch (InvalidValueException e) {
      assertThat(e.getOption().getShortName()).isEqualTo("s");
      assertThat(e.getValue()).isEqualTo("a");
    }

  }

  @Test
  public void testThatInvalidValuesAsDefaultValueAreReported() throws CommandLineException {
    CommandLine line = new CommandLine();
    try {
      line.addOption(OptionModel.<Integer>builder().shortName("s").type(Integer.class).defaultValue("a").build());
    } catch (IllegalArgumentException e) {
      assertThat(e.getCause()).isInstanceOf(InvalidValueException.class);
      InvalidValueException cause = (InvalidValueException) e.getCause();
      assertThat(cause.getOption().getShortName()).isEqualTo("s");
      assertThat(cause.getValue()).isEqualTo("a");
    }
  }

}