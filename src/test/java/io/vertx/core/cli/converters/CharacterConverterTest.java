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

package io.vertx.core.cli.converters;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;


public class CharacterConverterTest {

  CharacterConverter converter = CharacterConverter.INSTANCE;

  @Test
  public void testFromString() throws Exception {
    assertThat(converter.fromString("a")).isEqualTo('a');
  }

  @Test(expected = NullPointerException.class)
  public void testWithNull() throws Exception {
    converter.fromString(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testWithEmptyString() throws Exception {
    converter.fromString("");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testWithLongString() throws Exception {
    converter.fromString("ab");
  }
}
