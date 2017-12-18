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


public class CustomConverterTest {

  @Test
  public void testCreation() {
    assertThat(new Person4Converter().fromString("bob, morane").first).isEqualToIgnoringCase("bob");
    assertThat(new Person4Converter().fromString("bob, morane").last).isEqualToIgnoringCase("morane");
  }

  @Test
  public void testConvertion() {
    Person4 p4 = Converters.create("bob, morane", new Person4Converter());
    assertThat(p4.first).isEqualTo("bob");
    assertThat(p4.last).isEqualTo("morane");
  }

}
