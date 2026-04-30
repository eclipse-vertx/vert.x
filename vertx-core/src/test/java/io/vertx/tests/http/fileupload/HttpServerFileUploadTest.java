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
package io.vertx.tests.http.fileupload;

import io.netty.handler.codec.DecoderException;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.http.*;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.streams.WriteStream;
import io.vertx.test.core.TestUtils;
import io.vertx.test.http.HttpConfig;
import io.vertx.test.http.HttpTestBase;
import io.vertx.test.http.SimpleHttpTest;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Assert;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/**
 */
public abstract class HttpServerFileUploadTest extends SimpleHttpTest {

  @Rule
  public TemporaryFolder testFolder = new TemporaryFolder();

  protected File testDir;

  protected HttpServerFileUploadTest(HttpConfig config) {
    super(config, ReportMode.FORBIDDEN);
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    testDir = testFolder.newFolder();
  }

  @Test
  public void testClientResetMultipartUploadCleansDecoder() throws Exception {
    Assume.assumeTrue(config.version() == HttpVersion.HTTP_1_1 || config.version() == HttpVersion.HTTP_2);
    AtomicReference<HttpClientRequest> clientRequest = new AtomicReference<>();
    AtomicBoolean completed = new AtomicBoolean();
    server.requestHandler(req -> {
      if (req.method() == HttpMethod.POST) {
        Context context = Vertx.currentContext();
        req.setExpectMultipart(true);
        req.exceptionHandler(err -> context.runOnContext(v -> {
          if (completed.compareAndSet(false, true)) {
            req.headers().set(HttpHeaders.CONTENT_TYPE, "text/plain");
            try {
              req.setExpectMultipart(true);
              Assert.fail("Should throw IllegalStateException");
            } catch (IllegalStateException e) {
              Assert.assertTrue(e.getMessage(), e.getMessage().contains("valid content-type"));
            }
            testComplete();
          }
        }));
        req.uploadHandler(upload -> {
          upload.handler(buffer -> {});
          HttpClientRequest request = clientRequest.get();
          if (request != null) {
            request.reset();
          }
        });
      }
    });
    startServer(testAddress);

    String boundary = "multipart-cleanup";
    String body = "--" + boundary + "\r\n" +
      "Content-Disposition: form-data; name=\"file\"; filename=\"tmp-0.txt\"\r\n" +
      "Content-Type: text/plain\r\n" +
      "\r\n" +
      "content";

    client.request(new RequestOptions(requestOptions)
      .setMethod(HttpMethod.POST)
      .setURI("/form")).onComplete(TestUtils.onSuccess(request -> {
      clientRequest.set(request);
      request
        .putHeader(HttpHeaders.CONTENT_TYPE, "multipart/form-data; boundary=" + boundary)
        .putHeader(HttpHeaders.CONTENT_LENGTH, "999999")
        .response().onComplete(ar -> {});
      request.write(body);
    }));
    await();
  }

  @Test
  public void testFormUploadEmptyFile() {
    testFormUploadFile("", false, false, false, false);
  }

  @Test
  public void testFormUploadEmptyFileWithContentLength() {
    testFormUploadFile("", true, false, false, false);
  }

  @Test
  public void testFormUploadSmallFile() {
    testFormUploadFile(TestUtils.randomAlphaString(100), false, false, false, false);
  }

  @Test
  public void testFormUploadSmallFileWithContentLength() {
    testFormUploadFile(TestUtils.randomAlphaString(100), true, false, false, false);
  }

  @Test
  public void testFormUploadMediumFile() {
    testFormUploadFile(TestUtils.randomAlphaString(20000), false, false, false, false);
  }

  @Test
  public void testFormUploadMediumFileWithContentLength() {
    testFormUploadFile(TestUtils.randomAlphaString(20000), true, false, false, false);
  }

  @Test
  public void testFormUploadLargeFile() {
    testFormUploadFile(TestUtils.randomAlphaString(4 * 1024 * 1024), false, false, false, false);
  }

