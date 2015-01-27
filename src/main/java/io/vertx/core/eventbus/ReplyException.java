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

package io.vertx.core.eventbus;

import io.vertx.core.VertxException;

/**
 * Represents the failure of a message send.
 * <p>
 * If a message was sent specifying a reply handler and the message delivery fails, a failure will be provided to the
 * reply handler and the cause of the failure will be an instance of this.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ReplyException extends VertxException {

  private final ReplyFailure failureType;
  private final int failureCode;

  /**
   * Create a ReplyException
   *
   * @param failureType  the failure type
   * @param failureCode  the failure code
   * @param message  the failure message
   */
  public ReplyException(ReplyFailure failureType, int failureCode, String message) {
    super(message);
    this.failureType = failureType;
    this.failureCode = failureCode;
  }

  /**
   * Create a ReplyException
   *
   * @param failureType  the failure type
   * @param message  the failure message
   */
  public ReplyException(ReplyFailure failureType, String message) {
    super(message);
    this.failureType = failureType;
    this.failureCode = -1;
  }

  /**
   * Create a ReplyException
   *
   * @param failureType  the failure type
   */
  public ReplyException(ReplyFailure failureType) {
    super((String)null);
    this.failureType = failureType;
    this.failureCode = -1;
  }

  /**
   * Get the failure type for the message
   *
   * @return  the failure type
   */
  public ReplyFailure failureType() {
    return failureType;
  }

  /**
   * Get the failure code for the message
   *
   * @return  the failure code
   */
  public int failureCode() {
    return failureCode;
  }

}
