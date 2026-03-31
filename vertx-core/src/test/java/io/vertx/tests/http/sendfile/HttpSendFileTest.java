/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.http.sendfile;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpResponseExpectation;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.HttpVersion;
import io.vertx.test.core.DetectFileDescriptorLeaks;
import io.vertx.test.core.TestUtils;
import io.vertx.test.http.HttpTestBase;
import org.junit.Assume;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static io.vertx.test.core.AssertExpectations.that;

public abstract class HttpSendFileTest extends HttpTestBase {

  public HttpSendFileTest() {
    super(ReportMode.FORBIDDEN);
  }

  @Test
  @DetectFileDescriptorLeaks(iterations = 40)
  public void testSendFile() throws Exception {
    String content = TestUtils.randomUnicodeString(10000);
    sendFile("test-send-file.html", content, false, () -> client.request(requestOptions));
  }

  @Test
  public void testSendFileUpperCaseSuffix() throws Exception {
    String content = TestUtils.randomUnicodeString(10000);
    sendFile("test-send-file.HTML", content, true, () -> client.request(requestOptions));
  }

  @Test
  public void testSendFileWithHandler() throws Exception {
    String content = TestUtils.randomUnicodeString(10000);
    sendFile("test-send-file.html", content, true, () -> client.request(requestOptions));
  }

  protected void sendFile(String fileName, String contentExpected, boolean useHandler, Supplier<Future<HttpClientRequest>> requestFact) throws Exception {
    waitFor(2);
    File fileToSend = setupFile(fileName, contentExpected);
    server.requestHandler(req -> {
      if (useHandler) {
        req.response().sendFile(fileToSend.getAbsolutePath()).onComplete(TestUtils.onSuccess(v -> complete()));
      } else {
        req.response().sendFile(fileToSend.getAbsolutePath());
        complete();
      }
    });
    startServer(testAddress);
    requestFact.get().compose(req -> req
        .send()
        .expecting(that(resp -> {
          Assert.assertEquals(200, resp.statusCode());
          Assert.assertEquals("text/html", resp.headers().get("Content-Type"));
          Assert.assertEquals(fileToSend.length(), Long.parseLong(resp.headers().get("content-length")));
          resp.exceptionHandler(err -> Assert.fail(err.getMessage()));
        }))
        .compose(HttpClientResponse::body))
      .onComplete(TestUtils.onSuccess(buff -> {
        Assert.assertEquals(contentExpected, buff.toString());
        complete();
      }));
    await();
  }

  @Test
  public void testSendNonExistingFile() throws Exception {
    server.requestHandler(req -> {
      final Context ctx = vertx.getOrCreateContext();
      req.response().sendFile("/not/existing/path").onComplete(event -> {
        Assert.assertEquals(ctx, vertx.getOrCreateContext());
        if (event.failed()) {
          req.response().end("failed");
        }
      });
    });

    startServer(testAddress);
    client.request(requestOptions)
      .compose(req -> req
        .send()
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(HttpClientResponse::body)
        .expecting(that(buff -> Assert.assertEquals("failed", buff.toString()))))
      .onComplete(TestUtils.onSuccess(v -> testComplete()));

    await();
  }

  @Test
  public void testSendFileOverrideHeaders() throws Exception {
    String content = TestUtils.randomUnicodeString(10000);
    File file = setupFile("test-send-file.html", content);

    server.requestHandler(req -> {
      req.response().putHeader("Content-Type", "wibble");
      req.response().sendFile(file.getAbsolutePath());
    });

    startServer(testAddress);
    client.request(requestOptions)
      .compose(req -> req
        .send()
        .expecting(that(resp -> {
          Assert.assertEquals(200, resp.statusCode());
          Assert.assertEquals(file.length(), Long.parseLong(resp.headers().get("content-length")));
          Assert.assertEquals("wibble", resp.headers().get("content-type"));
        }))
        .compose(HttpClientResponse::body)
        .expecting(that(buff -> Assert.assertEquals(content, buff.toString()))))
      .onComplete(TestUtils.onSuccess(v -> testComplete()));

    await();
  }

  @Test
  public void testSendFileNotFound() throws Exception {
    waitFor(2);

    server.requestHandler(req -> {
      req.response().putHeader("Content-Type", "wibble");
      req.response().sendFile("nosuchfile.html").onComplete(TestUtils.onFailure(v -> complete()));
    });

    startServer(testAddress);
    AtomicBoolean completed = new AtomicBoolean();
    client.request(requestOptions).onComplete(TestUtils.onSuccess(req -> {
      req.send().onComplete(ar -> {
        if (!completed.get()) {
          Assert.fail();
        }
      });
    }));
    vertx.setTimer(100, tid -> {
      completed.set(true);
      complete();
    });

    await();
  }

