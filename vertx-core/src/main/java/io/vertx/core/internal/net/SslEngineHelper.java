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
package io.vertx.core.internal.net;

import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;

import javax.net.ssl.SSLEngine;
import java.util.List;

/**
 * Pre Java 21 implementation: named groups not supported.
 */
public class SslEngineHelper {

  private static final Logger log = LoggerFactory.getLogger(SslEngineHelper.class);

  public static void applyNamedGroups(SSLEngine engine, List<String> groups) {
    log.warn("Cannot apply key exchange groups " + groups + " on JDK SSL engine: requires JDK 20+");
  }

}