  @Test
  public void testFormUploadLargeFileWithContentLength() {
    testFormUploadFile(TestUtils.randomAlphaString(4 * 1024 * 1024), true, false, false, false);
  }

  @Test
  public void testFormUploadEmptyFileStreamToDisk() {
    testFormUploadFile("", false, true, false, false);
  }

  @Test
  public void testFormUploadSmallFileStreamToDisk() {
    testFormUploadFile(TestUtils.randomAlphaString(100), false, true, false, false);
  }

  @Test
  public void testFormUploadMediumFileStreamToDisk() {
    testFormUploadFile(TestUtils.randomAlphaString(20 * 1024), false, true, false, false);
  }

  @Test
  public void testFormUploadLargeFileStreamToDisk() {
    testFormUploadFile(TestUtils.randomAlphaString(4 * 1024 * 1024), false, true, false, false);
  }

  @Test
  public void testFormUploadVeryLargeFileStreamToDisk() {
    long one_kb = 1024L;
    long one_mb = one_kb * 1024L;
    long one_gb = one_mb * 1024L;
//    long length = one_gb * 10L;
    long length = one_mb + 128; // 128MB
    Content content = new Content() {
      @Override
      public long length() {
        return length;
      }
      Buffer chunk_1k = TestUtils.randomBuffer(1024);
      long chunkLength = chunk_1k.length();
      private void pump(long remaining, WriteStream<Buffer> out, Promise<Void> done) {
        while (!out.writeQueueFull()) {
          if (remaining > chunkLength) {
            out.write(chunk_1k);
            remaining -= chunkLength;
          } else {
            Buffer last = chunk_1k.slice(0, (int)remaining);
            out.write(last).onComplete(done);
            return;
          }
        }
        long propagated = remaining;
        // System.out.println("Full - remaining is " + propagated + "M");
        out.drainHandler(v -> {
          pump(propagated, out, done);
        });
      }
      @Override
      public Future<Void> write(WriteStream<Buffer> out) {
        Promise<Void> done = ((ContextInternal)vertx.getOrCreateContext()).promise();
        pump(length, out, done);
        return done.future();
      }
      @Override
      public boolean verify(Buffer expected) {
        return true;
      }
    };
    testFormUploadFile("tmp-0.txt", "tmp-0.txt", content, false, true, false, false);
  }

  @Test
  public void testFormUploadWithExtFilename() {
    testFormUploadFile(null, "%c2%a3%20and%20%e2%82%ac%20rates", "the-content", false, true, false, false);
  }

  @Test
  public void testBrokenFormUploadEmptyFile() {
    testFormUploadFile("", false, true, true, false);
  }

  @Test
  public void testBrokenFormUploadSmallFile() {
    testFormUploadFile(TestUtils.randomAlphaString(100), false, true, true, false);
  }

  @Test
  public void testBrokenFormUploadMediumFile() {
    testFormUploadFile(TestUtils.randomAlphaString(20 * 1024), false, true, true, false);
  }

  @Test
  public void testBrokenFormUploadLargeFile() {
    testFormUploadFile(TestUtils.randomAlphaString(4 * 1024 * 1024), false, true, true, false);
  }

  @Test
  public void testBrokenFormUploadEmptyFileStreamToDisk() {
    testFormUploadFile("", false, true, true, false);
  }

  @Test
  public void testBrokenFormUploadSmallFileStreamToDisk() {
    testFormUploadFile(TestUtils.randomAlphaString(100), false, true, true, false);
  }

  @Test
  public void testBrokenFormUploadMediumFileStreamToDisk() {
    testFormUploadFile(TestUtils.randomAlphaString(20 * 1024), false, true, true, false);
  }

  @Test
  public void testBrokenFormUploadLargeFileStreamToDisk() {
    testFormUploadFile(TestUtils.randomAlphaString(4 * 1024 * 1024), false, true, true, false);
  }

  @Test
  public void testCancelFormUploadEmptyFileStreamToDisk() {
    testFormUploadFile("", false, true, false, true);
  }

