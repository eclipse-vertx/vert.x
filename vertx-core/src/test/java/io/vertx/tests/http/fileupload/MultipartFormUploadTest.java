/*
 * Copyright 2014 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */
package io.vertx.tests.http.fileupload;

import io.netty.handler.codec.http.multipart.HttpPostRequestEncoder;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.ClientForm;
import io.vertx.core.http.ClientMultipartForm;
import io.vertx.core.http.impl.ClientMultipartFormImpl;
import io.vertx.core.http.impl.ClientMultipartFormUpload;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.test.core.TestUtils;
import io.vertx.test.http.HttpTestBase;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assume.assumeTrue;

public class MultipartFormUploadTest extends HttpTestBase {

  @ClassRule
  public static TemporaryFolder testFolder = new TemporaryFolder();

  private VertxInternal vertx;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    vertx = (VertxInternal) Vertx.vertx();
  }

  @Test
  public void testSimpleAttribute() throws Exception {
    Buffer result = Buffer.buffer();
    ContextInternal context = vertx.getOrCreateContext();
    ClientMultipartFormUpload upload = new ClientMultipartFormUpload(context, (ClientMultipartFormImpl) ClientForm.form().attribute("foo", "bar"), false, HttpPostRequestEncoder.EncoderMode.RFC1738);
    upload.endHandler(v -> {
      assertEquals("foo=bar", result.toString());
      testComplete();
    });
    upload.handler(result::appendBuffer);
    upload.resume();
    context.runOnContext(v -> upload.pump());
  }

  @Test
  public void testFileUploadEventLoopContext() throws Exception {
    testFileUpload(vertx.createEventLoopContext(), false);
  }

  @Test
  public void testFileUploadWorkerContext() throws Exception {
    testFileUpload(vertx.createWorkerContext(), false);
  }

  @Test
  public void testFileUploadVirtualThreadContext() throws Exception {
    assumeTrue(vertx.isVirtualThreadAvailable());
    testFileUpload(vertx.createVirtualThreadContext(), false);
  }

  @Test
  public void testFileUploadPausedEventLoopContext() throws Exception {
    testFileUpload(vertx.createEventLoopContext(), true);
  }

  @Test
  public void testFileUploadPausedWorkerContext() throws Exception {
    testFileUpload(vertx.createWorkerContext(), true);
  }

  @Test
  public void testFileUploadPausedVirtualThreadContext() throws Exception {
    assumeTrue(vertx.isVirtualThreadAvailable());
    testFileUpload(vertx.createVirtualThreadContext(), true);
  }

  private void testFileUpload(ContextInternal context, boolean paused) throws Exception {
    File file = testFolder.newFile();
    Files.write(file.toPath(), TestUtils.randomByteArray(32 * 1024));

    String filename = file.getName();
    String pathname = file.getAbsolutePath();

    context.runOnContext(v1 -> {
      try {
        ClientMultipartFormUpload upload = new ClientMultipartFormUpload(context, (ClientMultipartFormImpl) ClientMultipartForm
          .multipartForm()
          .textFileUpload("the-file", filename, "text/plain", pathname)
          , true, HttpPostRequestEncoder.EncoderMode.RFC1738);
        List<Buffer> buffers = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger end = new AtomicInteger();
        upload.endHandler(v2 -> {
          assertEquals(0, end.getAndIncrement());
          assertFalse(buffers.isEmpty());
          testComplete();
        });
        upload.handler(buffer -> {
          assertEquals(0, end.get());
          buffers.add(buffer);
        });
        if (!paused) {
          upload.resume();
        }
        upload.pump();
        if (paused) {
          context.runOnContext(v3 -> upload.resume());
        }
      } catch (Exception e) {
        fail(e);
      }
    });
  }
}
