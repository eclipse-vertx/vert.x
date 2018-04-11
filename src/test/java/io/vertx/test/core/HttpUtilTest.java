/*
 * Copyright (c) 2011-2018 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.test.core;

import io.vertx.core.http.impl.HttpUtils;
import org.junit.Test;

public class HttpUtilTest {

  @Test
  public void testParseKeepAliveTimeout() {
    assertKeepAliveTimeout("timeout=5", 5);
    assertKeepAliveTimeout(" timeout=5", 5);
    assertKeepAliveTimeout("timeout=5 ", 5);
    assertKeepAliveTimeout("a=4,timeout=5", 5);
    assertKeepAliveTimeout(" a=4,timeout=5", 5);
    assertKeepAliveTimeout("a=4 ,timeout=5", 5);
    assertKeepAliveTimeout("a=4, timeout=5", 5);
    assertKeepAliveTimeout("a=4,timeout=5 ", 5);

    assertKeepAliveTimeout("", -1);
    assertKeepAliveTimeout("a=4", -1);
    assertKeepAliveTimeout("timeout", -1);
    assertKeepAliveTimeout("timeout=", -1);
    assertKeepAliveTimeout("timeout=a", -1);
    assertKeepAliveTimeout("timeout=-5", -1);
    assertKeepAliveTimeout("timeout=5_", -1);
  }

  private static void assertKeepAliveTimeout(CharSequence header, int expected) {
    org.junit.Assert.assertEquals(expected, HttpUtils.parseKeepAliveHeaderTimeout(header));
  }

}
