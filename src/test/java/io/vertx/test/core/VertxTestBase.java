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

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.VertxException;
import io.vertx.core.VertxOptions;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.impl.LoggerFactory;
import io.vertx.core.net.PemCaOptions;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.net.KeyCertOptions;
import io.vertx.core.net.PfxOptions;
import io.vertx.core.net.TCPSSLOptions;
import io.vertx.core.net.CaOptions;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.test.fakecluster.FakeClusterManager;
import org.junit.Rule;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

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
      CountDownLatch latch = new CountDownLatch(vertices.length);
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
    startNodes(numNodes, new VertxOptions());
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


  protected String findFileOnClasspath(String fileName) {
    URL url = getClass().getClassLoader().getResource(fileName);
    if (url == null) {
      throw new IllegalArgumentException("Cannot find file " + fileName + " on classpath");
    }
    try {
      File file = new File(url.toURI());
      return file.getAbsolutePath();
    } catch (URISyntaxException e) {
      throw new VertxException(e);
    }
  }

  protected <T> Handler<AsyncResult<T>> onSuccess(Consumer<T> consumer) {
    return result -> {
      if (result.failed()) {
        result.cause().printStackTrace();
        fail(result.cause().getMessage());
      } else {
        consumer.accept(result.result());
      }
    };
  }

  protected <T> Handler<AsyncResult<T>> onFailure(Consumer<Throwable> consumer) {
    return result -> {
      assertFalse(result.succeeded());
      consumer.accept(result.cause());
    };
  }

  protected void awaitLatch(CountDownLatch latch) throws InterruptedException {
    assertTrue(latch.await(10, TimeUnit.SECONDS));
  }

  protected void waitUntil(BooleanSupplier supplier) {
    waitUntil(supplier, 10000);
  }

  protected void waitUntil(BooleanSupplier supplier, long timeout) {
    long start = System.currentTimeMillis();
    while (true) {
      if (supplier.getAsBoolean()) {
        break;
      }
      try {
        Thread.sleep(10);
      } catch (InterruptedException ignore) {
      }
      long now = System.currentTimeMillis();
      if (now - start > timeout) {
        throw new IllegalStateException("Timed out");
      }
    }
  }

  protected void setOptions(TCPSSLOptions sslOptions, KeyCertOptions options) {
    if (options instanceof JksOptions) {
      sslOptions.setKeyStoreOptions((JksOptions) options);
    } else if (options instanceof PfxOptions) {
      sslOptions.setPfxKeyCertOptions((PfxOptions) options);
    } else {
      sslOptions.setPemKeyCertOptions((PemKeyCertOptions) options);
    }
  }

  protected void setOptions(TCPSSLOptions sslOptions, CaOptions options) {
    if (options instanceof JksOptions) {
      sslOptions.setTrustStoreOptions((JksOptions) options);
    } else if (options instanceof PfxOptions) {
      sslOptions.setPfxCaOptions((PfxOptions) options);
    } else {
      sslOptions.setPemCaOptions((PemCaOptions) options);
    }
  }

  protected CaOptions getClientTrustOptions(TS trust) {
    switch (trust) {
      case JKS:
        return new JksOptions().setPath(findFileOnClasspath("tls/client-truststore.jks")).setPassword("wibble");
      case PKCS12:
        return new PfxOptions().setPath(findFileOnClasspath("tls/client-truststore.p12")).setPassword("wibble");
      case PEM:
        return new PemCaOptions().addCertPath(findFileOnClasspath("tls/server-cert.pem"));
      case PEM_CA:
        return new PemCaOptions().addCertPath(findFileOnClasspath("tls/ca/ca-cert.pem"));
      default:
        return null;
    }
  }

  protected KeyCertOptions getClientCertOptions(KS cert) {
    switch (cert) {
      case JKS:
        return new JksOptions().setPath(findFileOnClasspath("tls/client-keystore.jks")).setPassword("wibble");
      case PKCS12:
        return new PfxOptions().setPath(findFileOnClasspath("tls/client-keystore.p12")).setPassword("wibble");
      case PEM:
        return new PemKeyCertOptions().setKeyPath(findFileOnClasspath("tls/client-key.pem")).setCertPath(findFileOnClasspath("tls/client-cert.pem"));
      case PEM_CA:
        return new PemKeyCertOptions().setKeyPath(findFileOnClasspath("tls/client-key.pem")).setCertPath(findFileOnClasspath("tls/client-cert-ca.pem"));
      default:
        return null;
    }
  }

  protected CaOptions getServerTrustOptions(TS trust) {
    switch (trust) {
      case JKS:
        return new JksOptions().setPath(findFileOnClasspath("tls/server-truststore.jks")).setPassword("wibble");
      case PKCS12:
        return new PfxOptions().setPath(findFileOnClasspath("tls/server-truststore.p12")).setPassword("wibble");
      case PEM:
        return new PemCaOptions().addCertPath(findFileOnClasspath("tls/client-cert.pem"));
      case PEM_CA:
        return new PemCaOptions().addCertPath(findFileOnClasspath("tls/ca/ca-cert.pem"));
      default:
        return null;
    }
  }

  protected KeyCertOptions getServerCertOptions(KS cert) {
    switch (cert) {
      case JKS:
        return new JksOptions().setPath(findFileOnClasspath("tls/server-keystore.jks")).setPassword("wibble");
      case PKCS12:
        return new PfxOptions().setPath(findFileOnClasspath("tls/server-keystore.p12")).setPassword("wibble");
      case PEM:
        return new PemKeyCertOptions().setKeyPath(findFileOnClasspath("tls/server-key.pem")).setCertPath(findFileOnClasspath("tls/server-cert.pem"));
      case PEM_CA:
        return new PemKeyCertOptions().setKeyPath(findFileOnClasspath("tls/server-key.pem")).setCertPath(findFileOnClasspath("tls/server-cert-ca.pem"));
      default:
        return null;
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

}
