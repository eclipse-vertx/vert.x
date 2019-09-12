/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.http.impl;

import io.vertx.core.http.Cookie;

/**
 * Add specific state for managing the cookie on the server
 */
public interface ServerCookie extends Cookie {

  /**
   * Has the cookie been changed? Changed cookieMap will be saved out in the response and sent to the browser.
   *
   * @return true  if changed
   */
  boolean isChanged();

  /**
   * Set the cookie as being changed. Changed will be true for a cookie just created, false by default if just
   * read from the request
   *
   * @param changed  true if changed
   */
  void setChanged(boolean changed);

  /**
   * Has this Cookie been sent from the User Agent (the browser)? or was created during the executing on the request.
   *
   * @return true if the cookie comes from the User Agent.
   */
  boolean isFromUserAgent();

}
