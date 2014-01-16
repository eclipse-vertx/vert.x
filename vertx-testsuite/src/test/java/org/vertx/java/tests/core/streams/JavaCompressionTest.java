/*
 * Copyright (c) 2011-2013 The original author or authors
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
package org.vertx.java.tests.core.streams;

import org.junit.Assert;
import org.junit.Test;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.streams.Compression;
import org.vertx.java.core.streams.ReadStream;
import org.vertx.java.core.streams.WriteStream;
import org.vertx.java.testframework.TestUtils;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class JavaCompressionTest {

  @Test
  public void testZlib() {
    FakeReadStream readStream = new FakeReadStream();
    FakeWriteStream writeStream = new FakeWriteStream();

    ReadStream<?> compressedReadStream = Compression.zlib(readStream);
    WriteStream<?> compressedWriteStream = Compression.zlib(writeStream);
    testCompression(readStream, writeStream, compressedReadStream, compressedWriteStream);
  }

  @Test
  public void testZlibWithCompressionLevel() {
    FakeReadStream readStream = new FakeReadStream();
    FakeWriteStream writeStream = new FakeWriteStream();

    ReadStream<?> compressedReadStream = Compression.zlib(readStream);
    WriteStream<?> compressedWriteStream = Compression.zlib(writeStream, 9);
    testCompression(readStream, writeStream, compressedReadStream, compressedWriteStream);
  }


  @Test
  public void testZlibCorrupt() {
    FakeReadStream readStream = new FakeReadStream();
    ReadStream<?> compressedReadStream = Compression.zlib(readStream);
    testCompressionCorrupt(readStream, compressedReadStream);
  }

  @Test
  public void testGzip() {
    FakeReadStream readStream = new FakeReadStream();
    FakeWriteStream writeStream = new FakeWriteStream();

    ReadStream<?> compressedReadStream = Compression.gzip(readStream);
    WriteStream<?> compressedWriteStream = Compression.gzip(writeStream);
    testCompression(readStream, writeStream, compressedReadStream, compressedWriteStream);
  }

  @Test
  public void testGzipWithCompressionLevel() {
    FakeReadStream readStream = new FakeReadStream();
    FakeWriteStream writeStream = new FakeWriteStream();

    ReadStream<?> compressedReadStream = Compression.gzip(readStream);
    WriteStream<?> compressedWriteStream = Compression.gzip(writeStream, 7);
    testCompression(readStream, writeStream, compressedReadStream, compressedWriteStream);
  }

  @Test
  public void testGzipCorrupt() {
    FakeReadStream readStream = new FakeReadStream();
    ReadStream<?> compressedReadStream = Compression.gzip(readStream);
    testCompressionCorrupt(readStream, compressedReadStream);
  }

  private static void testCompression(FakeReadStream readStream, FakeWriteStream writeStream, ReadStream<?> compressedReadStream, WriteStream<?> compressedWriteStream) {
    Buffer inp = new Buffer();
    for (int j = 0; j < 10; j++) {
      Buffer b = TestUtils.generateRandomBuffer(100);
      inp.appendBuffer(b);
      compressedWriteStream.write(b);
    }

    Buffer written = writeStream.received();
    Assert.assertFalse(TestUtils.buffersEqual(inp, written));

    final Buffer out = new Buffer();
    final AtomicBoolean end = new AtomicBoolean();
    compressedReadStream.dataHandler(new Handler<Buffer>() {
      @Override
      public void handle(Buffer event) {
        out.appendBuffer(event);
      }
    });
    compressedReadStream.endHandler(new Handler<Void>() {
      @Override
      public void handle(Void event) {
        end.set(true);
      }
    });
    readStream.addData(written);
    readStream.end();

    Assert.assertTrue(end.get());
    Assert.assertTrue(TestUtils.buffersEqual(inp, out));
  }

  private static void testCompressionCorrupt(FakeReadStream readStream, ReadStream<?> compressedReadStream) {
    final AtomicBoolean error = new AtomicBoolean();
    compressedReadStream.exceptionHandler(new Handler<Throwable>() {
      @Override
      public void handle(Throwable event) {
        error.set(true);
      }
    });
    Buffer inp = new Buffer();
    for (int j = 0; j < 10; j++) {
      Buffer b = TestUtils.generateRandomBuffer(100);
      inp.appendBuffer(b);
      readStream.addData(b);
    }
    readStream.end();
    Assert.assertTrue(error.get());

  }
}
