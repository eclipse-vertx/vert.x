/*
 * Copyright (c) 2011-2017 The original author or authors
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

package io.vertx.core.impl;

import org.junit.Test;

import java.io.FileNotFoundException;
import java.net.URL;

import static org.junit.Assert.*;

/**
 * @author Thomas Segismont
 */
public class ResolvConfTest {

  @Test
  public void testEnv() throws Exception {
    assertEquals(new ResolvConf(1, false), ResolvConf.load("debug", null));
    assertEquals(new ResolvConf(4, true), ResolvConf.load("rotate debug ndots:4", null));
    assertEquals(new ResolvConf(3, true), ResolvConf.load("rotate ndots:4 debug ndots:3", null));
    assertEquals(new ResolvConf(4, false), ResolvConf.load("ndots:4 debug", null));
    assertEquals(new ResolvConf(1, true), ResolvConf.load("debug rotate", null));
  }

  @Test
  public void testFile() throws Exception {
    assertEquals(new ResolvConf(1, false), ResolvConf.load(null, getPath("resolv-default")));
    assertEquals(new ResolvConf(4, true), ResolvConf.load(null, getPath("resolv-singleline")));
    assertEquals(new ResolvConf(4, true), ResolvConf.load(null, getPath("resolv-multiline")));
  }

  @Test
  public void testPrecedence() throws Exception {
    assertEquals(new ResolvConf(6, false), ResolvConf.load("debug ndots:6", getPath("resolv-multiline")));
  }

  private String getPath(String filename) throws FileNotFoundException {
    URL resource = getClass().getClassLoader().getResource(filename);
    if (resource == null) {
      throw new FileNotFoundException(filename);
    }
    return resource.getPath();
  }
}