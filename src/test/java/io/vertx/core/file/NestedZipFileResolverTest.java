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

import java.net.MalformedURLException;
import java.net.URL;

/**
 * @author <a href="http://www.ernestojpg.com">Ernesto J. Perez</a>
 */
public class NestedZipFileResolverTest extends FileResolverTestBase {

  private ClassLoader prevCL;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    // This folder is inside the embedded zip file called nested.zip, inside webroot6.zip
    webRoot = "webroot6";
    // We will store the current classloader
    prevCL = Thread.currentThread().getContextClassLoader();
    URL webroot6URL = prevCL.getResource("webroot6.zip");
    ClassLoader loader = new ClassLoader(prevCL = Thread.currentThread().getContextClassLoader()) {
      @Override
      public URL getResource(String name) {
        try {
          if (name.startsWith("lib/")) {
            return new URL("jar:" + webroot6URL + "!/" + name);
          } else if (name.startsWith("webroot6")) {
            return new URL("jar:" + webroot6URL + "!/lib/nested.zip!/" + name.substring(7));
          }
        } catch (MalformedURLException e) {
          throw new AssertionError(e);
        }
        return super.getResource(name);
      }
    };
    Thread.currentThread().setContextClassLoader(loader);
  }

  @Override
  public void after() throws Exception {
    if (prevCL != null) {
      Thread.currentThread().setContextClassLoader(prevCL);
    }
    super.after();
  }
}
