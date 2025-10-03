/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.net;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.buffer.Buffer;

/**
 * Details of a Quic connection close.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject
public class QuicConnectionClose {

  private int error;
  private Buffer reason;

  /**
   * @return the close error code
   */
  public int getError() {
    return error;
  }

  /**
   * Set the close error code.
   *
   * @param error the error code
   * @return this instance
   */
  public QuicConnectionClose setError(int error) {
    this.error = error;
    return this;
  }

  /**
   * @return the close reason
   */
  public Buffer getReason() {
    return reason;
  }

  /**
   * Set the close reason
   *
   * @param reason the close reason
   * @return this instance
   */
  public QuicConnectionClose setReason(Buffer reason) {
    this.reason = reason;
    return this;
  }
}
