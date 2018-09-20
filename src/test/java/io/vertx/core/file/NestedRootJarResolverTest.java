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

import io.vertx.core.file.FileResolverTestBase;

import java.net.URL;
import java.net.URLClassLoader;

/**
 * @author Thomas Segismont
 */
public class NestedRootJarResolverTest extends FileResolverTestBase {

  private ClassLoader prevCL;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    // This folder is inside the nested-inf/classes directory, inside nestedroot.jar
    webRoot = "webroot2";

    prevCL = Thread.currentThread().getContextClassLoader();
    URL jarUrl = prevCL.getResource("nestedroot.jar");
    URL rootUrl = new URL("jar:" + jarUrl + "!/nested-inf/classes!/");
    URLClassLoader urlClassLoader = new URLClassLoader(new URL[]{rootUrl}, prevCL);
    Thread.currentThread().setContextClassLoader(urlClassLoader);
  }

  @Override
  public void after() throws Exception {
    if (prevCL != null) {
      Thread.currentThread().setContextClassLoader(prevCL);
    }
    super.after();
  }
}
