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
import io.vertx.core.impl.Arguments;

/**
 * HTTP client HTTP redirect config.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject
@Unstable
public class ClientRedirectConfig {

  private int maxRedirects;
  private int maxBufferedSize;

  public ClientRedirectConfig() {
    this.maxRedirects = HttpClientOptions.DEFAULT_MAX_REDIRECTS;
    this.maxBufferedSize = HttpClientOptions.DEFAULT_MAX_REDIRECT_BUFFERED_SIZE;
  }

  public ClientRedirectConfig(ClientRedirectConfig other) {
    this.maxRedirects = other.maxRedirects;
    this.maxBufferedSize = other.maxBufferedSize;
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
   * @return the maximum size in bytes of the redirect buffer when redirecting QUERY requests
   */
  public int getMaxBufferedSize() {
    return maxBufferedSize;
  }

  /**
   * Set the maximum size of the redirect buffer in bytes when redirecting QUERY requests.
   *
   * @param maxBufferedSize the maximum buffer size
   * @return a reference to this, so the API can be used fluently
   */
  public ClientRedirectConfig setMaxBufferedSize(int maxBufferedSize) {
    Arguments.require(maxBufferedSize >= 0, "Max redirect buffer size must be >= 0");
    this.maxBufferedSize = maxBufferedSize;
    return this;
  }
}
