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

package io.vertx.core.spi;

import io.vertx.core.VertxOptions;
import io.vertx.core.internal.VertxBootstrap;
import io.vertx.core.spi.file.FileResolver;

/**
 * A factory for the pluggable file resolver SPI.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface FileResolverFactory extends VertxServiceProvider {

  @Override
  default void init(VertxBootstrap builder) {
    if (builder.fileResolver() == null) {
      FileResolver fileResolver = resolver(builder.options());
      builder.fileResolver(fileResolver);
    }
  }

  /**
   * Create a new {@link FileResolver} object.<p/>
   *
   * @param options the vertx configuration options
   * @return the file resolver
   */
  FileResolver resolver(VertxOptions options);

}
