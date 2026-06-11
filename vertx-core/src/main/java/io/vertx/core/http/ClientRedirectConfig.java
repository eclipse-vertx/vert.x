/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.http;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.Unstable;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * HTTP client HTTP redirect config.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject
@Unstable
public class ClientRedirectConfig {

  private int maxRedirects;
  private Set<String> sameOriginBlockedHeaders;
  private Set<String> crossOriginBlockedHeaders;

  public ClientRedirectConfig() {
    this.maxRedirects = HttpClientOptions.DEFAULT_MAX_REDIRECTS;
    this.sameOriginBlockedHeaders = new HashSet<>(HttpClientOptions.DEFAULT_SAME_ORIGIN_REDIRECT_BLOCKED_HEADERS);
    this.crossOriginBlockedHeaders = new HashSet<>(HttpClientOptions.DEFAULT_CROSS_ORIGIN_REDIRECT_BLOCKED_HEADERS);
  }

  public ClientRedirectConfig(ClientRedirectConfig other) {
    this.maxRedirects = other.maxRedirects;
    this.sameOriginBlockedHeaders = new HashSet<>(other.sameOriginBlockedHeaders);
    this.crossOriginBlockedHeaders = new HashSet<>(other.crossOriginBlockedHeaders);
  }

  /**
   * @return the maximum number of redirection a request can follow
   */
  public int getMaxRedirects() {
    return maxRedirects;
  }

  /**
   * Set to {@code maxRedirects} the maximum number of redirection a request can follow.
   *
   * @param maxRedirects the maximum number of redirection
   * @return a reference to this, so the API can be used fluently
   */
  public ClientRedirectConfig setMaxRedirects(int maxRedirects) {
    this.maxRedirects = maxRedirects;
    return this;
  }

  /**
   * @return the set of blocked HTTP headers on a same-origin redirection, the default being {@link HttpClientOptions#DEFAULT_SAME_ORIGIN_REDIRECT_BLOCKED_HEADERS}
   */
  public Set<String> getSameOriginBlockedHeaders() {
    return sameOriginBlockedHeaders;
  }

  /**
   * Update the set of blocked HTTP headers on a same-origin redirection.
   *
   * @param sameOriginBlockedHeaders the new set of headers to block
   * @return a reference to this, so the API can be used fluently
   */
  public ClientRedirectConfig setSameOriginBlockedHeaders(Set<String> sameOriginBlockedHeaders) {
    this.sameOriginBlockedHeaders = Objects.requireNonNull(sameOriginBlockedHeaders);
    return this;
  }

  /**
   * @return the set of blocked HTTP headers on a cross-origin redirection, the default being {@link HttpClientOptions#DEFAULT_CROSS_ORIGIN_REDIRECT_BLOCKED_HEADERS}
   */
  public Set<String> getCrossOriginBlockedHeaders() {
    return crossOriginBlockedHeaders;
  }

  /**
   * Update the set of blocked HTTP headers on a cross-origin redirection.
   *
   * @param crossOriginBlockedHeaders the new set of headers to block
   * @return a reference to this, so the API can be used fluently
   */
  public ClientRedirectConfig setCrossOriginBlockedHeaders(Set<String> crossOriginBlockedHeaders) {
    this.crossOriginBlockedHeaders = Objects.requireNonNull(crossOriginBlockedHeaders);
    return this;
  }
}
