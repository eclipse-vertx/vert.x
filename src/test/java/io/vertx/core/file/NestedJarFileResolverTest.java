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

package io.vertx.core.file;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class NestedJarFileResolverTest extends FileResolverTestBase {

  @Override
  protected ClassLoader resourcesLoader(File baseDir) throws Exception {
    URL webroot4URL = new File(baseDir, "nested-files.jar").toURI().toURL();
    return new ClassLoader(Thread.currentThread().getContextClassLoader()) {
      @Override
      public URL getResource(String name) {
        try {
          if (name.startsWith("lib/")) {
            return new URL("jar:" + webroot4URL + "!/" + name);
          } else if (name.startsWith("webroot")) {
            return new URL("jar:" + webroot4URL + "!/lib/nested.jar!/" + name.substring(7));
          } else if (name.equals("afile.html")) {
            return new URL("jar:" + webroot4URL + "!/lib/nested.jar!afile.html/");
          } else if (name.equals("afile with spaces.html")) {
            return new URL("jar:" + webroot4URL + "!/lib/nested.jar!afile with spaces.html/");
          }
        } catch (MalformedURLException e) {
          throw new AssertionError(e);
        }
        return super.getResource(name);
      }
    };
  }
}
