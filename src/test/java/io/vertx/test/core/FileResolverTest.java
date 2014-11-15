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

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class FileResolverTest extends VertxTestBase {

  FileResolver resolver = new FileResolver(vertx);
  String fileName1 = "webroot/somefile.html";
  String fileName2 = "webroot/someotherfile.html";

  @Test
  public void testResolveNotExist() {
    File file = resolver.resolveFile("doesnotexist.txt");
    assertFalse(file.exists());
    assertEquals("doesnotexist.txt", file.getPath());
  }

  @Test
  public void testResolveFromClasspath() throws Exception {
    File file = resolver.resolveFile(fileName1);
    assertTrue(file.exists());
    assertTrue(file.getPath().startsWith(".vertx/file-cache-"));
    // Resolve again
    File file2 = resolver.resolveFile(fileName1);
    assertTrue(file2.exists());
    assertTrue(file2.getPath().startsWith(".vertx/file-cache-"));
    // Resolve some other file
    File file3 = resolver.resolveFile(fileName2);
    assertTrue(file3.exists());
    assertTrue(file3.getPath().startsWith(".vertx/file-cache-"));
  }

  @Test
  public void testDeleteCacheDir() throws Exception {
    Vertx vertx2 = Vertx.vertx();
    FileResolver resolver2 = new FileResolver(vertx2);
    File file = resolver2.resolveFile(fileName1);
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
    File file = vertx2.resolveFile(fileName1);
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
  public void testReadFileFromClasspath() {
    Buffer buffer = vertx.fileSystem().readFileSync(fileName1);
    assertNotNull(buffer);
    assertTrue(buffer.toString().startsWith("<html><body>blah</body></html>"));
  }

  @Test
  public void testSendFileFromClasspath() {
    vertx.createHttpServer(new HttpServerOptions().setPort(8080)).requestHandler(res -> {
      res.response().sendFile(fileName1);
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
}