  @Test
  public void testCancelFormUploadSmallFileStreamToDisk() {
    testFormUploadFile(TestUtils.randomAlphaString(100), false, true, false, true);
  }

  @Test
  public void testCancelFormUploadMediumFileStreamToDisk() {
    testFormUploadFile(TestUtils.randomAlphaString(20 * 1024), false, true, false, true);
  }

  @Test
  public void testCancelFormUploadLargeFileStreamToDisk() {
    testFormUploadFile(TestUtils.randomAlphaString(4 * 1024 * 1024), false, true, false, true);
  }

  private void testFormUploadFile(String contentStr, boolean includeLength, boolean streamToDisk, boolean abortClient, boolean cancelStream) {
    testFormUploadFile("tmp-0.txt", "tmp-0.txt", contentStr, includeLength, streamToDisk, abortClient, cancelStream);
  }

  interface Content {

    long length();

    Future<Void> write(WriteStream<Buffer> out);

    boolean verify(Buffer expected);
  }

  private void testFormUploadFile(String filename,
                                  String extFilename,
                                  String contentStr,
                                  boolean includeLength,
                                  boolean streamToDisk,
                                  boolean abortClient,
                                  boolean cancelStream) {
    testFormUploadFile(filename, extFilename, Buffer.buffer(contentStr, "UTF-8"), includeLength,
      streamToDisk, abortClient, cancelStream);
  }

  private void testFormUploadFile(String filename,
                                  String extFilename,
                                  Buffer contentBytes,
                                  boolean includeLength,
                                  boolean streamToDisk,
                                  boolean abortClient,
                                  boolean cancelStream) {
    Content content = new Content() {
      @Override
      public long length() {
        return contentBytes.length();
      }
      @Override
      public Future<Void> write(WriteStream<Buffer> out) {
        return out.write(contentBytes);
      }
      @Override
      public boolean verify(Buffer expected) {
        return contentBytes.equals(expected);
      }
    };
    testFormUploadFile(filename, extFilename, content, includeLength, streamToDisk, abortClient, cancelStream);

  }

