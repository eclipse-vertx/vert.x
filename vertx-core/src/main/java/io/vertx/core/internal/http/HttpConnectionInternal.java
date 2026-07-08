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
package io.vertx.core.internal.http;

import io.vertx.core.http.HttpConnection;

/**
 * Extends to expose internal methods that are necessary for integration.
 */
public interface HttpConnectionInternal extends HttpConnection {

  /**
   * Get an attachment previously stored with {@link #set(Object, Object)}.
   * <p/>
   * Attachments are scoped to the lifecycle of the connection: they are discarded when the
   * connection closes and do not need to be cleaned up manually.
   *
   * @param key the attachment key
   * @return the attachment value, or {@code null} if absent
   */
  <T> T get(Object key);

  /**
   * Store an attachment on this connection, associated with {@code key}.
   *
   * @param key the attachment key
   * @param value the attachment value
   */
  void set(Object key, Object value);

}
