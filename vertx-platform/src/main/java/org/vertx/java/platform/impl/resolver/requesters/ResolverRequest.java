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
import org.vertx.java.core.Vertx;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.json.impl.Base64;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

public abstract class ResolverRequest {

  private static final Logger log = LoggerFactory.getLogger(ResolverRequest.class);

  private static final String HTTP_PROXY_HOST_PROP_NAME = "http.proxyHost";
  private static final String HTTP_PROXY_PORT_PROP_NAME = "http.proxyPort";

  private static final String HTTP_BASIC_AUTH_USER_PROP_NAME = "http.authUser";
  private static final String HTTP_BASIC_AUTH_PASSWORD_PROP_NAME = "http.authPass";

  protected static final String proxyHost = getProxyHost();
  protected static final int proxyPort = getProxyPort();

  protected HttpClient client;
  private String username;
  private String password;
  private String scheme;
  private String host;
  private int port;

  protected HttpHandlers handlers;

  private final Handler<HttpClientResponse> responseHandler = new Handler<HttpClientResponse>() {
    public void handle(HttpClientResponse resp) {
      if (!handlers.handle(resp)) {
        unknownStatusHandler.handle(resp);
      }
    }
  };

  // Default unknown status code handler just logs the problem
  private Handler<HttpClientResponse> unknownStatusHandler = new Handler<HttpClientResponse>() {
    public void handle(HttpClientResponse resp) {
      log.info(resp.statusCode() + " - " + resp.statusMessage());
    }
  };

  // default Exception Handler
  private static final Handler<Throwable> defaultExceptionHandler = new Handler<Throwable>() {
    public void handle(Throwable t) {
      log.error("Exception thrown during request", t);
    }
  };

  public ResolverRequest(HttpClient httpClient) {
    this(httpClient, null, null);
  }

  public ResolverRequest(HttpClient httpClient, String username, String password) {
    this.handlers = new HttpHandlers();
    this.client = httpClient;
    this.scheme = httpClient.isSSL() ? "https" : "http";
    this.host = httpClient.getHost();
    this.port = httpClient.getPort();
    this.username = username;
    this.password = password;
    setDefaultExceptionHandler(defaultExceptionHandler);
  }


  public static HttpClient createClient(Vertx vertx, String scheme, String host, int port) {
    HttpClient client = vertx.createHttpClient();
    client.setKeepAlive(false); // Not all servers will allow keep alive connections
    if (proxyHost != null) {
      client.setHost(proxyHost);
      if (proxyPort != 80) {
        client.setPort(proxyPort);
      } else {
        client.setPort(80);
      }
    } else {
      client.setHost(host);
      client.setPort(port);
    }
    if (scheme.equals("https")) {
      client.setSSL(true);
    }

    return client;
  }

  // Keeps the old handlers
  public abstract ResolverRequest cloneWithNewClient(HttpClient client);

  protected abstract HttpClientRequest applyMethod(String uri, Handler<HttpClientResponse> responseHandler);

  public void send(String uri) {
    uri = applyProxy(scheme, host, port, uri);
    HttpClientRequest req = applyMethod(uri, responseHandler);
    putHeaders(host, getProxyHost(), req);
    req.end();
  }

  protected String applyProxy(String scheme, String host, int port, String uri) {
    if (proxyHost != null) {
      // We use an absolute URI
      uri = scheme + "://" + host + ":" + port + uri;
    }
    return uri;
  }

  private void putHeaders(String host, String proxyHost, HttpClientRequest req) {
    if (proxyHost != null) {
      req.putHeader("host", proxyHost);
    } else {
      req.putHeader("host", host);
    }

    if (getBasicAuth() != null) {
      log.debug("Using HTTP Basic Authorization");
      req.putHeader("Authorization", "Basic " + getBasicAuth());
    }

    req.putHeader("user-agent", "Vert.x Module Installer");
  }

  private String getBasicAuth() {
    if (username != null && password != null) {
      return autoInfo(username, password);
    } else {
      String user = System.getProperty(HTTP_BASIC_AUTH_USER_PROP_NAME);
      if (user != null) {
        String pass = System.getProperty(HTTP_BASIC_AUTH_PASSWORD_PROP_NAME);
        if (pass != null) {
          return autoInfo(user, pass);
        }
      }
    }
    return null;
  }

  public void setDefaultExceptionHandler(Handler<Throwable> defaultExceptionHandler) {
    client.exceptionHandler(defaultExceptionHandler);
  }

  public void setUnknownStatusHandler(Handler<HttpClientResponse> unknownStatusHandler) {
    this.unknownStatusHandler = unknownStatusHandler;
  }

  private String autoInfo(String user, String pass) {
    return Base64.encodeBytes((user + ":" + pass).getBytes());
  }

  private static String getProxyHost() {
    return System.getProperty(HTTP_PROXY_HOST_PROP_NAME);
  }

  private static int getProxyPort() {
    return Integer.parseInt(System.getProperty(HTTP_PROXY_PORT_PROP_NAME, "80"));
  }

  public void addHandler(int statusCode, Handler<HttpClientResponse> handler) {
    handlers.addHandler(statusCode, handler);
  }

  public void removeHandlersWithStatusCode(int statusCode) {
    handlers.removeHandlersWithStatusCode(statusCode);
  }

  public void setHandlers(HttpHandlers handlers) {
    this.handlers = handlers;
  }
}
