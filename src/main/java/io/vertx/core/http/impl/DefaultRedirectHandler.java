/*
 * Copyright (c) 2011-2024 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.http.impl;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.*;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * <p>Default implementation of an HTTP redirection handler, this handler:</p>
 *
 * <ul>
 *   <li>redirects the following status codes
 *   <ul>
 *     <li>303: always redirected as a GET method</li>
 *     <li>301, 302, 307, 308: redirected only for GET and HEAD methods</li>
 *   </ul>
 *   </li>
 *   <li>removes the following headers
 *   <ul>
 *     <li>same-origin: <i>cookie</i>, <i>content-length</i></li>
 *     <li>cross-origin: <i>authorization</i>, <i>cookie</i>, <i>proxy-authorization</i>, <i>content-length</i></li>
 *   </ul>
 *   </li>
 * </ul>
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class DefaultRedirectHandler implements Function<HttpClientResponse, Future<RequestOptions>> {

  private final List<String> crossOriginBlockedHeaders;
  private final List<String> sameOriginBlockedHeaders;

  /**
   * Create a redirect handler configured with stripped headers.
   *
   * @param sameOriginBlockedHeaders the same-origin stripped headers
   * @param crossOriginBlockedHeaders the cross-origin stripped headers
   */
  public DefaultRedirectHandler(List<String> sameOriginBlockedHeaders, List<String> crossOriginBlockedHeaders) {
    this.crossOriginBlockedHeaders = new ArrayList<>(crossOriginBlockedHeaders);
    this.sameOriginBlockedHeaders = new ArrayList<>(sameOriginBlockedHeaders);
  }

  /**
   * Returns whether the status code should be redirected.
   *
   * @implNote implements the default documented policy
   * @param statusCode the HTTP status code
   * @return whether to redirect
   */
  protected boolean wantRedirect(int statusCode) {
    return statusCode == 301 || statusCode == 302 || statusCode == 303 || statusCode == 307 || statusCode == 308;
  }

  /**
   * Returns the list of stripped headers.
   *
   * @implNote returns {@link #sameOriginBlockedHeaders} when {@code sameOrigin == true}, {@link #crossOriginBlockedHeaders} when {@code sameOrigin == false}
   * @param sameOrigin whether the request is a same-origin or cross-origin redirection.
   * @return the list of stripped headers
   */
  protected List<String> strippedHeaders(boolean sameOrigin) {
    return sameOrigin ? sameOriginBlockedHeaders : crossOriginBlockedHeaders;
  }

  /**
   * Filter the {@code headers} before redirection.
   *
   * @implNote strip headers according to the documented policy
   * @param sameOrigin whether the request is a same-origin or cross-origin redirection.
   * @param headers the request headers of the redirected request
   */
  protected void filterHeaders(boolean sameOrigin, MultiMap headers) {
    List<String> strippedHeaders = strippedHeaders(sameOrigin);
    for (String s : strippedHeaders) {
      headers.remove(s);
    }
  }

  @Override
  public Future<RequestOptions> apply(HttpClientResponse response) {
    try {
      int statusCode = response.statusCode();
      String location = response.getHeader(HttpHeaders.LOCATION);
      if (location != null && wantRedirect(statusCode)) {
        HttpClientRequest request = response.request();
        HttpMethod m = request.getMethod();
        if (statusCode == 303) {
          m = HttpMethod.GET;
        } else if (m != HttpMethod.GET && m != HttpMethod.HEAD) {
          return null;
        }
        URI redirectUri = HttpUtils.resolveURIReference(request.absoluteURI(), location);
        boolean ssl;
        int port = redirectUri.getPort();
        String protocol = redirectUri.getScheme();
        char chend = protocol.charAt(protocol.length() - 1);
        if (chend == 'p') {
          ssl = false;
          if (port == -1) {
            port = 80;
          }
        } else if (chend == 's') {
          ssl = true;
          if (port == -1) {
            port = 443;
          }
        } else {
          return null;
        }
        String redirectRequestUri = redirectUri.getPath();
        if (redirectRequestUri == null || redirectRequestUri.isEmpty()) {
          redirectRequestUri = "/";
        }
        String redirectQueryString = redirectUri.getQuery();
        if (redirectQueryString != null) {
          redirectRequestUri += "?" + redirectQueryString;
        }
        MultiMap headers = request.headers();
        boolean sameOrigin = isSameOrigin(request, ssl, redirectUri.getHost(), port);
        filterHeaders(sameOrigin, headers);
        RequestOptions options = new RequestOptions();
        options.setMethod(m);
        options.setHost(redirectUri.getHost());
        options.setPort(port);
        options.setSsl(ssl);
        options.setURI(redirectRequestUri);
        options.setHeaders(headers);
        return Future.succeededFuture(options);
      }
      return null;
    } catch (Exception e) {
      return Future.failedFuture(e);
    }
  }

  private static boolean isSameOrigin(HttpClientRequest request, boolean ssl, String host, int port) {
    int defaultPort = ssl ? 443 : 80;
    int requestAuthority = request.getPort() == -1 ? defaultPort : request.getPort();
    return request.connection().isSsl() == ssl && request.getHost().equals(host) && requestAuthority == port;
  }
}
