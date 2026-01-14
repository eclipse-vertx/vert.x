/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.http.impl;

import io.vertx.core.http.impl.AltSvc;
import io.vertx.core.net.HostAndPort;
import io.vertx.test.core.TestParser;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

public class AltSvcParserTest {

  public static TestParser token = AltSvc::parseParameter;
  public static TestParser alternative = AltSvc::parseAlternative;
  public static TestParser altValue = AltSvc::parseAltValue;

  @Test
  public void testParseRawAltValue() {
    altValue.assertParse("abc=\"def\"");
    altValue.assertFailParse("abc=\"def\" ");
    altValue.assertFailParse("abc=\"def\" ;");
    altValue.assertFailParse("abc=\"def\" ; ");
    altValue.assertParse("abc=\"def\";a=b");
    altValue.assertParse("abc=\"def\" ;a=b");
    altValue.assertParse("abc=\"def\"; a=b");
    altValue.assertParse("abc=\"def\" ; a=b");
    altValue.assertFailParse("abc=\"def\";a=b ");
  }

  @Test
  public void testParseAltValue() {
    AltSvc.Value value = AltSvc.parseAltValue("abc=\":10\"");
    assertNotNull(value);
    assertEquals("abc", value.protocolId());
    assertEquals(HostAndPort.authority("", 10), value.altAuthority());
    assertEquals(Map.of(), value.parameters());
    value = AltSvc.parseAltValue("abc=\"foo:10\"");
    assertNotNull(value);
    assertEquals("abc", value.protocolId());
    assertEquals(HostAndPort.authority("foo", 10), value.altAuthority());
    assertEquals(Map.of(), value.parameters());
    value = AltSvc.parseAltValue("abc=\"192.168.0.1:10\"");
    assertNotNull(value);
    assertEquals("abc", value.protocolId());
    assertEquals(HostAndPort.authority("192.168.0.1", 10), value.altAuthority());
    assertEquals(Map.of(), value.parameters());
    value = AltSvc.parseAltValue("abc=\"[::]:10\"");
    assertNotNull(value);
    assertEquals("abc", value.protocolId());
    assertEquals(HostAndPort.authority("[::]", 10), value.altAuthority());
    assertEquals(Map.of(), value.parameters());
    value = AltSvc.parseAltValue("abc=\"foo:10\";a=b");
    assertNotNull(value);
    assertEquals("abc", value.protocolId());
    assertEquals(HostAndPort.authority("foo", 10), value.altAuthority());
    assertEquals(Map.of("a", "b"), value.parameters());
    value = AltSvc.parseAltValue("abc=\"foo:10\";a=\"b\"");
    assertNotNull(value);
    assertEquals("abc", value.protocolId());
    assertEquals(HostAndPort.authority("foo", 10), value.altAuthority());
    assertEquals(Map.of("a", "b"), value.parameters());
    assertNull(AltSvc.parseAltValue("abc=\"def\";a"));
    assertNull(AltSvc.parseAltValue("abc=\"def\";a="));
  }

  @Test
  public void testParseAlternative() {
    alternative.assertParse("abc=\"def\"");
    alternative.assertFailParse("abc=def");
  }

  @Test
  public void testParseParameter() {
    token.assertParse("abc=def");
    token.assertParse("abc=\"def\"");
    token.assertFailParse("");
    token.assertFailParse("abc");
    token.assertFailParse("abc=");
    token.assertFailParse("abc=\"def");
    token.assertFailParse("abc=def\"");
    token.assertFailParse("abc =def");
    token.assertFailParse("abc= def");
  }

  @Test
  public void testParseAltSvc() {
    AltSvc.Clear clear = (AltSvc.Clear)AltSvc.parseAltSvc("clear");
    assertNotNull(clear);
    AltSvc.ListOfValue values = (AltSvc.ListOfValue)AltSvc.parseAltSvc("abc=\":10\"" + "," + "abc=\":12\"");
    assertEquals(2, values.size());
    values = (AltSvc.ListOfValue)AltSvc.parseAltSvc("abc=\":10\"" + ",");
    assertNull(values);
    values = (AltSvc.ListOfValue)AltSvc.parseAltSvc("abc=\":10\"" + " a");
    assertNull(values);
  }
}
