
/*
 * Copyright (c) 2011-2018 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.file;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

import static io.vertx.core.file.impl.FileResolver.DISABLE_CP_RESOLVING_PROP_NAME;
import static io.vertx.core.file.impl.FileResolver.DISABLE_FILE_CACHING_PROP_NAME;

/**
 * Vert.x file system base configuration, this class can be extended by provider implementations to configure
 * those specific implementations.
 */
@DataObject(generateConverter = true, publicConverter = false)
public class FileSystemOptions {

  /**
   * The default behavior for caching files for class path resolution = {@code false} if and only if the system property {@code "vertx.disableFileCaching"} exists and is set to the string {@code "false"}
   */
  public static final boolean DEFAULT_FILE_CACHING_ENABLED = !Boolean.getBoolean(DISABLE_FILE_CACHING_PROP_NAME);

  /**
   * The default behavior to cache or not class path resolution = {@code false} if and only if the system property {@code "vertx.disableFileCPResolving"} exists and is set to the string {@code "false"}
   */
  public static final boolean DEFAULT_CLASS_PATH_RESOLVING_ENABLED = !Boolean.getBoolean(DISABLE_CP_RESOLVING_PROP_NAME);

  private boolean classPathResolvingEnabled = DEFAULT_CLASS_PATH_RESOLVING_ENABLED;
  private boolean fileCachingEnabled = DEFAULT_FILE_CACHING_ENABLED;

  /**
   * Default constructor
   */
  public FileSystemOptions() {
  }

  /**
   * Copy constructor
   *
   * @param other The other {@link FileSystemOptions} to copy when creating this
   */
  public FileSystemOptions(FileSystemOptions other) {
    this.classPathResolvingEnabled = other.isClassPathResolvingEnabled();
    this.fileCachingEnabled = other.isFileCachingEnabled();
  }

  /**
   * Creates a new instance of {@link FileSystemOptions} from the JSON object. This JSOn object has (generally)
   * been generated using {@link #toJson()}.
   *
   * @param json the json object
   */
  public FileSystemOptions(JsonObject json) {
    this();

    FileSystemOptionsConverter.fromJson(json, this);
  }

  /**
   * Builds a JSON object representing the current {@link FileSystemOptions}.
   *
   * @return the JSON representation
   */
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    FileSystemOptionsConverter.toJson(this, json);
    return json;
  }

  /**
   * @return whether classpath resolving is enabled
   */
  public boolean isClassPathResolvingEnabled() {
    return this.classPathResolvingEnabled;
  }

  /**
   * When vert.x cannot find the file on the filesystem it tries to resolve the
   * file from the class path when this is set to {@code true}.
   *
   * @param classPathResolvingEnabled the value
   * @return a reference to this, so the API can be used fluently
   */
  public FileSystemOptions setClassPathResolvingEnabled(boolean classPathResolvingEnabled) {
    this.classPathResolvingEnabled = classPathResolvingEnabled;
    return this;
  }

  /**
   * @return whether file caching is enabled for class path resolving
   */
  public boolean isFileCachingEnabled() {
    return this.fileCachingEnabled;
  }

  /**
   * Set to {@code true} to cache files on the real file system
   * when the filesystem performs class path resolving.
   *
   * @param fileCachingEnabled the value
   * @return a reference to this, so the API can be used fluently
   */
  public FileSystemOptions setFileCachingEnabled(boolean fileCachingEnabled) {
    this.fileCachingEnabled = fileCachingEnabled;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    FileSystemOptions that = (FileSystemOptions) o;

    if (classPathResolvingEnabled != that.isClassPathResolvingEnabled()) return false;
    return fileCachingEnabled == that.isFileCachingEnabled();
  }

  @Override
  public int hashCode() {
    int result = (classPathResolvingEnabled ? 1 : 0);
    result += (fileCachingEnabled ? 1 : 0);
    return 31 * result;
  }

  @Override
  public String toString() {
    return "FileSystemOptions{" +
    "classPathResolvingEnabled=" + classPathResolvingEnabled +
    ", fileCachingEnabled=" + fileCachingEnabled +
    '}';
  }
}
