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


public class StringConverterTest {

  @Test
  public void testFromString() throws Exception {
    assertThat(StringConverter.INSTANCE.fromString("hello")).isEqualTo("hello");
    assertThat(StringConverter.INSTANCE.fromString("")).isEqualTo("");
    assertThat(StringConverter.INSTANCE.fromString(null)).isEqualTo(null);
  }
}