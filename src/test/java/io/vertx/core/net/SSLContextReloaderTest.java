package io.vertx.core.net;

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

import io.vertx.core.Vertx;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.test.tls.Cert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class SSLContextReloaderTest {
  private static final Logger log = LoggerFactory.getLogger(SSLContextReloaderTest.class);

  private Vertx vertx = Vertx.vertx();

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  @Test
  public void testKeystoreReloaded() throws Exception {
    Path certPath = tmp.newFile().toPath().toAbsolutePath();
    Files.copy(Paths.get("src/test/resources/tls/server-keystore.p12").toAbsolutePath(), certPath, StandardCopyOption.REPLACE_EXISTING);
    HttpServerOptions serverOptions = new HttpServerOptions();
    serverOptions.setKeyStoreOptions(new JksOptions().setPath(certPath.toString()).setPassword("wibble").setReloadCerts(true).setCertRefreshRateInSeconds(1L));
    serverOptions.setTrustStoreOptions(new JksOptions().setPath("tls/server-truststore.p12").setPassword("wibble"));
    serverOptions.setSsl(true);

    HttpServer server = vertx.createHttpServer(serverOptions).requestHandler(req -> {
      req.response().end("success");
    });
    server.exceptionHandler(exp -> log.error("Exception thrown: " + exp));
    server.listen(9438, "localhost");

    Promise<Future<Buffer>> catchResult = Promise.promise();
    CountDownLatch latch = new CountDownLatch(1);
    HttpClient client = getClient(getClientOptions("tls/client-truststore.p12"));
    sendRequestToServerAndFailOnError(client, latch, catchResult);
    latch.await();
    catchResult.future().onFailure(resp -> fail());

    Files.copy(Paths.get("src/test/resources/tls/server-keystore-2.p12").toAbsolutePath(), certPath, StandardCopyOption.REPLACE_EXISTING);

    Promise<Future<Buffer>> catchResultForNewTruststoreCall = Promise.promise();
    CountDownLatch latch2 = new CountDownLatch(1);
    HttpClient clientWithNewTruststore = getClient(getClientOptions("tls/client-truststore-2.p12"));
    while (!catchResultForNewTruststoreCall.future().isComplete()) {
      sendRequestToServerWithoutFailing(clientWithNewTruststore, latch2, catchResultForNewTruststoreCall); // request should pass with new truststore
    }
    latch2.await();
    catchResultForNewTruststoreCall.future().onFailure(resp -> fail());

    Promise<Future<Buffer>> catchExpectedFailure = Promise.promise();
    CountDownLatch latch3 = new CountDownLatch(1);
    HttpClient client3 = getClient(getClientOptions("tls/client-truststore.p12"));
    sendRequestToServerAndFailOnError(client3, latch3, catchExpectedFailure); // request should fail with old client
    latch3.await();
    catchExpectedFailure.future().onSuccess(resp -> fail());
  }

  private HttpClientOptions getClientOptions(String trustStore) {
    HttpClientOptions clientOptions = new HttpClientOptions();
    clientOptions.setSsl(true);
    clientOptions.setKeyStoreOptions(Cert.CLIENT_JKS.get());
    clientOptions.setTrustStoreOptions(new JksOptions().setPath(trustStore).setPassword("wibble"));
    return clientOptions;
  }

  private HttpClient getClient(HttpClientOptions clientOptions) {
    return vertx.createHttpClient(clientOptions);
  }

  private void sendRequestToServerAndFailOnError(HttpClient client, CountDownLatch latch, Promise<Future<Buffer>> catchResult) {
    client.request(HttpMethod.GET, 9438, "localhost", "/", req -> {
      if (req.cause() != null) {
        latch.countDown();
        catchResult.fail(req.cause());
      } else {
        req.result().send(resp -> {
          latch.countDown();
          catchResult.complete(resp.result().body());
          assertEquals(200, resp.result().statusCode());
        });
      }
    });
  }

  private void sendRequestToServerWithoutFailing(HttpClient client, CountDownLatch latch, Promise<Future<Buffer>> catchResult) {
    client.request(HttpMethod.GET, 9438, "localhost", "/", req -> {
      if (req.cause() == null) {
        req.result().send(resp -> {
          latch.countDown();
          if (!catchResult.future().isComplete()) {
            catchResult.complete(resp.result().body());
          }
          assertEquals(200, resp.result().statusCode());
        });
      }
    });
  }
}
