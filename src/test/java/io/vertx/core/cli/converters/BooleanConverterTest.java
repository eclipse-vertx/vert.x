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

import static org.assertj.core.api.Assertions.assertThat;


public class BooleanConverterTest {

  private BooleanConverter converter = BooleanConverter.INSTANCE;

  @Test
  public void testYesNo() throws Exception {
    assertThat(converter.fromString("yes")).isTrue();
    assertThat(converter.fromString("YeS")).isTrue();
    assertThat(converter.fromString("no")).isFalse();
    assertThat(converter.fromString("nO")).isFalse();
  }

  @Test
  public void testOnOff() throws Exception {
    assertThat(converter.fromString("on")).isTrue();
    assertThat(converter.fromString("ON")).isTrue();
    assertThat(converter.fromString("off")).isFalse();
    assertThat(converter.fromString("oFf")).isFalse();
  }

  @Test
  public void testTrueFalse() throws Exception {
    assertThat(converter.fromString("true")).isTrue();
    assertThat(converter.fromString("TruE")).isTrue();
    assertThat(converter.fromString("fALse")).isFalse();
    assertThat(converter.fromString("false")).isFalse();
  }

  @Test
  public void testNumbers() throws Exception {
    assertThat(converter.fromString("1")).isTrue();
    assertThat(converter.fromString("2")).isFalse();
    assertThat(converter.fromString("0")).isFalse();
  }

  @Test
  public void testWithNullAndEmptyString() throws Exception {
    assertThat(converter.fromString(null)).isFalse();
    assertThat(converter.fromString("")).isFalse();
  }

  @Test
  public void testWithRandomString() throws Exception {
    assertThat(converter.fromString("aaaa")).isFalse();
    assertThat(converter.fromString("welcome true")).isFalse();
    assertThat(converter.fromString("true welcome")).isFalse();
  }
}