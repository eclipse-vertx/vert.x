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

package io.vertx.core.file.impl;

import io.vertx.core.file.FileProps;

import java.nio.file.attribute.BasicFileAttributes;


public class FilePropsImpl implements FileProps {

  private final long creationTime;
  private final long lastAccessTime;
  private final long lastModifiedTime;
  private final boolean isDirectory;
  private final boolean isOther;
  private final boolean isRegularFile;
  private final boolean isSymbolicLink;
  private final long size;

  public FilePropsImpl(BasicFileAttributes attrs) {
    creationTime = attrs.creationTime().toMillis();
    lastModifiedTime = attrs.lastModifiedTime().toMillis();
    lastAccessTime = attrs.lastAccessTime().toMillis();
    isDirectory = attrs.isDirectory();
    isOther = attrs.isOther();
    isRegularFile = attrs.isRegularFile();
    isSymbolicLink = attrs.isSymbolicLink();
    size = attrs.size();
  }

  @Override
  public long creationTime() {
    return creationTime;
  }

  @Override
  public long lastAccessTime() {
    return lastAccessTime;
  }

  @Override
  public long lastModifiedTime() {
    return lastModifiedTime;
  }

  @Override
  public boolean isDirectory() {
    return isDirectory;
  }

  @Override
  public boolean isOther() {
    return isOther;
  }

  @Override
  public boolean isRegularFile() {
    return isRegularFile;
  }

  @Override
  public boolean isSymbolicLink() {
    return isSymbolicLink;
  }

  @Override
  public long size() {
    return size;
  }
}
