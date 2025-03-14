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

package examples.h3devexamples;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
public class HTTP3ClientExamplesAsyncTestCase {
  public void example03Local(Vertx vertx) {

    HttpClientOptions options = new HttpClientOptions().
      setSsl(true).
      setIdleTimeout(1).
      setReadIdleTimeout(1).
      setWriteIdleTimeout(1).
      setIdleTimeoutUnit(TimeUnit.HOURS).
      setUseAlpn(true).
      setForceSni(true).
      setVerifyHost(false).
      setTrustAll(true).
      setProtocolVersion(HttpVersion.HTTP_3);

    options
      .getSslOptions()
      .setSslHandshakeTimeout(1)
      .setSslHandshakeTimeoutUnit(TimeUnit.HOURS);
    HttpClient client = vertx.createHttpClient(options);

    String path = "/";
    int port = 8090;
    String host = "localhost";

    AtomicInteger requests = new AtomicInteger();

    int n = 1;

      client.request(HttpMethod.GET, port, host, path)
      .compose(req -> {
        System.out.println("sending request ...");
        return req.send();
      })
      .compose(resp -> {
        System.out.println("receiving resp ...");
          assert 200 == resp.statusCode();
          return resp.end();
        }
      )
      .onSuccess(event -> {
        System.out.println("testComplete() called! ");
      })
      .onComplete(event -> requests.incrementAndGet())
      .onFailure(Throwable::printStackTrace)
    ;

    while (requests.get() != n) {
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
    vertx.close();

  }

  public static void main(String[] args) {
    Vertx vertx =
      Vertx.vertx(new VertxOptions().setBlockedThreadCheckInterval(1_000_000_000));
    new HTTP3ClientExamplesAsyncTestCase().example03Local(vertx);
  }
}