  private void testFormUploadFile(String filename,
                                  String extFilename,
                                  Content content,
                                  boolean includeLength,
                                  boolean streamToDisk,
                                  boolean abortClient,
                                  boolean cancelStream) {
    String expectedFilename;
    try {
      if (extFilename != null) {
        expectedFilename = URLDecoder.decode(extFilename, "UTF-8");
      } else {
        expectedFilename = filename;
      }
    } catch (UnsupportedEncodingException e) {
      Assert.fail(e.getMessage());
      return;
    }

    waitFor(2);

    AtomicInteger attributeCount = new AtomicInteger();

    AtomicReference<HttpConnection> clientConn = new AtomicReference<>();
    AtomicReference<HttpConnection> serverConn = new AtomicReference<>();
    Runnable checkClose = () -> {
      if (clientConn.get() != null && serverConn.get() != null) {
        clientConn.get().close();
      }
    };

    server.requestHandler(req -> {

      Context requestContext = vertx.getOrCreateContext();
      if (req.method() == HttpMethod.POST) {
        Assert.assertEquals(req.path(), "/form");
        req.response().setChunked(true);
        req.setExpectMultipart(true);
        Assert.assertTrue(req.isExpectMultipart());

        // Now try setting again, it shouldn't have an effect
        req.setExpectMultipart(true);
        Assert.assertTrue(req.isExpectMultipart());

        req.uploadHandler(upload -> {

          Context uploadContext = Vertx.currentContext();
          Assert.assertNotNull(uploadContext);
          Assert.assertSame(requestContext, uploadContext);

          serverConn.set(req.connection());
          checkClose.run();

          Buffer tot = Buffer.buffer();
          Assert.assertEquals("file", upload.name());
          Assert.assertEquals(expectedFilename, upload.filename());
          Assert.assertEquals("image/gif", upload.contentType());
          String uploadedFileName;
          if (!streamToDisk) {
            upload.handler(tot::appendBuffer);
            upload.exceptionHandler(err -> {
              Assert.assertTrue(abortClient);
              complete();
            });
            upload.endHandler(v -> {
              Assert.assertFalse(abortClient);
              Assert.assertTrue(content.verify(tot));
              Assert.assertTrue(upload.isSizeAvailable());
              Assert.assertEquals(content.length(), upload.size());
              Assert.assertNull(upload.file());
              complete();
            });
          } else {
            uploadedFileName = new File(testDir, UUID.randomUUID().toString()).getPath();
            upload.streamToFileSystem(uploadedFileName).onComplete(ar -> {
              if (ar.succeeded()) {
                File f = new File(uploadedFileName);
                if (f.length() < 10 * 1024 * 1024) {
                  Buffer uploaded = vertx.fileSystem().readFileBlocking(uploadedFileName);
                  Assert.assertEquals(content.length(), uploaded.length());
                  Assert.assertTrue(content.verify(uploaded));
                } else {
                  // We check the size only
                  Assert.assertEquals(f.length(), content.length());
                }
                AsyncFile file = upload.file();
                Assert.assertNotNull(file);
                try {
                  file.flush();
                  Assert.fail("Was expecting uploaded file to be closed");
                } catch (IllegalStateException ignore) {
                  // File has been closed
                }
              } else {
                Assert.assertTrue(ar.failed());
              }
              if (cancelStream) {
                File f = new File(uploadedFileName);
                if (!f.exists() || f.length() == content.length()) {
                  // Was fully uploaded or removed already
                  complete();
                } else {
                  long now = System.currentTimeMillis();
                  vertx.setPeriodic(10, id -> {
                    if (f.exists()) {
                      Assert.assertTrue(System.currentTimeMillis() - now < 20_000);
                    } else {
                      vertx.cancelTimer(id);
                      complete();
                    }
                  });
                }
              } else {
                complete();
              }
            });
            if (cancelStream) {
              BooleanSupplier test = () -> {
                File f = new File(uploadedFileName);
                if (f.length() >= content.length() / 2 && f.length() < content.length()) {
                  Assert.assertTrue(upload.cancelStreamToFileSystem());
                  return true;
                } else {
                  return false;
                }
              };
              if (!test.getAsBoolean()) {
                long now = System.currentTimeMillis();
                vertx.setPeriodic(10, id -> {
                  Assert.assertTrue(System.currentTimeMillis() - now < 20_000);
                  if (test.getAsBoolean()) {
                    vertx.cancelTimer(id);
                  }
                });
              }
            }
          }
        });
        req.endHandler(v -> {
          MultiMap attrs = req.formAttributes();
          attributeCount.set(attrs.size());
          Assert.assertTrue(req.isExpectMultipart());
          req.response().end();
        });
      }
    });

    server.listen(testAddress).await();

    HttpClientRequest request = client.request(new RequestOptions(requestOptions)
      .setMethod(HttpMethod.POST)
      .setURI("/form")).await();
    String boundary = "dLV9Wyq26L_-JQxk6ferf-RT153LhOO";
    String epi = "\r\n" +
      "--" + boundary + "--\r\n";
    String pro = "--" + boundary + "\r\n" +
      "Content-Disposition: form-data; name=\"file\"" + (filename == null ? "" : "; filename=\"" + filename + "\"" ) + (extFilename == null ? "" : "; filename*=\"UTF-8''" + extFilename) + "\"\r\n" +
      "Content-Type: image/gif\r\n" +
      (includeLength ? "Content-Length: " + Long.toUnsignedString(content.length()) + "\r\n" : "") +
      "\r\n";

    request.headers().set(HttpHeaders.CONTENT_LENGTH, Long.toUnsignedString((((long) pro.length() + content.length() + (long) epi.length()))));
//    request.setChunked(true);
    request.headers().set(HttpHeaders.CONTENT_TYPE, "multipart/form-data; boundary=" + boundary);

    vertx.runOnContext(v1 -> {
      request.write(pro);
      Future<Void> fut = content.write(request).compose(v -> request.end(epi));
      if (abortClient) {
        fut.onComplete(TestUtils.onSuccess(v -> {
          clientConn.set(request.connection());
          checkClose.run();
        }));
      }
      if (abortClient) {
        request.response().onComplete(ar -> complete());
      } else {
        request.response().onComplete(TestUtils.onSuccess(resp -> {
          Assert.assertEquals(200, resp.statusCode());
          resp.bodyHandler(body -> {
            Assert.assertEquals(0, body.length());
          });
          Assert.assertEquals(0, attributeCount.get());
          complete();
        }));
      }
    });

    await();
  }

