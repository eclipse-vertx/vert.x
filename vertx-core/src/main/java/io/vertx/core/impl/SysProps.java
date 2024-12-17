/*
 * Copyright (c) 2011-2024 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.impl;

import io.vertx.core.internal.http.HttpHeadersInternal;

import java.io.File;

/**
 * Vert.x known system properties.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public enum SysProps {

  /**
   * Duplicate of {@link HttpHeadersInternal#DISABLE_HTTP_HEADERS_VALIDATION}
   */
  DISABLE_HTTP_HEADERS_VALIDATION("vertx.disableHttpHeadersValidation"),

  /**
   * Internal property that disables websockets benchmarking purpose.
   */
  DISABLE_WEBSOCKETS("vertx.disableWebsockets"),

  /**
   * Internal property that disables metrics for benchmarking purpose.
   */
  DISABLE_METRICS("vertx.disableMetrics"),

  /**
   * Internal property that disables the context task execution measures for benchmarking purpose.
   */
  DISABLE_CONTEXT_TIMINGS("vertx.disableContextTimings"),

  /**
   * Disable Netty DNS resolver usage.
   *
   * Documented and (not much) tested.
   */
  DISABLE_DNS_RESOLVER("vertx.disableDnsResolver"),

  /**
   * Default value of {@link io.vertx.core.file.FileSystemOptions#DEFAULT_FILE_CACHING_ENABLED}
   *
   */
  DISABLE_FILE_CACHING("vertx.disableFileCaching"),

  /**
   * Default value of {@link io.vertx.core.file.FileSystemOptions#DEFAULT_CLASS_PATH_RESOLVING_ENABLED}
   *
   */
  DISABLE_FILE_CP_RESOLVING("vertx.disableFileCPResolving"),

  /**
   * Default value of {@link io.vertx.core.file.FileSystemOptions#DEFAULT_FILE_CACHING_DIR}
   *
   */
  FILE_CACHE_DIR("vertx.cacheDirBase") {
    @Override
    public String get() {
      String val = super.get();
      if (val == null) {
        // get the system default temp dir location (can be overriden by using the standard java system property)
        // if not present default to the process start CWD
        String tmpDir = System.getProperty("java.io.tmpdir", ".");
        String cacheDirBase = "vertx-cache";
        val = tmpDir + File.separator + cacheDirBase;
      }
      return val;
    }
  },

  /**
   * Configure the Vert.x logger.
   *
   * Documented and tested.
   */
  LOGGER_DELEGATE_FACTORY_CLASS_NAME("vertx.logger-delegate-factory-class-name"),

  ;

  public final String name;

  SysProps(String name) {
    this.name = name;
  }

  public String get() {
    return System.getProperty(name);
  }

  public boolean getBoolean() {
    return Boolean.getBoolean(name);
  }

}
