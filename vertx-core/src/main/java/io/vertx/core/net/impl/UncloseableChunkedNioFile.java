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
package io.vertx.core.net.impl;

import io.netty.handler.stream.ChunkedNioFile;

import java.io.IOException;
import java.nio.channels.FileChannel;

/**
 * A chunked NIO file that does close the underlying resource, letting the file user control the lifecycle of
 * the file descriptor.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class UncloseableChunkedNioFile extends ChunkedNioFile {

  public UncloseableChunkedNioFile(FileChannel in, long offset, long length) throws IOException {
    super(in, offset, length, 8192);
  }

  @Override
  public void close() throws Exception {
  }
}
