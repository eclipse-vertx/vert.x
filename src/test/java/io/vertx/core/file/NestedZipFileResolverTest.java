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
import java.io.FileOutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/**
 * @author <a href="http://www.ernestojpg.com">Ernesto J. Perez</a>
 */
public class NestedZipFileResolverTest extends FileResolverTestBase {

  @Override
  protected ClassLoader resourcesLoader(File baseDir) throws Exception {
    File nestedFiles = new File("target/nested-files.zip");
    if (!nestedFiles.exists()) {
      File files = ZipFileResolverTest.getFiles(baseDir);
      try (JarOutputStream jar = new JarOutputStream(new FileOutputStream(nestedFiles))) {
        jar.putNextEntry(new JarEntry("lib/"));
        jar.closeEntry();
        jar.putNextEntry(new JarEntry("lib/nested.zip"));
        jar.write(Files.readAllBytes(files.toPath()));
        jar.closeEntry();
      }
    }
    URL webrootURL = nestedFiles.toURI().toURL();
    return new ClassLoader(Thread.currentThread().getContextClassLoader()) {
      @Override
      public URL getResource(String name) {
        try {
          if (name.startsWith("lib/")) {
            return new URL("jar:" + webrootURL + "!/" + name);
          } else if (name.startsWith("webroot")) {
            return new URL("jar:" + webrootURL + "!/lib/nested.zip!/" + name.substring(7));
          } else if (name.equals("afile.html")) {
            return new URL("jar:" + webrootURL + "!/lib/nested.zip!afile.html/");
          } else if (name.equals("afile with spaces.html")) {
            return new URL("jar:" + webrootURL + "!/lib/nested.zip!afile with spaces.html/");
          } else if (name.equals("afilewithspaceatend ")) {
            return new URL("jar:" + webrootURL + "!/lib/nested.zip!afilewithspaceatend /");
          }
        } catch (MalformedURLException e) {
          throw new AssertionError(e);
        }
        return super.getResource(name);
      }
    };
  }
}
