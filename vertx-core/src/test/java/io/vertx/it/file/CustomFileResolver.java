/*
 * Copyright (c) 2011-2021 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.it.file;

import io.vertx.core.spi.file.FileResolver;

import java.io.File;
import java.io.IOException;

public class CustomFileResolver implements FileResolver {

  private boolean closed;

  @Override
  public void close() throws IOException {
    closed = true;
  }

  @Override
  public File resolve(String fileName) {
    return new File(fileName);
  }
}
