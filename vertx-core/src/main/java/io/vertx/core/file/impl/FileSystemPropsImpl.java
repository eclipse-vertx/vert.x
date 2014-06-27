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

package io.vertx.core.file.impl;

import io.vertx.core.file.FileSystemProps;

public class FileSystemPropsImpl implements FileSystemProps {

  private final long totalSpace;
  private final long unallocatedSpace;
  private final long usableSpace;

  public FileSystemPropsImpl(long totalSpace, long unallocatedSpace, long usableSpace) {
    this.totalSpace = totalSpace;
    this.unallocatedSpace = unallocatedSpace;
    this.usableSpace = usableSpace;
  }

  @Override
  public long totalSpace() {
    return totalSpace;
  }

  @Override
  public long unallocatedSpace() {
    return unallocatedSpace;
  }

  @Override
  public long usableSpace() {
    return usableSpace;
  }
}
