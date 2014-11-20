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

package io.vertx.core.file;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.codegen.annotations.VertxGen;

import java.util.List;

/**
 * Contains a broad set of operations for manipulating files.<p>
 * A blocking and non blocking version of each operation is provided.<p>
 * The non blocking versions take a handler which is called when the operation completes or an error occurs.<p>
 * The blocking versions return the results, or throw exceptions directly.<p>
 * It is highly recommended the non blocking versions are used unless you are sure the operation
 * will not block for a significant period of time.<p>
 * Instances of FileSystem are thread-safe.<p>
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public interface FileSystem {

  /**
   * Copy a file from the path {@code from} to path {@code to}, asynchronously.<p>
   * The copy will fail if the destination already exists.<p>
   */
  @Fluent
  FileSystem copy(String from, String to, Handler<AsyncResult<Void>> handler);

  /**
   * Blocking version of {@link #copy(String, String, Handler)}
   */
  @Fluent
  FileSystem copyBlocking(String from, String to) ;

  /**
   * Copy a file from the path {@code from} to path {@code to}, asynchronously.<p>
   * If {@code recursive} is {@code true} and {@code from} represents a directory, then the directory and its contents
   * will be copied recursively to the destination {@code to}.<p>
   * The copy will fail if the destination if the destination already exists.<p>
   */
  @Fluent
  FileSystem copyRecursive(String from, String to, boolean recursive, Handler<AsyncResult<Void>> handler);

  /**
   * Blocking version of {@link #copyRecursive(String, String, boolean, Handler)}
   */
  @Fluent
  FileSystem copyRecursiveBlocking(String from, String to, boolean recursive) ;

  /**
   * Move a file from the path {@code from} to path {@code to}, asynchronously.<p>
   * The move will fail if the destination already exists.<p>
   */
  @Fluent
  FileSystem move(String from, String to, Handler<AsyncResult<Void>> handler);

  /**
   * Blocking version of {@link #move(String, String, Handler)}
   */
  @Fluent
  FileSystem moveBlocking(String from, String to) ;

  /**
   * Truncate the file represented by {@code path} to length {@code len} in bytes, asynchronously.<p>
   * The operation will fail if the file does not exist or {@code len} is less than {@code zero}.
   */
  @Fluent
  FileSystem truncate(String path, long len, Handler<AsyncResult<Void>> handler);

  /**
   * Blocking version of {@link #truncate(String, long, Handler)}
   */
  @Fluent
  FileSystem truncateBlocking(String path, long len) ;

  /**
   * Change the permissions on the file represented by {@code path} to {@code perms}, asynchronously.
   * The permission String takes the form rwxr-x--- as
   * specified <a href="http://download.oracle.com/javase/7/docs/api/java/nio/file/attribute/PosixFilePermissions.html">here</a>.<p>
   */
  @Fluent
  FileSystem chmod(String path, String perms, Handler<AsyncResult<Void>> handler);

  /**
   * Blocking version of {@link #chmod(String, String, Handler) }
   */
  @Fluent
  FileSystem chmodBlocking(String path, String perms) ;

  /**
   * Change the permissions on the file represented by {@code path} to {@code perms}, asynchronously.
   * The permission String takes the form rwxr-x--- as
   * specified in {<a href="http://download.oracle.com/javase/7/docs/api/java/nio/file/attribute/PosixFilePermissions.html">here</a>}.<p>
   * If the file is directory then all contents will also have their permissions changed recursively. Any directory permissions will
   * be set to {@code dirPerms}, whilst any normal file permissions will be set to {@code perms}.<p>
   */
  @Fluent
  FileSystem chmodRecursive(String path, String perms, String dirPerms, Handler<AsyncResult<Void>> handler);

  /**
   * Blocking version of {@link #chmodRecursive(String, String, String, Handler)}
   */
  @Fluent
  FileSystem chmodRecursiveBlocking(String path, String perms, String dirPerms) ;


  /**
   * Change the ownership on the file represented by {@code path} to {@code user} and {code group}, asynchronously.
   *
   */
  @Fluent
  FileSystem chown(String path, String user, String group, Handler<AsyncResult<Void>> handler);

  /**
   * Blocking version of {@link #chown(String, String, String, Handler)}
   *
   */
  @Fluent
  FileSystem chownBlocking(String path, String user, String group) ;

  /**
   * Obtain properties for the file represented by {@code path}, asynchronously.
   * If the file is a link, the link will be followed.
   */
  @Fluent
  FileSystem props(String path, Handler<AsyncResult<FileProps>> handler);

  /**
   * Blocking version of {@link #props(String, Handler)}
   */
  FileProps propsBlocking(String path) ;

  /**
   * Obtain properties for the link represented by {@code path}, asynchronously.
   * The link will not be followed.
   */
  @Fluent
  FileSystem lprops(String path, Handler<AsyncResult<FileProps>> handler);

  /**
   * Blocking version of {@link #lprops(String, Handler)}
   */
  FileProps lpropsBlocking(String path) ;

  /**
   * Create a hard link on the file system from {@code link} to {@code existing}, asynchronously.
   */
  @Fluent
  FileSystem link(String link, String existing, Handler<AsyncResult<Void>> handler);

  /**
   * Blocking version of {@link #link(String, String, Handler)}
   */
  @Fluent
  FileSystem linkBlocking(String link, String existing) ;

  /**
   * Create a symbolic link on the file system from {@code link} to {@code existing}, asynchronously.
   */
  @Fluent
  FileSystem symlink(String link, String existing, Handler<AsyncResult<Void>> handler);

  /**
   * Blocking version of {@link #link(String, String, Handler)}
   */
  @Fluent
  FileSystem symlinkBlocking(String link, String existing) ;

  /**
   * Unlinks the link on the file system represented by the path {@code link}, asynchronously.
   */
  @Fluent
  FileSystem unlink(String link, Handler<AsyncResult<Void>> handler);

  /**
   * Blocking version of {@link #unlink(String, Handler)}
   */
  @Fluent
  FileSystem unlinkBlocking(String link) ;

  /**
   * Returns the path representing the file that the symbolic link specified by {@code link} points to, asynchronously.
   */
  @Fluent
  FileSystem readSymlink(String link, Handler<AsyncResult<String>> handler);

  /**
   * Blocking version of {@link #readSymlink(String, Handler)}
   */
  String readSymlinkBlocking(String link) ;

  /**
   * Deletes the file represented by the specified {@code path}, asynchronously.
   */
  @Fluent
  FileSystem delete(String path, Handler<AsyncResult<Void>> handler);

  /**
   * Blocking version of {@link #delete(String, Handler)}
   */
  @Fluent
  FileSystem deleteBlocking(String path) ;

  /**
   * Deletes the file represented by the specified {@code path}, asynchronously.<p>
   * If the path represents a directory and {@code recursive = true} then the directory and its contents will be
   * deleted recursively.
   */
  @Fluent
  FileSystem deleteRecursive(String path, boolean recursive, Handler<AsyncResult<Void>> handler);

  /**
   * Blocking version of {@link #deleteRecursive(String, boolean, Handler)}
   */
  @Fluent
  FileSystem deleteRecursiveBlocking(String path, boolean recursive) ;

  /**
   * Create the directory represented by {@code path}, asynchronously.<p>
   * The operation will fail if the directory already exists.
   */
  @Fluent
  FileSystem mkdir(String path, Handler<AsyncResult<Void>> handler);

  /**
   * Blocking version of {@link #mkdir(String, Handler)}
   */
  @Fluent
  FileSystem mkdirBlocking(String path) ;

  /**
   * Create the directory represented by {@code path}, asynchronously.<p>
   * The new directory will be created with permissions as specified by {@code perms}.
   * The permission String takes the form rwxr-x--- as specified
   * in <a href="http://download.oracle.com/javase/7/docs/api/java/nio/file/attribute/PosixFilePermissions.html">here</a>.<p>
   * The operation will fail if the directory already exists.
   */
  @Fluent
  FileSystem mkdir(String path, String perms, Handler<AsyncResult<Void>> handler);

  /**
   * Blocking version of {@link #mkdir(String, String, Handler)}
   */
  @Fluent
  FileSystem mkdirBlocking(String path, String perms) ;

  /**
   * Create the directory represented by {@code path}, asynchronously.<p>
   * If {@code createParents} is set to {@code true} then any non-existent parent directories of the directory
   * will also be created.<p>
   * The operation will fail if the directory already exists.
   */
  @Fluent
  FileSystem mkdirs(String path, Handler<AsyncResult<Void>> handler);

  /**
   * Blocking version of {@link #mkdirs(String, Handler)}
   */
  @Fluent
  FileSystem mkdirsBlocking(String path) ;

  /**
   * Create the directory represented by {@code path}, asynchronously.<p>
   * The new directory will be created with permissions as specified by {@code perms}.
   * The permission String takes the form rwxr-x--- as specified
   * in <a href="http://download.oracle.com/javase/7/docs/api/java/nio/file/attribute/PosixFilePermissions.html">here</a>.<p>
   * If {@code createParents} is set to {@code true} then any non-existent parent directories of the directory
   * will also be created.<p>
   * The operation will fail if the directory already exists.<p>
   */
  @Fluent
  FileSystem mkdirs(String path, String perms, Handler<AsyncResult<Void>> handler);

  /**
   * Blocking version of {@link #mkdirs(String, String, Handler)}
   */
  @Fluent
  FileSystem mkdirsBlocking(String path, String perms) ;

  /**
   * Read the contents of the directory specified by {@code path}, asynchronously.<p>
   * The result is an array of String representing the paths of the files inside the directory.
   */
  @Fluent
  FileSystem readDir(String path, Handler<AsyncResult<List<String>>> handler);

  /**
   * Blocking version of {@link #readDir(String, Handler)}
   */
  List<String> readDirBlocking(String path) ;

  /**
   * Read the contents of the directory specified by {@code path}, asynchronously.<p>
   * The parameter {@code filter} is a regular expression. If {@code filter} is specified then only the paths that
   * match  @{filter}will be returned.<p>
   * The result is an array of String representing the paths of the files inside the directory.
   */
  @Fluent
  FileSystem readDir(String path, String filter, Handler<AsyncResult<List<String>>> handler);

  /**
   * Blocking version of {@link #readDir(String, String, Handler)}
   */
  List<String> readDirBlocking(String path, String filter) ;

  /**
   * Reads the entire file as represented by the path {@code path} as a {@link Buffer}, asynchronously.<p>
   * Do not user this method to read very large files or you risk running out of available RAM.
   */
  @Fluent
  FileSystem readFile(String path, Handler<AsyncResult<Buffer>> handler);

  /**
   * Blocking version of {@link #readFile(String, Handler)}
   */
  Buffer readFileBlocking(String path) ;

  /**
   * Creates the file, and writes the specified {@code Buffer data} to the file represented by the path {@code path},
   * asynchronously.
   */
  @Fluent
  FileSystem writeFile(String path, Buffer data, Handler<AsyncResult<Void>> handler);

  /**
   * Blocking version of {@link #writeFile(String, Buffer, Handler)}
   */
  @Fluent
  FileSystem writeFileBlocking(String path, Buffer data) ;

  /**
   * Open the file represented by {@code path}, asynchronously.<p>
   * The file is opened for both reading and writing. If the file does not already exist it will be created.
   * Write operations will not automatically flush to storage.
   */
  @Fluent
  FileSystem open(String path, OpenOptions options, Handler<AsyncResult<AsyncFile>> handler);

  /**
   * Blocking version of {@link #open(String, io.vertx.core.file.OpenOptions, Handler)}
   */
  AsyncFile openBlocking(String path, OpenOptions options);

  /**
   * Creates an empty file with the specified {@code path}, asynchronously.
   */
  @Fluent
  FileSystem createFile(String path, Handler<AsyncResult<Void>> handler);

  /**
   * Blocking version of {@link #createFile(String, Handler)}
   */
  @Fluent
  FileSystem createFileBlocking(String path) ;

  /**
   * Creates an empty file with the specified {@code path} and permissions {@code perms}, asynchronously.
   */
  @Fluent
  FileSystem createFile(String path, String perms, Handler<AsyncResult<Void>> handler);

  /**
   * Blocking version of {@link #createFile(String, String, Handler)}
   */
  @Fluent
  FileSystem createFileBlocking(String path, String perms) ;

  /**
   * Determines whether the file as specified by the path {@code path} exists, asynchronously.
   */
  @Fluent
  FileSystem exists(String path, Handler<AsyncResult<Boolean>> handler);

  /**
   * Blocking version of {@link #exists(String, Handler)}
   */
  boolean existsBlocking(String path) ;

  /**
   * Returns properties of the file-system being used by the specified {@code path}, asynchronously.
   */
  @Fluent
  FileSystem fsProps(String path, Handler<AsyncResult<FileSystemProps>> handler);

  /**
   * Blocking version of {@link #fsProps(String, Handler)}
   */
  FileSystemProps fsPropsBlocking(String path) ;

}