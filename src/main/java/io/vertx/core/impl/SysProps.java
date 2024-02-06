package io.vertx.core.impl;

import java.io.File;

public enum SysProps {

  /**
   * Duplicate of {@link io.vertx.core.http.HttpHeaders#DISABLE_HTTP_HEADERS_VALIDATION}
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
   * Default value of {@link io.vertx.core.VertxOptions#DEFAULT_DISABLE_TCCL}
   */
  DISABLE_TCCL("vertx.disableTCCL"),

  /**
   * Default value of {@link io.vertx.core.file.FileSystemOptions#DEFAULT_FILE_CACHING_ENABLED}
   */
  DISABLE_FILE_CACHING("vertx.disableFileCaching"),

  /**
   * Default value of {@link io.vertx.core.file.FileSystemOptions#DEFAULT_CLASS_PATH_RESOLVING_ENABLED}
   */
  DISABLE_FILE_CP_RESOLVING("vertx.disableFileCPResolving"),

  /**
   * Default value of {@link io.vertx.core.file.FileSystemOptions#DEFAULT_FILE_CACHING_DIR}
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
