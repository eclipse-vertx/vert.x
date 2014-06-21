/*
 * Copyright (c) 2014 The original author or authors.
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

package org.vertx.java.core.http.impl;

import org.vertx.java.core.VertxException;

/**
 * Exception thrown by the ConnectionPool class
 *
 * @author <a href="mailto:wangjunbo924@gmail.com">Junbo Wang</a>
 */
public class ConnectionPoolTooBusyException extends VertxException {

  /**
   * Construct a {@code ConnectionPoolTooBusyException} with a message as specified by {@code msg}
   */
  public ConnectionPoolTooBusyException(String msg) {
    super(msg);
  }
}
