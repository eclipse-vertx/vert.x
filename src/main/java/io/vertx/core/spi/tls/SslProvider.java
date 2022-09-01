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
package io.vertx.core.spi.tls;

import java.util.List;
import java.util.Set;

/**
 * Provides an {@link SslContextFactory} based on Vert.x options.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface SslProvider {

  /**
   * @param enabledCipherSuites the enabled cipher suites
   * @param applicationProtocols the application protocols
   * @return a new {@link SslContextFactory}, ready to be configured 
   */
  SslContextFactory contextFactory(Set<String> enabledCipherSuites,
                                   List<String> applicationProtocols);

}
