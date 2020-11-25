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
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JarFileResolverTest extends FileResolverTestBase {

  static File getFiles(File baseDir) throws Exception {
    return ZipFileResolverTest.getFiles(
      baseDir,
      new File("target", "files.jar"),
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
    return new URLClassLoader(new URL[]{files.toURI().toURL()}, Thread.currentThread().getContextClassLoader());
  }
}
