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
package io.vertx.test.core;

import io.vertx.core.http.impl.HttpUtils;
import org.junit.Test;

import java.net.URI;

import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class URITest {

  @Test
  public void testResolve() throws Exception {
    assertResolve("http://a/b/c/g", "http://a/b/c/d;p?q", "g");
    assertResolve("http://a/b/c/g", "http://a/b/c/d;p?q", "./g");
    assertResolve("http://a/b/c/g/", "http://a/b/c/d;p?q", "g/");
    assertResolve("http://a/g", "http://a/b/c/d;p?q", "/g");
    assertResolve("http://g", "http://a/b/c/d;p?q", "//g");
    assertResolve("http://a/b/c/d;p?y", "http://a/b/c/d;p?q", "?y");
    assertResolve("http://a/b/c/g?y", "http://a/b/c/d;p?q", "g?y");
    assertResolve("http://a/b/c/d;p?q#s", "http://a/b/c/d;p?q", "#s");
    assertResolve("http://a/b/c/g#s", "http://a/b/c/d;p?q", "g#s");
    assertResolve("http://a/b/c/;x", "http://a/b/c/d;p?q", ";x");
    assertResolve("http://a/b/c/g;x", "http://a/b/c/d;p?q", "g;x");
    assertResolve("http://a/b/c/g;x?y#s", "http://a/b/c/d;p?q", "g;x?y#s");
    assertResolve("http://a/b/c/d;p?q", "http://a/b/c/d;p?q", "");
    assertResolve("http://a/b/c/", "http://a/b/c/d;p?q", ".");
    assertResolve("http://a/b/c/", "http://a/b/c/d;p?q", "./");
    assertResolve("http://a/b/", "http://a/b/c/d;p?q", "..");
    assertResolve("http://a/", "http://a/b/c/d;p?q", "../..");
    assertResolve("http://a/", "http://a/b/c/d;p?q", "../../");
    assertResolve("http://a/g", "http://a/b/c/d;p?q", "../../g");
    assertResolve("http://a/g", "http://a/b/c/d;p?q", "../../../g");
    assertResolve("http://a/g", "http://a/b/c/d;p?q", "../../../../g");
    assertResolve("http://example.com/path", "https://example.com/path", "http://example.com/path");
    assertResolve("https://example.com/relativeUrl", "https://example.com/path?q=2", "/relativeUrl");
    assertResolve("https://example.com/path?q=2#test", "https://example.com/path?q=2", "#test"); // correct ?
    assertResolve("https://example.com/relativePath?q=3", "https://example.com/path?q=2", "/relativePath?q=3");
    assertResolve("https://example.com/path?q=3", "https://example.com/path?q=2", "?q=3"); // correct ?
  }

  private void assertResolve(String expected, String base, String rel) throws Exception {
    URI resolved = HttpUtils.resolveURIReference(base, rel);
    assertEquals(URI.create(expected), resolved);
  }
}
