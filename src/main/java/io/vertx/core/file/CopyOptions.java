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

package io.vertx.core.file;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

/**
 * Describes the copy (and move) options.
 *
 * @author Thomas Segismont
 */
@DataObject(generateConverter = true, publicConverter = false)
public class CopyOptions {

  /**
   * Whether an existing file, empty directory, or link should be replaced by default = false.
   */
  public static final boolean DEFAULT_REPLACE_EXISTING = false;

  /**
   * Whether the file attributes should be copied by default = false.
   */
  public static final boolean DEFAULT_COPY_ATTRIBUTES = false;

  /**
   * Whether move should be performed as an atomic filesystem operation by default = false.
   */
  public static final boolean DEFAULT_ATOMIC_MOVE = false;

  /**
   * Whether symbolic links should not be followed during copy or move operations by default = false.
   */
  public static final boolean DEFAULT_NOFOLLOW_LINKS = false;

  private boolean replaceExisting = DEFAULT_REPLACE_EXISTING;
  private boolean copyAttributes = DEFAULT_COPY_ATTRIBUTES;
  private boolean atomicMove = DEFAULT_ATOMIC_MOVE;
  private boolean nofollowLinks = DEFAULT_NOFOLLOW_LINKS;

  /**
   * Default constructor.
   */
  public CopyOptions() {
  }

  /**
   * Copy constructor.
   *
   * @param other the options to copy
   */
  public CopyOptions(CopyOptions other) {
    this.replaceExisting = other.replaceExisting;
    this.copyAttributes = other.copyAttributes;
    this.atomicMove = other.atomicMove;
    this.nofollowLinks = other.nofollowLinks;
  }


  /**
   * Constructor to create options from JSON.
   *
   * @param json the JSON
   */
  public CopyOptions(JsonObject json) {
    this();
    CopyOptionsConverter.fromJson(json, this);
  }

  /**
   * @return true if an existing file, empty directory, or link should be replaced, false otherwise
   */
  public boolean isReplaceExisting() {
    return replaceExisting;
  }

  /**
   * Whether an existing file, empty directory, or link should be replaced. Defaults to {@code false}.
   *
   * @param replaceExisting true to replace, false otherwise
   * @return a reference to this, so the API can be used fluently
   */
  public CopyOptions setReplaceExisting(boolean replaceExisting) {
    this.replaceExisting = replaceExisting;
    return this;
  }

  /**
   * @return true if the file attributes should be copied, false otherwise
   */
  public boolean isCopyAttributes() {
    return copyAttributes;
  }

  /**
   * Whether the file attributes should be copied. Defaults to {@code false}.
   *
   * @param copyAttributes true to copy attributes, false otherwise
   * @return a reference to this, so the API can be used fluently
   */
  public CopyOptions setCopyAttributes(boolean copyAttributes) {
    this.copyAttributes = copyAttributes;
    return this;
  }

  /**
   * @return true if move should be performed as an atomic filesystem operation, false otherwise
   */
  public boolean isAtomicMove() {
    return atomicMove;
  }

  /**
   * Whether move should be performed as an atomic filesystem operation. Defaults to {@code false}.
   *
   * @param atomicMove true to perform as an atomic filesystem operation, false otherwise
   * @return a reference to this, so the API can be used fluently
   */
  public CopyOptions setAtomicMove(boolean atomicMove) {
    this.atomicMove = atomicMove;
    return this;
  }

  /**
   * @return true if the operation should not follow links, false otherwise
   */
  public boolean isNofollowLinks() {
    return nofollowLinks;
  }

  /**
   * Whether symbolic links should not be followed during copy or move operations. Defaults to {@code false}.
   *
   * @param nofollowLinks true to not follow links, false otherwise. Defaults to {@code false}.
   * @return a reference to this, so the API can be used fluently
   */
  public CopyOptions setNofollowLinks(boolean nofollowLinks) {
    this.nofollowLinks = nofollowLinks;
    return this;
  }
}
