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

import io.vertx.test.core.TestUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/**
 * @author Clement Escoffier
 */
public class JarFileResolverWithSpacesTest extends FileResolverTestBase {

  @Override
  protected ClassLoader resourcesLoader(File baseDir) throws Exception {
    File dirWithSpaces = Files.createTempDirectory(TestUtils.MAVEN_TARGET_DIR.toPath(),  "dir with spaces").toFile();
    File files = ZipFileResolverTest.getFiles(
      baseDir,
      new File(dirWithSpaces, "files.jar"),
      out -> {
        try {
          return new JarOutputStream(out);
        } catch (IOException e) {
          throw new AssertionError(e);
        }
      }, JarEntry::new);
    return new URLClassLoader(new URL[]{files.toURI().toURL()}, Thread.currentThread().getContextClassLoader());
  }
}
