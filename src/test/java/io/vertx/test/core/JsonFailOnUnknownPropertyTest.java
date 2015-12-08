/*
 * Copyright (c) 2011-2014 The original author or authors
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

package io.vertx.test.core;

import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * @author  Mike Whitlaw
 */
public class JsonFailOnUnknownPropertyTest {


  private static class SourceClass {
    private String str1;
    private String str2;

    public String getStr1() {
      return str1;
    }

    public void setStr1(String str1) {
      this.str1 = str1;
    }

    public SourceClass withStr1(String str1) {
      this.setStr1(str1);
      return this;
    }

    public String getStr2() {
      return str2;
    }

    public void setStr2(String str2) {
      this.str2 = str2;
    }

    public SourceClass withStr2(String str2) {
      this.setStr2(str2);
      return this;
    }
  }

  private static class TargetClass {
    private String str1;

    public String getStr1() {
      return str1;
    }

    public void setStr1(String str1) {
      this.str1 = str1;
    }
    public TargetClass withStr1(String str1) {
      this.setStr1(str1);
      return this;
    }

  }

  @Test
  public void testDecodingJsonWithUnknownProperty() {
    String sourceJsonStr = Json.encode(new SourceClass().withStr1("v1").withStr2("v2"));
    TargetClass targetClass = Json.decodeValue(sourceJsonStr, TargetClass.class, false);
    assertNotNull(targetClass);
    assertEquals(targetClass.getStr1(), "v1");
    SourceClass sourceClass = Json.decodeValue(sourceJsonStr, SourceClass.class, false);
    assertNotNull(sourceClass);
    assertEquals(sourceClass.getStr1(), "v1");
    assertEquals(sourceClass.getStr2(), "v2");
    SourceClass sourceClass2 = Json.decodeValue(sourceJsonStr, SourceClass.class);
    assertNotNull(sourceClass2);
    assertEquals(sourceClass2.getStr1(), "v1");
    assertEquals(sourceClass2.getStr2(), "v2");
  }

  @Test
  public void testDecodingJsonWithUnknownPropertyFail() {
    String sourceJsonStr = Json.encode(new SourceClass().withStr1("v1").withStr2("v2"));
    try {
      Json.decodeValue(sourceJsonStr, TargetClass.class, true);
      fail();
    } catch (DecodeException e) {
      // OK
    }
  }

  @Test
  public void testDecodingJsonWithUnknownPropertyFailByDefault() {
    String sourceJsonStr = Json.encode(new SourceClass().withStr1("v1").withStr2("v2"));
    try {
      Json.decodeValue(sourceJsonStr, TargetClass.class);
      fail();
    } catch (DecodeException e) {
      // OK
    }
  }




}


