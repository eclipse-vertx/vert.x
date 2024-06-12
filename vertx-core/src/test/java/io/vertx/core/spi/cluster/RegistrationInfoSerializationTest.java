/*
 * Copyright (c) 2011-2020 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.spi.cluster;

import io.vertx.core.buffer.Buffer;
import io.vertx.test.core.TestUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;

/**
 * @author Thomas Segismont
 */
@RunWith(Parameterized.class)
public class RegistrationInfoSerializationTest {

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{
      {new RegistrationInfo("foo", -13004, true)},
      {new RegistrationInfo("bar", +13004, false)}
    });
  }

  private final RegistrationInfo expected;

  public RegistrationInfoSerializationTest(RegistrationInfo expected) {
    this.expected = expected;
  }

  @Test
  public void testSerialization() {
    Buffer padding = TestUtils.randomBuffer(TestUtils.randomShort());
    Buffer buffer = Buffer.buffer();
    buffer.appendBuffer(padding);
    expected.writeToBuffer(buffer);
    RegistrationInfo registrationInfo = new RegistrationInfo();
    int pos = registrationInfo.readFromBuffer(padding.length(), buffer);
    assertEquals(expected, registrationInfo);
    assertEquals(buffer.length(), pos);
  }
}
