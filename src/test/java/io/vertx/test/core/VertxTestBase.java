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

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.PemTrustOptions;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.net.KeyCertOptions;
import io.vertx.core.net.PfxOptions;
import io.vertx.core.net.TCPSSLOptions;
import io.vertx.core.net.TrustOptions;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.test.fakecluster.FakeClusterManager;
import org.junit.Rule;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class VertxTestBase extends AsyncTestBase {

  private static final Logger log = LoggerFactory.getLogger(VertxTestBase.class);

  @Rule
  public RepeatRule repeatRule = new RepeatRule();

  protected Vertx vertx;

  protected Vertx[] vertices;

  protected void vinit() {
    vertx = null;
    vertices = null;
  }

  public void setUp() throws Exception {
    super.setUp();
    vinit();
    vertx = Vertx.vertx(getOptions());
  }

  protected VertxOptions getOptions() {
    return new VertxOptions();
  }

  protected void tearDown() throws Exception {
    if (vertx != null) {
      CountDownLatch latch = new CountDownLatch(1);
      vertx.close(ar -> {
        latch.countDown();
      });
      awaitLatch(latch);
    }
    if (vertices != null) {
      int numVertices = 0;
      for (int i = 0; i < vertices.length; i++) {
        if (vertices[i] != null) {
          numVertices++;
        }
      }
      CountDownLatch latch = new CountDownLatch(numVertices);
      for (Vertx vertx: vertices) {
        if (vertx != null) {
          vertx.close(ar -> {
            if (ar.failed()) {
              log.error("Failed to shutdown vert.x", ar.cause());
            }
            latch.countDown();
          });
        }
      }
      assertTrue(latch.await(180, TimeUnit.SECONDS));
    }
    FakeClusterManager.reset(); // Bit ugly
    super.tearDown();
  }

  protected ClusterManager getClusterManager() {
    return null;
  }

  protected void startNodes(int numNodes) {
    startNodes(numNodes, getOptions());
  }

  protected void startNodes(int numNodes, VertxOptions options) {
    CountDownLatch latch = new CountDownLatch(numNodes);
    vertices = new Vertx[numNodes];
    for (int i = 0; i < numNodes; i++) {
      int index = i;
      Vertx.clusteredVertx(options.setClusterHost("localhost").setClusterPort(0).setClustered(true)
        .setClusterManager(getClusterManager()), ar -> {
        if (ar.failed()) {
          ar.cause().printStackTrace();
        }
        assertTrue("Failed to start node", ar.succeeded());
        vertices[index] = ar.result();
        latch.countDown();
      });
    }
    try {
      assertTrue(latch.await(2, TimeUnit.MINUTES));
    } catch (InterruptedException e) {
      fail(e.getMessage());
    }
  }


  protected static void setOptions(TCPSSLOptions sslOptions, KeyCertOptions options) {
    if (options instanceof JksOptions) {
      sslOptions.setKeyStoreOptions((JksOptions) options);
    } else if (options instanceof PfxOptions) {
      sslOptions.setPfxKeyCertOptions((PfxOptions) options);
    } else {
      sslOptions.setPemKeyCertOptions((PemKeyCertOptions) options);
    }
  }

  protected static void setOptions(TCPSSLOptions sslOptions, TrustOptions options) {
    if (options instanceof JksOptions) {
      sslOptions.setTrustStoreOptions((JksOptions) options);
    } else if (options instanceof PfxOptions) {
      sslOptions.setPfxTrustOptions((PfxOptions) options);
    } else {
      sslOptions.setPemTrustOptions((PemTrustOptions) options);
    }
  }

  protected static final String[] ENABLED_CIPHER_SUITES =
    new String[] {
      "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256",
      "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256",
      "TLS_RSA_WITH_AES_128_CBC_SHA256",
      "TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA256",
      "TLS_ECDH_RSA_WITH_AES_128_CBC_SHA256",
      "TLS_DHE_RSA_WITH_AES_128_CBC_SHA256",
      "TLS_DHE_DSS_WITH_AES_128_CBC_SHA256",
      "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA",
      "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",
      "TLS_RSA_WITH_AES_128_CBC_SHA",
      "TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA",
      "TLS_ECDH_RSA_WITH_AES_128_CBC_SHA",
      "TLS_DHE_RSA_WITH_AES_128_CBC_SHA",
      "TLS_DHE_DSS_WITH_AES_128_CBC_SHA",
      "TLS_ECDHE_ECDSA_WITH_RC4_128_SHA",
      "TLS_ECDHE_RSA_WITH_RC4_128_SHA",
      "SSL_RSA_WITH_RC4_128_SHA",
      "TLS_ECDH_ECDSA_WITH_RC4_128_SHA",
      "TLS_ECDH_RSA_WITH_RC4_128_SHA",
      "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
      "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
      "TLS_RSA_WITH_AES_128_GCM_SHA256",
      "TLS_ECDH_ECDSA_WITH_AES_128_GCM_SHA256",
      "TLS_ECDH_RSA_WITH_AES_128_GCM_SHA256",
      "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256",
      "TLS_DHE_DSS_WITH_AES_128_GCM_SHA256",
      "TLS_ECDHE_ECDSA_WITH_3DES_EDE_CBC_SHA",
      "TLS_ECDHE_RSA_WITH_3DES_EDE_CBC_SHA",
      "SSL_RSA_WITH_3DES_EDE_CBC_SHA",
      "TLS_ECDH_ECDSA_WITH_3DES_EDE_CBC_SHA",
      "TLS_ECDH_RSA_WITH_3DES_EDE_CBC_SHA",
      "SSL_DHE_RSA_WITH_3DES_EDE_CBC_SHA",
      "SSL_DHE_DSS_WITH_3DES_EDE_CBC_SHA",
      "SSL_RSA_WITH_RC4_128_MD5",
      "TLS_EMPTY_RENEGOTIATION_INFO_SCSV",
      "TLS_DH_anon_WITH_AES_128_GCM_SHA256",
      "TLS_DH_anon_WITH_AES_128_CBC_SHA256",
      "TLS_ECDH_anon_WITH_AES_128_CBC_SHA",
      "TLS_DH_anon_WITH_AES_128_CBC_SHA",
      "TLS_ECDH_anon_WITH_RC4_128_SHA",
      "SSL_DH_anon_WITH_RC4_128_MD5",
      "TLS_ECDH_anon_WITH_3DES_EDE_CBC_SHA",
      "SSL_DH_anon_WITH_3DES_EDE_CBC_SHA",
      "TLS_RSA_WITH_NULL_SHA256",
      "TLS_ECDHE_ECDSA_WITH_NULL_SHA",
      "TLS_ECDHE_RSA_WITH_NULL_SHA",
      "SSL_RSA_WITH_NULL_SHA",
      "TLS_ECDH_ECDSA_WITH_NULL_SHA",
      "TLS_ECDH_RSA_WITH_NULL_SHA",
      "TLS_ECDH_anon_WITH_NULL_SHA",
      "SSL_RSA_WITH_NULL_MD5",
      "SSL_RSA_WITH_DES_CBC_SHA",
      "SSL_DHE_RSA_WITH_DES_CBC_SHA",
      "SSL_DHE_DSS_WITH_DES_CBC_SHA",
      "SSL_DH_anon_WITH_DES_CBC_SHA",
      "SSL_RSA_EXPORT_WITH_RC4_40_MD5",
      "SSL_DH_anon_EXPORT_WITH_RC4_40_MD5",
      "SSL_RSA_EXPORT_WITH_DES40_CBC_SHA",
      "SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA",
      "SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA",
      "SSL_DH_anon_EXPORT_WITH_DES40_CBC_SHA",
      "TLS_KRB5_WITH_RC4_128_SHA",
      "TLS_KRB5_WITH_RC4_128_MD5",
      "TLS_KRB5_WITH_3DES_EDE_CBC_SHA",
      "TLS_KRB5_WITH_3DES_EDE_CBC_MD5",
      "TLS_KRB5_WITH_DES_CBC_SHA",
      "TLS_KRB5_WITH_DES_CBC_MD5",
      "TLS_KRB5_EXPORT_WITH_RC4_40_SHA",
      "TLS_KRB5_EXPORT_WITH_RC4_40_MD5",
      "TLS_KRB5_EXPORT_WITH_DES_CBC_40_SHA",
      "TLS_KRB5_EXPORT_WITH_DES_CBC_40_MD5"
    };

  /**
   * Create a worker verticle for the current Vert.x and return its context.
   *
   * @return the context
   * @throws Exception anything preventing the creation of the worker
   */
  protected Context createWorker() throws Exception {
    CompletableFuture<Context> fut = new CompletableFuture<>();
    vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start() throws Exception {
        fut.complete(context);
      }
    }, new DeploymentOptions().setWorker(true), ar -> {
      if (ar.failed()) {
        fut.completeExceptionally(ar.cause());
      }
    });
    return fut.get();
  }

  /**
   * Create worker verticles for the current Vert.x and returns the list of their contexts.
   *
   * @param num the number of verticles to create
   * @return the contexts
   * @throws Exception anything preventing the creation of the workers
   */
  protected List<Context> createWorkers(int num) throws Exception {
    List<Context> contexts = new ArrayList<>();
    for (int i = 0;i < num;i++) {
      contexts.add(createWorker());
    }
    return contexts;
  }
}
