/*
 * Copyright (c) 2011-2013 The original author or authors
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

package org.vertx.java.core.eventbus;

import org.vertx.java.core.VertxException;

public class ReplyException extends VertxException {

  private final ReplyFailure failureType;
  private final int failureCode;

  public ReplyException(ReplyFailure failureType, int failureCode, String message) {
    super(message);
    this.failureType = failureType;
    this.failureCode = failureCode;
  }

  public ReplyException(ReplyFailure failureType, String message) {
    super(message);
    this.failureType = failureType;
    this.failureCode = -1;
  }

  public ReplyException(ReplyFailure failureType) {
    super((String)null);
    this.failureType = failureType;
    this.failureCode = -1;
  }

  public ReplyFailure failureType() {
    return failureType;
  }

  public int failureCode() {
    return failureCode;
  }

}
