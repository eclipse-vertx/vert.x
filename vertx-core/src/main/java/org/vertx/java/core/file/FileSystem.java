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
  FileSystem copy(String from, String to, AsyncResultHandler<Void> handler);

  /**
   * Synchronous version of {@link #copy(String, String, AsyncResultHandler)}
   */
  FileSystem copySync(String from, String to) ;

  /**
   * Copy a file from the path {@code from} to path {@code to}, asynchronously.<p>
   * If {@code recursive} is {@code true} and {@code from} represents a directory, then the directory and its contents
   * will be copied recursively to the destination {@code to}.<p>
   * The copy will fail if the destination if the destination already exists.<p>
   */
  FileSystem copy(String from, String to, boolean recursive, AsyncResultHandler<Void> handler);

  /**
   * Synchronous version of {@link #copy(String, String, boolean, AsyncResultHandler)}
   */
  FileSystem copySync(String from, String to, boolean recursive) ;

  /**
   * Move a file from the path {@code from} to path {@code to}, asynchronously.<p>
   * The move will fail if the destination already exists.<p>
   */
  FileSystem move(String from, String to, AsyncResultHandler<Void> handler);

  /**
   * Synchronous version of {@link #move(String, String, AsyncResultHandler)}
   */
  FileSystem moveSync(String from, String to) ;

  /**
   * Truncate the file represented by {@code path} to length {@code len} in bytes, asynchronously.<p>
   * The operation will fail if the file does not exist or {@code len} is less than {@code zero}.
   */
  FileSystem truncate(String path, long len, AsyncResultHandler<Void> handler);

  /**
   * Synchronous version of {@link #truncate(String, long, AsyncResultHandler)}
   */
  FileSystem truncateSync(String path, long len) ;

  /**
   * Change the permissions on the file represented by {@code path} to {@code perms}, asynchronously.
   * The permission String takes the form rwxr-x--- as
   * specified <a href="http://download.oracle.com/javase/7/docs/api/java/nio/file/attribute/PosixFilePermissions.html">here</a>.<p>
   */
  FileSystem chmod(String path, String perms, AsyncResultHandler<Void> handler);

  /**
   * Synchronous version of {@link #chmod(String, String, AsyncResultHandler)}
   */
  FileSystem chmodSync(String path, String perms) ;

  /**
   * Change the permissions on the file represented by {@code path} to {@code perms}, asynchronously.
   * The permission String takes the form rwxr-x--- as
   * specified in {<a href="http://download.oracle.com/javase/7/docs/api/java/nio/file/attribute/PosixFilePermissions.html">here</a>}.<p>
   * If the file is directory then all contents will also have their permissions changed recursively. Any directory permissions will
   * be set to {@code dirPerms}, whilst any normal file permissions will be set to {@code perms}.<p>
   */
  FileSystem chmod(String path, String perms, String dirPerms, AsyncResultHandler<Void> handler);

  /**
   * Synchronous version of {@link #chmod(String, String, String, AsyncResultHandler)}
   */
  FileSystem chmodSync(String path, String perms, String dirPerms) ;

  /**
   * Obtain properties for the file represented by {@code path}, asynchronously.
   * If the file is a link, the link will be followed.
   */
  FileSystem props(String path, AsyncResultHandler<FileProps> handler);

  /**
   * Synchronous version of {@link #props(String, AsyncResultHandler)}
   */
  FileProps propsSync(String path) ;

  /**
   * Obtain properties for the link represented by {@code path}, asynchronously.
   * The link will not be followed.
   */
  FileSystem lprops(String path, AsyncResultHandler<FileProps> handler);

  /**
   * Synchronous version of {@link #lprops(String, AsyncResultHandler)}
   */
  FileProps lpropsSync(String path) ;

  /**
   * Create a hard link on the file system from {@code link} to {@code existing}, asynchronously.
   */
  FileSystem link(String link, String existing, AsyncResultHandler<Void> handler);

  /**
   * Synchronous version of {@link #link(String, String, AsyncResultHandler)}
   */
  FileSystem linkSync(String link, String existing) ;

  /**
   * Create a symbolic link on the file system from {@code link} to {@code existing}, asynchronously.
   */
  FileSystem symlink(String link, String existing, AsyncResultHandler<Void> handler);

  /**
   * Synchronous version of {@link #link(String, String, AsyncResultHandler)}
   */
  FileSystem symlinkSync(String link, String existing) ;

  /**
   * Unlinks the link on the file system represented by the path {@code link}, asynchronously.
   */
  FileSystem unlink(String link, AsyncResultHandler<Void> handler);

  /**
   * Synchronous version of {@link #unlink(String, AsyncResultHandler)}
   */
  FileSystem unlinkSync(String link) ;

  /**
   * Returns the path representing the file that the symbolic link specified by {@code link} points to, asynchronously.
   */
  FileSystem readSymlink(String link, AsyncResultHandler<String> handler);

  /**
   * Synchronous version of {@link #readSymlink(String, AsyncResultHandler)}
   */
  String readSymlinkSync(String link) ;

  /**
   * Deletes the file represented by the specified {@code path}, asynchronously.
   */
  FileSystem delete(String path, AsyncResultHandler<Void> handler);

  /**
   * Synchronous version of {@link #delete(String, AsyncResultHandler)}
   */
  FileSystem deleteSync(String path) ;

  /**
   * Deletes the file represented by the specified {@code path}, asynchronously.<p>
   * If the path represents a directory and {@code recursive = true} then the directory and its contents will be
   * deleted recursively.
   */
  FileSystem delete(String path, boolean recursive, AsyncResultHandler<Void> handler);

  /**
   * Synchronous version of {@link #delete(String, boolean, AsyncResultHandler)}
   */
  FileSystem deleteSync(String path, boolean recursive) ;

  /**
   * Create the directory represented by {@code path}, asynchronously.<p>
   * The operation will fail if the directory already exists.
   */
  FileSystem mkdir(String path, AsyncResultHandler<Void> handler);

  /**
   * Synchronous version of {@link #mkdir(String, AsyncResultHandler)}
   */
  FileSystem mkdirSync(String path) ;

  /**
   * Create the directory represented by {@code path}, asynchronously.<p>
   * If {@code createParents} is set to {@code true} then any non-existent parent directories of the directory
   * will also be created.<p>
   * The operation will fail if the directory already exists.
   */
  FileSystem mkdir(String path, boolean createParents, AsyncResultHandler<Void> handler);

  /**
   * Synchronous version of {@link #mkdir(String, boolean, AsyncResultHandler)}
   */
  FileSystem mkdirSync(String path, boolean createParents) ;

  /**
   * Create the directory represented by {@code path}, asynchronously.<p>
   * The new directory will be created with permissions as specified by {@code perms}.
   * The permission String takes the form rwxr-x--- as specified
   * in <a href="http://download.oracle.com/javase/7/docs/api/java/nio/file/attribute/PosixFilePermissions.html">here</a>.<p>
   * The operation will fail if the directory already exists.
   */
  FileSystem mkdir(String path, String perms, AsyncResultHandler<Void> handler);

  /**
   * Synchronous version of {@link #mkdir(String, String, AsyncResultHandler)}
   */
  FileSystem mkdirSync(String path, String perms) ;

  /**
   * Create the directory represented by {@code path}, asynchronously.<p>
   * The new directory will be created with permissions as specified by {@code perms}.
   * The permission String takes the form rwxr-x--- as specified
   * in <a href="http://download.oracle.com/javase/7/docs/api/java/nio/file/attribute/PosixFilePermissions.html">here</a>.<p>
   * If {@code createParents} is set to {@code true} then any non-existent parent directories of the directory
   * will also be created.<p>
   * The operation will fail if the directory already exists.<p>
   */
  FileSystem mkdir(String path, String perms, boolean createParents, AsyncResultHandler<Void> handler);

  /**
   * Synchronous version of {@link #mkdir(String, String, boolean, AsyncResultHandler)}
   */
  FileSystem mkdirSync(String path, String perms, boolean createParents) ;

  /**
   * Read the contents of the directory specified by {@code path}, asynchronously.<p>
   * The result is an array of String representing the paths of the files inside the directory.
   */
  FileSystem readDir(String path, AsyncResultHandler<String[]> handler);

  /**
   * Synchronous version of {@link #readDir(String, AsyncResultHandler)}
   */
  String[] readDirSync(String path) ;

  /**
   * Read the contents of the directory specified by {@code path}, asynchronously.<p>
   * The paramater {@code filter} is a regular expression. If {@code filter} is specified then only the paths that
   * match  @{filter}will be returned.<p>
   * The result is an array of String representing the paths of the files inside the directory.
   */
  FileSystem readDir(String path, String filter, AsyncResultHandler<String[]> handler);

  /**
   * Synchronous version of {@link #readDir(String, String, AsyncResultHandler)}
   */
  String[] readDirSync(String path, String filter) ;

  /**
   * Reads the entire file as represented by the path {@code path} as a {@link Buffer}, asynchronously.<p>
   * Do not user this method to read very large files or you risk running out of available RAM.
   */
  FileSystem readFile(String path, AsyncResultHandler<Buffer> handler);

  /**
   * Synchronous version of {@link #readFile(String, AsyncResultHandler)}
   */
  Buffer readFileSync(String path) ;

  /**
   * Creates the file, and writes the specified {@code Buffer data} to the file represented by the path {@code path},
   * asynchronously.
   */
  FileSystem writeFile(String path, Buffer data, AsyncResultHandler<Void> handler);

  /**
   * Synchronous version of {@link #writeFile(String, Buffer, AsyncResultHandler)}
   */
  FileSystem writeFileSync(String path, Buffer data) ;

  /**
   * Open the file represented by {@code path}, asynchronously.<p>
   * The file is opened for both reading and writing. If the file does not already exist it will be created.
   * Write operations will not automatically flush to storage.
   */
  FileSystem open(String path, AsyncResultHandler<AsyncFile> handler);

  /**
   * Synchronous version of {@link #open(String, AsyncResultHandler)}
   */
  AsyncFile openSync(String path) ;

  /**
   * Open the file represented by {@code path}, asynchronously.<p>
   * The file is opened for both reading and writing. If the file does not already exist it will be created with the
   * permissions as specified by {@code perms}.
   * Write operations will not automatically flush to storage.
   */
  FileSystem open(String path, String perms, AsyncResultHandler<AsyncFile> handler);

  /**
   * Synchronous version of {@link #open(String, String, AsyncResultHandler)}
   */
  AsyncFile openSync(String path, String perms) ;

  /**
   * Open the file represented by {@code path}, asynchronously.<p>
   * The file is opened for both reading and writing. If the file does not already exist and
   * {@code createNew} is {@code true} it will be created with the permissions as specified by {@code perms}, otherwise
   * the operation will fail.
   * Write operations will not automatically flush to storage.
   */
  FileSystem open(String path, String perms, boolean createNew, AsyncResultHandler<AsyncFile> handler);

  /**
   * Synchronous version of {@link #open(String, String, boolean, AsyncResultHandler)}
   */
  AsyncFile openSync(String path, String perms, boolean createNew) ;

  /**
   * Open the file represented by {@code path}, asynchronously.<p>
   * If {@code read} is {@code true} the file will be opened for reading. If {@code write} is {@code true} the file
   * will be opened for writing.<p>
   * If the file does not already exist and
   * {@code createNew} is {@code true} it will be created with the permissions as specified by {@code perms}, otherwise
   * the operation will fail.<p>
   * Write operations will not automatically flush to storage.
   */
  FileSystem open(String path, String perms, boolean read, boolean write, boolean createNew, AsyncResultHandler<AsyncFile> handler);

  /**
   * Synchronous version of {@link #open(String, String, boolean, boolean, boolean, AsyncResultHandler)}
   */
  AsyncFile openSync(String path, String perms, boolean read, boolean write, boolean createNew) ;

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
  FileSystem open(String path, String perms, boolean read, boolean write, boolean createNew,
      boolean flush, AsyncResultHandler<AsyncFile> handler);

  /**
   * Synchronous version of {@link #open(String, String, boolean, boolean, boolean, boolean, AsyncResultHandler)}
   */
  AsyncFile openSync(String path, String perms, boolean read, boolean write, boolean createNew, boolean flush) ;

  /**
   * Creates an empty file with the specified {@code path}, asynchronously.
   */
  FileSystem createFile(String path, AsyncResultHandler<Void> handler);

  /**
   * Synchronous version of {@link #createFile(String, AsyncResultHandler)}
   */
  FileSystem createFileSync(String path) ;

  /**
   * Creates an empty file with the specified {@code path} and permissions {@code perms}, asynchronously.
   */
  FileSystem createFile(String path, String perms, AsyncResultHandler<Void> handler);

  /**
   * Synchronous version of {@link #createFile(String, String, AsyncResultHandler)}
   */
  FileSystem createFileSync(String path, String perms) ;

  /**
   * Determines whether the file as specified by the path {@code path} exists, asynchronously.
   */
  FileSystem exists(String path, AsyncResultHandler<Boolean> handler);

  /**
   * Synchronous version of {@link #exists(String, AsyncResultHandler)}
   */
  boolean existsSync(String path) ;

  /**
   * Returns properties of the file-system being used by the specified {@code path}, asynchronously.
   */
  FileSystem fsProps(String path, AsyncResultHandler<FileSystemProps> handler);

  /**
   * Synchronous version of {@link #fsProps(String, AsyncResultHandler)}
   */
  FileSystemProps fsPropsSync(String path) ;

}