/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.core.file;

import java.nio.file.attribute.BasicFileAttributes;
import java.util.Date;

/**
 * Represents properties of a file on the file system<p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class FileProps {

  /**
   * The date the file was created
   */
  public final Date creationTime;

  /**
   * The date the file was last accessed
   */
  public final Date lastAccessTime;

  /**
   * The date the file was last modified
   */
  public final Date lastModifiedTime;

  /**
   * Is the file a directory?
   */
  public final boolean isDirectory;

  /**
   * Is the file some other type? (I.e. not a directory, regular file or symbolic link)
   */
  public final boolean isOther;

  /**
   * Is the file a regular file?
   */
  public final boolean isRegularFile;

  /**
   * Is the file a symbolic link?
   */
  public final boolean isSymbolicLink;

  /**
   * The size of the file, in bytes
   */
  public final long size;

  public FileProps(BasicFileAttributes attrs) {
    creationTime = new Date(attrs.creationTime().toMillis());
    lastModifiedTime = new Date(attrs.lastModifiedTime().toMillis());
    lastAccessTime = new Date(attrs.lastAccessTime().toMillis());
    isDirectory = attrs.isDirectory();
    isOther = attrs.isOther();
    isRegularFile = attrs.isRegularFile();
    isSymbolicLink = attrs.isSymbolicLink();
    size = attrs.size();
  }
}
