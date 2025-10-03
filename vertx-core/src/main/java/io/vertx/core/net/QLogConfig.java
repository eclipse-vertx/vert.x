/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.net;

import io.vertx.codegen.annotations.DataObject;

/**
 * <a href="https://datatracker.ietf.org/doc/draft-ietf-quic-qlog-main-schema/">QLog</a> configuration.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject
public class QLogConfig {

  private String path;
  private String title;
  private String description;

  public QLogConfig() {
  }

  public QLogConfig(QLogConfig other) {
    this.path = other.path;
    this.title = other.title;
    this.description = other.description;
  }

  /**
   * @return the path of the log file
   */
  public String getPath() {
    return path;
  }

  /**
   * The path to the log file to use, this file must not exist yet. If the path is a directory the filename will be generated.
   *
   * @param path the path
   * @return this object instance
   */
  public QLogConfig setPath(String path) {
    this.path = path;
    return this;
  }

  /**
   * @return the log title
   */
  public String getTitle() {
    return title;
  }

  /**
   * Set the log title,
   *
   * @param title the title
   * @return this object instance
   */
  public QLogConfig setTitle(String title) {
    this.title = title;
    return this;
  }

  /**
   * @return the description
   */
  public String getDescription() {
    return description;
  }

  /**
   * Set the log description.
   *
   * @param description the description
   * @return this object instance
   */
  public QLogConfig setDescription(String description) {
    this.description = description;
    return this;
  }
}
