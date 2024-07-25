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

package io.vertx.core.net;

import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.tls.SslContextFactory;

/**
 * The SSL engine implementation to use in a Vert.x server or client.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class SSLEngineOptions {

  /**
   * The default thread pool type for SSL blocking operations.
   */
  public static final boolean DEFAULT_USE_WORKER_POOL = false;

  private boolean useWorkerThread;

  public abstract SSLEngineOptions copy();

  public SSLEngineOptions() {
    this.useWorkerThread = DEFAULT_USE_WORKER_POOL;
  }

  public SSLEngineOptions(SSLEngineOptions that) {
    this.useWorkerThread = that.useWorkerThread;
  }

  public SSLEngineOptions(JsonObject json) {
    this.useWorkerThread = json.getBoolean("useWorkerThread", DEFAULT_USE_WORKER_POOL);
  }

  /**
   * @return a {@link SslContextFactory} that will be used to produce the Netty {@code SslContext}
   */
  public abstract SslContextFactory sslContextFactory();

  /**
   * @return whether to use the worker pool for SSL blocking operationsg
   */
  public boolean getUseWorkerThread() {
    return useWorkerThread;
  }

  /**
   * Set the thread pool to use for SSL blocking operations.
   *
   * @param useWorkerThread whether to use the vertx internal worker pool for SSL blocking operations
   * @return a reference to this, so the API can be used fluently
   */
  public SSLEngineOptions setUseWorkerThread(boolean useWorkerThread) {
    this.useWorkerThread = useWorkerThread;
    return this;
  }
}