  @Test
  public void testSendFileDirectoryWithHandler() throws Exception {

    File dir = Files.createTempDirectory("vertx").toFile();

    server.requestHandler(req -> {
      req.response().putHeader("Content-Type", "wibble");
      req.response().sendFile(dir.getAbsolutePath())
        .onComplete(TestUtils.onFailure(t -> {
          Assert.assertTrue(t instanceof FileNotFoundException);
          testComplete();
        }));
    });

    startServer(testAddress);
    client.request(requestOptions).onComplete(TestUtils.onSuccess(req -> {
      req.send().onComplete(TestUtils.onFailure(err -> {}));
    }));

    await();
  }

  @Test
  public void testSendOpenRangeFileFromClasspath() throws Exception {
    server.requestHandler(res -> {
      res.response().sendFile("hosts_config.txt", 13);
    });
    startServer(testAddress);
    client.request(requestOptions).onComplete(TestUtils.onSuccess(req -> {
      client.request(requestOptions)
        .compose(HttpClientRequest::send)
        .expecting(that(resp -> Assert.assertEquals(String.valueOf(10), resp.headers().get("Content-Length"))))
        .compose(HttpClientResponse::body)
        .onComplete(TestUtils.onSuccess(body -> {
          Assert.assertTrue(body.toString().startsWith("server.net"));
          Assert.assertEquals(10, body.toString().length());
          testComplete();
        }));
    }));
    await();
  }

  @Test
  public void testSendRangeFileFromClasspath() throws Exception {
    server.requestHandler(res -> {
      res.response().sendFile("hosts_config.txt", 13, 10);
    });
    startServer(testAddress);
    client.request(requestOptions)
      .compose(req -> req
        .send()
        .expecting(that(resp -> Assert.assertEquals(String.valueOf(10), resp.headers().get("Content-Length"))))
        .compose(HttpClientResponse::body))
      .onComplete(TestUtils.onSuccess(body -> {
        Assert.assertEquals("server.net", body.toString());
        Assert.assertEquals(10, body.toString().length());
        testComplete();
      }));
    await();
  }

  @Test
  public void testSendZeroRangeFile() throws Exception {
    File f = setupFile("twenty_three_bytes.txt", TestUtils.randomAlphaString(23));
    server.requestHandler(res -> res.response().sendFile(f.getAbsolutePath(), 23, 0));
    startServer(testAddress);
    client.request(requestOptions)
      .compose(req -> req
        .send()
        .expecting(that(resp -> Assert.assertEquals(String.valueOf(0), resp.headers().get("Content-Length"))))
        .compose(HttpClientResponse::body))
      .onComplete(TestUtils.onSuccess(body -> {
        Assert.assertEquals("", body.toString());
        Assert.assertEquals(0, body.toString().length());
        testComplete();
      }));
    await();
  }

  @Test
  public void testSendFileOffsetIsHigherThanFileLength() throws Exception {
    testSendFileWithFailure(
      (resp, f) -> resp.sendFile(f.getAbsolutePath(), 33, 10),
      err -> Assert.assertEquals("offset : 33 is larger than the requested file length : 23", err.getMessage()));
  }

  @Test
  public void testSendFileWithNegativeLength() throws Exception {
    testSendFileWithFailure((resp, f) -> resp.sendFile(f.getAbsolutePath(), 0, -100), err -> {
      Assert.assertEquals("length : -100 (expected: >= 0)", err.getMessage());
    });
  }

  @Test
  public void testSendFileWithNegativeOffset() throws Exception {
    testSendFileWithFailure((resp, f) -> resp.sendFile(f.getAbsolutePath(), -100, 23), err -> {
      Assert.assertEquals("offset : -100 (expected: >= 0)", err.getMessage());
    });
  }

  private void testSendFileWithFailure(BiFunction<HttpServerResponse, File, Future<Void>> sendFile, Consumer<Throwable> checker) throws Exception {
    waitFor(2);
    File f = setupFile("twenty_three_bytes.txt", TestUtils.randomAlphaString(23));
    server.requestHandler(res -> {
      // Expected
      sendFile
        .apply(res.response(), f)
        .andThen(TestUtils.onFailure(checker::accept))
        .recover(v -> res.response().setStatusCode(500).end())
        .onComplete(TestUtils.onSuccess(v -> {
          complete();
        }));
    });
    startServer(testAddress);
    client.request(requestOptions)
      .compose(HttpClientRequest::send)
      .onComplete(TestUtils.onSuccess(response -> {
        Assert.assertEquals(500, response.statusCode());
        complete();
      }));

    await();
  }

