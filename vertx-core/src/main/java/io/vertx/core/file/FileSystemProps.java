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
 * Represents properties of the file system.
 *
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@DataObject
public interface FileSystemProps {

  /**
   * @return The name of this file store
   */
  String name();

  /**
   * @return The total space on the file system, in bytes
   */
  long totalSpace();

  /**
   * @return The total un-allocated space on the file system, in bytes
   */
  long unallocatedSpace();

  /**
   * @return The total usable space on the file system, in bytes
   */
  long usableSpace();
}
