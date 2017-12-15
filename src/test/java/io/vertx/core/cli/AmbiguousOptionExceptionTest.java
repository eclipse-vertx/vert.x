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

package io.vertx.core.cli;

import org.junit.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test the {@link AmbiguousOptionException}.
 */
public class AmbiguousOptionExceptionTest {

  @Test
  public void testCreation() {
    AmbiguousOptionException exception = new AmbiguousOptionException("foo",
        Arrays.asList(new Option().setLongName("foobar"), new Option().setLongName("foobaz")));
    assertThat(exception.getOptions()).hasSize(2);
    assertThat(exception.getToken()).isEqualTo("foo");
    assertThat(exception.getMessage())
        .contains("Ambiguous argument in command line")
        .contains("'foo'")
        .contains("foobar").contains("foobaz");
  }

}
