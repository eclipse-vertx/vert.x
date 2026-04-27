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

package io.vertx.core.json.jackson;

import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.spi.json.JsonCodec;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class JacksonFactory implements io.vertx.core.spi.JsonFactory {

  private static final Logger logger = LoggerFactory.getLogger(JacksonFactory.class);

  public static final JacksonFactory INSTANCE = new JacksonFactory();

  public static final JsonCodec CODEC;

  static {
    JsonCodec codec;
    try {
      codec = new DatabindCodec();
      logger.debug("Using io.vertx.core.json.DatabindCodec");
    } catch (Throwable reason1) {
      // No v2 databind
      logger.debug("Jackson v2 databind not found: " + reason1.getMessage());
      try {
        codec = new JacksonCodec();
        logger.debug("Using io.vertx.core.json.JacksonCodec");
      } catch (Throwable reason2) {
        // No v2 core
        logger.debug("Jackson v2 core not found: " + reason2.getMessage());
        try {
          codec = new io.vertx.core.json.jackson.v3.DatabindCodec();
          logger.debug("Using io.vertx.core.json.jackson.v3.DatabindCodec");
        } catch (Throwable reason3) {
          // No v3 databind
          logger.debug("Jackson v3 databind not found: " + reason3.getMessage());
          codec = new io.vertx.core.json.jackson.v3.JacksonCodec();
          logger.debug("Using io.vertx.core.json.jackson.v3.JacksonCodec");
        }
      }
    }
    CODEC = codec;
  }

  @Override
  public JsonCodec codec() {
    return CODEC;
  }
}
