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

package io.vertx.core.file;

import io.vertx.core.VertxException;

/**
 * Exception thrown by the FileSystem class
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class FileSystemException extends VertxException {

  public FileSystemException(String message) {
    super(message);
  }

  public FileSystemException(String message, Throwable cause) {
    super(message, cause);
  }

  public FileSystemException(Throwable cause) {
    super(cause);
  }
}
