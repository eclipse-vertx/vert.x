/*
 * Copyright (c) 2011-2014 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.file;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

/**
 * Describes how an {@link io.vertx.core.file.AsyncFile} should be opened.
 * 
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@DataObject
public class OpenOptions {

  public static final String DEFAULT_PERMS = null;
  public static final boolean DEFAULT_READ = true;
  public static final boolean DEFAULT_WRITE = true;
  public static final boolean DEFAULT_CREATE = true;
  public static final boolean DEFAULT_CREATENEW = false;
  public static final boolean DEFAULT_DSYNC = false;
  public static final boolean DEFAULT_SYNC = false;
  public static final boolean DEFAULT_DELETEONCLOSE = false;
  public static final boolean DEFAULT_TRUNCATEEXISTING = false;
  public static final boolean DEFAULT_SPARSE = false;

  private String perms = DEFAULT_PERMS;
  private boolean read = DEFAULT_READ;
  private boolean write = DEFAULT_WRITE;
  private boolean create = DEFAULT_CREATE;
  private boolean createNew = DEFAULT_CREATENEW;
  private boolean dsync = DEFAULT_DSYNC;
  private boolean sync = DEFAULT_SYNC;
  private boolean deleteOnClose = DEFAULT_DELETEONCLOSE;
  private boolean truncateExisting = DEFAULT_TRUNCATEEXISTING;
  private boolean sparse = DEFAULT_SPARSE;

  /**
   * Default constructor
   */
  public OpenOptions() {
    super();
  }

  /**
   * Copy constructor
   *
   * @param other  the options to copy
   */
  public OpenOptions(OpenOptions other) {
    this.perms = other.perms;
    this.read = other.read;
    this.write = other.write;
    this.create = other.create;
    this.createNew = other.createNew;
    this.dsync = other.dsync;
    this.sync = other.sync;
    this.deleteOnClose = other.deleteOnClose;
    this.truncateExisting = other.truncateExisting;
    this.sparse = other.sparse;
  }

  /**
   * Constructor to create options from JSON
   *
   * @param json  the JSON
   */
  public OpenOptions(JsonObject json) {
    this.perms = json.getString("perms", DEFAULT_PERMS);
    this.read = json.getBoolean("read", DEFAULT_READ);
    this.write = json.getBoolean("write", DEFAULT_WRITE);
    this.create = json.getBoolean("create", DEFAULT_CREATE);
    this.createNew = json.getBoolean("createNew", DEFAULT_CREATENEW);
    this.dsync = json.getBoolean("dsync", DEFAULT_DSYNC);
    this.sync = json.getBoolean("sync", DEFAULT_SYNC);
    this.deleteOnClose = json.getBoolean("deleteOnClose", DEFAULT_DELETEONCLOSE);
    this.truncateExisting = json.getBoolean("truncateExisting", DEFAULT_TRUNCATEEXISTING);
    this.sparse = json.getBoolean("sparse", DEFAULT_SPARSE);
  }

  /**
   * Get the permissions string to be used if creating a file
   *
   * @return  the permissions string
   */
  public String getPerms() {
    return perms;
  }

  /**
   * Set the permissions string
   *
   * @param perms  the permissions string
   * @return a reference to this, so the API can be used fluently
   */
  public OpenOptions setPerms(String perms) {
    this.perms = perms;
    return this;
  }

  /**
   * Is the file to opened for reading?
   *
   * @return true if to be opened for reading
   */
  public boolean isRead() {
    return read;
  }

  /**
   * Set whether the file is to be opened for reading
   *
   * @param read  true if the file is to be opened for reading
   * @return a reference to this, so the API can be used fluently
   */
  public OpenOptions setRead(boolean read) {
    this.read = read;
    return this;
  }

  /**
   * Is the file to opened for writing?
   *
   * @return true if to be opened for writing
   */
  public boolean isWrite() {
    return write;
  }

  /**
   * Set whether the file is to be opened for writing
   *
   * @param write  true if the file is to be opened for writing
   * @return a reference to this, so the API can be used fluently
   */
  public OpenOptions setWrite(boolean write) {
    this.write = write;
    return this;
  }

  /**
   * Should the file be created if it does not already exist?
   *
   * @return true if the file should be created if it does not already exist
   */
  public boolean isCreate() {
    return create;
  }

  /**
   * Set whether the file should be created if it does not already exist.
   *
   * @param create  true if the file should be created if it does not already exist
   * @return a reference to this, so the API can be used fluently
   */
  public OpenOptions setCreate(boolean create) {
    this.create = create;
    return this;
  }

  /**
   * Should the file be created if and the open fail if it already exists?
   *
   * @return true if the file should be created if and the open fail if it already exists.
   */
  public boolean isCreateNew() {
    return createNew;
  }

  /**
   * Set whether the file should be created and fail if it does exist already.
   *
   * @param createNew  true if the file should be created or fail if it exists already
   * @return a reference to this, so the API can be used fluently
   */
  public OpenOptions setCreateNew(boolean createNew) {
    this.createNew = createNew;
    return this;
  }

  /**
   * Should the file be deleted when it's closed, or the JVM is shutdown.
   *
   * @return  true if the file should be deleted when it's closed or the JVM shutdown
   */
  public boolean isDeleteOnClose() {
    return deleteOnClose;
  }

  /**
   * Set whether the file should be deleted when it's closed, or the JVM is shutdown.
   *
   * @param deleteOnClose whether the file should be deleted when it's closed, or the JVM is shutdown.
   * @return  a reference to this, so the API can be used fluently
   */
  public OpenOptions setDeleteOnClose(boolean deleteOnClose) {
    this.deleteOnClose = deleteOnClose;
    return this;
  }

  /**
   * If the file exists and is opened for writing should the file be truncated to zero length on open?
   *
   * @return true  if the file exists and is opened for writing and the file be truncated to zero length on open
   */
  public boolean isTruncateExisting() {
    return truncateExisting;
  }

  /**
   * Set whether the file should be truncated to zero length on opening if it exists and is opened for write
   *
   * @param truncateExisting  true if the file should be truncated to zero length on opening if it exists and is opened for write
   * @return a reference to this, so the API can be used fluently
   */
  public OpenOptions setTruncateExisting(boolean truncateExisting) {
    this.truncateExisting = truncateExisting;
    return this;
  }

  /**
   * Set whether a hint should be provided that the file to created is sparse
   *
   * @return true if a hint should be provided that the file to created is sparse
   */
  public boolean isSparse() {
    return sparse;
  }

  /**
   * Set whether a hint should be provided that the file to created is sparse
   * @param sparse true if a hint should be provided that the file to created is sparse
   * @return a reference to this, so the API can be used fluently
   */
  public OpenOptions setSparse(boolean sparse) {
    this.sparse = sparse;
    return this;
  }

  /**
   * If true then every write to the file's content and metadata will be written synchronously to the underlying hardware.
   *
   * @return true if sync
   */
  public boolean isSync() {
    return sync;
  }

  /**
   * Set whether every write to the file's content and meta-data will be written synchronously to the underlying hardware.
   * @param sync  true if sync
   * @return a reference to this, so the API can be used fluently
   */
  public OpenOptions setSync(boolean sync) {
    this.sync = sync;
    return this;
  }

  /**
   * If true then every write to the file's content will be written synchronously to the underlying hardware.
   *
   * @return true if sync
   */
  public boolean isDSync() {
    return dsync;
  }

  /**
   * Set whether every write to the file's content  ill be written synchronously to the underlying hardware.
   * @param dsync  true if sync
   * @return a reference to this, so the API can be used fluently
   */
  public OpenOptions setDSync(boolean dsync) {
    this.dsync = dsync;
    return this;
  }
}
