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
package io.vertx.core.cli.converters;

import org.junit.Test;

import java.io.File;
import java.net.URL;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;


public class ConvertersTest {

  @Test
  public void testCreatingSingleValueUsingValueOfOnEnumeration() {
    assertThat(Converters.create(HttpMethod.class, "GET")).isEqualTo(HttpMethod.GET);
    try {
      assertThat(Converters.create(HttpMethod.class, null)).isNull();
      fail("Exception expected");
    } catch (IllegalArgumentException e) {
      // OK.
    }

    // Invalid value
    try {
      assertThat(Converters.create(HttpMethod.class, "FOO_IS_NOT_A_METHOD")).isNull();
      fail("Exception expected");
    } catch (IllegalArgumentException e) {
      // OK.
    }
  }

  @Test
  public void testWithString() throws NoSuchMethodException {
    assertThat(Converters.create(String.class, "Hello")).isEqualTo("Hello");
    assertThat(Converters.create(String.class, "")).isEqualTo("");
  }

  @Test
  public void testWithPrimitives() throws NoSuchMethodException {
    assertThat(Converters.create(Integer.class, "1")).isEqualTo(1);
    assertThat(Converters.create(Integer.TYPE, "1")).isEqualTo(1);

    assertThat(Converters.create(Long.class, "2")).isEqualTo(2l);
    assertThat(Converters.create(Long.TYPE, "2")).isEqualTo(2l);

    assertThat(Converters.create(Short.class, "3")).isEqualTo((short) 3);
    assertThat(Converters.create(Short.TYPE, "3")).isEqualTo((short) 3);

    assertThat(Converters.create(Byte.class, "4")).isEqualTo((byte) 4);
    assertThat(Converters.create(Byte.TYPE, "4")).isEqualTo((byte) 4);

    assertThat(Converters.create(Float.class, "5.5")).isEqualTo(5.5f);
    assertThat(Converters.create(Float.TYPE, "5.5")).isEqualTo(5.5f);

    assertThat(Converters.create(Double.class, "5.5")).isEqualTo(5.5d);
    assertThat(Converters.create(Double.TYPE, "5.5")).isEqualTo(5.5d);

    assertThat(Converters.create(Character.class, "a")).isEqualTo('a');
    assertThat(Converters.create(Character.TYPE, "a")).isEqualTo('a');

    assertThat(Converters.create(Boolean.class, "true")).isTrue();
    assertThat(Converters.create(Boolean.TYPE, "on")).isTrue();
    assertThat(Converters.create(Boolean.class, "")).isFalse();
  }

  @Test
  public void testUsingFrom() throws NoSuchMethodException {
    assertThat(Converters.create(Person2.class, "vertx").name).isEqualTo("vertx");
  }

  @Test
  public void testUsingFromString() throws NoSuchMethodException {
    assertThat(Converters.create(Person3.class, "vertx").name).isEqualTo("vertx");
  }

  @Test(expected = NoSuchElementException.class)
  public void testMissingConvertion() throws NoSuchMethodException {
    Converters.create(Object.class, "hello");
  }

  @Test
  public void testWithURL() {
    final URL url = Converters.create(URL.class, "http://vertx.io");
    assertThat(url.toExternalForm()).isEqualToIgnoringCase("http://vertx.io");
  }

  @Test
  public void testWithFile() {
    final File file = Converters.create(File.class, "foo/hello.txt");
    assertThat(file).hasName("hello.txt")
        .doesNotExist();
  }

  private enum HttpMethod {
    GET
  }

}