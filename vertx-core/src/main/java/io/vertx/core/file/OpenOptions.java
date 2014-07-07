/*
 * Copyright 2014 Red Hat, Inc.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.file;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */

public class OpenOptions {

  private String perms;
  private boolean read = true;
  private boolean write = true;
  private boolean create = true;
  private boolean createNew = false;
  private boolean dsync = false;
  private boolean sync = false;
  private boolean deleteOnClose = false;
  private boolean truncateExisting = false;
  private boolean sparse = false;

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
