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

package io.vertx.tests.file;

import org.junit.Test;

import java.io.File;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class FileSystemFileResolverTest extends FileResolverTestBase {

  @Override
  protected ClassLoader resourcesLoader(File baseDir) throws Exception {
    return new URLClassLoader(new URL[]{new File(baseDir, "files").toURI().toURL()}, Thread.currentThread().getContextClassLoader());
  }

  @Test
  public void testResolvePlusSignsOnName() {
    File file = resolver.resolve("this+that");
    assertFalse(file.exists());
    assertEquals("this+that", file.getPath());
  }

  @Test
  public void testResolveInvalidFileName()  throws Exception{
    for (int i = 0;i < 256;i++) {
      String s = "file-" + (char) i + "-";
      File f = File.createTempFile("vertx", ".txt");
      Files.write(f.toPath(), "the_content".getBytes());
      Thread thread = Thread.currentThread();
      ClassLoader prev = thread.getContextClassLoader();
      ClassLoader next = new URLClassLoader(new URL[0], prev) {
        @Override
        public URL getResource(String name) {
          if (s.equals(name)) {
            try {
              return f.toURL();
            } catch (MalformedURLException e) {
              fail(e);
            }
          }
          return super.getResource(name);
        }
      };
      thread.setContextClassLoader(next);
      try {
        File file = resolver.resolve(s);
        assertNotNull(file);
      } finally {
        thread.setContextClassLoader(prev);
      }
    }
  }

  @Override
  public void testResolveFileWithSpaceAtEndFromClasspath() {
  }

  @Test
  public void testOverrideClassLoaderURL() throws Exception {
    File f = File.createTempFile("vertx", ".txt");
    Files.write(f.toPath(), "the_content".getBytes());
    URL url = f.toURI().toURL();
    Thread thread = Thread.currentThread();
    ClassLoader prev = thread.getContextClassLoader();
    ClassLoader next = new URLClassLoader(new URL[0], prev) {
      @Override
      public URL getResource(String name) {
        if ("a/a.txt".equals(name)) {
          return url;
        }
        return super.getResource(name);
      }
    };
    thread.setContextClassLoader(next);
    try {
      File file = resolver.resolve("a/a.txt");
      String content = Files.readString(file.toPath());
      assertEquals("the_content", content);
    } finally {
      thread.setContextClassLoader(prev);
    }

  }
}
