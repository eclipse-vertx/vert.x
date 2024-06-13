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

package io.vertx.impl.core.http;

import io.vertx.core.http.Cookie;
import io.vertx.core.http.CookieSameSite;
import io.vertx.core.http.impl.CookieImpl;


/**
 * Vert.x cookie implementation
 *
 * @author <a href="http://pmlopes@gmail.com">Paulo Lopes</a>
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ServerCookieImpl extends CookieImpl {

  private boolean changed;

  /**
   * Internal constructor, only used by the CookieJar.
   *
   * @param nettyCookie the underlying cookie object
   */
  ServerCookieImpl(io.netty.handler.codec.http.cookie.Cookie nettyCookie) {
    super(nettyCookie);
  }

  @Override
  public Cookie setValue(final String value) {
    this.changed = true;
    return super.setValue(value);
  }

  @Override
  public Cookie setDomain(final String domain) {
    this.changed = true;
    return super.setDomain(domain);
  }

  @Override
  public Cookie setPath(final String path) {
    this.changed = true;
    return super.setPath(path);
  }

  @Override
  public Cookie setMaxAge(final long maxAge) {
    this.changed = true;
    return super.setMaxAge(maxAge);
  }

  @Override
  public Cookie setSecure(final boolean secure) {
    this.changed = true;
    return super.setSecure(secure);
  }

  @Override
  public Cookie setHttpOnly(final boolean httpOnly) {
    this.changed = true;
    return super.setHttpOnly(httpOnly);
  }

  @Override
  public Cookie setSameSite(final CookieSameSite sameSite) {
    this.changed = true;
    return super.setSameSite(sameSite);
  }

  public boolean isChanged() {
    return changed;
  }

  public boolean isFromUserAgent() {
    return true;
  }
}
