/*
 * Copyright (c) 2011-2013 The original author or authors
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

package org.vertx.java.core.file;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;

/**
 * Contains a broad set of operations for manipulating files.<p>
 * An asynchronous and a synchronous version of each operation is provided.<p>
 * The asynchronous versions take a handler which is called when the operation completes or an error occurs.<p>
 * The synchronous versions return the results, or throw exceptions directly.<p>
 * It is highly recommended the asynchronous versions are used unless you are sure the operation
 * will not block for a significant period of time.<p>
 * Instances of FileSystem are thread-safe.<p>
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface FileSystem {

  /**
   * Copy a file from the path {@code from} to path {@code to}, asynchronously.<p>
   * The copy will fail if the destination already exists.<p>
   */
  FileSystem copy(String from, String to, Handler<AsyncResult<Void>> handler);

  /**
   * Synchronous version of {@link #copy(String, String, Handler)}
   */
  FileSystem copySync(String from, String to) ;

  /**
   * Copy a file from the path {@code from} to path {@code to}, asynchronously.<p>
   * If {@code recursive} is {@code true} and {@code from} represents a directory, then the directory and its contents
   * will be copied recursively to the destination {@code to}.<p>
   * The copy will fail if the destination if the destination already exists.<p>
   */
  FileSystem copy(String from, String to, boolean recursive, Handler<AsyncResult<Void>> handler);

  /**
   * Synchronous version of {@link #copy(String, String, boolean, Handler)}
   */
  FileSystem copySync(String from, String to, boolean recursive) ;

  /**
   * Move a file from the path {@code from} to path {@code to}, asynchronously.<p>
   * The move will fail if the destination already exists.<p>
   */
  FileSystem move(String from, String to, Handler<AsyncResult<Void>> handler);

  /**
   * Synchronous version of {@link #move(String, String, Handler)}
   */
  FileSystem moveSync(String from, String to) ;

  /**
   * Truncate the file represented by {@code path} to length {@code len} in bytes, asynchronously.<p>
   * The operation will fail if the file does not exist or {@code len} is less than {@code zero}.
   */
  FileSystem truncate(String path, long len, Handler<AsyncResult<Void>> handler);

  /**
   * Synchronous version of {@link #truncate(String, long, Handler)}
   */
  FileSystem truncateSync(String path, long len) ;

  /**
   * Change the permissions on the file represented by {@code path} to {@code perms}, asynchronously.
   * The permission String takes the form rwxr-x--- as
   * specified <a href="http://download.oracle.com/javase/7/docs/api/java/nio/file/attribute/PosixFilePermissions.html">here</a>.<p>
   */
  FileSystem chmod(String path, String perms, Handler<AsyncResult<Void>> handler);

  /**
   * Synchronous version of {@link #chmod(String, String, Handler) }
   */
  FileSystem chmodSync(String path, String perms) ;

  /**
   * Change the permissions on the file represented by {@code path} to {@code perms}, asynchronously.
   * The permission String takes the form rwxr-x--- as
   * specified in {<a href="http://download.oracle.com/javase/7/docs/api/java/nio/file/attribute/PosixFilePermissions.html">here</a>}.<p>
   * If the file is directory then all contents will also have their permissions changed recursively. Any directory permissions will
   * be set to {@code dirPerms}, whilst any normal file permissions will be set to {@code perms}.<p>
   */
  FileSystem chmod(String path, String perms, String dirPerms, Handler<AsyncResult<Void>> handler);

  /**
   * Synchronous version of {@link #chmod(String, String, String, Handler)}
   */
  FileSystem chmodSync(String path, String perms, String dirPerms) ;


  /**
   * Change the ownership on the file represented by {@code path} to {@code user} and {code group}, asynchronously.
   *
   */
  FileSystem chown(String path, String user, String group, Handler<AsyncResult<Void>> handler);

  /**
   * Synchronous version of {@link #chown(String, String, String, Handler)}
   *
   */
  FileSystem chownSync(String path, String user, String group) ;

  /**
   * Obtain properties for the file represented by {@code path}, asynchronously.
   * If the file is a link, the link will be followed.
   */
  FileSystem props(String path, Handler<AsyncResult<FileProps>> handler);

  /**
   * Synchronous version of {@link #props(String, Handler)}
   */
  FileProps propsSync(String path) ;

  /**
   * Obtain properties for the link represented by {@code path}, asynchronously.
   * The link will not be followed.
   */
  FileSystem lprops(String path, Handler<AsyncResult<FileProps>> handler);

  /**
   * Synchronous version of {@link #lprops(String, Handler)}
   */
  FileProps lpropsSync(String path) ;

  /**
   * Create a hard link on the file system from {@code link} to {@code existing}, asynchronously.
   */
  FileSystem link(String link, String existing, Handler<AsyncResult<Void>> handler);

  /**
   * Synchronous version of {@link #link(String, String, Handler)}
   */
  FileSystem linkSync(String link, String existing) ;

  /**
   * Create a symbolic link on the file system from {@code link} to {@code existing}, asynchronously.
   */
  FileSystem symlink(String link, String existing, Handler<AsyncResult<Void>> handler);

  /**
   * Synchronous version of {@link #link(String, String, Handler)}
   */
  FileSystem symlinkSync(String link, String existing) ;

  /**
   * Unlinks the link on the file system represented by the path {@code link}, asynchronously.
   */
  FileSystem unlink(String link, Handler<AsyncResult<Void>> handler);

  /**
   * Synchronous version of {@link #unlink(String, Handler)}
   */
  FileSystem unlinkSync(String link) ;

  /**
   * Returns the path representing the file that the symbolic link specified by {@code link} points to, asynchronously.
   */
  FileSystem readSymlink(String link, Handler<AsyncResult<String>> handler);

  /**
   * Synchronous version of {@link #readSymlink(String, Handler)}
   */
  String readSymlinkSync(String link) ;

  /**
   * Deletes the file represented by the specified {@code path}, asynchronously.
   */
  FileSystem delete(String path, Handler<AsyncResult<Void>> handler);

  /**
   * Synchronous version of {@link #delete(String, Handler)}
   */
  FileSystem deleteSync(String path) ;

  /**
   * Deletes the file represented by the specified {@code path}, asynchronously.<p>
   * If the path represents a directory and {@code recursive = true} then the directory and its contents will be
   * deleted recursively.
   */
  FileSystem delete(String path, boolean recursive, Handler<AsyncResult<Void>> handler);

  /**
   * Synchronous version of {@link #delete(String, boolean, Handler)}
   */
  FileSystem deleteSync(String path, boolean recursive) ;

  /**
   * Create the directory represented by {@code path}, asynchronously.<p>
   * The operation will fail if the directory already exists.
   */
  FileSystem mkdir(String path, Handler<AsyncResult<Void>> handler);

  /**
   * Synchronous version of {@link #mkdir(String, Handler)}
   */
  FileSystem mkdirSync(String path) ;

  /**
   * Create the directory represented by {@code path}, asynchronously.<p>
   * If {@code createParents} is set to {@code true} then any non-existent parent directories of the directory
   * will also be created.<p>
   * The operation will fail if the directory already exists.
   */
  FileSystem mkdir(String path, boolean createParents, Handler<AsyncResult<Void>> handler);

  /**
   * Synchronous version of {@link #mkdir(String, boolean, Handler)}
   */
  FileSystem mkdirSync(String path, boolean createParents) ;

  /**
   * Create the directory represented by {@code path}, asynchronously.<p>
   * The new directory will be created with permissions as specified by {@code perms}.
   * The permission String takes the form rwxr-x--- as specified
   * in <a href="http://download.oracle.com/javase/7/docs/api/java/nio/file/attribute/PosixFilePermissions.html">here</a>.<p>
   * The operation will fail if the directory already exists.
   */
  FileSystem mkdir(String path, String perms, Handler<AsyncResult<Void>> handler);

  /**
   * Synchronous version of {@link #mkdir(String, String, Handler)}
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
  FileSystem mkdir(String path, String perms, boolean createParents, Handler<AsyncResult<Void>> handler);

  /**
   * Synchronous version of {@link #mkdir(String, String, boolean, Handler)}
   */
  FileSystem mkdirSync(String path, String perms, boolean createParents) ;

  /**
   * Read the contents of the directory specified by {@code path}, asynchronously.<p>
   * The result is an array of String representing the paths of the files inside the directory.
   */
  FileSystem readDir(String path, Handler<AsyncResult<String[]>> handler);

  /**
   * Synchronous version of {@link #readDir(String, Handler)}
   */
  String[] readDirSync(String path) ;

  /**
   * Read the contents of the directory specified by {@code path}, asynchronously.<p>
   * The parameter {@code filter} is a regular expression. If {@code filter} is specified then only the paths that
   * match  @{filter}will be returned.<p>
   * The result is an array of String representing the paths of the files inside the directory.
   */
  FileSystem readDir(String path, String filter, Handler<AsyncResult<String[]>> handler);

  /**
   * Synchronous version of {@link #readDir(String, String, Handler)}
   */
  String[] readDirSync(String path, String filter) ;

  /**
   * Reads the entire file as represented by the path {@code path} as a {@link Buffer}, asynchronously.<p>
   * Do not user this method to read very large files or you risk running out of available RAM.
   */
  FileSystem readFile(String path, Handler<AsyncResult<Buffer>> handler);

  /**
   * Synchronous version of {@link #readFile(String, Handler)}
   */
  Buffer readFileSync(String path) ;

  /**
   * Creates the file, and writes the specified {@code Buffer data} to the file represented by the path {@code path},
   * asynchronously.
   */
  FileSystem writeFile(String path, Buffer data, Handler<AsyncResult<Void>> handler);

  /**
   * Synchronous version of {@link #writeFile(String, Buffer, Handler)}
   */
  FileSystem writeFileSync(String path, Buffer data) ;

  /**
   * Open the file represented by {@code path}, asynchronously.<p>
   * The file is opened for both reading and writing. If the file does not already exist it will be created.
   * Write operations will not automatically flush to storage.
   */
  FileSystem open(String path, Handler<AsyncResult<AsyncFile>> handler);

  /**
   * Synchronous version of {@link #open(String, Handler)}
   */
  AsyncFile openSync(String path) ;

  /**
   * Open the file represented by {@code path}, asynchronously.<p>
   * The file is opened for both reading and writing. If the file does not already exist it will be created with the
   * permissions as specified by {@code perms}.
   * Write operations will not automatically flush to storage.
   */
  FileSystem open(String path, String perms, Handler<AsyncResult<AsyncFile>> handler);

  /**
   * Synchronous version of {@link #open(String, String, Handler)}
   */
  AsyncFile openSync(String path, String perms) ;

  /**
   * Open the file represented by {@code path}, asynchronously.<p>
   * The file is opened for both reading and writing. If the file does not already exist and
   * {@code createNew} is {@code true} it will be created with the permissions as specified by {@code perms}, otherwise
   * the operation will fail.
   * Write operations will not automatically flush to storage.
   */
  FileSystem open(String path, String perms, boolean createNew, Handler<AsyncResult<AsyncFile>> handler);

  /**
   * Synchronous version of {@link #open(String, String, boolean, Handler)}
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
  FileSystem open(String path, String perms, boolean read, boolean write, boolean createNew, Handler<AsyncResult<AsyncFile>> handler);

  /**
   * Synchronous version of {@link #open(String, String, boolean, boolean, boolean, Handler)}
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
      boolean flush, Handler<AsyncResult<AsyncFile>> handler);

  /**
   * Synchronous version of {@link #open(String, String, boolean, boolean, boolean, boolean, Handler)}
   */
  AsyncFile openSync(String path, String perms, boolean read, boolean write, boolean createNew, boolean flush) ;

  /**
   * Creates an empty file with the specified {@code path}, asynchronously.
   */
  FileSystem createFile(String path, Handler<AsyncResult<Void>> handler);

  /**
   * Synchronous version of {@link #createFile(String, Handler)}
   */
  FileSystem createFileSync(String path) ;

  /**
   * Creates an empty file with the specified {@code path} and permissions {@code perms}, asynchronously.
   */
  FileSystem createFile(String path, String perms, Handler<AsyncResult<Void>> handler);

  /**
   * Synchronous version of {@link #createFile(String, String, Handler)}
   */
  FileSystem createFileSync(String path, String perms) ;

  /**
   * Determines whether the file as specified by the path {@code path} exists, asynchronously.
   */
  FileSystem exists(String path, Handler<AsyncResult<Boolean>> handler);

  /**
   * Synchronous version of {@link #exists(String, Handler)}
   */
  boolean existsSync(String path) ;

  /**
   * Returns properties of the file-system being used by the specified {@code path}, asynchronously.
   */
  FileSystem fsProps(String path, Handler<AsyncResult<FileSystemProps>> handler);

  /**
   * Synchronous version of {@link #fsProps(String, Handler)}
   */
  FileSystemProps fsPropsSync(String path) ;

}