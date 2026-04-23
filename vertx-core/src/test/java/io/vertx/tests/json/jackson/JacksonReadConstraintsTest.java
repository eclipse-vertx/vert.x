/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.json.jackson;

import org.junit.Test;

import static com.fasterxml.jackson.core.StreamReadConstraints.*;
import static com.fasterxml.jackson.core.StreamReadConstraints.DEFAULT_MAX_DOC_LEN;
import static com.fasterxml.jackson.core.StreamReadConstraints.DEFAULT_MAX_NAME_LEN;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class JacksonReadConstraintsTest extends JacksonReadConstraintsTestBase {

  @Test
  public void testDefaultConstraints() {
    testReadConstraints(
      DEFAULT_MAX_DEPTH,
      DEFAULT_MAX_NUM_LEN,
      DEFAULT_MAX_STRING_LEN,
      DEFAULT_MAX_NAME_LEN,
      DEFAULT_MAX_DOC_LEN);
  }
}
