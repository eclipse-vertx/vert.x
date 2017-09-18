/*
 * Copyright (c) 2011-2017 The original author or authors
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     https://www.eclipse.org/legal/epl-2.0/
 *
 *     The Apache License v2.0 is available at
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.test.core;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class NestedJarFileResolverTest extends FileResolverTestBase {

  private ClassLoader prevCL;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    // This folder is inside the embedded jar file called nested.jar, inside webroot4.jar
    webRoot = "webroot4";

    prevCL = Thread.currentThread().getContextClassLoader();
    URL webroot4URL = prevCL.getResource("webroot4.jar");
    ClassLoader loader = new ClassLoader(prevCL = Thread.currentThread().getContextClassLoader()) {
      @Override
      public URL getResource(String name) {
        try {
          if (name.startsWith("lib/")) {
            return new URL("jar:" + webroot4URL + "!/" + name);
          } else if (name.startsWith("webroot4")) {
            return new URL("jar:" + webroot4URL + "!/lib/nested.jar!/" + name.substring(7));
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
