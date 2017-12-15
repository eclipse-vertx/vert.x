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

package io.vertx.core.json;

/**
 * Instances of this Exception are thrown if failed to decode a JSON string, because of invalid JSON.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DecodeException extends RuntimeException {

  public DecodeException() {
  }

  public DecodeException(String message) {
    super(message);
  }

  public DecodeException(String message, Throwable cause) {
    super(message, cause);
  }
}
