/*
 * Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.test.core;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.TrustOptions;
import io.vertx.core.net.impl.KeyStoreHelper;
import okhttp3.CertificatePinner;
import okhttp3.ConnectionSpec;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;
import org.junit.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http2Test extends HttpTestBase {


  @Override
  public void setUp() throws Exception {
    super.setUp();

    HttpServerOptions options = new HttpServerOptions()
        .setPort(4043)
        .setHost("localhost")
        .setUseAlpn(true)
        .setSsl(true)
        .addEnabledCipherSuite("TLS_RSA_WITH_AES_128_CBC_SHA") // Non Diffie-helman -> debuggable in wireshark
        .setKeyStoreOptions((JksOptions) getServerCertOptions(KeyCert.JKS));

    server = vertx.createHttpServer(options);

  }

  private OkHttpClient createHttp2Client() throws Exception {
    CertificatePinner certificatePinner = new CertificatePinner.Builder()
        .add("localhost", "sha1/c9qKvZ9pYojzJD4YQRfuAd0cHVA=")
        .build();

    KeyStoreHelper helper = KeyStoreHelper.create((VertxInternal) vertx, (TrustOptions) getServerCertOptions(KeyCert.JKS));
    TrustManager[] trustMgrs = helper.getTrustMgrs((VertxInternal) vertx);
    SSLContext sc = SSLContext.getInstance("SSL");
    sc.init(null, trustMgrs, new java.security.SecureRandom());

    return new OkHttpClient.Builder()
        .readTimeout(100, TimeUnit.SECONDS)
        .writeTimeout(100, TimeUnit.SECONDS)
        .sslSocketFactory(sc.getSocketFactory()).hostnameVerifier((hostname, session) -> true)
        .protocols(Arrays.asList(Protocol.HTTP_2, Protocol.HTTP_1_1))
        .certificatePinner(certificatePinner)
        .connectionSpecs(Collections.singletonList(ConnectionSpec.MODERN_TLS))
        .build();
  }

  @Test
  public void testGet() throws Exception {
    String content = TestUtils.randomAlphaString(1000);
    AtomicBoolean requestEnded = new AtomicBoolean();
    CountDownLatch latch = new CountDownLatch(1);
    server.requestHandler(req -> {
          req.endHandler(v -> {
            requestEnded.set(true);
          });
          req.response().putHeader("content-type", "text/plain").end(content);
        })
        .listen(ar -> {
          assertTrue(ar.succeeded());
          latch.countDown();
        });
    awaitLatch(latch);
    OkHttpClient client = createHttp2Client();
    Request request = new Request.Builder().url("https://localhost:4043/").build();
    Response response = client.newCall(request).execute();
    assertEquals(Protocol.HTTP_2, response.protocol());
    assertEquals(content, response.body().string());
  }

  @Test
  public void testPost() throws Exception {
    String expectedContent = TestUtils.randomAlphaString(1000);
    CountDownLatch latch = new CountDownLatch(1);
    Buffer postContent = Buffer.buffer();
    server.requestHandler(req -> {
      req.handler(postContent::appendBuffer);
      req.endHandler(v -> {
        req.response().putHeader("content-type", "text/plain").end("");
      });
        })
        .listen(ar -> {
          assertTrue(ar.succeeded());
          latch.countDown();
        });
    awaitLatch(latch);
    OkHttpClient client = createHttp2Client();
    Request request = new Request.Builder()
        .post(RequestBody.create(MediaType.parse("test/plain"), expectedContent))
        .url("https://localhost:4043/")
        .build();
    Response response = client.newCall(request).execute();
    assertEquals(Protocol.HTTP_2, response.protocol());
    assertEquals(expectedContent, postContent.toString());
  }

  @Test
  public void testServerRequestPause() throws Exception {
    String expectedContent = TestUtils.randomAlphaString(1000);
    CountDownLatch latch = new CountDownLatch(1);
    Thread t = Thread.currentThread();
    AtomicBoolean done = new AtomicBoolean();
    Buffer received = Buffer.buffer();
    server.requestHandler(req -> {
      vertx.setPeriodic(1, timerID -> {
        if (t.getState() == Thread.State.WAITING) {
          vertx.cancelTimer(timerID);
          done.set(true);
          // Let some time to accumulate some more buffers
          vertx.setTimer(100, id -> {
            req.resume();
          });
        }
      });
      req.handler(received::appendBuffer);
      req.endHandler(v -> {
        req.response().end("hello");
      });
      req.pause();
    })
        .listen(ar -> {
          assertTrue(ar.succeeded());
          latch.countDown();
        });
    awaitLatch(latch);
    OkHttpClient client = createHttp2Client();
    Buffer sent = Buffer.buffer();
    Request request = new Request.Builder()
        .post(new RequestBody() {
          @Override
          public MediaType contentType() {
            return MediaType.parse("text/plain");
          }
          @Override
          public void writeTo(BufferedSink sink) throws IOException {
            while (!done.get()) {
              sent.appendString(expectedContent);
              sink.write(expectedContent.getBytes());
              sink.flush();
            }
            sink.close();
          }
        })
        .url("https://localhost:4043/")
        .build();
    Response response = client.newCall(request).execute();
    assertEquals(Protocol.HTTP_2, response.protocol());
    assertEquals("hello", response.body().string());
    assertEquals(received, sent);
  }
}
