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

package io.vertx.core.impl.launcher.commands;

import io.vertx.core.spi.launcher.Command;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test the priority check.
 */
public class PriorityCommandTest extends CommandTestBase {

  private Command hello;

  @Before
  public void setUp() throws IOException {
    super.setUp();
    // run the command
    cli.execute("hello", "-name", "priority 100");
    // it should be stored as an instance so we can inspect it...
    hello = cli.getExistingCommandInstance("hello");
  }

  @Test
  public void testHelloCommandIsOfTypeHello2() {
    // ensure that we get the right command, Hello2 is the command "hello" with higher priority
    assertThat(hello.getClass())
      .isEqualTo(Hello2Command.class);
  }
}
