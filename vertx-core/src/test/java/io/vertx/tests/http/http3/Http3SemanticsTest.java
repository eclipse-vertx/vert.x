/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.http.http3;

import io.vertx.tests.http.HttpSemanticsTest;
import org.junit.Ignore;
import org.junit.Test;

public class Http3SemanticsTest extends HttpSemanticsTest {

  public Http3SemanticsTest() {
    super(Http3Configurator.INSTANCE);
  }

  @Ignore("A request without authority hangs: the client request never completes and the server never sees it")
  @Test
  @Override
  public void testMissingAuthority() throws Exception {
  }
}