  @Test
  public void testFormUploadAttributes() throws Exception {
    AtomicInteger attributeCount = new AtomicInteger();
    server.requestHandler(req -> {
      if (req.method() == HttpMethod.POST) {
        Assert.assertEquals(req.path(), "/form");
        req.response().setChunked(true);
        req.setExpectMultipart(true);
        req.uploadHandler(upload -> upload.handler(buffer -> {
          Assert.fail("Should get here");
        }));
        req.endHandler(v -> {
          MultiMap attrs = req.formAttributes();
          attributeCount.set(attrs.size());
          Assert.assertEquals("vert x", attrs.get("framework"));
          Assert.assertEquals("vert x", req.getFormAttribute("framework"));
          Assert.assertEquals("vert x", req.formAttributes().get("framework"));
          Assert.assertEquals(Collections.singletonList("vert x"), req.formAttributes().getAll("framework"));
          Assert.assertEquals("jvm", attrs.get("runson"));
          Assert.assertEquals("jvm", req.getFormAttribute("runson"));
          Assert.assertEquals("jvm", req.formAttributes().get("runson"));
          Assert.assertEquals(Collections.singletonList("jvm"), req.formAttributes().getAll("runson"));
          Assert.assertEquals("0", attrs.get("list"));
          Assert.assertEquals("0", req.getFormAttribute("list"));
          Assert.assertEquals("0", req.formAttributes().get("list"));
          Assert.assertEquals(Arrays.asList("0", "1"), req.formAttributes().getAll("list"));
          req.response().end();
        });
      }
    });

    Buffer buffer = Buffer.buffer();
    // Make sure we have one param that needs url encoding
    buffer.appendString(
      "framework=" + URLEncoder.encode("vert x", "UTF-8") +
      "&runson=jvm" +
      "&list=0" +
      "&list=1"
      , "UTF-8");
    server.listen(testAddress).onComplete(TestUtils.onSuccess(s -> {
      client.request(new RequestOptions(requestOptions)
        .setMethod(HttpMethod.POST)
        .setURI("/form")).onComplete(TestUtils.onSuccess(req -> {
        req
          .putHeader("content-length", String.valueOf(buffer.length()))
          .putHeader("content-type", "application/x-www-form-urlencoded")
          .send(buffer).onComplete(TestUtils.onSuccess(resp -> {
            // assert the response
            Assert.assertEquals(200, resp.statusCode());
            resp.bodyHandler(body -> {
              Assert.assertEquals(0, body.length());
            });
            Assert.assertEquals(3, attributeCount.get());
            testComplete();
          }));
      }));
    }));

    await();
  }

