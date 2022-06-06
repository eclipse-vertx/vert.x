/*
* Copyright (c) 2011-2022 Contributors to the Eclipse Foundation
*
* This program and the accompanying materials are made available under the
* terms of the Eclipse Public License 2.0 which is available at
* http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
* which is available at https://www.apache.org/licenses/LICENSE-2.0.
*
* SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
*/

package io.vertx.core.net.impl;

import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.function.Supplier;

import io.vertx.core.VertxException;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.VertxInternal;

public class KeyStoreFileSpi extends DelegatingKeyStoreSpi {

  private static final Logger log = LoggerFactory.getLogger(KeyStoreFileSpi.class);

  private final VertxInternal vertx;
  private final String type;
  private final String provider;
  private final String path;
  private final String password;
  private final String alias;
  private FileTime lastModified;

  public KeyStoreFileSpi(VertxInternal vertx, String type, String provider, String path, String password, String alias) throws Exception {
    if (password == null) {
      throw new VertxException("Password must not be null");
    }

    this.vertx = vertx;
    this.type = type;
    this.provider = provider;
    this.path = path;
    this.password = password;
    this.alias = alias;

    refresh();
  }

  /**
   * Reload keystore if it was modified on disk since it was last loaded.
   */
  void refresh() throws Exception {
    // If keystore has been previously loaded, check the modification timestamp to decide if reload is needed.
    if ((lastModified != null) && (lastModified.compareTo(Files.getLastModifiedTime(Paths.get(path))) > 0)) {
      // File was not modified since last reload: do nothing.
      return;
    }

    // Load keystore from disk.
    Supplier<Buffer> value;
    value = () -> vertx.fileSystem().readFileBlocking(path);
    setKeyStoreDelegate(KeyStoreHelper.loadKeyStore(type, provider, password, value, alias));
    this.lastModified = Files.getLastModifiedTime(Paths.get(path));
  }

}
