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
package io.vertx.core.http;

import io.vertx.core.http.impl.HttpUtils;
import org.junit.Test;

import java.net.URI;

import static org.junit.Assert.*;

public class HttpUtilsTest {

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

  @Test
  public void testResolveUri() throws Exception {
    assertResolveUri("http://a/b/c/g", "http://a/b/c/d;p?q", "g");
    assertResolveUri("http://a/b/c/g", "http://a/b/c/d;p?q", "./g");
    assertResolveUri("http://a/b/c/g/", "http://a/b/c/d;p?q", "g/");
    assertResolveUri("http://a/g", "http://a/b/c/d;p?q", "/g");
    assertResolveUri("http://g", "http://a/b/c/d;p?q", "//g");
    assertResolveUri("http://a/b/c/d;p?y", "http://a/b/c/d;p?q", "?y");
    assertResolveUri("http://a/b/c/g?y", "http://a/b/c/d;p?q", "g?y");
    assertResolveUri("http://a/b/c/d;p?q#s", "http://a/b/c/d;p?q", "#s");
    assertResolveUri("http://a/b/c/g#s", "http://a/b/c/d;p?q", "g#s");
    assertResolveUri("http://a/b/c/;x", "http://a/b/c/d;p?q", ";x");
    assertResolveUri("http://a/b/c/g;x", "http://a/b/c/d;p?q", "g;x");
    assertResolveUri("http://a/b/c/g;x?y#s", "http://a/b/c/d;p?q", "g;x?y#s");
    assertResolveUri("http://a/b/c/d;p?q", "http://a/b/c/d;p?q", "");
    assertResolveUri("http://a/b/c/", "http://a/b/c/d;p?q", ".");
    assertResolveUri("http://a/b/c/", "http://a/b/c/d;p?q", "./");
    assertResolveUri("http://a/b/", "http://a/b/c/d;p?q", "..");
    assertResolveUri("http://a/", "http://a/b/c/d;p?q", "../..");
    assertResolveUri("http://a/", "http://a/b/c/d;p?q", "../../");
    assertResolveUri("http://a/g", "http://a/b/c/d;p?q", "../../g");
    assertResolveUri("http://a/g", "http://a/b/c/d;p?q", "../../../g");
    assertResolveUri("http://a/g", "http://a/b/c/d;p?q", "../../../../g");
    assertResolveUri("http://example.com/path", "https://example.com/path", "http://example.com/path");
    assertResolveUri("https://example.com/relativeUrl", "https://example.com/path?q=2", "/relativeUrl");
    assertResolveUri("https://example.com/path?q=2#test", "https://example.com/path?q=2", "#test"); // correct ?
    assertResolveUri("https://example.com/relativePath?q=3", "https://example.com/path?q=2", "/relativePath?q=3");
    assertResolveUri("https://example.com/path?q=3", "https://example.com/path?q=2", "?q=3"); // correct ?
  }

  @Test
  public void testNoLeadingSlash() throws Exception {
    assertEquals("/path/with/no/leading/slash", HttpUtils.normalizePath("path/with/no/leading/slash"));
  }

  @Test
  public void testNullPath() throws Exception {
    assertNull(HttpUtils.normalizePath(null));
  }

  @Test
  public void testPathWithSpaces1() throws Exception {
    // this is a special case since only percent encoded values should be unescaped from the path
    assertEquals("/foo+blah/eek", HttpUtils.normalizePath("/foo+blah/eek"));
  }

  @Test
  public void testPathWithSpaces2() throws Exception {
    assertEquals("/foo%20blah/eek", HttpUtils.normalizePath("/foo%20blah/eek"));
  }

  @Test
  public void testDodgyPath1() throws Exception {
    assertEquals("/blah", HttpUtils.normalizePath("/foo/../../blah"));
  }

  @Test
  public void testDodgyPath2() throws Exception {
    assertEquals("/blah", HttpUtils.normalizePath("/foo/../../../blah"));
  }

