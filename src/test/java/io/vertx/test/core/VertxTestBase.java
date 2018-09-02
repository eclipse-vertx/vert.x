/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.test.core;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.KeyCertOptions;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.net.PfxOptions;
import io.vertx.core.net.TCPSSLOptions;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.test.fakecluster.FakeClusterManager;
import org.junit.Rule;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class VertxTestBase extends AsyncTestBase {

  public static final boolean USE_NATIVE_TRANSPORT = Boolean.getBoolean("vertx.useNativeTransport");
  public static final boolean USE_DOMAIN_SOCKETS = Boolean.getBoolean("vertx.useDomainSockets");
  private static final Logger log = LoggerFactory.getLogger(VertxTestBase.class);

  @Rule
  public RepeatRule repeatRule = new RepeatRule();

  protected Vertx vertx;

  protected Vertx[] vertices;

  private List<Vertx> created;

  protected void vinit() {
    vertx = null;
    vertices = null;
    created = null;
  }

  public void setUp() throws Exception {
    super.setUp();
    vinit();
    VertxOptions options = getOptions();
    boolean nativeTransport = options.getPreferNativeTransport();
    vertx = Vertx.vertx(options);
    if (nativeTransport) {
      assertTrue(vertx.isNativeTransportEnabled());
    }
  }

  protected VertxOptions getOptions() {
    VertxOptions options = new VertxOptions();
    options.setPreferNativeTransport(USE_NATIVE_TRANSPORT);
    return options;
  }

  protected void tearDown() throws Exception {
    if (vertx != null) {
      close(vertx);
    }
    if (created != null) {
      closeClustered(created);
    }
    FakeClusterManager.reset(); // Bit ugly
    super.tearDown();
  }

  protected void closeClustered(List<Vertx> clustered) throws Exception {
    CountDownLatch latch = new CountDownLatch(clustered.size());
    for (Vertx clusteredVertx : clustered) {
      clusteredVertx.close(ar -> {
        if (ar.failed()) {
          log.error("Failed to shutdown vert.x", ar.cause());
        }
        latch.countDown();
      });
    }
    assertTrue(latch.await(180, TimeUnit.SECONDS));
  }

  /**
   * @return create a blank new Vert.x instance with no options closed when tear down executes.
   */
  protected Vertx vertx() {
    if (created == null) {
      created = new ArrayList<>();
    }
    Vertx vertx = Vertx.vertx();
    created.add(vertx);
    return vertx;
  }

  /**
   * @return create a blank new Vert.x instance with @{@code options} closed when tear down executes.
   */
  protected Vertx vertx(VertxOptions options) {
    if (created == null) {
      created = new ArrayList<>();
    }
    Vertx vertx = Vertx.vertx(options);
    created.add(vertx);
    return vertx;
  }

  /**
   * Create a blank new clustered Vert.x instance with @{@code options} closed when tear down executes.
   */
  protected void clusteredVertx(VertxOptions options, Handler<AsyncResult<Vertx>> ar) {
    if (created == null) {
      created = Collections.synchronizedList(new ArrayList<>());
    }
    Vertx.clusteredVertx(options, event -> {
      if (event.succeeded()) {
        created.add(event.result());
      }
      ar.handle(event);
    });
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
      clusteredVertx(options.setClusterHost("localhost").setClusterPort(0).setClustered(true)
        .setClusterManager(getClusterManager()), ar -> {
          try {
            if (ar.failed()) {
              ar.cause().printStackTrace();
            }
            assertTrue("Failed to start node", ar.succeeded());
            vertices[index] = ar.result();
          }
          finally {
            latch.countDown();
          }
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

  protected static final String[] ENABLED_CIPHER_SUITES;

  static {
    String[] suites = new String[0];
    try {
      suites = SSLContext.getDefault().getSocketFactory().getSupportedCipherSuites();
    } catch (NoSuchAlgorithmException e) {
      e.printStackTrace();
    }
    ENABLED_CIPHER_SUITES = suites;
  }

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
