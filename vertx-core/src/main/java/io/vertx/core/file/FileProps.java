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

package io.vertx.core.file;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.VertxGen;

/**
 * Represents properties of a file on the file system.
 * <p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@DataObject
public interface FileProps {

  /**
   * The date the file was created
   */
  long creationTime();

  /**
   * The date the file was last accessed
   */
  long lastAccessTime();

  /**
   * The date the file was last modified
   */
  long lastModifiedTime();

  /**
   * Is the file a directory?
   */
  boolean isDirectory();

  /**
   * Is the file some other type? (I.e. not a directory, regular file or symbolic link)
   */
  boolean isOther();

  /**
   * Is the file a regular file?
   */
  boolean isRegularFile();

  /**
   * Is the file a symbolic link?
   */
  boolean isSymbolicLink();

  /**
   * The size of the file, in bytes
   */
  long size();

}
