/*
 * Copyright (c) 2011-2022 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.file;

import io.vertx.test.core.TestUtils;
import org.junit.Assert;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author Thomas Segismont
 */
public class NestedRootJarResolverTest extends FileResolverTestBase {

  @Override
  protected ClassLoader resourcesLoader(File baseDir) throws Exception {
    File nestedFiles = Files.createTempFile(TestUtils.MAVEN_TARGET_DIR.toPath(), "", "nestedroot.jar").toFile();
    Assert.assertTrue(nestedFiles.delete());
    ZipFileResolverTest.getFiles(baseDir, nestedFiles, ZipOutputStream::new, name -> new ZipEntry("nested-inf/classes/" + name));
    URL webrootURL = nestedFiles.toURI().toURL();
    return new ClassLoader(Thread.currentThread().getContextClassLoader()) {
      @Override
      public URL getResource(String name) {
        try {
          if (name.startsWith("webroot")) {
            return new URL("jar:" + webrootURL + "!/nested-inf/classes!/" + name);
          } else if (name.equals("afile.html") || name.equals("afile with spaces.html") || name.equals("afilewithspaceatend ")) {
            return new URL("jar:" + webrootURL + "!/nested-inf/classes!/" + name);
          }
        } catch (MalformedURLException e) {
          throw new AssertionError(e);
        }
        return super.getResource(name);
      }
    };
  }

}
