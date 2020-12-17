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
package io.vertx.core.http;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.net.SocketAddress;
import io.vertx.test.core.TestUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/**
 */
public abstract class HttpServerFileUploadTest extends HttpTestBase {

  @Rule
  public TemporaryFolder testFolder = new TemporaryFolder();

  protected File testDir;
  private File tmp;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    testDir = testFolder.newFolder();
    if (USE_DOMAIN_SOCKETS) {
      assertTrue("Native transport not enabled", USE_NATIVE_TRANSPORT);
      tmp = TestUtils.tmpFile(".sock");
      testAddress = SocketAddress.domainSocketAddress(tmp.getAbsolutePath());
      requestOptions.setServer(testAddress);
    }
  }

  @Test
  public void testFormUploadEmptyFile() {
    testFormUploadFile("", false, false, false);
  }

  @Test
  public void testFormUploadSmallFile() {
    testFormUploadFile(TestUtils.randomAlphaString(100), false, false, false);
  }

  @Test
  public void testFormUploadMediumFile() {
    testFormUploadFile(TestUtils.randomAlphaString(20000), false, false, false);
  }

  @Test
  public void testFormUploadLargeFile() {
    testFormUploadFile(TestUtils.randomAlphaString(4 * 1024 * 1024), false, false, false);
  }

  @Test
  public void testFormUploadEmptyFileStreamToDisk() {
    testFormUploadFile("", true, false, false);
  }

  @Test
  public void testFormUploadSmallFileStreamToDisk() {
    testFormUploadFile(TestUtils.randomAlphaString(100), true, false, false);
  }

  @Test
  public void testFormUploadMediumFileStreamToDisk() {
    testFormUploadFile(TestUtils.randomAlphaString(20 * 1024), true, false, false);
  }

  @Test
  public void testFormUploadLargeFileStreamToDisk() {
    testFormUploadFile(TestUtils.randomAlphaString(4 * 1024 * 1024), true, false, false);
  }

  @Test
  public void testFormUploadWithExtFilename() {
    testFormUploadFile(null, "%c2%a3%20and%20%e2%82%ac%20rates", "the-content", true, false, false);
  }

  @Test
  public void testBrokenFormUploadEmptyFile() {
    testFormUploadFile("", true, true, false);
  }

  @Test
  public void testBrokenFormUploadSmallFile() {
    testFormUploadFile(TestUtils.randomAlphaString(100), true, true, false);
  }

  @Test
  public void testBrokenFormUploadMediumFile() {
    testFormUploadFile(TestUtils.randomAlphaString(20 * 1024), true, true, false);
  }

  @Test
  public void testBrokenFormUploadLargeFile() {
    testFormUploadFile(TestUtils.randomAlphaString(4 * 1024 * 1024), true, true, false);
  }

  @Test
  public void testBrokenFormUploadEmptyFileStreamToDisk() {
    testFormUploadFile("", true, true, false);
  }

  @Test
  public void testBrokenFormUploadSmallFileStreamToDisk() {
    testFormUploadFile(TestUtils.randomAlphaString(100), true, true, false);
  }

  @Test
  public void testBrokenFormUploadMediumFileStreamToDisk() {
    testFormUploadFile(TestUtils.randomAlphaString(20 * 1024), true, true, false);
  }

  @Test
  public void testBrokenFormUploadLargeFileStreamToDisk() {
    testFormUploadFile(TestUtils.randomAlphaString(4 * 1024 * 1024), true, true, false);
  }

  @Test
  public void testCancelFormUploadEmptyFileStreamToDisk() {
    testFormUploadFile("", true, false, true);
  }

  @Test
  public void testCancelFormUploadSmallFileStreamToDisk() {
    testFormUploadFile(TestUtils.randomAlphaString(100), true, false, true);
  }

  @Test
  public void testCancelFormUploadMediumFileStreamToDisk() {
    testFormUploadFile(TestUtils.randomAlphaString(20 * 1024), true, false, true);
  }

  @Test
  public void testCancelFormUploadLargeFileStreamToDisk() {
    testFormUploadFile(TestUtils.randomAlphaString(4 * 1024 * 1024), true, false, true);
  }

  private void testFormUploadFile(String contentStr, boolean streamToDisk, boolean abortClient, boolean cancelStream) {
    testFormUploadFile("tmp-0.txt", "tmp-0.txt", contentStr, streamToDisk, abortClient, cancelStream);
  }

  private void testFormUploadFile(String filename,
                                  String extFilename,
                                  String contentStr,
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
      fail(e);
      return;
    }

    waitFor(2);

    Buffer content = Buffer.buffer(contentStr);

    AtomicInteger attributeCount = new AtomicInteger();

    AtomicReference<HttpConnection> clientConn = new AtomicReference<>();
    AtomicReference<HttpConnection> serverConn = new AtomicReference<>();
    Runnable checkClose = () -> {
      if (clientConn.get() != null && serverConn.get() != null) {
        clientConn.get().close();
      }
    };

    server.requestHandler(req -> {
      if (req.method() == HttpMethod.POST) {
        assertEquals(req.path(), "/form");
        req.response().setChunked(true);
        req.setExpectMultipart(true);
        assertTrue(req.isExpectMultipart());

        // Now try setting again, it shouldn't have an effect
        req.setExpectMultipart(true);
        assertTrue(req.isExpectMultipart());

        req.uploadHandler(upload -> {

          assertNotNull(Vertx.currentContext());

          serverConn.set(req.connection());
          checkClose.run();

          Buffer tot = Buffer.buffer();
          assertEquals("file", upload.name());
          assertEquals(expectedFilename, upload.filename());
          assertEquals("image/gif", upload.contentType());
          String uploadedFileName;
          if (!streamToDisk) {
            upload.handler(tot::appendBuffer);
            upload.exceptionHandler(err -> {
              assertTrue(abortClient);
              complete();
            });
            upload.endHandler(v -> {
              assertFalse(abortClient);
              assertEquals(content, tot);
              assertTrue(upload.isSizeAvailable());
              assertEquals(content.length(), upload.size());
              assertNull(upload.file());
              complete();
            });
          } else {
            uploadedFileName = new File(testDir, UUID.randomUUID().toString()).getPath();
            upload.streamToFileSystem(uploadedFileName, ar -> {
              if (ar.succeeded()) {
                Buffer uploaded = vertx.fileSystem().readFileBlocking(uploadedFileName);
                assertEquals(content.length(), uploaded.length());
                assertEquals(content, uploaded);
                AsyncFile file = upload.file();
                assertNotNull(file);
                try {
                  file.flush();
                  fail("Was expecting uploaded file to be closed");
                } catch (IllegalStateException ignore) {
                  // File has been closed
                }
              } else {
                assertTrue(ar.failed());
              }
              complete();
            });
            if (cancelStream) {
              BooleanSupplier test = () -> {
                File f = new File(uploadedFileName);
                if (f.length() == contentStr.length() / 2) {
                  assertTrue(upload.cancelStreamToFileSystem());
                  long now = System.currentTimeMillis();
                  vertx.setPeriodic(10, id -> {
                    assertTrue(System.currentTimeMillis() - now < 20_000);
                    if (!new File(uploadedFileName).exists()) {
                      vertx.cancelTimer(id);
                      req.response().end();
                    }
                  });
                  return true;
                } else {
                  return false;
                }
              };
              if (!test.getAsBoolean()) {
                long now = System.currentTimeMillis();
                vertx.setPeriodic(10, id -> {
                  assertTrue(System.currentTimeMillis() - now < 20_000);
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
          req.response().end();
        });
      }
    });

    server.listen(testAddress, onSuccess(s -> {
      client.request(new RequestOptions(requestOptions)
        .setMethod(HttpMethod.POST)
        .setURI("/form"))
        .onComplete(onSuccess(req -> {
          String boundary = "dLV9Wyq26L_-JQxk6ferf-RT153LhOO";
          String epi = "\r\n" +
            "--" + boundary + "--\r\n";
          String pro = "--" + boundary + "\r\n" +
            "Content-Disposition: form-data; name=\"file\"" + (filename == null ? "" : "; filename=\"" + filename + "\"" ) + (extFilename == null ? "" : "; filename*=\"UTF-8''" + extFilename) + "\"\r\n" +
            "Content-Type: image/gif\r\n" +
            "\r\n";
          req.headers().set("content-length", "" + (pro + contentStr + epi).length());
          req.headers().set("content-type", "multipart/form-data; boundary=" + boundary);
          if (abortClient || cancelStream) {
            Future<Void> fut = req.write(pro + contentStr.substring(0, contentStr.length() / 2));
            if (abortClient) {
              fut.onComplete(onSuccess(v -> {
                clientConn.set(req.connection());
                checkClose.run();
              }));
            }
          } else {
            req.end(pro + contentStr + epi);
          }
          if (abortClient) {
            req.response(onFailure(err -> complete()));
          } else {
            req.response(onSuccess(resp -> {
              assertEquals(200, resp.statusCode());
              resp.bodyHandler(body -> {
                assertEquals(0, body.length());
              });
              assertEquals(0, attributeCount.get());
              complete();
            }));
          }
        }));
    }));
    await();
  }

  @Test
  public void testFormUploadAttributes() throws Exception {
    AtomicInteger attributeCount = new AtomicInteger();
    server.requestHandler(req -> {
      if (req.method() == HttpMethod.POST) {
        assertEquals(req.path(), "/form");
        req.response().setChunked(true);
        req.setExpectMultipart(true);
        req.uploadHandler(upload -> upload.handler(buffer -> {
          fail("Should get here");
        }));
        req.endHandler(v -> {
          MultiMap attrs = req.formAttributes();
          attributeCount.set(attrs.size());
          assertEquals("vert x", attrs.get("framework"));
          assertEquals("vert x", req.getFormAttribute("framework"));
          assertEquals("jvm", attrs.get("runson"));
          assertEquals("jvm", req.getFormAttribute("runson"));
          req.response().end();
        });
      }
    });

    Buffer buffer = Buffer.buffer();
    // Make sure we have one param that needs url encoding
    buffer.appendString("framework=" + URLEncoder.encode("vert x", "UTF-8") + "&runson=jvm", "UTF-8");
    server.listen(testAddress, onSuccess(s -> {
      client.request(new RequestOptions(requestOptions)
        .setMethod(HttpMethod.POST)
        .setURI("/form"), onSuccess(req -> {
        req
          .putHeader("content-length", String.valueOf(buffer.length()))
          .putHeader("content-type", "application/x-www-form-urlencoded")
          .send(buffer, onSuccess(resp -> {
            // assert the response
            assertEquals(200, resp.statusCode());
            resp.bodyHandler(body -> {
              assertEquals(0, body.length());
            });
            assertEquals(2, attributeCount.get());
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
        assertEquals(req.path(), "/form");
        req.setExpectMultipart(true);
        req.uploadHandler(event -> event.handler(buffer -> {
          fail("Should not get here");
        }));
        req.endHandler(v -> {
          MultiMap attrs = req.formAttributes();
          attributeCount.set(attrs.size());
          assertEquals("junit-testUserAlias", attrs.get("origin"));
          assertEquals("admin@foo.bar", attrs.get("login"));
          assertEquals("admin", attrs.get("pass word"));
          req.response().end();
        });
      }
    });

    server.listen(testAddress, onSuccess(s -> {
      Buffer buffer = Buffer.buffer();
      buffer.appendString("origin=junit-testUserAlias&login=admin%40foo.bar&pass+word=admin");
      client.request(new RequestOptions(requestOptions)
        .setMethod(HttpMethod.POST)
        .setURI("/form")).onComplete(onSuccess(req -> {
        req.putHeader("content-length", String.valueOf(buffer.length()))
          .putHeader("content-type", "application/x-www-form-urlencoded")
          .response(onSuccess(resp -> {
            // assert the response
            assertEquals(200, resp.statusCode());
            resp.bodyHandler(body -> {
              assertEquals(0, body.length());
            });
            assertEquals(3, attributeCount.get());
            testComplete();
          })).end(buffer);
      }));
    }));

    await();
  }
}
