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
package io.vertx.core.impl.launcher.commands;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks the behavior of the {@link ExecUtils} class.
 *
 * @author Clement Escoffier <clement@apache.org>
 */
public class ExecUtilsTest {

  private Field field;
  private String originalOsName;

  @Before
  public void setUp() throws NoSuchFieldException, IllegalAccessException {
    field = ExecUtils.class.getDeclaredField("osName");
    field.setAccessible(true);
    originalOsName = (String) field.get(null);
  }

  @After
  public void tearDown() throws IllegalAccessException {
     set(originalOsName);
  }

  private void set(String value) throws IllegalAccessException {
    field.setAccessible(true);
    field.set(null, value);
  }

  @Test
  public void testAddArgument() throws Exception {
    List<String> args = new ArrayList<>();
    ExecUtils.addArgument(args, "hello");
    ExecUtils.addArgument(args, "-foo");
    ExecUtils.addArgument(args, "--bar");
    ExecUtils.addArgument(args, "--baz=hello");
    ExecUtils.addArgument(args, "with spaces");
    ExecUtils.addArgument(args, "with'single'_quotes");
    ExecUtils.addArgument(args, "with\"double\"quotes");
    ExecUtils.addArgument(args, "with \"double\" quotes and spaces");
    ExecUtils.addArgument(args, "with 'single' quotes and spaces");
    ExecUtils.addArgument(args, "'wrapped_in_single_quotes'");
    ExecUtils.addArgument(args, "\"wrapped_in_double_quotes\"");

    assertThat(args).contains("hello", "-foo", "--bar", "--baz=hello",
        "\"with spaces\"",
        "\"with'single'_quotes\"",  "'with\"double\"quotes'",
        "'with \"double\" quotes and spaces'", "\"with 'single' quotes and spaces\"",
        "wrapped_in_single_quotes", "wrapped_in_double_quotes");
  }

  @Test
  public void testIsWindows() throws IllegalAccessException {
    set("windows 98");
    assertThat(ExecUtils.isWindows()).isTrue();
    set("windows me");
    assertThat(ExecUtils.isWindows()).isTrue();
    set("windows nt");
    assertThat(ExecUtils.isWindows()).isTrue();
    set("windows 2000");
    assertThat(ExecUtils.isWindows()).isTrue();
    set("windows xp");
    assertThat(ExecUtils.isWindows()).isTrue();
    set("windows 2003");
    assertThat(ExecUtils.isWindows()).isTrue();
    set("windows vista");
    assertThat(ExecUtils.isWindows()).isTrue();
    set("windows 7");
    assertThat(ExecUtils.isWindows()).isTrue();
    set("windows 8");
    assertThat(ExecUtils.isWindows()).isTrue();

    // NT Unknown
    set("windows nt (unknown)");
    assertThat(ExecUtils.isWindows()).isTrue();
  }

  @Test
  public void testIsNotWindows() throws IllegalAccessException {
    set("mac os x");
    assertThat(ExecUtils.isWindows()).isFalse();

    set("linux");
    assertThat(ExecUtils.isWindows()).isFalse();
  }
}