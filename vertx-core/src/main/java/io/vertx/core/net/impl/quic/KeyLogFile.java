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
package io.vertx.core.net.impl.quic;

import io.netty.handler.codec.quic.BoringSSLKeylog;

import javax.net.ssl.SSLEngine;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class KeyLogFile implements BoringSSLKeylog {

  private final File file;

  KeyLogFile(File file) {
    this.file = file;
  }

  @Override
  public void logKey(SSLEngine engine, String key) {
    try (PrintWriter out = new PrintWriter(new FileWriter(file, StandardCharsets.UTF_8, true))) {
      out.println(key);
      out.flush();
    } catch (Exception ignore) {
    }
  }
}
