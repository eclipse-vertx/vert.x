/*
 * Copyright (c) 2011-2017 The original author or authors
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     https://www.eclipse.org/legal/epl-2.0/
 *
 *     The Apache License v2.0 is available at
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * You may elect to redistribute this code under either of these licenses.
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
