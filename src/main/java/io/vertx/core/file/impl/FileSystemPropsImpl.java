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
