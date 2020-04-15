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

import org.junit.Ignore;
import org.junit.Test;

import java.io.File;

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
  public void setUp() throws Exception {
    super.setUp();
    webRoot = "webroot";
  }

  @Test
  public void testResolvePlusSignsOnName() {
    File file = resolver.resolveFile("this+that");
    assertFalse(file.exists());
    assertEquals("this+that", file.getPath());
  }

  @Test
  public void testResolveInvalidFileName()  throws Exception{
    for (int i = 0;i < 256;i++) {
      String s = "file-" + (char) i;
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
        File file = resolver.resolveFile(s);
        assertNotNull(file);
      } finally {
        thread.setContextClassLoader(prev);
      }
    }
  }

  @Ignore
  @Test
  public void testResolveAbsoluteFile() throws Exception {
    File target = new File("target");
    File folder = Files.createTempDirectory(target.toPath(), "testResolveAbsoluteFile").toFile();
    File f = new File(folder, "child.txt");
    Files.write(f.toPath(), "the_child".getBytes());
    File notFound = new File(folder, "not_found");
    Thread thread = Thread.currentThread();
    ClassLoader prev = thread.getContextClassLoader();
    ClassLoader next = new URLClassLoader(new URL[0], prev) {
      @Override
      public URL getResource(String name) {
        File f = new File(name);
        if (f.exists()) {
          try {
            return f.toURI().toURL();
          } catch (MalformedURLException e) {
            fail(e);
          }
        }
        return super.getResource(name);
      }
    };
    thread.setContextClassLoader(next);
    try {
      File file = resolver.resolveFile(notFound.getAbsolutePath());
      assertFalse(file.exists());
    } finally {
      thread.setContextClassLoader(prev);
    }
  }
}
