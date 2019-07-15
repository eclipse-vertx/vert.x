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
package io.vertx.core.net.impl;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Used for lazy loading the default JDK cipher suite.
 */
class DefaultJDKCipherSuite {

  private static final List<String> DEFAULT_JDK_CIPHER_SUITE;

  static {
    ArrayList<String> suite = new ArrayList<>();
    try {
      SSLContext context = SSLContext.getInstance("TLS");
      context.init(null, null, null);
      SSLEngine engine = context.createSSLEngine();
      Collections.addAll(suite, engine.getEnabledCipherSuites());
    } catch (Throwable e) {
      suite = null;
    }
    DEFAULT_JDK_CIPHER_SUITE = suite != null ? Collections.unmodifiableList(suite) : null;
  }

  static List<String> get() {
    return DEFAULT_JDK_CIPHER_SUITE;
  }
}
