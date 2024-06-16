/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.tests.file;

import io.vertx.test.core.TestUtils;
import org.junit.Assert;

import java.io.File;
import java.io.IOException;
import java.net.*;
import java.nio.file.Files;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

/**
 * Custom jar file resolution test, à la spring boot nested URLs: https://docs.spring.io/spring-boot/docs/current/reference/html/executable-jar.html#appendix.executable-jar.nested-jars
 *
 * what we are trying to resolve:
 * - BOOT-INF/classes -> jar:file:/path/to/file.jar!/BOOT-INF/classes/
 * - webroot/hello.txt -> jar:nested:/path/to/file.jar/!BOOT-INF/classes/!/webroot/hello.txt
 */
public class CustomJarFileResolverTest extends FileResolverTestBase {

  static File getFiles(File baseDir) throws Exception {
    File file = Files.createTempFile(TestUtils.MAVEN_TARGET_DIR.toPath(), "", "files.custom").toFile();
    Assert.assertTrue(file.delete());
    return ZipFileResolverTest.getFiles(
      baseDir,
      file,
      out -> {
        try {
          return new JarOutputStream(out);
        } catch (IOException e) {
          throw new AssertionError(e);
        }
      }, JarEntry::new);
  }

  @Override
  protected ClassLoader resourcesLoader(File baseDir) throws Exception {
    File files = getFiles(baseDir);
    return new ClassLoader() {
      @Override
      public URL getResource(String name) {
        try {
          try (JarFile jf = new JarFile(files)) {
            if (jf.getJarEntry(name) == null) {
              return super.getResource(name);
            }
          }
          return new URL("jar", "null" , -1, "custom:/whatever!/" + name, new URLStreamHandler() {
            @Override
            protected URLConnection openConnection(URL u) throws IOException {
              // Use file protocol here on purpose otherwise we would need to register the protocol
              return new JarURLConnection(new URL("jar:file:/whatever!/" + name)) {
                @Override
                public JarFile getJarFile() throws IOException {
                  return new JarFile(files);
                }

                @Override
                public int getContentLength() {
                  try {
                    return (int) getJarFile().getJarEntry(name).getSize();
                  } catch (IOException e) {
                    return -1;
                  }
                }

                @Override
                public void connect() throws IOException {
                }
              };
            }
          });
        } catch (Exception e) {
          return null;
        }
      }
    };
  }
}
