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

  public void setPerms(String perms) {
    this.perms = perms;
  }

  public boolean isRead() {
    return read;
  }

  public void setRead(boolean read) {
    this.read = read;
  }

  public boolean isWrite() {
    return write;
  }

  public void setWrite(boolean write) {
    this.write = write;
  }

  public boolean isCreate() {
    return create;
  }

  public void setCreate(boolean create) {
    this.create = create;
  }

  public boolean isCreateNew() {
    return createNew;
  }

  public void setCreateNew(boolean createNew) {
    this.createNew = createNew;
  }

  public boolean isDeleteOnClose() {
    return deleteOnClose;
  }

  public void setDeleteOnClose(boolean deleteOnClose) {
    this.deleteOnClose = deleteOnClose;
  }

  public boolean isTruncateExisting() {
    return truncateExisting;
  }

  public void setTruncateExisting(boolean truncateExisting) {
    this.truncateExisting = truncateExisting;
  }

  public boolean isSparse() {
    return sparse;
  }

  public void setSparse(boolean sparse) {
    this.sparse = sparse;
  }

  public boolean isSync() {
    return sync;
  }

  public void setSync(boolean sync) {
    this.sync = sync;
  }

  public boolean isDSync() {
    return dsync;
  }

  public void setDSync(boolean dsync) {
    this.dsync = dsync;
  }
}