  @Test
  public void testSendFileWithFileChannel() throws Exception {
    int fileLength = 16 * 1024 * 1024;
    BiFunction<RandomAccessFile, HttpServerResponse, Future<?>> sender = (file, response) -> response.sendFile(file.getChannel());
    try (RandomAccessFile raf = testSendFileWithFileChannel(fileLength, sender, "application/octet-stream", fileLength)) {
      Assert.assertTrue(raf.getChannel().isOpen());
    }
  }

  @Test
  public void testSendFileWithFileChannelAndExtension() throws Exception {
    int fileLength = 16 * 1024 * 1024;
    BiFunction<RandomAccessFile, HttpServerResponse, Future<?>> sender = (file, response) -> response
      .putHeader(HttpHeaders.CONTENT_TYPE, "video/mp4")
      .sendFile(file.getChannel());
    try (RandomAccessFile raf =  testSendFileWithFileChannel(fileLength, sender, "video/mp4", fileLength)) {
      Assert.assertTrue(raf.getChannel().isOpen());
    }
  }

  @Test
  public void testSendFileWithFileChannelRange() throws Exception {
    int fileLength = 16 * 1024 * 1024;
    int offset = 1024 * 4;
    int expectedRange = fileLength - offset;
    BiFunction<RandomAccessFile, HttpServerResponse, Future<?>> sender = (file, response) -> response
      .putHeader(HttpHeaders.CONTENT_TYPE, "video/mp4")
      .sendFile(file.getChannel(), offset, expectedRange);
    try (RandomAccessFile raf =  testSendFileWithFileChannel(fileLength, sender, "video/mp4", expectedRange)) {
      Assert.assertTrue(raf.getChannel().isOpen());
    }
  }

  @Test
  public void testSendFileWithRandomAccessFile() throws Exception {
    int fileLength = 16 * 1024 * 1024;
    BiFunction<RandomAccessFile, HttpServerResponse, Future<?>> sender = (file, response) -> response.sendFile(file);
    try (RandomAccessFile raf =  testSendFileWithFileChannel(fileLength, sender, "application/octet-stream", fileLength)) {
      raf.getFilePointer();
    }
  }

  @Test
  public void testSendFileWithRandomAccessFileAndExtension() throws Exception {
    int fileLength = 16 * 1024 * 1024;
    BiFunction<RandomAccessFile, HttpServerResponse, Future<?>> sender = (file, response) -> response
      .putHeader(HttpHeaders.CONTENT_TYPE, "video/mp4")
      .sendFile(file);
    try (RandomAccessFile raf =  testSendFileWithFileChannel(fileLength, sender, "video/mp4", fileLength)) {
      raf.getFilePointer();
    }
  }

  @Test
  public void testSendFileWithRandomAccessFileRange() throws Exception {
    int fileLength = 16 * 1024 * 1024;
    int offset = 1024 * 4;
    int expectedRange = fileLength - offset;
    BiFunction<RandomAccessFile, HttpServerResponse, Future<?>> sender = (file, response) -> response
      .putHeader(HttpHeaders.CONTENT_TYPE, "video/mp4")
      .sendFile(file, offset, expectedRange);
    try (RandomAccessFile raf =  testSendFileWithFileChannel(fileLength, sender, "video/mp4", expectedRange)) {
      raf.getFilePointer();
    }
  }

  private RandomAccessFile testSendFileWithFileChannel(int flen, BiFunction<RandomAccessFile, HttpServerResponse, Future<?>> sender,
                                                       String expectedContentType, long expectedLength) throws Exception {
    Assume.assumeTrue(createBaseClientOptions().getProtocolVersion() == HttpVersion.HTTP_1_1 || createBaseServerOptions().getHttp2MultiplexImplementation());
    File file = TestUtils.tmpFile(".dat", flen);
    RandomAccessFile raf = new RandomAccessFile(file, "r");
    server.requestHandler(req -> sender.apply(raf, req.response()).onComplete(TestUtils.onSuccess(v -> testComplete())));
    startServer(testAddress);
    Buffer body = client.request(requestOptions)
      .compose(req -> req.send()
        .expecting(HttpResponseExpectation.contentType(expectedContentType))
        .compose(HttpClientResponse::body)).await();
    Assert.assertEquals(body.length(), expectedLength);
    await();
    return raf;
  }
}
