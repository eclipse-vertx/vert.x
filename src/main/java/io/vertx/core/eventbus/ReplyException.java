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
   * Create a ReplyException with all attributes.
   *
   * @param failureType  the failure type
   * @param failureCode  the failure code (e.g. 404)
   * @param message  the failure message
   */
  public ReplyException(ReplyFailure failureType, int failureCode, String message, boolean noStackTrace) {
    super(message, noStackTrace);
    this.failureType = failureType;
    this.failureCode = failureCode;
  }

  /**
   * Create a ReplyException
   *
   * @param failureType  the failure type
   * @param failureCode  the failure code
   * @param message  the failure message
   */
  public ReplyException(ReplyFailure failureType, int failureCode, String message) {
    super(message, true);
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
    this(failureType, -1, message);
  }

  /**
   * Create a ReplyException
   *
   * @param failureType  the failure type
   */
  public ReplyException(ReplyFailure failureType) {
    this(failureType, -1, null);
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

  @Override
  public String toString() {
    String message = getMessage();
    return "(" + failureType + "," + failureCode + ") " + (message != null ? message : "");
  }
}
