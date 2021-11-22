/*
 * Copyright (c) 2011-2021 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.http.impl;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.impl.CloseFuture;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.shareddata.Shareable;

public class HttpClientHolder implements Shareable {

  private final int count;
  private final HttpClient client;
  private final CloseFuture closeFuture;

  public HttpClientHolder() {
    count = 1;
    client = null;
    closeFuture = null;
  }

  private HttpClientHolder(int count, HttpClient client, CloseFuture closeFuture) {
    this.count = count;
    this.client = client;
    this.closeFuture = closeFuture;
  }

  public HttpClient get() {
    return client;
  }

  public HttpClientHolder increment() {
    return client == null ? null : new HttpClientHolder(count + 1, client, closeFuture);
  }

  public HttpClientHolder init(VertxInternal vertx, HttpClientOptions options) {
    CloseFuture closeFuture = new CloseFuture();
    HttpClient client = new HttpClientImpl(vertx, options, closeFuture);
    return new HttpClientHolder(count, client, closeFuture);
  }

  public HttpClientHolder decrement() {
    return count == 1 ? null : new HttpClientHolder(count - 1, client, closeFuture);
  }

  public Future<Void> close() {
    return CompositeFuture.all(client.close(), closeFuture.close()).mapEmpty();
  }

  @Override
  public String toString() {
    return "HttpClientHolder{" +
      "count=" + count +
      '}';
  }
}
