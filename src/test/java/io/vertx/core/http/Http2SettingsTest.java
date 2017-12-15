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

package io.vertx.core.http;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Thomas Segismont
 */
public class Http2SettingsTest {

  @Test
  public void testEqualsHashCode() throws Exception {
    Http2Settings s1 = new Http2Settings().setHeaderTableSize(1024);
    Http2Settings s2 = new Http2Settings().setHeaderTableSize(1024);
    Http2Settings s3 = new Http2Settings(s1.toJson());
    Http2Settings s4 = new Http2Settings().setHeaderTableSize(2048);

    assertEquals(s1, s1);
    assertEquals(s2, s2);
    assertEquals(s3, s3);

    assertEquals(s1, s2);
    assertEquals(s2, s1);
    assertEquals(s2, s3);
    assertEquals(s3, s2);

    assertEquals(s1, s3);
    assertEquals(s3, s1);

    assertEquals(s1.hashCode(), s2.hashCode());
    assertEquals(s2.hashCode(), s3.hashCode());

    assertFalse(s1.equals(null));
    assertFalse(s2.equals(null));
    assertFalse(s3.equals(null));

    assertNotEquals(s1, s4);
    assertNotEquals(s4, s1);
    assertNotEquals(s2, s4);
    assertNotEquals(s4, s2);
    assertNotEquals(s3, s4);
    assertNotEquals(s4, s3);

    assertNotEquals(s1.hashCode(), s4.hashCode());
    assertNotEquals(s2.hashCode(), s4.hashCode());
    assertNotEquals(s3.hashCode(), s4.hashCode());
  }
}
