/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.json.jackson;

import io.vertx.core.ServiceHelper;
import io.vertx.core.spi.JsonFactory;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class DefaultJsonFactory implements JsonFactory {

  public static JacksonCodec JACKSON_CODEC;

  /**
   * Load the factory with the {@code ServiceLoader}, when no factory is found then a factory
   * using Jackson will be returned.
   * <br/>
   * When {@code jackson-databind} is available then a codec using it will be used otherwise
   * the codec will only use {@code jackson-core} and provide best effort mapping.
   */
  public static JsonFactory load() {
    JsonFactory factory = ServiceHelper.loadFactoryOrNull(JsonFactory.class);
    if (factory == null) {
      factory = () -> JACKSON_CODEC;
    }
    return factory;
  }

  static {
    JacksonCodec codec;
    try {
      codec = new DatabindCodec();
    } catch (Throwable ignore) {
      // No databind
      codec = new JacksonCodec();
    }
    JACKSON_CODEC = codec;
  }

  @Override
  public JacksonCodec codec() {
    return JACKSON_CODEC;
  }
}
