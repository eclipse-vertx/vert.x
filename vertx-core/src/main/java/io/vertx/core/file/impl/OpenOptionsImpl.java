/*
 * Copyright 2014 Red Hat, Inc.
 *
 *   Red Hat licenses this file to you under the Apache License, version 2.0
 *   (the "License"); you may not use this file except in compliance with the
 *   License.  You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *   WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 *   License for the specific language governing permissions and limitations
 *   under the License.
 */

package io.vertx.core.file.impl;

import io.vertx.core.file.OpenOptions;
import io.vertx.core.json.JsonObject;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class OpenOptionsImpl implements OpenOptions {

  private static final String DEFAULT_PERMS = null;
  private static final boolean DEFAULT_READ = true;
  private static final boolean DEFAULT_WRITE = true;
  private static final boolean DEFAULT_CREATE = true;
  private static final boolean DEFAULT_CREATENEW = false;
  private static final boolean DEFAULT_DSYNC = false;
  private static final boolean DEFAULT_SYNC = false;
  private static final boolean DEFAULT_DELETEONCLOSE = false;
  private static final boolean DEFAULT_TRUNCATEEXISTING = false;
  private static final boolean DEFAULT_SPARSE = false;

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

  OpenOptionsImpl() {
    super();
  }

  OpenOptionsImpl(JsonObject json) {
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

  public String getPerms() {
    return perms;
  }

  public OpenOptions setPerms(String perms) {
    this.perms = perms;
    return this;
  }

  public boolean isRead() {
    return read;
  }

  public OpenOptions setRead(boolean read) {
    this.read = read;
    return this;
  }

  public boolean isWrite() {
    return write;
  }

  public OpenOptions setWrite(boolean write) {
    this.write = write;
    return this;
  }

  public boolean isCreate() {
    return create;
  }

  public OpenOptions setCreate(boolean create) {
    this.create = create;
    return this;
  }

  public boolean isCreateNew() {
    return createNew;
  }

  public OpenOptions setCreateNew(boolean createNew) {
    this.createNew = createNew;
    return this;
  }

  public boolean isDeleteOnClose() {
    return deleteOnClose;
  }

  public OpenOptions setDeleteOnClose(boolean deleteOnClose) {
    this.deleteOnClose = deleteOnClose;
    return this;
  }

  public boolean isTruncateExisting() {
    return truncateExisting;
  }

  public OpenOptions setTruncateExisting(boolean truncateExisting) {
    this.truncateExisting = truncateExisting;
    return this;
  }

  public boolean isSparse() {
    return sparse;
  }

  public OpenOptions setSparse(boolean sparse) {
    this.sparse = sparse;
    return this;
  }

  public boolean isSync() {
    return sync;
  }

  public OpenOptions setSync(boolean sync) {
    this.sync = sync;
    return this;
  }

  public boolean isDSync() {
    return dsync;
  }

  public OpenOptions setDSync(boolean dsync) {
    this.dsync = dsync;
    return this;
  }
}
