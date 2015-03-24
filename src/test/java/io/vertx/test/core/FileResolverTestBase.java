/*
 * Copyright (c) 2011-2014 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.test.core;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.impl.FileResolver;
import io.vertx.core.impl.VertxInternal;
import org.junit.Test;

import java.io.File;
import java.util.concurrent.CountDownLatch;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class FileResolverTestBase extends VertxTestBase {

  protected FileResolver resolver;

  protected String webRoot;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    resolver = new FileResolver(vertx);
  }

  @Override
  protected void tearDown() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    resolver.deleteCacheDir(onSuccess(res -> {
      latch.countDown();
    }));
    awaitLatch(latch);
    super.tearDown();
  }

  @Test
  public void testResolveNotExistFile() {
    File file = resolver.resolveFile("doesnotexist.txt");
    assertFalse(file.exists());
    assertEquals("doesnotexist.txt", file.getPath());
  }

  @Test
  public void testResolveNotExistDirectory() {
    File file = resolver.resolveFile("somedir");
    assertFalse(file.exists());
    assertEquals("somedir", file.getPath());
  }

  @Test
  public void testResolveNotExistFileInDirectory() {
    File file = resolver.resolveFile("somedir/doesnotexist.txt");
    assertFalse(file.exists());
    assertEquals("somedir" + File.separator + "doesnotexist.txt", file.getPath());
  }

  @Test
  public void testResolveFileFromClasspath() throws Exception {
    for (int i = 0; i < 2; i++) {
      File file = resolver.resolveFile("afile.html");
      assertTrue(file.exists());
      assertTrue(file.getPath().startsWith(".vertx" + File.separator + "file-cache-"));
      assertFalse(file.isDirectory());
      assertEquals("<html><body>afile</body></html>", readFile(file));
    }
  }

  @Test
  public void testResolveFileFromClasspathDisableCaching() throws Exception {
    System.setProperty(FileResolver.DISABLE_FILE_CACHING_PROP_NAME, "true");
    resolver = new FileResolver(vertx);
    for (int i = 0; i < 2; i++) {
      File file = resolver.resolveFile("afile.html");
      assertTrue(file.exists());
      assertTrue(file.getPath().startsWith(".vertx" + File.separator + "file-cache-"));
      assertFalse(file.isDirectory());
      assertEquals("<html><body>afile</body></html>", readFile(file));
    }
  }

  @Test
  public void testResolveFileWithSpacesFromClasspath() throws Exception {
    for (int i = 0; i < 2; i++) {
      File file = resolver.resolveFile("afile with spaces.html");
      assertTrue(file.exists());
      assertTrue(file.getPath().startsWith(".vertx" + File.separator + "file-cache-"));
      assertFalse(file.isDirectory());
      assertEquals("<html><body>afile with spaces</body></html>", readFile(file));
    }
  }

  @Test
  public void testResolveDirectoryFromClasspath() throws Exception {
    for (int i = 0; i < 2; i++) {
      File file = resolver.resolveFile(webRoot);
      assertTrue(file.exists());
      assertTrue(file.getPath().startsWith(".vertx" + File.separator + "file-cache-"));
      assertTrue(file.isDirectory());
    }
  }

  @Test
  public void testResolveFileInDirectoryFromClasspath() throws Exception {
    for (int i = 0; i < 2; i++) {
      File file = resolver.resolveFile(webRoot + "/somefile.html");
      assertTrue(file.exists());
      assertTrue(file.getPath().startsWith(".vertx" + File.separator + "file-cache-"));
      assertFalse(file.isDirectory());
      assertEquals("<html><body>blah</body></html>", readFile(file));
    }
  }

  @Test
  public void testResolveSubDirectoryFromClasspath() throws Exception {
    for (int i = 0; i < 2; i++) {
      File file = resolver.resolveFile(webRoot + "/subdir");
      assertTrue(file.exists());
      assertTrue(file.getPath().startsWith(".vertx" + File.separator + "file-cache-"));
      assertTrue(file.isDirectory());
    }
  }

  @Test
  public void testResolveFileInSubDirectoryFromClasspath() throws Exception {
    for (int i = 0; i < 2; i++) {
      File file = resolver.resolveFile(webRoot + "/subdir/subfile.html");
      assertTrue(file.exists());
      assertTrue(file.getPath().startsWith(".vertx" + File.separator + "file-cache-"));
      assertFalse(file.isDirectory());
      assertEquals("<html><body>subfile</body></html>", readFile(file));
    }
  }

  @Test
  public void testRecursivelyUnpack() throws Exception {
    File file = resolver.resolveFile(webRoot + "/subdir");
    assertTrue(file.exists());
    File sub = new File(new File(file, "subdir2"), "subfile2.html");
    assertTrue(sub.exists());
    assertEquals("<html><body>subfile2</body></html>", readFile(sub));
  }

  @Test
  public void testRecursivelyUnpack2() throws Exception {
    File file = resolver.resolveFile(webRoot + "/subdir");
    assertTrue(file.exists());
    File sub = new File(new File(file, "subdir2"), "subfile2.html");
    assertTrue(sub.exists());
    assertEquals("<html><body>subfile2</body></html>", readFile(sub));
  }

  @Test
  public void testDeleteCacheDir() throws Exception {
    Vertx vertx2 = Vertx.vertx();
    FileResolver resolver2 = new FileResolver(vertx2);
    File file = resolver2.resolveFile(webRoot + "/somefile.html");
    assertTrue(file.exists());
    File cacheDir = file.getParentFile().getParentFile();
    assertTrue(cacheDir.exists());
    resolver2.deleteCacheDir(onSuccess(res -> {
      assertFalse(cacheDir.exists());
      vertx2.close(res2 -> {
        testComplete();
      });
    }));
    await();
  }

  @Test
  public void testCacheDirDeletedOnVertxClose() {
    VertxInternal vertx2 = (VertxInternal)Vertx.vertx();
    File file = vertx2.resolveFile(webRoot + "/somefile.html");
    assertTrue(file.exists());
    File cacheDir = file.getParentFile().getParentFile();
    assertTrue(cacheDir.exists());
    vertx2.close(onSuccess(v -> {
      assertFalse(cacheDir.exists());
      testComplete();
    }));
    await();
  }

  @Test
  public void testFileSystemReadFile() {
    assertTrue(vertx.fileSystem().existsBlocking("afile.html"));
    assertFalse(vertx.fileSystem().propsBlocking("afile.html").isDirectory());
    Buffer buffer = vertx.fileSystem().readFileBlocking("afile.html");
    assertNotNull(buffer);
    assertTrue(buffer.toString().startsWith("<html><body>afile</body></html>"));
  }

  @Test
  public void testFileSystemReadDirectory() {
    assertTrue(vertx.fileSystem().existsBlocking("webroot"));
    assertTrue(vertx.fileSystem().propsBlocking("webroot").isDirectory());
  }

  @Test
  public void testSendFileFromClasspath() {
    vertx.createHttpServer(new HttpServerOptions().setPort(8080)).requestHandler(res -> {
      res.response().sendFile(webRoot + "/somefile.html");
    }).listen(onSuccess(res -> {
      vertx.createHttpClient(new HttpClientOptions()).request(HttpMethod.GET, 8080, "localhost", "/", resp -> {
        resp.bodyHandler(buff -> {
          assertTrue(buff.toString().startsWith("<html><body>blah</body></html>"));
          testComplete();
        });
      }).end();
    }));
    await();
  }

  private String readFile(File file) {
    return vertx.fileSystem().readFileBlocking(file.getAbsolutePath()).toString();
  }

}
