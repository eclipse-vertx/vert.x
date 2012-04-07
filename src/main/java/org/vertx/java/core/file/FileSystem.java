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

import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.buffer.Buffer;

/**
 * Contains a broad set of operations for manipulating files.<p>
 * An asynchronous and a synchronous version of each operation is provided.<p>
 * The asynchronous versions take an {@code AsynchronousResultHandler} which is
 * called when the operation completes or an error occurs.<p>
 * The synchronous versions return the results, or throw exceptions directly.<p>
 * It is highly recommended the asynchronous versions are used unless you are sure the operation
 * will not block for a significant period of time<p>
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface FileSystem {

  /**
   * Copy a file from the path {@code from} to path {@code to}, asynchronously.<p>
   * The copy will fail if the destination already exists.<p>
   */
  void copy(String from, String to, AsyncResultHandler<Void> handler);

  /**
   * Synchronous version of {@link #copy(String, String, AsyncResultHandler)}
   */
  void copySync(String from, String to) throws Exception;

  /**
   * Copy a file from the path {@code from} to path {@code to}, asynchronously.<p>
   * If {@code recursive} is {@code true} and {@code from} represents a directory, then the directory and its contents
   * will be copied recursively to the destination {@code to}.<p>
   * The copy will fail if the destination if the destination already exists.<p>
   */
  void copy(String from, String to, boolean recursive, AsyncResultHandler<Void> handler);

  /**
   * Synchronous version of {@link #copy(String, String, boolean, AsyncResultHandler)}
   */
  void copySync(String from, String to, boolean recursive) throws Exception;

  /**
   * Move a file from the path {@code from} to path {@code to}, asynchronously.<p>
   * The move will fail if the destination already exists.<p>
   */
  void move(String from, String to, AsyncResultHandler<Void> handler);

  /**
   * Synchronous version of {@link #move(String, String, AsyncResultHandler)}
   */
  void moveSync(String from, String to) throws Exception;

  /**
   * Truncate the file represented by {@code path} to length {@code len} in bytes, asynchronously.<p>
   * The operation will fail if the file does not exist or {@code len} is less than {@code zero}.
   */
  void truncate(String path, long len, AsyncResultHandler<Void> handler);

  /**
   * Synchronous version of {@link #truncate(String, long, AsyncResultHandler)}
   */
  void truncateSync(String path, long len) throws Exception;

  /**
   * Change the permissions on the file represented by {@code path} to {@code perms}, asynchronously.
   * The permission String takes the form rwxr-x--- as
   * specified <a href="http://download.oracle.com/javase/7/docs/api/java/nio/file/attribute/PosixFilePermissions.html">here</a>.<p>
   */
  void chmod(String path, String perms, AsyncResultHandler<Void> handler);

  /**
   * Synchronous version of {@link #chmod(String, String, AsyncResultHandler)}
   */
  void chmodSync(String path, String perms) throws Exception;

  /**
   * Change the permissions on the file represented by {@code path} to {@code perms}, asynchronously.
   * The permission String takes the form rwxr-x--- as
   * specified in {<a href="http://download.oracle.com/javase/7/docs/api/java/nio/file/attribute/PosixFilePermissions.html">here</a>}.<p>
   * If the file is directory then all contents will also have their permissions changed recursively. Any directory permissions will
   * be set to {@code dirPerms}, whilst any normal file permissions will be set to {@code perms}.<p>
   */
  void chmod(String path, String perms, String dirPerms, AsyncResultHandler<Void> handler);

  /**
   * Synchronous version of {@link #chmod(String, String, String, AsyncResultHandler)}
   */
  void chmodSync(String path, String perms, String dirPerms) throws Exception;

  /**
   * Obtain properties for the file represented by {@code path}, asynchronously.
   * If the file is a link, the link will be followed.
   */
  void props(String path, AsyncResultHandler<FileProps> handler);

  /**
   * Synchronous version of {@link #props(String, AsyncResultHandler)}
   */
  FileProps propsSync(String path) throws Exception;

  /**
   * Obtain properties for the link represented by {@code path}, asynchronously.
   * The link will not be followed.
   */
  void lprops(String path, AsyncResultHandler<FileProps> handler);

  /**
   * Synchronous version of {@link #lprops(String, AsyncResultHandler)}
   */
  FileProps lpropsSync(String path) throws Exception;

  /**
   * Create a hard link on the file system from {@code link} to {@code existing}, asynchronously.
   */
  void link(String link, String existing, AsyncResultHandler<Void> handler);

  /**
   * Synchronous version of {@link #link(String, String, AsyncResultHandler)}
   */
  void linkSync(String link, String existing) throws Exception;

  /**
   * Create a symbolic link on the file system from {@code link} to {@code existing}, asynchronously.
   */
  void symlink(String link, String existing, AsyncResultHandler<Void> handler);

  /**
   * Synchronous version of {@link #link(String, String, AsyncResultHandler)}
   */
  void symlinkSync(String link, String existing) throws Exception;

  /**
   * Unlinks the link on the file system represented by the path {@code link}, asynchronously.
   */
  void unlink(String link, AsyncResultHandler<Void> handler);

  /**
   * Synchronous version of {@link #unlink(String, AsyncResultHandler)}
   */
  void unlinkSync(String link) throws Exception;

  /**
   * Returns the path representing the file that the symbolic link specified by {@code link} points to, asynchronously.
   */
  void readSymlink(String link, AsyncResultHandler<String> handler);

  /**
   * Synchronous version of {@link #readSymlink(String, AsyncResultHandler)}
   */
  String readSymlinkSync(String link) throws Exception;

  /**
   * Deletes the file represented by the specified {@code path}, asynchronously.
   */
  void delete(String path, AsyncResultHandler<Void> handler);

  /**
   * Synchronous version of {@link #delete(String, AsyncResultHandler)}
   */
  void deleteSync(String path) throws Exception;

  /**
   * Deletes the file represented by the specified {@code path}, asynchronously.<p>
   * If the path represents a directory and {@code recursive = true} then the directory and its contents will be
   * deleted recursively.
   */
  void delete(String path, boolean recursive, AsyncResultHandler<Void> handler);

  /**
   * Synchronous version of {@link #delete(String, boolean, AsyncResultHandler)}
   */
  void deleteSync(String path, boolean recursive) throws Exception;

  /**
   * Create the directory represented by {@code path}, asynchronously.<p>
   * The operation will fail if the directory already exists.
   */
  void mkdir(String path, AsyncResultHandler<Void> handler);

  /**
   * Synchronous version of {@link #mkdir(String, AsyncResultHandler)}
   */
  void mkdirSync(String path) throws Exception;

  /**
   * Create the directory represented by {@code path}, asynchronously.<p>
   * If {@code createParents} is set to {@code true} then any non-existent parent directories of the directory
   * will also be created.<p>
   * The operation will fail if the directory already exists.
   */
  void mkdir(String path, boolean createParents, AsyncResultHandler<Void> handler);

  /**
   * Synchronous version of {@link #mkdir(String, boolean, AsyncResultHandler)}
   */
  void mkdirSync(String path, boolean createParents) throws Exception;

  /**
   * Create the directory represented by {@code path}, asynchronously.<p>
   * The new directory will be created with permissions as specified by {@code perms}.
   * The permission String takes the form rwxr-x--- as specified
   * in <a href="http://download.oracle.com/javase/7/docs/api/java/nio/file/attribute/PosixFilePermissions.html">here</a>.<p>
   * The operation will fail if the directory already exists.
   */
  void mkdir(String path, String perms, AsyncResultHandler<Void> handler);

  /**
   * Synchronous version of {@link #mkdir(String, String, AsyncResultHandler)}
   */
  void mkdirSync(String path, String perms) throws Exception;

  /**
   * Create the directory represented by {@code path}, asynchronously.<p>
   * The new directory will be created with permissions as specified by {@code perms}.
   * The permission String takes the form rwxr-x--- as specified
   * in <a href="http://download.oracle.com/javase/7/docs/api/java/nio/file/attribute/PosixFilePermissions.html">here</a>.<p>
   * If {@code createParents} is set to {@code true} then any non-existent parent directories of the directory
   * will also be created.<p>
   * The operation will fail if the directory already exists.<p>
   */
  void mkdir(String path, String perms, boolean createParents, AsyncResultHandler<Void> handler);

  /**
   * Synchronous version of {@link #mkdir(String, String, boolean, AsyncResultHandler)}
   */
  void mkdirSync(String path, String perms, boolean createParents) throws Exception;

  /**
   * Read the contents of the directory specified by {@code path}, asynchronously.<p>
   * The result is an array of String representing the paths of the files inside the directory.
   */
  void readDir(String path, AsyncResultHandler<String[]> handler);

  /**
   * Synchronous version of {@link #readDir(String, AsyncResultHandler)}
   */
  String[] readDirSync(String path) throws Exception;

  /**
   * Read the contents of the directory specified by {@code path}, asynchronously.<p>
   * The paramater {@code filter} is a regular expression. If {@code filter} is specified then only the paths that
   * match  @{filter}will be returned.<p>
   * The result is an array of String representing the paths of the files inside the directory.
   */
  void readDir(String path, String filter, AsyncResultHandler<String[]> handler);

  /**
   * Synchronous version of {@link #readDir(String, String, AsyncResultHandler)}
   */
  String[] readDirSync(String path, String filter) throws Exception;

  /**
   * Reads the entire file as represented by the path {@code path} as a {@link Buffer}, asynchronously.<p>
   * Do not user this method to read very large files or you risk running out of available RAM.
   */
  void readFile(String path, AsyncResultHandler<Buffer> handler);

  /**
   * Synchronous version of {@link #readFile(String, AsyncResultHandler)}
   */
  Buffer readFileSync(String path) throws Exception;

  /**
   * Creates the file, and writes the specified {@code Buffer data} to the file represented by the path {@code path},
   * asynchronously.
   */
  void writeFile(String path, Buffer data, AsyncResultHandler<Void> handler);

  /**
   * Synchronous version of {@link #writeFile(String, Buffer, AsyncResultHandler)}
   */
  void writeFileSync(String path, Buffer data) throws Exception;

  /**
   * Open the file represented by {@code path}, asynchronously.<p>
   * The file is opened for both reading and writing. If the file does not already exist it will be created.
   * Write operations will not automatically flush to storage.
   */
  void open(String path, AsyncResultHandler<AsyncFile> handler);

  /**
   * Synchronous version of {@link #open(String, AsyncResultHandler)}
   */
  AsyncFile openSync(String path) throws Exception;

  /**
   * Open the file represented by {@code path}, asynchronously.<p>
   * The file is opened for both reading and writing. If the file does not already exist it will be created with the
   * permissions as specified by {@code perms}.
   * Write operations will not automatically flush to storage.
   */
  void open(String path, String perms, AsyncResultHandler<AsyncFile> handler);

  /**
   * Synchronous version of {@link #open(String, String, AsyncResultHandler)}
   */
  AsyncFile openSync(String path, String perms) throws Exception;

  /**
   * Open the file represented by {@code path}, asynchronously.<p>
   * The file is opened for both reading and writing. If the file does not already exist and
   * {@code createNew} is {@code true} it will be created with the permissions as specified by {@code perms}, otherwise
   * the operation will fail.
   * Write operations will not automatically flush to storage.
   */
  void open(String path, String perms, boolean createNew, AsyncResultHandler<AsyncFile> handler);

  /**
   * Synchronous version of {@link #open(String, String, boolean, AsyncResultHandler)}
   */
  AsyncFile openSync(String path, String perms, boolean createNew) throws Exception;

  /**
   * Open the file represented by {@code path}, asynchronously.<p>
   * If {@code read} is {@code true} the file will be opened for reading. If {@code write} is {@code true} the file
   * will be opened for writing.<p>
   * If the file does not already exist and
   * {@code createNew} is {@code true} it will be created with the permissions as specified by {@code perms}, otherwise
   * the operation will fail.<p>
   * Write operations will not automatically flush to storage.
   */
  void open(String path, String perms, boolean read, boolean write, boolean createNew, AsyncResultHandler<AsyncFile> handler);

  /**
   * Synchronous version of {@link #open(String, String, boolean, boolean, boolean, AsyncResultHandler)}
   */
  AsyncFile openSync(String path, String perms, boolean read, boolean write, boolean createNew) throws Exception;

  /**
   * Open the file represented by {@code path}, asynchronously.<p>
   * If {@code read} is {@code true} the file will be opened for reading. If {@code write} is {@code true} the file
   * will be opened for writing.<p>
   * If the file does not already exist and
   * {@code createNew} is {@code true} it will be created with the permissions as specified by {@code perms}, otherwise
   * the operation will fail.<p>
   * If {@code flush} is {@code true} then all writes will be automatically flushed through OS buffers to the underlying
   * storage on each write.
   */
  void open(String path, String perms, boolean read, boolean write, boolean createNew,
      boolean flush, AsyncResultHandler<AsyncFile> handler);

  /**
   * Synchronous version of {@link #open(String, String, boolean, boolean, boolean, boolean, AsyncResultHandler)}
   */
  AsyncFile openSync(String path, String perms, boolean read, boolean write, boolean createNew, boolean flush) throws Exception;

  /**
   * Creates an empty file with the specified {@code path}, asynchronously.
   */
  void createFile(String path, AsyncResultHandler<Void> handler);

  /**
   * Synchronous version of {@link #createFile(String, AsyncResultHandler)}
   */
  void createFileSync(String path) throws Exception;

  /**
   * Creates an empty file with the specified {@code path} and permissions {@code perms}, asynchronously.
   */
  void createFile(String path, String perms, AsyncResultHandler<Void> handler);

  /**
   * Synchronous version of {@link #createFile(String, String, AsyncResultHandler)}
   */
  void createFileSync(String path, String perms) throws Exception;

  /**
   * Determines whether the file as specified by the path {@code path} exists, asynchronously.
   */
  void exists(String path, AsyncResultHandler<Boolean> handler);

  /**
   * Synchronous version of {@link #exists(String, AsyncResultHandler)}
   */
  boolean existsSync(String path) throws Exception;

  /**
   * Returns properties of the file-system being used by the specified {@code path}, asynchronously.
   */
  void fsProps(String path, AsyncResultHandler<FileSystemProps> handler);

  /**
   * Synchronous version of {@link #fsProps(String, AsyncResultHandler)}
   */
  FileSystemProps fsPropsSync(String path) throws Exception;

}