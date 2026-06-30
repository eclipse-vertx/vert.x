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

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import java.util.List;

/**
 * Java 21+ implementation: applies named groups via SSLParameters.
 */
public class SslEngineHelper {

  public static void applyNamedGroups(SSLEngine engine, List<String> groups) {
    SSLParameters params = engine.getSSLParameters();
    params.setNamedGroups(groups.toArray(new String[0]));
    engine.setSSLParameters(params);
  }

}
