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
package io.vertx.core.http;

import io.vertx.codegen.annotations.DataObject;

/**
 * HTTP/3 server configuration.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject
public class Http3ServerConfig {

  private Http3Settings initialSettings;

  public Http3ServerConfig() {
  }

  public Http3ServerConfig(Http3ServerConfig other) {
    this.initialSettings = other.getInitialSettings() != null ? new Http3Settings(other.initialSettings) : null;
  }

  /**
   * @return the initial HTTP/3 connection settings sent by the server when a client connects
   */
  public Http3Settings getInitialSettings() {
    return initialSettings;
  }

  /**
   * Set the HTTP/3 connection settings sent by the server when a client connects.
   *
   * @param settings the settings value
   * @return a reference to this, so the API can be used fluently
   */
  public Http3ServerConfig setInitialSettings(Http3Settings settings) {
    this.initialSettings = settings;
    return this;
  }
}
