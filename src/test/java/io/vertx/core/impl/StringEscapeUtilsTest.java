/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Modified from original form by Tim Fox
 */
package io.vertx.core.impl;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class StringEscapeUtilsTest {

  @Rule
  public final ExpectedException thrown = ExpectedException.none();

  @Test
  public void testEscapeJava() throws Exception {
    Assert.assertNull(StringEscapeUtils.escapeJava(null));

    Assert.assertEquals("\\\\f'o\\\"o\\rb\\fa\\t\\nr\\bb/a",
      StringEscapeUtils.escapeJava("\\f\'o\"o\rb\fa\t\nr\bb/a"));
    Assert.assertEquals("\\uFFFF\\u0FFF\\u00FF\\u000F\\u0010",
      StringEscapeUtils.escapeJava("\uffff\u0fff\u00ff\u000f\u0010"));
  }

  @Test
  public void testEscapeJavaThrowException() throws IOException {
    thrown.expect(IllegalArgumentException.class);
    StringEscapeUtils.escapeJava(null, "foo");
  }

  @Test
  public void testEscapeJavaScript() throws Exception {
    Assert.assertNull(StringEscapeUtils.escapeJavaScript(null));

    Assert.assertEquals("\\\\f\\'o\\\"o\\rb\\fa\\t\\nr\\bb\\/a",
      StringEscapeUtils.escapeJavaScript("\\f\'o\"o\rb\fa\t\nr\bb/a"));
    Assert.assertEquals("\\uFFFF\\u0FFF\\u00FF\\u000F\\u0010",
      StringEscapeUtils.escapeJavaScript("\uffff\u0fff\u00ff\u000f\u0010"));
  }

  @Test
  public void testEscapeJavaScriptThrowException() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    StringEscapeUtils.escapeJavaScript(null, "foo");
  }

  @Test
  public void testUnescapeJava() throws Exception {
    Assert.assertNull(StringEscapeUtils.unescapeJava(null));

    Assert.assertEquals("foo", StringEscapeUtils.unescapeJava("foo"));
    Assert.assertEquals("\\", StringEscapeUtils.unescapeJava("\\"));
    Assert.assertEquals("\\", StringEscapeUtils.unescapeJava("\\\\"));
    Assert.assertEquals("\'", StringEscapeUtils.unescapeJava("\\\'"));
    Assert.assertEquals("\"", StringEscapeUtils.unescapeJava("\\\""));
    Assert.assertEquals("\r", StringEscapeUtils.unescapeJava("\\r"));
    Assert.assertEquals("\f", StringEscapeUtils.unescapeJava("\\f"));
    Assert.assertEquals("\t", StringEscapeUtils.unescapeJava("\\t"));
    Assert.assertEquals("\n", StringEscapeUtils.unescapeJava("\\n"));
    Assert.assertEquals("\b", StringEscapeUtils.unescapeJava("\\b"));
    Assert.assertEquals("a", StringEscapeUtils.unescapeJava("\\a"));
    Assert.assertEquals("\uffff", StringEscapeUtils.unescapeJava("\\uffff"));
  }

  @Test
  public void testUnescapeJavaScript() throws Exception {
    Assert.assertNull(StringEscapeUtils.unescapeJavaScript(null));

    Assert.assertEquals("foo", StringEscapeUtils.unescapeJavaScript("foo"));
    Assert.assertEquals("\\", StringEscapeUtils.unescapeJavaScript("\\"));
    Assert.assertEquals("\\", StringEscapeUtils.unescapeJavaScript("\\\\"));
    Assert.assertEquals("\'", StringEscapeUtils.unescapeJavaScript("\\\'"));
    Assert.assertEquals("\"", StringEscapeUtils.unescapeJavaScript("\\\""));
    Assert.assertEquals("\r", StringEscapeUtils.unescapeJavaScript("\\r"));
    Assert.assertEquals("\f", StringEscapeUtils.unescapeJavaScript("\\f"));
    Assert.assertEquals("\t", StringEscapeUtils.unescapeJavaScript("\\t"));
    Assert.assertEquals("\n", StringEscapeUtils.unescapeJavaScript("\\n"));
    Assert.assertEquals("\b", StringEscapeUtils.unescapeJavaScript("\\b"));
    Assert.assertEquals("a", StringEscapeUtils.unescapeJavaScript("\\a"));
    Assert.assertEquals("\uffff", StringEscapeUtils.unescapeJavaScript("\\uffff"));
  }

  @Test
  public void testUnescapeJavaScriptThrowException() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    StringEscapeUtils.unescapeJavaScript(null, "foo");
  }
}
