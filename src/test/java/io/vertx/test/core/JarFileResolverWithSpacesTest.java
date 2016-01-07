/*
 *  Copyright (c) 2011-2015 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *       The Eclipse Public License is available at
 *       http://www.eclipse.org/legal/epl-v10.html
 *
 *       The Apache License v2.0 is available at
 *       http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.test.core;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * @author Clement Escoffier
 */
public class JarFileResolverWithSpacesTest extends FileResolverTestBase {

  private ClassLoader original;

  @Override
  public void setUp() throws Exception {
    original = Thread.currentThread().getContextClassLoader();
    URLClassLoader someClassloader = new URLClassLoader(new URL[] { new File("src/test/resources/dir with " +
        "spaces/webroot3.jar").toURI().toURL()}, JarFileResolverWithSpacesTest.class.getClassLoader());
    Thread.currentThread().setContextClassLoader(someClassloader);
    super.setUp();
    // This is inside the jar webroot2.jar
    webRoot = "webroot3";
  }

  @Override
  public void tearDown() throws Exception {
    super.tearDown();
    Thread.currentThread().setContextClassLoader(original);
  }

}