  @Test
  public void testDodgyPath3() throws Exception {
    assertEquals("/blah", HttpUtils.normalizePath("/foo/../blah"));
  }

  @Test
  public void testDodgyPath4() throws Exception {
    assertEquals("/blah", HttpUtils.normalizePath("/../blah"));
  }

  @Test
  public void testMultipleSlashPath1() throws Exception {
    assertEquals("/blah", HttpUtils.normalizePath("//blah"));
  }

  @Test
  public void testMultipleSlashPath2() throws Exception {
    assertEquals("/blah", HttpUtils.normalizePath("///blah"));
  }

  @Test
  public void testMultipleSlashPath3() throws Exception {
    assertEquals("/foo/blah", HttpUtils.normalizePath("/foo//blah"));
  }

  @Test
  public void testMultipleSlashPath4() throws Exception {
    assertEquals("/foo/blah/", HttpUtils.normalizePath("/foo//blah///"));
  }

  @Test
  public void testSlashesAndDodgyPath1() throws Exception {
    assertEquals("/blah", HttpUtils.normalizePath("//../blah"));
  }

  @Test
  public void testSlashesAndDodgyPath2() throws Exception {
    assertEquals("/blah", HttpUtils.normalizePath("/..//blah"));
  }

  @Test
  public void testSlashesAndDodgyPath3() throws Exception {
    assertEquals("/blah", HttpUtils.normalizePath("//..//blah"));
  }

  @Test
  public void testDodgyPathEncoded() throws Exception {
    assertEquals("/..%2Fblah", HttpUtils.normalizePath("/%2E%2E%2Fblah"));
  }

  @Test
  public void testTrailingSlash() throws Exception {
    assertEquals("/blah/", HttpUtils.normalizePath("/blah/"));
  }

  @Test
  public void testMultipleTrailingSlashes1() throws Exception {
    assertEquals("/blah/", HttpUtils.normalizePath("/blah//"));
  }

  @Test
  public void testMultipleTrailingSlashes2() throws Exception {
    assertEquals("/blah/", HttpUtils.normalizePath("/blah///"));
  }

  @Test
  public void testBadURL() throws Exception {
    try {
      HttpUtils.normalizePath("/%7B%channel%%7D");
      fail();
    } catch (IllegalArgumentException e) {
      // expected!
    }
  }

  @Test
  public void testDoubleDot() throws Exception {
    assertEquals("/foo/bar/abc..def", HttpUtils.normalizePath("/foo/bar/abc..def"));
  }

  @Test
  public void testSpec() throws Exception {
    assertEquals("/a/g", HttpUtils.normalizePath("/a/b/c/./../../g"));
    assertEquals("/mid/6", HttpUtils.normalizePath("mid/content=5/../6"));
    assertEquals("/~username/", HttpUtils.normalizePath("/%7Eusername/"));
    assertEquals("/b/", HttpUtils.normalizePath("/b/c/.."));
  }

  private void assertResolveUri(String expected, String base, String rel) throws Exception {
    URI resolved = HttpUtils.resolveURIReference(base, rel);
    assertEquals(URI.create(expected), resolved);
  }

  @Test
  public void testResolveURIEncode() throws Exception {
    check("https://foo.com", "/%7E", "/%7E");
    check("https://foo.com", "/%7E/", "/%7E/");
    check("https://foo.com", "%7E/", "/%7E/");
    check("https://foo.com/A", "%7E/", "/%7E/");
    check("https://foo.com/%6E/", "%7E/", "/%6E/%7E/");
    check("https://foo.com", "https://bar.com/%7E", "/%7E");
    check("https://foo.com", "https://bar.com/%7E/", "/%7E/");
    check("https://foo.com/%6E", "", "/%6E");
    check("https://foo.com/%6E/", "", "/%6E/");
  }

  private void check(String base, String ref, String expected) throws Exception {
    URI uri = HttpUtils.resolveURIReference(base, ref);
    assertEquals(expected, uri.getPath());
  }

}
