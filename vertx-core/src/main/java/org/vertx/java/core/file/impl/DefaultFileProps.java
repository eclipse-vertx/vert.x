package org.vertx.java.core.file.impl;

import org.vertx.java.core.file.FileProps;

import java.nio.file.attribute.BasicFileAttributes;
import java.util.Date;

/*
 * Copyright 2013 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DefaultFileProps implements FileProps {
  
  private final Date creationTime;
  private final Date lastAccessTime;
  private final Date lastModifiedTime;
  private final boolean isDirectory;
  private final boolean isOther;
  private final boolean isRegularFile;
  private final boolean isSymbolicLink;
  private final long size;

  public DefaultFileProps(BasicFileAttributes attrs) {
    creationTime = new Date(attrs.creationTime().toMillis());
    lastModifiedTime = new Date(attrs.lastModifiedTime().toMillis());
    lastAccessTime = new Date(attrs.lastAccessTime().toMillis());
    isDirectory = attrs.isDirectory();
    isOther = attrs.isOther();
    isRegularFile = attrs.isRegularFile();
    isSymbolicLink = attrs.isSymbolicLink();
    size = attrs.size();
  }

  @Override
  public Date creationTime() {
    return creationTime;
  }

  @Override
  public Date lastAccessTime() {
    return lastAccessTime;
  }

  @Override
  public Date lastModifiedTime() {
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
