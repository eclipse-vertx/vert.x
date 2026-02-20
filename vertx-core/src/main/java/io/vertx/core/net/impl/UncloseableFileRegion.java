/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.net.impl;

import io.netty.channel.DefaultFileRegion;

import java.io.File;
import java.nio.channels.FileChannel;

/**
 * A file region that does close the underlying resource, letting the file user control the lifecycle of
 * the file descriptor.
 *
 * @author <a href="mailto:dreamlike.vertx@gmail.com">MengYang Li</a>
 */
public class UncloseableFileRegion extends DefaultFileRegion {
  public UncloseableFileRegion(FileChannel fileChannel, long position, long count) {
    super(fileChannel, position, count);
  }

  public UncloseableFileRegion(File file, long position, long count) {
    super(file, position, count);
  }

  @Override
  protected void deallocate() {

  }
}
