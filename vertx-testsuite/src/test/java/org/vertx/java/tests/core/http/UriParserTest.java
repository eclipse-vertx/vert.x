/*
 * Copyright (c) 2014 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */
package org.vertx.java.tests.core.http;

import org.junit.Test;
import org.vertx.java.core.http.impl.UriParser;
import org.vertx.java.testframework.TestBase;

/**
 * Unit tests for {@link org.vertx.java.core.http.impl.UriParser}
 */
public class UriParserTest extends TestBase {

  @Test
  public void testPathAbsoluteUri() throws Exception {

    String uri = "http://test.org/expected/path?a=b&c=d";
    String path;
    String expected = "/expected/path";

    path = UriParser.path(uri);
    assertEquals(expected, path);

  }

  @Test
  public void testPathAbsoluteUriNoQuery() throws Exception {

    String uri = "http://test.org/expected/path";
    String path;
    String expected = "/expected/path";

    path = UriParser.path(uri);
    assertEquals(expected, path);

  }

  @Test
  public void testPathAbsoluteUriHostOnly() throws Exception {

    String uri = "http://test.org?a=b";
    String path;
    String expected = "/";

    path = UriParser.path(uri);
    assertEquals(expected, path);

  }

  @Test
  public void testPathAbsoluteUriWithUnencodedUriQuery() throws Exception {

    String uri = "http://test.org/expected/path?a=http://test.org/wrong/path&c=d";
    String path;
    String expected = "/expected/path";

    path = UriParser.path(uri);
    assertEquals(expected, path);

  }

  @Test
  public void testPathRelativeUri() throws Exception {

    String uri = "/expected/path?a=b&c=d";
    String path;
    String expected = "/expected/path";

    path = UriParser.path(uri);
    assertEquals(expected, path);

  }

  @Test
  public void testPathRelativeUriNoQuery() throws Exception {

    String uri = "/expected/path?a=b&c=d";
    String path;
    String expected = "/expected/path";

    path = UriParser.path(uri);
    assertEquals(expected, path);

  }

  @Test
  public void testPathRelativeUriWithUnencodedUriQuery() throws Exception {

    String uri = "/expected/path?a=http://test.org/wrong/path&c=d";
    String path;
    String expected = "/expected/path";

    path = UriParser.path(uri);
    assertEquals(expected, path);

  }

  @Test
  public void testPathOneLevelUpRelativeUri() throws Exception {

    String uri = "../expected/path?a=b&c=d";
    String path;
    String expected = "../expected/path";

    path = UriParser.path(uri);
    assertEquals(expected, path);

  }

  @Test
  public void testPathOneSegmentRelativeUri() throws Exception {

    String uri = "expected?a=b&c=d";
    String path;
    String expected = "expected";

    path = UriParser.path(uri);
    assertEquals(expected, path);

  }

  @Test
  public void testQuery() throws Exception {

    String uri = "/path?query=a";
    String expected = "query=a";
    String query;

    query = UriParser.query(uri);
    assertEquals(expected, query);

  }

  @Test
  public void testQueryNoQuery() throws Exception {

    String uri = "/path";
    String query;

    query = UriParser.query(uri);
    assertNull(query);

  }

}
