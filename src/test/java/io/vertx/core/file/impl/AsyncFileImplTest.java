/*
 * Copyright (c) 2011-2017 The original author or authors
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

package io.vertx.core.file.impl;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import io.vertx.core.Vertx;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.VertxInternal;
import io.vertx.test.core.TestUtils;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author Thomas Segismont
 */
public class AsyncFileImplTest extends VertxTestBase {

  @Test
  public void testWriteStreamError() throws Exception {
    FileSystem fs = Jimfs.newFileSystem(Configuration.unix().toBuilder()
      .setBlockSize(8 * 1024)
      .setMaxSize(32 * 1024)
      .build());
    Path foo = fs.getPath("/foo");
    Files.createDirectory(foo);
    Path hello = foo.resolve("bar");

    VertxInternal vertx = (VertxInternal) Vertx.vertx();
    ContextImpl context = vertx.getOrCreateContext();

    AsyncFileImpl asyncFile = new AsyncFileImpl(vertx, hello.toUri(), new OpenOptions(), context);

    asyncFile.write(TestUtils.randomBuffer(64 * 1024), 0, ar -> {
      if (ar.failed()) {
        complete();
      } else {
        fail("Should not succeed");
      }
    });

    await();
  }
}