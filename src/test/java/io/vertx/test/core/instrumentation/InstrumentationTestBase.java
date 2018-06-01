/*
 * Copyright 2015 Red Hat, Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.test.core.instrumentation;

import io.vertx.core.spi.instrumentation.InstrumentationFactory;
import io.vertx.test.core.VertxTestBase;

public abstract class InstrumentationTestBase extends VertxTestBase {

  protected TestInstrumentation instrumentation = new TestInstrumentation();

  @Override
  public void setUp() throws Exception {
    InstrumentationFactory.setInstrumentation(instrumentation);
    super.setUp();
    InstrumentationFactory.setInstrumentation(null);
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    instrumentation.assertNoErrors();
  }
}
