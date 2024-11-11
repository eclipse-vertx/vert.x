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

package examples;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;

import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
public class HTTP3ClientExamples {
  private final static String okText =
    "\n  ____   _  __  \n"+
    " / __ \\ | |/ /  \n"+
    "| |  | ||   <   \n"+
    "| |  | || |\\ \\  \n"+
    "| |__| || | \\ \\ \n"+
    " \\____/ |_|  \\_\\ \n";

  public void example02Local(Vertx vertx) {

    String path = "/";
    int port = 8090;
    String host = "localhost";

    HttpClientOptions options = new HttpClientOptions().
      setSsl(true).
      setIdleTimeout(1).
      setReadIdleTimeout(1).
      setWriteIdleTimeout(1).
      setIdleTimeoutUnit(TimeUnit.HOURS).
      setUseAlpn(true).
      setForceSni(true).
      setDefaultHost(host).
      setVerifyHost(false).
      setTrustAll(true).
      setProtocolVersion(HttpVersion.HTTP_3);

    options
      .getSslOptions()
      .setSslHandshakeTimeout(1)
      .setSslHandshakeTimeoutUnit(TimeUnit.HOURS);


    HttpClient client = vertx.createHttpClient(options);

    System.out.print(String.format("Trying to fetch %s:%s%s\n", host, port,
      path));


    client.request(HttpMethod.GET, port, host, path)
      .compose(req -> req.send(" M1 "))
      .compose(HttpClientResponse::body)
      .onSuccess(body -> System.out.println("M1 The response body is: " + body))
      .onFailure(Throwable::printStackTrace)
    ;

    client.request(HttpMethod.GET, port, host, path)
      .compose(req -> req.send(" M2 "))
      .compose(HttpClientResponse::body)
      .onSuccess(body -> System.out.println("M2 The response body is: " + body))
      .onFailure(Throwable::printStackTrace)
    ;

    try {
      Thread.sleep(1_000_000);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  public static void main(String[] args) throws Exception {
    VertxOptions options = new VertxOptions()
      .setBlockedThreadCheckInterval(1_000_000_000);

    Vertx vertx = Vertx.vertx(options);
    new HTTP3ClientExamples().example02Local(vertx);
  }
}
