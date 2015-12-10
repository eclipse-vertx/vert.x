/*
 * Copyright (c) 2011-2014 The original author or authors
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
