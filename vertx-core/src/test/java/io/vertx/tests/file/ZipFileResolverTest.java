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

import io.vertx.core.buffer.Buffer;
import io.vertx.test.core.TestUtils;
import org.junit.Assert;
import org.junit.Test;

import java.io.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author <a href="http://www.ernestojpg.com">Ernesto J. Perez</a>
 */
public class ZipFileResolverTest extends FileResolverTestBase {

  static File getFiles(File baseDir) throws Exception {
    File file = Files.createTempFile(TestUtils.MAVEN_TARGET_DIR.toPath(), "", "files.jar").toFile();
    Assert.assertTrue(file.delete());
    return ZipFileResolverTest.getFiles(
      baseDir,
      file, ZipOutputStream::new, ZipEntry::new);
  }

  static File getFiles(File baseDir, File files, Function<OutputStream, ZipOutputStream> zipFact, Function<String, ZipEntry> entryFact) throws Exception {
    if (!files.exists()) {
      try (ZipOutputStream zip = zipFact.apply(new FileOutputStream(files))) {
        Path filesPath = new File(baseDir, "files").toPath();
        Files.walkFileTree(filesPath, new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            ZipEntry entry = entryFact.apply(TestUtils.getJarEntryName(filesPath.relativize(file)));
            zip.putNextEntry(entry);
            zip.write(Files.readAllBytes(file));
            zip.closeEntry();
            return FileVisitResult.CONTINUE;
          }
          @Override
          public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            String name = TestUtils.getJarEntryName(filesPath.relativize(dir));
            if (!name.isEmpty()) {
              ZipEntry entry = entryFact.apply(name + "/");
              zip.putNextEntry(entry);
              zip.closeEntry();
            }
            return FileVisitResult.CONTINUE;
          }
        });
        // Add file with space at end that cannot exist on the FS because of windows
        zip.putNextEntry(entryFact.apply("afilewithspaceatend "));
        zip.write("afilewithspaceatend ".getBytes());
        zip.closeEntry();
      }
    }
    return files;
  }

  @Test
  public void testFileResolverShouldNotUseCachedURLConnection() throws Exception {
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    InputStream in = cl.getResourceAsStream("afile.html");
    try {
      Buffer buffer = vertx.fileSystem().readFileBlocking("afile.html");
      byte[] tmp = new byte[256];
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      for (int amount = 0;amount != -1;amount = in.read(tmp)) {
        baos.write(tmp, 0, amount);
      }
      baos.close();
      assertEquals(baos.toString(), buffer.toString());
    } finally {
      in.close();
    }
  }

  @Override
  protected ClassLoader resourcesLoader(File baseDir) throws Exception {
    File files = getFiles(baseDir);
    return new URLClassLoader(new URL[]{files.toURI().toURL()}, Thread.currentThread().getContextClassLoader());
  }
}