  @Test
  public void testFormUploadAttributes2() {
    AtomicInteger attributeCount = new AtomicInteger();
    server.requestHandler(req -> {
      if (req.method() == HttpMethod.POST) {
        Assert.assertEquals(req.path(), "/form");
        req.setExpectMultipart(true);
        req.uploadHandler(event -> event.handler(buffer -> {
          Assert.fail("Should not get here");
        }));
        req.endHandler(v -> {
          MultiMap attrs = req.formAttributes();
          attributeCount.set(attrs.size());
          Assert.assertEquals("junit-testUserAlias", attrs.get("origin"));
          Assert.assertEquals("admin@foo.bar", attrs.get("login"));
          Assert.assertEquals("admin", attrs.get("pass word"));
          req.response().end();
        });
      }
    });

    server.listen(testAddress).onComplete(TestUtils.onSuccess(s -> {
      Buffer buffer = Buffer.buffer();
      buffer.appendString("origin=junit-testUserAlias&login=admin%40foo.bar&pass+word=admin");
      client.request(new RequestOptions(requestOptions)
        .setMethod(HttpMethod.POST)
        .setURI("/form")).onComplete(TestUtils.onSuccess(req -> {
        req.putHeader("content-length", String.valueOf(buffer.length()))
          .putHeader("content-type", "application/x-www-form-urlencoded")
          .response().onComplete(TestUtils.onSuccess(resp -> {
            // assert the response
            Assert.assertEquals(200, resp.statusCode());
            resp.bodyHandler(body -> {
              Assert.assertEquals(0, body.length());
            });
            Assert.assertEquals(3, attributeCount.get());
            testComplete();
          }));
        req.end(buffer);
      }));
    }));

    await();
  }

  @Test
  public void testAttributeSizeOverflow() {
    server.close();
    server = config.forServer().setMaxFormAttributeSize(9).create(vertx);
    server.requestHandler(req -> {
      if (req.method() == HttpMethod.POST) {
        Assert.assertEquals(req.path(), "/form");
        AtomicReference<Throwable> err = new AtomicReference<>();
        req
          .setExpectMultipart(true)
          .exceptionHandler(err::set)
          .endHandler(v -> {
            Assert.assertNotNull(err.get());
            Assert.assertTrue(err.get() instanceof DecoderException);
            Assert.assertTrue(err.get().getMessage().contains("Size exceed allowed maximum capacity"));
            Assert.assertEquals(0, req.formAttributes().size());
          req.response().end();
        });
      }
    });

    server.listen(testAddress).onComplete( TestUtils.onSuccess(s -> {
      Buffer buffer = Buffer.buffer();
      buffer.appendString("origin=0123456789");
      client.request(new RequestOptions(requestOptions)
        .setMethod(HttpMethod.POST)
        .setURI("/form")).onComplete(TestUtils.onSuccess(req -> {
        req.putHeader("content-length", String.valueOf(buffer.length()))
          .putHeader("content-type", "application/x-www-form-urlencoded")
          .response().onComplete(TestUtils.onSuccess(resp -> {
            Assert.assertEquals(200, resp.statusCode());
            testComplete();
          }));
        req.end(buffer);
      }));
    }));

    await();
  }

  @Test
  public void testInvalidPostFileUpload() throws Exception {
    server.requestHandler(req -> {
      req.setExpectMultipart(true);
      AtomicInteger errCount = new AtomicInteger();
      req.exceptionHandler(err -> {
        errCount.incrementAndGet();
      });
      req.endHandler(v -> {
        Assert.assertTrue(errCount.get() > 0);
        testComplete();
      });
    });
    startServer(testAddress);

    String contentType = "multipart/form-data; boundary=a4e41223-a527-49b6-ac1c-315d76be757e";
    String body = "--a4e41223-a527-49b6-ac1c-315d76be757e\r\n" +
      "Content-Disposition: form-data; name=\"file\"; filename=\"tmp-0.txt\"\r\n" +
      "Content-Type: image/gif; charset=ABCD\r\n" +
      "Content-Length: 12\r\n" +
      "\r\n" +
      "some-content\r\n" +
      "--a4e41223-a527-49b6-ac1c-315d76be757e--\r\n";

    client.request(new RequestOptions(requestOptions).setMethod(HttpMethod.POST).setURI("/form")).onComplete(TestUtils.onSuccess(req -> {
      req.putHeader(HttpHeaders.CONTENT_TYPE, contentType);
      req.putHeader(HttpHeaders.CONTENT_LENGTH, "" + body.length());
      req.end(body);
    }));
    await();
  }

