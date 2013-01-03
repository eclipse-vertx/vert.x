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

package org.vertx.groovy.core.file

import org.vertx.groovy.core.buffer.Buffer
import org.vertx.java.core.AsyncResult
import org.vertx.java.core.AsyncResultHandler
import org.vertx.java.core.file.FileProps
import org.vertx.java.core.file.FileSystem as JFileSystem
import org.vertx.java.core.file.FileSystemProps

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
class FileSystem {

  static FileSystem instance = new FileSystem()

  private final JFileSystem jFS

  private FileSystem(JFileSystem jFS) {
    this.jFS = jFS
  }

  /**
   * Copy a file from the path {@code from} to path {@code to}, asynchronously.<p>
   * If {@code recursive} is {@code true} and {@code from} represents a directory, then the directory and its contents
   * will be copied recursively to the destination {@code to}.<p>
   * The copy will fail if the destination if the destination already exists.<p>
   */
  void copy(String from, String to, boolean recursive = false, Closure handler) {
    jFS.copy(from, to, recursive, handler as AsyncResultHandler)
  }

  /**
   * Synchronous version of {@link #copy(String, String, boolean, Closure)}
   */
  void copySync(String from, String to, boolean recursive = false) {
    jFS.copySync(from, to, recursive)    
  }

  /**
   * Move a file from the path {@code from} to path {@code to}, asynchronously.<p>
   * The move will fail if the destination already exists.<p>
   */
  void move(String from, String to, Closure handler) {
    jFS.move(from, to, handler as AsyncResultHandler)
  }

  /**
   * Synchronous version of {@link #move(String, String, Closure)}
   */
  void moveSync(String from, String to) {
    jFS.moveSync(from, to)
  }

  /**
   * Truncate the file represented by {@code path} to length {@code len} in bytes, asynchronously.<p>
   * The operation will fail if the file does not exist or {@code len} is less than {@code zero}.
   */
  void truncate(String path, long len, Closure handler) {
    jFS.truncate(path, len, handler as AsyncResultHandler)
  }

  /**
   * Synchronous version of {@link #truncate(String, long, Closure)}
   */
  void truncateSync(String path, long len) {
    jFS.truncateSync(path, len)
  }

  /**
   * Change the permissions on the file represented by {@code path} to {@code perms}, asynchronously.
   * The permission String takes the form rwxr-x--- as
   * specified in {<a href="http://download.oracle.com/javase/7/docs/api/java/nio/file/attribute/PosixFilePermissions.html">here</a>}.<p>
   * If the file is directory then all contents will also have their permissions changed recursively. Any directory permissions will
   * be set to {@code dirPerms}, whilst any normal file permissions will be set to {@code perms}.<p>
   */
  void chmod(String path, String perms, String dirPerms = null, Closure handler) {
    jFS.chmod(path, perms, dirPerms, handler as AsyncResultHandler)
  }

  /**
   * Synchronous version of {@link #chmod(String, String, String, Closure)}
   */
  void chmodSync(String path, String perms, String dirPerms = null) {
    jFS.chmodSync(path, perms, dirPerms)
  }

  /**
   * Obtain properties for the file represented by {@code path}, asynchronously.
   * If the file is a link, the link will be followed.
   */
  void props(String path, Closure handler) {
    jFS.props(path, handler as AsyncResultHandler)
  }

  /**
   * Synchronous version of {@link #props(String, Closure)}
   */
  FileProps propsSync(String path) {
    return jFS.propsSync(path)
  }

  /**
   * Obtain properties for the link represented by {@code path}, asynchronously.
   * The link will not be followed.
   */
  void lprops(String path, Closure handler) {
    jFS.lprops(path, handler as AsyncResultHandler)
  }

  /**
   * Synchronous version of {@link #lprops(String, Closure)}
   */
  FileProps lpropsSync(String path) {
    return jFS.lpropsSync(path)
  }

  /**
   * Create a hard link on the file system from {@code link} to {@code existing}, asynchronously.
   */
  void link(String link, String existing, Closure handler) {
    jFS.link(link, existing, handler as AsyncResultHandler)
  }

  /**
   * Synchronous version of {@link #link(String, String, Closure)}
   */
  void linkSync(String link, String existing) {
    jFS.linkSync(link, existing)
  }

  /**
   * Create a symbolic link on the file system from {@code link} to {@code existing}, asynchronously.
   */
  void symlink(String link, String existing, Closure handler) {
    jFS.symlink(link, existing, handler as AsyncResultHandler)
  }

  /**
   * Synchronous version of {@link #link(String, String, Closure)}
   */
  void symlinkSync(String link, String existing) {
    jFS.symlinkSync(link, existing)
  }

  /**
   * Unlinks the link on the file system represented by the path {@code link}, asynchronously.
   */
  void unlink(String link, Closure handler) {
    jFS.unlink(link, handler as AsyncResultHandler)
  }

  /**
   * Synchronous version of {@link #unlink(String, Closure)}
   */
  void unlinkSync(String link) {
    jFS.unlinkSync(link)
  }

  /**
   * Returns the path representing the file that the symbolic link specified by {@code link} points to, asynchronously.
   */
  void readSymlink(String link, Closure handler) {
    jFS.readSymlink(link, handler as AsyncResultHandler)
  }

  /**
   * Synchronous version of {@link #readSymlink(String, Closure)}
   */
  String readSymlinkSync(String link) {
    return jFS.readSymlinkSync(link)
  }

