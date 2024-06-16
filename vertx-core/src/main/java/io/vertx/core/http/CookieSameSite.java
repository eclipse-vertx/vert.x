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
package io.vertx.core.http;

import io.vertx.codegen.annotations.VertxGen;

/**
 * Represents the Cookie SameSite policy to be used. For more info <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Cookies#SameSite_cookies">https://developer.mozilla.org/en-US/docs/Web/HTTP/Cookies#SameSite_cookies</a>.
 *
 * @author <a href="mailto:plopes@redhat.com">Paulo Lopes</a>
 */
@VertxGen
public enum CookieSameSite {

  /**
   * The browser will send cookies with both cross-site requests and same-site requests.
   */
  NONE("None"),

  /**
   * The browser will only send cookies for same-site requests (requests originating from the site that set the cookie).
   * If the request originated from a different URL than the URL of the current location, none of the cookies tagged
   * with the Strict attribute will be included.
   */
  STRICT("Strict"),

  /**
   * Same-site cookies are withheld on cross-site subrequests, such as calls to load images or frames, but will be sent
   * when a user navigates to the URL from an external site; for example, by following a link.
   */
  LAX("Lax");

  /**
   * Just use a human friendly label instead of the capitalized name.
   */
  private final String label;

  CookieSameSite(String label) {
    this.label = label;
  }

  @Override
  public String toString() {
    return label;
  }
}
