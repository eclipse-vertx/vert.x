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

package io.vertx.core.http;

import io.vertx.core.VertxException;

/**
 * Represents a failure to add a HttpClientRequest to the wait queue on an ConnectionManager.
 * <p>
 * If an HttpClient receives a request but is already handling maxPoolSize requests it will attempt to put the new
 * request on it's wait queue.  If the maxWaitQueueSize is set and the new request would cause the wait queue to exceed
 * that size then the request will receive this exception.
 *
 * @author <a href="mailto:ddimensia@gmail.com">Gil Markham</a>
 * @author <a href="mailto:wangjunbo924@gmail.com">Junbo Wang</a>
 */
public class ConnectionPoolTooBusyException extends VertxException {

  /**
   * Create a ConnectionPoolTooBusyException
   *
   * @param message the failure message
   */
  public ConnectionPoolTooBusyException(String message) {
    super(message);
  }

}