  @Test
  public void testMaxFormFieldsDefaultPass() throws Exception {
    testMaxFormFields(256, true);
  }

  @Test
  public void testMaxFormFieldDefaultFail() throws Exception {
    testMaxFormFields(257 + 1, false);
  }

  @Test
  public void testMaxFormFieldsOverridePass() throws Exception {
    testMaxFormFieldOverride(true);
  }

  @Test
  public void testMaxFormFieldOverrideFail() throws Exception {
    testMaxFormFieldOverride(false);
  }

  private void testMaxFormFieldOverride(boolean pass) throws Exception {
    int newMax = 512;
    server.close();
    server = config.forServer().setMaxFormFields(newMax).create(vertx);
    testMaxFormFields(pass ? newMax : (newMax + 2), pass);
  }

  private void testMaxFormFields(int num, boolean pass) throws Exception {

    server.requestHandler(req -> {
      req.setExpectMultipart(true);
      req.end()
        .onComplete(ar -> {
          req.response().setStatusCode(ar.succeeded() ? 200 : 400).end();
        });
    });
    startServer(testAddress);

    client.request(new RequestOptions(requestOptions).setMethod(HttpMethod.POST)).onComplete(TestUtils.onSuccess(req -> {
      req.setChunked(true);
      req.putHeader("content-type", "application/x-www-form-urlencoded");
      StringBuilder sb = new StringBuilder();
      for (int i = 0;i < num;i++) {
        if (i > 0) {
          sb.append('&');
        }
        sb.append("a").append(i).append("=").append("b");
      }
      req.write(sb.toString());
      vertx.setTimer(10, id -> {
        req.end();
      });
      req
        .response()
        .compose(resp -> {
          if (pass) {
            Assert.assertEquals(200, resp.statusCode());
          } else {
            Assert.assertEquals(400, resp.statusCode());
          }
          return resp.end();
        }).onComplete(TestUtils.onSuccess(v -> testComplete()));
    }));
    await();
  }

  @Test
  public void testFormMaxBufferedBytesDefaultPass() throws Exception {
    testFormMaxBufferedBytes(1024, true);
  }

  @Test
  public void testFormMaxBufferedBytesDefaultFail() throws Exception {
    testFormMaxBufferedBytes(1025, false);
  }

  @Test
  public void testFormMaxBufferedBytesOverridePass() throws Exception {
    testFormMaxBufferedBytesOverride(true);
  }

  @Test
  public void testFormMaxBufferedBytesOverrideFail() throws Exception {
    testFormMaxBufferedBytesOverride(false);
  }

  private void testFormMaxBufferedBytesOverride(boolean pass) throws Exception {
    int newMax = 2048;
    server.close();
    server = config.forServer().setMaxFormBufferedBytes(newMax).create(vertx);
    testFormMaxBufferedBytes(pass ? newMax : (newMax + 1), pass);
  }

  public void testFormMaxBufferedBytes(int len, boolean pass) throws Exception {

    server.requestHandler(req -> {
      req.setExpectMultipart(true);
      req.end()
        .onComplete(ar -> {
          req.response().setStatusCode(ar.succeeded() ? 200 : 400).end();
        });
    });

    startServer(testAddress);

    client.request(new RequestOptions(requestOptions).setMethod(HttpMethod.POST)).onComplete(TestUtils.onSuccess(req -> {
      req.setChunked(true);
      req.putHeader("content-type", "application/x-www-form-urlencoded");
      StringBuilder sb = new StringBuilder();
      for (int i = 0;i < len;i++) {
        sb.append("a");
      }
      req.write(sb.toString());
      vertx.setTimer(10, id -> {
        req.end("=b");
      });
      req
        .response()
        .compose(resp -> {
          if (pass) {
            Assert.assertEquals(200, resp.statusCode());
          } else {
            Assert.assertEquals(400, resp.statusCode());
          }
          return resp.end();
        }).onComplete(TestUtils.onSuccess(v -> testComplete()));
    }));

    await();
  }
}
