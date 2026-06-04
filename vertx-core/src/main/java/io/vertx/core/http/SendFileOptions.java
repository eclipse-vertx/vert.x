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
import io.vertx.codegen.annotations.Unstable;
import io.vertx.codegen.json.annotations.JsonGen;
import io.vertx.core.impl.Arguments;
import io.vertx.core.json.JsonObject;

/**
 * Options controlling how {@link HttpServerResponse#sendFile(String)} streams a file when
 * the file-region/sendfile path cannot be used.
 */
@DataObject
@JsonGen(publicConverter = false)
@Unstable
public class SendFileOptions {

  /**
   * The default chunk size used by the send-file fallback path.
   */
  public static final int DEFAULT_CHUNK_SIZE = 8192;

  private int chunkSize;

  /**
   * Create default send-file options.
   */
  public SendFileOptions() {
    chunkSize = DEFAULT_CHUNK_SIZE;
  }

  /**
   * Create send-file options from JSON.
   *
   * @param json the JSON object
   */
  public SendFileOptions(JsonObject json) {
    this();
    SendFileOptionsConverter.fromJson(json, this);
  }

  /**
   * Copy constructor.
   *
   * @param other the options to copy
   */
  public SendFileOptions(SendFileOptions other) {
    chunkSize = other.chunkSize;
  }

  /**
   * @return the chunk size, in bytes, used when the file is streamed through the fallback path
   */
  public int getChunkSize() {
    return chunkSize;
  }

  /**
   * Set the chunk size, in bytes, used when the file is streamed through the fallback path.
   * <p>
   * This option is ignored when the file can be transferred using the file-region/sendfile path.
   *
   * @param chunkSize the chunk size in bytes
   * @return a reference to this, so the API can be used fluently
   */
  public SendFileOptions setChunkSize(int chunkSize) {
    Arguments.require(chunkSize > 0, "chunkSize must be > 0");
    this.chunkSize = chunkSize;
    return this;
  }

  /**
   * @return a JSON representation of these options
   */
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    SendFileOptionsConverter.toJson(this, json);
    return json;
  }
}
