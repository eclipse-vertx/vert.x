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

import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.PoolOptions;
import io.vertx.core.impl.Utils;
import io.vertx.core.net.NetClientOptions;
import io.vertx.test.core.TestUtils;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.util.concurrent.TimeUnit;

public class Http1xSendFileTest extends HttpSendFileTest {

  @Test
  public void testSendFileWithConnectionCloseHeader() throws Exception {
    String content = TestUtils.randomUnicodeString(1024 * 1024 * 2);
    sendFile("test-send-file.html", content, false,
      () -> client.request(requestOptions).map(req -> req.putHeader(HttpHeaders.CONNECTION, "close")));
  }

  @Test
  public void testSendFileFailsWhenClientClosesConnection() throws Exception {
    // 10 megs
    final File f = setupFile("file.pdf", TestUtils.randomUnicodeString(10 * 1024 * 1024));
    server.requestHandler(req -> {
      try {
        req.response().sendFile(f.getAbsolutePath()).onComplete(onFailure(err -> {
          // Broken pipe
          testComplete();
        }));
      } catch (Exception e) {
        // this was the bug reported with issues/issue-80
        fail(e);
      }
    });
    startServer(testAddress);
    vertx.createNetClient(new NetClientOptions()
      .setSsl(createBaseClientOptions().isSsl())
      .setHostnameVerificationAlgorithm("")
      .setTrustAll(true)
    ).connect(testAddress).onComplete(onSuccess(socket -> {
      socket.write("GET / HTTP/1.1\r\n\r\n");
      socket.close();
    }));
    await();
  }

  @Test
  public void testSendFilePipelined() throws Exception {
    int n = 2;
    waitFor(n);
    File sent = TestUtils.tmpFile(".dat", 16 * 1024);
    server.requestHandler(
      req -> {
        req.response().sendFile(sent.getAbsolutePath());
      });
    startServer(testAddress);
    client.close();
    client = vertx.createHttpClient(createBaseClientOptions().setPipelining(true), new PoolOptions().setHttp1MaxSize(1));
    for (int i = 0;i < n;i++) {
      client.request(requestOptions)
        .compose(req -> req.send().compose(HttpClientResponse::body))
        .onComplete(onSuccess(body -> {
          complete();
        }));
    }
    await();
  }

  @Test
  public void testHttpServerWithIdleTimeoutSendChunkedFile() throws Exception {
    // Does not pass reliably in CI (timeout)
    Assume.assumeTrue(!vertx.isNativeTransportEnabled() && !Utils.isWindows());
    int expected = 32 * 1024 * 1024;
    File file = TestUtils.tmpFile(".dat", expected);
    // Estimate the delay to transfer a file with a 1ms pause in chunks
    int delay = retrieveFileFromServer(file, createBaseServerOptions());
    // Now test with timeout relative to this delay
    int timeout = delay / 2;
    delay = retrieveFileFromServer(file, createBaseServerOptions().setIdleTimeout(timeout).setIdleTimeoutUnit(TimeUnit.MILLISECONDS));
    assertTrue(delay > timeout);
  }

  private int retrieveFileFromServer(File file, HttpServerOptions options) throws Exception {
    server.close().await();
    server = vertx
      .createHttpServer(options)
      .requestHandler(
        req -> {
          req.response().sendFile(file.getAbsolutePath());
        });
    startServer(testAddress);
    long now = System.currentTimeMillis();
    int[] length = {0};
    Integer len = client.request(requestOptions)
      .compose(req -> req.send()
        .compose(resp -> {
          resp.handler(buff -> {
            length[0] += buff.length();
            resp.pause();
            vertx.setTimer(1, id -> {
              resp.resume();
            });
          });
          resp.exceptionHandler(this::fail);
          return resp.end();
        }))
      .map(v -> length[0]).await();
    assertEquals((int)len, file.length());
    return (int) (System.currentTimeMillis() - now);
  }
}
