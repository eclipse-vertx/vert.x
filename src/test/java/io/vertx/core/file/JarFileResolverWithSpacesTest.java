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

package io.vertx.core.file;

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
