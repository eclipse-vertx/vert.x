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

package io.vertx.core.file;

import io.vertx.codegen.annotations.VertxGen;

/**
 * Represents properties of a file on the file system.
 * <p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
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
