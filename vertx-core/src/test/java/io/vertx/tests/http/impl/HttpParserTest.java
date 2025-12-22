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

import io.vertx.core.http.impl.HttpParser;
import io.vertx.test.core.TestParser;
import org.junit.Test;

public class HttpParserTest {

  public static TestParser OWS = HttpParser::parseOWS;
  public static TestParser quotedString = HttpParser::parseQuotedString;
  public static TestParser token = HttpParser::parseToken;

  @Test
  public void testParseOWS() {
    OWS.assertParse("");
    OWS.assertParse(" \t");
    OWS.assertParse("\t ");
    OWS.assertParse("\t");
  }

  @Test
  public void testParseQuotedString() {
    quotedString.assertParse("\"ABC\"");
    quotedString.assertParse("\"\t\"");
    quotedString.assertParse("\" \"");
    quotedString.assertParse("\"!\"");
    for (char c = '\u005D';c <= '\u007E';c++) {
      quotedString.assertParse("\"" + c + "\"");
    }
    for (char c = '\u0080';c <= '\u00FF';c++) {
      quotedString.assertParse("\"" + c + "\"");
    }
    quotedString.assertParse("\"\\\t\"");
    quotedString.assertParse("\"\\ \"");
    for (char c = '\u0021';c <= '\u007E';c++) {
      quotedString.assertParse("\"\\" + c + "\"");
    }
    for (char c = '\u0080';c <= '\u00FF';c++) {
      quotedString.assertParse("\"\\" + c + "\"");
    }
  }

  @Test
  public void testParseToken() {
    token.assertParse("!");
    token.assertParse("#");
    token.assertParse("$");
    token.assertParse("%");
    token.assertParse("&");
    token.assertParse("'");
    token.assertParse("*");
    token.assertParse("+");
    token.assertParse("-");
    token.assertParse(".");
    token.assertParse("^");
    token.assertParse("_");
    token.assertParse("`");
    token.assertParse("|");
    token.assertParse("~");
    for  (char c = '0';c <= '9';c++) {
      token.assertParse("" + c);
    }
    for  (char c = 'A';c <= 'Z';c++) {
      token.assertParse("" + c);
    }
    for  (char c = 'a';c <= 'z';c++) {
      token.assertParse("" + c);
    }
    token.assertFailParse("");
    token.assertFailParse(" ");
    token.assertFailParse("?");
  }
}
