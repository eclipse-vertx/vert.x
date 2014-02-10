/*
 * Copyright (c) 2011-2013 The original author or authors
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


package org.vertx.java.platform.impl.resolver.requesters;

import org.vertx.java.core.Handler;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;

public class GetRequest extends ResolverRequest {

  public GetRequest(HttpClient httpClient) {
    super(httpClient);
  }

  public GetRequest(HttpClient httpClient, String username, String password) {
    super(httpClient, username, password);
  }

  @Override
  protected HttpClientRequest applyMethod(String uri, Handler<HttpClientResponse> respHandler) {
    return client.get(uri, respHandler);
  }

  @Override
  public ResolverRequest cloneWithNewClient(HttpClient client) {
    HttpHandlers oldHandlers = new HttpHandlers(handlers);
    GetRequest getRequest = new GetRequest(client);
    getRequest.setHandlers(oldHandlers);
    return getRequest;
  }
}