  /**
   * Deletes the file represented by the specified {@code path}, asynchronously.<p>
   * If the path represents a directory and {@code recursive = true} then the directory and its contents will be
   * deleted recursively.
   */
  void delete(String path, boolean recursive = false, Closure handler) {
    jFS.delete(path, recursive, handler as AsyncResultHandler)
  }

  /**
   * Synchronous version of {@link #delete(String, boolean, Closure)}
   */
  void deleteSync(String path, boolean recursive) {
    jFS.deleteSync(path, recursive)
  }

  /**
   * Create the directory represented by {@code path}, asynchronously.<p>
   * The new directory will be created with permissions as specified by {@code perms}.
   * The permission String takes the form rwxr-x--- as specified
   * in <a href="http://download.oracle.com/javase/7/docs/api/java/nio/file/attribute/PosixFilePermissions.html">here</a>.<p>
   * If {@code createParents} is set to {@code true} then any non-existent parent directories of the directory
   * will also be created.<p>
   * The operation will fail if the directory already exists.<p>
   */
  void mkdir(String path, String perms = null, boolean createParents = false, Closure handler) {
    jFS.mkdir(path, perms, createParents, handler as AsyncResultHandler)
  }

  /**
   * Synchronous version of {@link #mkdir(String, String, boolean, Closure)}
   */
  void mkdirSync(String path, String perms = null, boolean createParents = false) {
    jFS.mkdirSync(path, perms, createParents)
  }

  /**
   * Read the contents of the directory specified by {@code path}, asynchronously.<p>
   * The result is an array of String representing the paths of the files inside the directory.
   */
  void readDir(String path, String filter = null, Closure handler) {
    jFS.readDir(path, filter, handler as AsyncResultHandler)
  }

  /**
   * Synchronous version of {@link #readDir(String, String, Closure)}
   */
  String[] readDirSync(String path, String filter) {
    return jFS.readDirSync(path, filter)
  }

  /**
   * Reads the entire file as represented by the path {@code path} as a {@link Buffer}, asynchronously.<p>
   * Do not user this method to read very large files or you risk running out of available RAM.
   */
  void readFile(String path, Closure handler) {
    jFS.readFile(path, { ar ->
      if (ar.succeeded()) {
        handler.call(new AsyncResult<Buffer>(new Buffer(ar.result)))
      } else {
        handler.call(ar)
      }
    } as AsyncResultHandler)
  }

  /**
   * Synchronous version of {@link #readFile(String, Closure)}
   */
  Buffer readFileSync(String path) {
    return new Buffer(jFS.readFileSync(path))
  }

  /**
   * Creates the file, and writes the specified {@code Buffer data} to the file represented by the path {@code path},
   * asynchronously.
   */
  void writeFile(String path, Buffer data, Closure handler) {
    jFS.writeFile(path, data.toJavaBuffer(), handler as AsyncResultHandler)
  }

  /**
   * Creates the file, and writes the specified {@code String data} to the file represented by the path {@code path},
   * asynchronously.
   */
  void writeFile(String path, String data, Closure handler) {
    jFS.writeFile(path, new org.vertx.java.core.buffer.Buffer(data), handler as AsyncResultHandler)
  }

  /**
   * Synchronous version of {@link #writeFile(String, Buffer, Closure)}
   */
  void writeFileSync(String path, Buffer data) {
    jFS.writeFileSync(path, data.toJavaBuffer())
  }

  /**
   * Synchronous version of {@link #writeFile(String, String, Closure)}
   */
  void writeFileSync(String path, String data) {
    jFS.writeFileSync(path, new org.vertx.java.core.buffer.Buffer(data))
  }

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
  void open(String path, String perms = null, boolean read = true, boolean write = true, boolean createNew = true, boolean flush = false, Closure handler) {
    jFS.open(path, perms, read, write, createNew, flush, { result ->
      if (result.succeeded()) {
        handler(new AsyncResult<AsyncFile>(new AsyncFile(result.result)))
      } else {
        handler(result)
      }
    } as AsyncResultHandler)
  }

  /**
   * Synchronous version of {@link #open(String, String, boolean, boolean, boolean, boolean, Closure)}
   */
  AsyncFile openSync(String path, String perms = null, boolean read = true, boolean write = true, boolean createNew = true, boolean flush = false) {
    return new AsyncFile(jFS.openSync(path, perms, read, write, createNew, flush))
  }

  /**
   * Creates an empty file with the specified {@code path}, asynchronously.
   */
  void createFile(String path, String perms = null, Closure handler) {
    jFS.createFile(path, perms, handler as AsyncResultHandler)
  }

  /**
   * Synchronous version of {@link #createFile(String, String, Closure)}
   */
  void createFileSync(String path, String perms = null) {
    jFS.createFileSync(path, perms)
  }

  /**
   * Determines whether the file as specified by the path {@code path} exists, asynchronously.
   */
  void exists(String path, Closure handler) {
    jFS.exists(path, handler as AsyncResultHandler)
  }

  /**
   * Synchronous version of {@link #exists(String, Closure)}
   */
  boolean existsSync(String path) {
    return jFS.existsSync(path)
  }

  /**
   * Returns properties of the file-system being used by the specified {@code path}, asynchronously.
   */
  void fsProps(String path, Closure handler) {
    jFS.fsProps(path, handler as AsyncResultHandler)
  }

  /**
   * Synchronous version of {@link #fsProps(String, Closure)}
   */
  FileSystemProps fsPropsSync(String path) {
    return jFS.fsPropsSync(path)
  }
}
