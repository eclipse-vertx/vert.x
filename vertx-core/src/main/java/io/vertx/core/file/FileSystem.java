/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.file;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;

import java.util.List;

/**
 * Contains a broad set of operations for manipulating files on the file system.
 * <p>
 * A (potential) blocking and non blocking version of each operation is provided.
 * <p>
 * The non blocking versions take a handler which is called when the operation completes or an error occurs.
 * <p>
 * The blocking versions are named {@code xxxBlocking} and return the results, or throw exceptions directly.
 * In many cases, depending on the operating system and file system some of the potentially blocking operations
 * can return quickly, which is why we provide them, but it's highly recommended that you test how long they take to
 * return in your particular application before using them on an event loop.
 * <p>
 * Please consult the documentation for more information on file system support.
 *
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public interface FileSystem {

  /**
   * Copy a file from the path {@code from} to path {@code to}, asynchronously.
   * <p>
   * The copy will fail if the destination already exists.
   *
   * @param from  the path to copy from
   * @param to  the path to copy to
   * @return a future notified on completion
   */
  Future<Void> copy(String from, String to);

  /**
   * Copy a file from the path {@code from} to path {@code to}, asynchronously.
   *
   * @param from    the path to copy from
   * @param to      the path to copy to
   * @param options options describing how the file should be copied
   * @return a future notified on completion
   */
  Future<Void> copy(String from, String to, CopyOptions options);

  /**
   * Blocking version of {@link #copy(String, String)}
   */
  @Fluent
  FileSystem copyBlocking(String from, String to) ;

  /**
   * Copy a file from the path {@code from} to path {@code to}, asynchronously.
   * <p>
   * If {@code recursive} is {@code true} and {@code from} represents a directory, then the directory and its contents
   * will be copied recursively to the destination {@code to}.
   * <p>
   * The copy will fail if the destination if the destination already exists.
   *
   * @param from  the path to copy from
   * @param to  the path to copy to
   * @param recursive
   * @return a future notified on completion
   */
  Future<Void> copyRecursive(String from, String to, boolean recursive);

  /**
   * Blocking version of {@link #copyRecursive(String, String, boolean)}
   */
  @Fluent
  FileSystem copyRecursiveBlocking(String from, String to, boolean recursive) ;

  /**
   * Move a file from the path {@code from} to path {@code to}, asynchronously.
   * <p>
   * The move will fail if the destination already exists.
   *
   * @param from  the path to copy from
   * @param to  the path to copy to
   * @return a future notified on completion
   */
  Future<Void> move(String from, String to);

  /**
   * Move a file from the path {@code from} to path {@code to}, asynchronously.
   *
   * @param from    the path to copy from
   * @param to      the path to copy to
   * @param options options describing how the file should be copied
   * @return a future notified on completion
   */
  Future<Void> move(String from, String to, CopyOptions options);

  /**
   * Blocking version of {@link #move(String, String)}
   */
  @Fluent
  FileSystem moveBlocking(String from, String to) ;

  /**
   * Truncate the file represented by {@code path} to length {@code len} in bytes, asynchronously.
   * <p>
   * The operation will fail if the file does not exist or {@code len} is less than {@code zero}.
   *
   * @param path  the path to the file
   * @param len  the length to truncate it to
   * @return a future notified on completion
   */
  Future<Void> truncate(String path, long len);

  /**
   * Blocking version of {@link #truncate(String, long)}
   */
  @Fluent
  FileSystem truncateBlocking(String path, long len) ;

  /**
   * Change the permissions on the file represented by {@code path} to {@code perms}, asynchronously.
   * <p>
   * The permission String takes the form rwxr-x--- as
   * specified <a href="http://download.oracle.com/javase/7/docs/api/java/nio/file/attribute/PosixFilePermissions.html">here</a>.
   *
   * @param path  the path to the file
   * @param perms  the permissions string
   * @return a future notified on completion
   */
  Future<Void> chmod(String path, String perms);

  /**
   * Blocking version of {@link #chmod(String, String) }
   */
  @Fluent
  FileSystem chmodBlocking(String path, String perms) ;

  /**
   * Change the permissions on the file represented by {@code path} to {@code perms}, asynchronously.<p>
   * The permission String takes the form rwxr-x--- as
   * specified in {<a href="http://download.oracle.com/javase/7/docs/api/java/nio/file/attribute/PosixFilePermissions.html">here</a>}.
   * <p>
   * If the file is directory then all contents will also have their permissions changed recursively. Any directory permissions will
   * be set to {@code dirPerms}, whilst any normal file permissions will be set to {@code perms}.
   *
   * @param path  the path to the file
   * @param perms  the permissions string
   * @param dirPerms  the directory permissions
   * @return a future notified on completion
   */
  Future<Void> chmodRecursive(String path, String perms, String dirPerms);

  /**
   * Blocking version of {@link #chmodRecursive(String, String, String)}
   */
  @Fluent
  FileSystem chmodRecursiveBlocking(String path, String perms, String dirPerms) ;


  /**
   * Change the ownership on the file represented by {@code path} to {@code user} and {code group}, asynchronously.
   *
   * @param path  the path to the file
   * @param user  the user name, {@code null} will not change the user name
   * @param group  the user group, {@code null} will not change the user group name
   * @return a future notified on completion
   */
  Future<Void> chown(String path, @Nullable String user, @Nullable String group);

  /**
   * Blocking version of {@link #chown(String, String, String)}
   *
   */
  @Fluent
  FileSystem chownBlocking(String path, @Nullable String user, @Nullable String group) ;

  /**
   * Obtain properties for the file represented by {@code path}, asynchronously.
   * <p>
   * If the file is a link, the link will be followed.
   *
   * @param path  the path to the file
   * @return a future notified on completion
   */
  Future<FileProps> props(String path);

  /**
   * Blocking version of {@link #props(String)}
   */
  FileProps propsBlocking(String path) ;

  /**
   * Obtain properties for the link represented by {@code path}, asynchronously.
   * <p>
   * The link will not be followed.
   *
   * @param path  the path to the file
   * @return a future notified on completion
   */
  Future<FileProps> lprops(String path);

  /**
   * Blocking version of {@link #lprops(String)}
   */
  FileProps lpropsBlocking(String path) ;

  /**
   * Create a hard link on the file system from {@code link} to {@code existing}, asynchronously.
   *
   * @param link  the link
   * @param existing  the link destination
   * @return a future notified on completion
   */
  Future<Void> link(String link, String existing);

  /**
   * Blocking version of {@link #link(String, String)}
   */
  @Fluent
  FileSystem linkBlocking(String link, String existing) ;

  /**
   * Create a symbolic link on the file system from {@code link} to {@code existing}, asynchronously.
   *
   * @param link  the link
   * @param existing  the link destination
   * @return a future notified on completion
   */
  Future<Void> symlink(String link, String existing);

  /**
   * Blocking version of {@link #link(String, String)}
   */
  @Fluent
  FileSystem symlinkBlocking(String link, String existing) ;

  /**
   * Unlinks the link on the file system represented by the path {@code link}, asynchronously.
   *
   * @param link  the link
   * @return a future notified on completion
   */
  Future<Void> unlink(String link);

  /**
   * Blocking version of {@link #unlink(String)}
   */
  @Fluent
  FileSystem unlinkBlocking(String link) ;

  /**
   * Returns the path representing the file that the symbolic link specified by {@code link} points to, asynchronously.
   *
   * @param link  the link
   * @return a future notified on completion
   */
  Future<String> readSymlink(String link);

  /**
   * Blocking version of {@link #readSymlink(String)}
   */
  String readSymlinkBlocking(String link) ;

  /**
   * Deletes the file represented by the specified {@code path}, asynchronously.
   *
   * @param path  path to the file
   * @return a future notified on completion
   */
  Future<Void> delete(String path);

  /**
   * Blocking version of {@link #delete(String)}
   */
  @Fluent
  FileSystem deleteBlocking(String path) ;

  /**
   * Deletes the file represented by the specified {@code path}, asynchronously.
   * <p>
   * If the path represents a directory and {@code recursive = true} then the directory and its contents will be
   * deleted recursively.
   *
   * @param path  path to the file
   * @return a future notified on completion
   */
  Future<Void> deleteRecursive(String path);

  /**
   * Blocking version of {@link #deleteRecursive(String)}
   */
  @Fluent
  FileSystem deleteRecursiveBlocking(String path) ;

  /**
   * Create the directory represented by {@code path}, asynchronously.
   * <p>
   * The operation will fail if the directory already exists.
   *
   * @param path  path to the file
   * @return a future notified on completion
   */
  Future<Void> mkdir(String path);

  /**
   * Blocking version of {@link #mkdir(String)}
   */
  @Fluent
  FileSystem mkdirBlocking(String path) ;

  /**
   * Create the directory represented by {@code path}, asynchronously.
   * <p>
   * The new directory will be created with permissions as specified by {@code perms}.
   * <p>
   * The permission String takes the form rwxr-x--- as specified
   * in <a href="http://download.oracle.com/javase/7/docs/api/java/nio/file/attribute/PosixFilePermissions.html">here</a>.
   * <p>
   * The operation will fail if the directory already exists.
   *
   * @param path  path to the file
   * @param perms  the permissions string
   * @return a future notified on completion
   */
  Future<Void> mkdir(String path, String perms);

  /**
   * Blocking version of {@link #mkdir(String, String)}
   */
  @Fluent
  FileSystem mkdirBlocking(String path, String perms) ;

  /**
   * Create the directory represented by {@code path} and any non existent parents, asynchronously.
   * <p>
   * The operation will fail if the {@code path} already exists but is not a directory.
   *
   * @param path  path to the file
   * @return a future notified on completion
   */
  Future<Void> mkdirs(String path);

  /**
   * Blocking version of {@link #mkdirs(String)}
   */
  @Fluent
  FileSystem mkdirsBlocking(String path) ;

  /**
   * Create the directory represented by {@code path} and any non existent parents, asynchronously.
   * <p>
   * The new directory will be created with permissions as specified by {@code perms}.
   * <p>
   * The permission String takes the form rwxr-x--- as specified
   * in <a href="http://download.oracle.com/javase/7/docs/api/java/nio/file/attribute/PosixFilePermissions.html">here</a>.
   * <p>
   * The operation will fail if the {@code path} already exists but is not a directory.
   *
   * @param path  path to the file
   * @param perms  the permissions string
   * @return a future notified on completion
   */
  Future<Void> mkdirs(String path, String perms);

  /**
   * Blocking version of {@link #mkdirs(String, String)}
   */
  @Fluent
  FileSystem mkdirsBlocking(String path, String perms) ;

  /**
   * Read the contents of the directory specified by {@code path}, asynchronously.
   * <p>
   * The result is an array of String representing the paths of the files inside the directory.
   *
   * @param path  path to the file
   * @return a future notified on completion
   */
  Future<List<String>> readDir(String path);

  /**
   * Blocking version of {@link #readDir(String)}
   */
  List<String> readDirBlocking(String path) ;

  /**
   * Read the contents of the directory specified by {@code path}, asynchronously.
   * <p>
   * The parameter {@code filter} is a regular expression. If {@code filter} is specified then only the paths that
   * match  @{filter}will be returned.
   * <p>
   * The result is an array of String representing the paths of the files inside the directory.
   *
   * @param path  path to the directory
   * @param filter  the filter expression
   * @return a future notified on completion
   */
  Future<List<String>> readDir(String path, String filter);

  /**
   * Blocking version of {@link #readDir(String, String)}
   */
  List<String> readDirBlocking(String path, String filter) ;

  /**
   * Reads the entire file as represented by the path {@code path} as a {@link Buffer}, asynchronously.
   * <p>
   * Do not use this method to read very large files or you risk running out of available RAM.
   *
   * @param path  path to the file
   * @return a future notified on completion
   */
  Future<Buffer> readFile(String path);

  /**
   * Blocking version of {@link #readFile(String)}
   */
  Buffer readFileBlocking(String path) ;

  /**
   * Creates the file, and writes the specified {@code Buffer data} to the file represented by the path {@code path},
   * asynchronously.
   *
   * @param path  path to the file
   * @return a future notified on completion
   */
  Future<Void> writeFile(String path, Buffer data);

  /**
   * Blocking version of {@link #writeFile(String, Buffer)}
   */
  @Fluent
  FileSystem writeFileBlocking(String path, Buffer data) ;

  /**
   * Open the file represented by {@code path}, asynchronously.
   * <p>
   * The file is opened for both reading and writing. If the file does not already exist it will be created.
   *
   * @param path  path to the file
   * @param options options describing how the file should be opened
   * @return a future notified on completion
   */
  Future<AsyncFile> open(String path, OpenOptions options);

  /**
   * Blocking version of {@link #open(String, io.vertx.core.file.OpenOptions)}
   */
  AsyncFile openBlocking(String path, OpenOptions options);

  /**
   * Creates an empty file with the specified {@code path}, asynchronously.
   *
   * @param path  path to the file
   * @return a future notified on completion
   */
  Future<Void> createFile(String path);

  /**
   * Blocking version of {@link #createFile(String)}
   */
  @Fluent
  FileSystem createFileBlocking(String path) ;

  /**
   * Creates an empty file with the specified {@code path} and permissions {@code perms}, asynchronously.
   *
   * @param path  path to the file
   * @param perms  the permissions string
   * @return a future notified on completion
   */
  Future<Void> createFile(String path, String perms);

  /**
   * Blocking version of {@link #createFile(String, String)}
   */
  @Fluent
  FileSystem createFileBlocking(String path, String perms) ;

  /**
   * Determines whether the file as specified by the path {@code path} exists, asynchronously.
   *
   * @param path  path to the file
   * @return a future notified on completion
   */
  Future<Boolean> exists(String path);

  /**
   * Blocking version of {@link #exists(String)}
   */
  boolean existsBlocking(String path) ;

  /**
   * Returns properties of the file-system being used by the specified {@code path}, asynchronously.
   *
   * @param path  path to anywhere on the filesystem
   * @return a future notified on completion
   */
  Future<FileSystemProps> fsProps(String path);

  /**
   * Blocking version of {@link #fsProps(String)}
   */
  FileSystemProps fsPropsBlocking(String path) ;

  /**
   * Creates a new directory in the default temporary-file directory, using the given
   * prefix to generate its name, asynchronously.
   *
   * <p>
   * As with the {@code File.createTempFile} methods, this method is only
   * part of a temporary-file facility.A {@link Runtime#addShutdownHook shutdown-hook},
   * or the {@link java.io.File#deleteOnExit} mechanism may be used to delete the directory automatically.
   * </p>
   *
   * @param prefix  the prefix string to be used in generating the directory's name;
   *                may be {@code null}
   * @return a future notified on completion
   */
  Future<String> createTempDirectory(String prefix);

  /**
   * Blocking version of {@link #createTempDirectory(String)}
   */
  String createTempDirectoryBlocking(String prefix);

  /**
   * Creates a new directory in the default temporary-file directory, using the given
   * prefix to generate its name, asynchronously.
   * <p>
   * The new directory will be created with permissions as specified by {@code perms}.
   * </p>
   * The permission String takes the form rwxr-x--- as specified
   * in <a href="http://download.oracle.com/javase/7/docs/api/java/nio/file/attribute/PosixFilePermissions.html">here</a>.
   *
   * <p>
   * As with the {@code File.createTempFile} methods, this method is only
   * part of a temporary-file facility.A {@link Runtime#addShutdownHook shutdown-hook},
   * or the {@link java.io.File#deleteOnExit} mechanism may be used to delete the directory automatically.
   * </p>
   *
   * @param prefix  the prefix string to be used in generating the directory's name;
   *                may be {@code null}
   * @param perms   the permissions string
   * @return a future notified on completion
   */
  Future<String> createTempDirectory(String prefix, String perms);

  /**
   * Blocking version of {@link #createTempDirectory(String, String)}
   */
  String createTempDirectoryBlocking(String prefix, String perms);

  /**
   * Creates a new directory in the directory provided by the path {@code path}, using the given
   * prefix to generate its name, asynchronously.
   * <p>
   * The new directory will be created with permissions as specified by {@code perms}.
   * </p>
   * The permission String takes the form rwxr-x--- as specified
   * in <a href="http://download.oracle.com/javase/7/docs/api/java/nio/file/attribute/PosixFilePermissions.html">here</a>.
   *
   * <p>
   * As with the {@code File.createTempFile} methods, this method is only
   * part of a temporary-file facility.A {@link Runtime#addShutdownHook shutdown-hook},
   * or the {@link java.io.File#deleteOnExit} mechanism may be used to delete the directory automatically.
   * </p>
   *
   * @param dir     the path to directory in which to create the directory
   * @param prefix  the prefix string to be used in generating the directory's name;
   *                may be {@code null}
   * @param perms   the permissions string
   * @return a future notified on completion
   */
  Future<String> createTempDirectory(String dir, String prefix, String perms);

  /**
   * Blocking version of {@link #createTempDirectory(String, String, String)}
   */
  String createTempDirectoryBlocking(String dir, String prefix, String perms);


  /**
   * Creates a new file in the default temporary-file directory, using the given
   * prefix and suffix to generate its name, asynchronously.
   *
   * <p>
   * As with the {@code File.createTempFile} methods, this method is only
   * part of a temporary-file facility.A {@link Runtime#addShutdownHook shutdown-hook},
   * or the {@link java.io.File#deleteOnExit} mechanism may be used to delete the directory automatically.
   * </p>
   *
   * @param prefix  the prefix string to be used in generating the directory's name;
   *                may be {@code null}
   * @param suffix  the suffix string to be used in generating the file's name;
   *                may be {@code null}, in which case "{@code .tmp}" is used
   * @return a future notified on completion
   */
  Future<String> createTempFile(String prefix, String suffix);

  /**
   * Blocking version of {@link #createTempFile(String, String)}
   */
  String createTempFileBlocking(String prefix, String suffix);

  /**
   * Creates a new file in the directory provided by the path {@code dir}, using the given
   * prefix and suffix to generate its name, asynchronously.
   *
   * <p>
   * As with the {@code File.createTempFile} methods, this method is only
   * part of a temporary-file facility.A {@link Runtime#addShutdownHook shutdown-hook},
   * or the {@link java.io.File#deleteOnExit} mechanism may be used to delete the directory automatically.
   * </p>
   *
   * @param prefix  the prefix string to be used in generating the directory's name;
   *                may be {@code null}
   * @param suffix  the suffix string to be used in generating the file's name;
   *                may be {@code null}, in which case "{@code .tmp}" is used
   * @return a future notified on completion
   */
  Future<String> createTempFile(String prefix, String suffix, String perms);

  /**
   * Blocking version of {@link #createTempFile(String, String, String)}
   */
  String createTempFileBlocking(String prefix, String suffix, String perms);

  /**
   * Creates a new file in the directory provided by the path {@code dir}, using the given
   * prefix and suffix to generate its name, asynchronously.
   * <p>
   * The new directory will be created with permissions as specified by {@code perms}.
   * </p>
   * The permission String takes the form rwxr-x--- as specified
   * in <a href="http://download.oracle.com/javase/7/docs/api/java/nio/file/attribute/PosixFilePermissions.html">here</a>.
   *
   * <p>
   * As with the {@code File.createTempFile} methods, this method is only
   * part of a temporary-file facility.A {@link Runtime#addShutdownHook shutdown-hook},
   * or the {@link java.io.File#deleteOnExit} mechanism may be used to delete the directory automatically.
   * </p>
   *
   * @param dir     the path to directory in which to create the directory
   * @param prefix  the prefix string to be used in generating the directory's name;
   *                may be {@code null}
   * @param suffix  the suffix string to be used in generating the file's name;
   *                may be {@code null}, in which case "{@code .tmp}" is used
   * @param perms   the permissions string
   * @return a future notified on completion
   */
  Future<String> createTempFile(String dir, String prefix, String suffix, String perms);

  /**
   * Blocking version of {@link #createTempFile(String, String, String, String)}
   */
  String createTempFileBlocking(String dir, String prefix, String suffix, String perms);

}
