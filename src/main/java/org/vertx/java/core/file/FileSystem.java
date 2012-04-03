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
import org.vertx.java.core.impl.BlockingAction;
import org.vertx.java.core.impl.Context;
import org.vertx.java.core.impl.VertxInternal;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileStore;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotLinkException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Contains a broad set of operations for manipulating files.
 * <p>
 * An asynchronous and a synchronous version of each operation is provided.
 * The asynchronous versions take an {@code AsynchronousResultHandler} which is
 * called when the operation completes or an error occurs.
 * The synchronous versions return the results, or throw exceptions directly.
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class FileSystem {
  
  private final VertxInternal vertx;

  public FileSystem(VertxInternal vertx) {
    this.vertx = vertx;
  }

  /**
   * Copy a file from the path {@code from} to path {@code to}, asynchronously.<p>
   * The copy will fail if the destination if the destination already exists.<p>
   * The handler will be called when the operation completes or an error occurs
   */
  public void copy(String from, String to, AsyncResultHandler<Void> handler) {
    copyInternal(from, to, handler).run();
  }

  /**
   * Synchronous version of {@link #copy(String, String, AsyncResultHandler)}
   */
  public void copySync(String from, String to) throws Exception {
    copyInternal(from, to, null).action();
  }

  /**
   * Copy a file from the path {@code from} to path {@code to}, asynchronously.<p>
   * If {@code recursive} is {@code true} and {@code from} represents a directory, then the directory and its contents
   * will be copied recursively to the destination {@code to}.<p>
   * The copy will fail if the destination if the destination already exists.<p>
   * The handler will be called when the operation completes or an error occurs
   */
  public void copy(String from, String to, boolean recursive, AsyncResultHandler<Void> handler) {
    copyInternal(from, to, recursive, handler).run();
  }

  /**
   * Synchronous version of {@link #copy(String, String, boolean, AsyncResultHandler)}
   */
  public void copySync(String from, String to, boolean recursive) throws Exception {
    copyInternal(from, to, recursive, null).action();
  }

  /**
   * Move a file from the path {@code from} to path {@code to}, asynchronously.<p>
   * The move will fail if the destination already exists.<p>
   * The handler will be called when the operation completes or an error occurs
   */
  public void move(String from, String to, AsyncResultHandler<Void> handler) {
    moveInternal(from, to, handler).run();
  }

  /**
   * Synchronous version of {@link #move(String, String, AsyncResultHandler)}
   */
  public void moveSync(String from, String to) throws Exception {
    moveInternal(from, to, null).action();
  }

  /**
   * Truncate the file represented by {@code path} to length {@code len} in bytes, asynchronously.<p>
   * The operation will fail if the file does not exist or {@code len} is less than {@code zero}.
   * The handler will be called when the operation completes or an error occurs
   */
  public void truncate(String path, long len, AsyncResultHandler<Void> handler) {
    truncateInternal(path, len, handler).run();
  }

  /**
   * Synchronous version of {@link #truncate(String, long, AsyncResultHandler)}
   */
  public void truncateSync(String path, long len) throws Exception {
    truncateInternal(path, len, null).action();
  }

  /**
   * Change the permissions on the file represented by {@code path} to {@code perms}, asynchronously.
   * The permission String takes the form rwxr-x--- as
   * specified <a href="http://download.oracle.com/javase/7/docs/api/java/nio/file/attribute/PosixFilePermissions.html">here</a>.<p>
   * The handler will be called when the operation completes or an error occurs
   */
  public void chmod(String path, String perms, AsyncResultHandler<Void> handler) {
    chmodInternal(path, perms, handler).run();
  }

  /**
   * Synchronous version of {@link #chmod(String, String, AsyncResultHandler)}
   */
  public void chmodSync(String path, String perms) throws Exception {
    chmodInternal(path, perms, null).action();
  }

  /**
   * Change the permissions on the file represented by {@code path} to {@code perms}, asynchronously.
   * The permission String takes the form rwxr-x--- as
   * specified in {<a href="http://download.oracle.com/javase/7/docs/api/java/nio/file/attribute/PosixFilePermissions.html">here</a>}.<p>
   * If the file is directory then all contents will also have their permissions changed recursively. Any directory permissions will
   * be set to {@code dirPerms}, whilst any normal file permissions will be set to {@code perms}.<p>
   * The handler will be called when the operation completes or an error occurs
   */
  public void chmod(String path, String perms, String dirPerms, AsyncResultHandler<Void> handler) {
    chmodInternal(path, perms, dirPerms, handler).run();
  }

  /**
   * Synchronous version of {@link #chmod(String, String, String, AsyncResultHandler)}
   */
  public void chmodSync(String path, String perms, String dirPerms) throws Exception {
    chmodInternal(path, perms, dirPerms, null).action();
  }

  /**
   * Obtain properties for the file represented by {@code path}, asynchronously.
   * If the file is a link, the link will be followed.
   * The handler will be called when the operation completes or an error occurs
   */
  public void props(String path, AsyncResultHandler<FileProps> handler) {
    propsInternal(path, handler).run();
  }

  /**
   * Synchronous version of {@link #props(String, AsyncResultHandler)}
   */
  public FileProps propsSync(String path) throws Exception {
    return propsInternal(path, null).action();
  }

  /**
   * Obtain properties for the link represented by {@code path}, asynchronously.
   * The link will not be followed.
   * The handler will be called when the operation completes or an error occurs
   */
  public void lprops(String path, AsyncResultHandler<FileProps> handler) {
    lpropsInternal(path, handler).run();
  }

  /**
   * Synchronous version of {@link #lprops(String, AsyncResultHandler)}
   */
  public FileProps lpropsSync(String path) throws Exception {
    return lpropsInternal(path, null).action();
  }

  /**
   * Create a hard link on the file system from {@code link} to {@code existing}, asynchronously.<p>
   * The handler will be called when the operation completes or an error occurs
   */
  public void link(String link, String existing, AsyncResultHandler<Void> handler) {
    linkInternal(link, existing, handler).run();
  }

  /**
   * Synchronous version of {@link #link(String, String, AsyncResultHandler)}
   */
  public void linkSync(String link, String existing) throws Exception {
    linkInternal(link, existing, null).action();
  }

  /**
   * Create a symbolic link on the file system from {@code link} to {@code existing}, asynchronously.<p>
   * The handler will be called when the operation completes or an error occurs
   */
  public void symlink(String link, String existing, AsyncResultHandler<Void> handler) {
    symlinkInternal(link, existing, handler).run();
  }

  /**
   * Synchronous version of {@link #link(String, String, AsyncResultHandler)}
   */
  public void symlinkSync(String link, String existing) throws Exception {
    symlinkInternal(link, existing, null).action();
  }

  /**
   * Unlinks the link on the file system represented by the path {@code link}, asynchronously.<p>
   * The handler will be called when the operation completes or an error occurs
   */
  public void unlink(String link, AsyncResultHandler<Void> handler) {
    unlinkInternal(link, handler).run();
  }

  /**
   * Synchronous version of {@link #unlink(String, AsyncResultHandler)}
   */
  public void unlinkSync(String link) throws Exception {
    unlinkInternal(link, null).action();
  }

  /**
   * Returns the path representing the file that the symbolic link specified by {@code link} points to, asynchronously.<p>
   * The handler will be called when the operation completes or an error occurs
   */
  public void readSymlink(String link, AsyncResultHandler<String> handler) {
    readSymlinkInternal(link, handler).run();
  }

  /**
   * Synchronous version of {@link #readSymlink(String, AsyncResultHandler)}
   */
  public String readSymlinkSync(String link) throws Exception {
    return readSymlinkInternal(link, null).action();
  }

  /**
   * Deletes the file represented by the specified {@code path}, asynchronously.<p>
   * The handler will be called when the operation completes or an error occurs
   */
  public void delete(String path, AsyncResultHandler<Void> handler) {
    deleteInternal(path, handler).run();
  }

  /**
   * Synchronous version of {@link #delete(String, AsyncResultHandler)}
   */
  public void deleteSync(String path) throws Exception {
    deleteInternal(path, null).action();
  }

  /**
   * Deletes the file represented by the specified {@code path}, asynchronously.<p>
   * If the path represents a directory and recursive = true then the directory and its contents will be deleted recursively.<p>
   * The handler will be called when the operation completes or an error occurs
   */
  public void delete(String path, boolean recursive, AsyncResultHandler<Void> handler) {
    deleteInternal(path, recursive, handler).run();
  }

  /**
   * Synchronous version of {@link #delete(String, boolean, AsyncResultHandler)}
   */
  public void deleteSync(String path, boolean recursive) throws Exception {
    deleteInternal(path, recursive, null).action();
  }

  /**
   * Create the directory represented by {@code path}, asynchronously.<p>
   * The operation will fail if the directory already exists.<p>
   * The handler will be called when the operation completes or an error occurs
   */
  public void mkdir(String path, AsyncResultHandler<Void> handler) {
    mkdirInternal(path, handler).run();
  }

  /**
   * Synchronous version of {@link #mkdir(String, AsyncResultHandler)}
   */
  public void mkdirSync(String path) throws Exception {
    mkdirInternal(path, null).action();
  }

  /**
   * Create the directory represented by {@code path}, asynchronously.<p>
   * If {@code createParents} is set to {@code true} then any non-existent parent directories of the directory
   * will also be created.<p>
   * The operation will fail if the directory already exists.<p>
   * The handler will be called when the operation completes or an error occurs
   */
  public void mkdir(String path, boolean createParents, AsyncResultHandler<Void> handler) {
    mkdirInternal(path, createParents, handler).run();
  }

  /**
   * Synchronous version of {@link #mkdir(String, boolean, AsyncResultHandler)}
   */
  public void mkdirSync(String path, boolean createParents) throws Exception {
    mkdirInternal(path, createParents, null).action();
  }


  /**
   * Create the directory represented by {@code path}, asynchronously.<p>
   * The new directory will be created with permissions as specified by {@code perms}.
   * The permission String takes the form rwxr-x--- as specified
   * in <a href="http://download.oracle.com/javase/7/docs/api/java/nio/file/attribute/PosixFilePermissions.html">here</a>.<p>
   * The operation will fail if the directory already exists.<p>
   * The handler will be called when the operation completes or an error occurs
   */
  public void mkdir(String path, String perms, AsyncResultHandler<Void> handler) {
    mkdirInternal(path, perms, handler).run();
  }

  /**
   * Synchronous version of {@link #mkdir(String, String, AsyncResultHandler)}
   */
  public void mkdirSync(String path, String perms) throws Exception {
    mkdirInternal(path, perms, null).action();
  }

  /**
   * Create the directory represented by {@code path}, asynchronously.<p>
   * The new directory will be created with permissions as specified by {@code perms}.
   * The permission String takes the form rwxr-x--- as specified
   * in <a href="http://download.oracle.com/javase/7/docs/api/java/nio/file/attribute/PosixFilePermissions.html">here</a>.<p>
   * If {@code createParents} is set to {@code true} then any non-existent parent directories of the directory
   * will also be created.<p>
   * The operation will fail if the directory already exists.<p>
   * The handler will be called when the operation completes or an error occurs
   */
  public void mkdir(String path, String perms, boolean createParents, AsyncResultHandler<Void> handler) {
    mkdirInternal(path, perms, createParents, handler).run();
  }

  /**
   * Synchronous version of {@link #mkdir(String, String, boolean, AsyncResultHandler)}
   */
  public void mkdirSync(String path, String perms, boolean createParents) throws Exception {
    mkdirInternal(path, perms, createParents, null).action();
  }

  /**
   * Read the contents of the directory specified by {@code path}, asynchronously
   * The handler will be called when the operation completes or an error occurs.
   * The result is an array of String representing the paths of the files inside the directory.
   */
  public void readDir(String path, AsyncResultHandler<String[]> handler) {
    readDirInternal(path, handler).run();
  }

  /**
   * Synchronous version of {@link #readDir(String, AsyncResultHandler)}
   */
  public String[] readDirSync(String path) throws Exception {
    return readDirInternal(path, null).action();
  }

  /**
   * Read the contents of the directory specified by {@code path}, asynchronously<p>
   * {@code filter} is a regular expression. If {@code filter} is specified then only the paths that match @{filter} will be returned.<p>
   * The handler will be called when the operation completes or an error occurs.
   * The result is an array of String representing the paths of the files inside the directory.
   */
  public void readDir(String path, String filter, AsyncResultHandler<String[]> handler) {
    readDirInternal(path, filter, handler).run();
  }

  /**
   * Synchronous version of {@link #readDir(String, String, AsyncResultHandler)}
   */
  public String[] readDirSync(String path, String filter) throws Exception {
    return readDirInternal(path, filter, null).action();
  }

  /**
   * Reads the entire file as represented by the path {@code path} as a {@link Buffer}, asynchronously.<p>
   * Do not user this method to read very large files or you risk running out of available RAM.<p>
   * The handler will be called when the operation completes or an error occurs.
   */
  public void readFile(String path, AsyncResultHandler<Buffer> handler) {
    readFileInternal(path, handler).run();
  }

  /**
   * Synchronous version of {@link #readFile(String, AsyncResultHandler)}
   */
  public Buffer readFileSync(String path) throws Exception {
    return readFileInternal(path, null).action();
  }

  /**
   * Creates the file, and writes the specified {@code Buffer data} to the file represented by the path {@code path}, asynchronously.<p>
   * The handler will be called when the operation completes or an error occurs.
   */
  public void writeFile(String path, Buffer data, AsyncResultHandler<Void> handler) {
    writeFileInternal(path, data, handler).run();
  }

  /**
   * Synchronous version of {@link #writeFile(String, Buffer, AsyncResultHandler)}
   */
  public void writeFileSync(String path, Buffer data) throws Exception {
    writeFileInternal(path, data, null).action();
  }

  /**
   * Open the file represented by {@code path}, asynchronously.<p>
   * The file is opened for both reading and writing. If the file does not already exist it will be created.
   * Write operations will not automatically flush to storage.
   * The handler will be called when the operation completes or an error occurs.
   */
  public void open(String path, AsyncResultHandler<AsyncFile> handler) {
    openInternal(path, handler).run();
  }

  /**
   * Synchronous version of {@link #open(String, AsyncResultHandler)}
   */
  public AsyncFile openSync(String path) throws Exception {
    return openInternal(path, null).action();
  }

  /**
   * Open the file represented by {@code path}, asynchronously.<p>
   * The file is opened for both reading and writing. If the file does not already exist it will be created with the
   * permissions as specified by {@code perms}.
   * Write operations will not automatically flush to storage.
   * The handler will be called when the operation completes or an error occurs.
   */
  public void open(String path, String perms, AsyncResultHandler<AsyncFile> handler) {
    openInternal(path, perms, handler).run();
  }

  /**
   * Synchronous version of {@link #open(String, String, AsyncResultHandler)}
   */
  public AsyncFile openSync(String path, String perms) throws Exception {
    return openInternal(path, perms, null).action();
  }

  /**
   * Open the file represented by {@code path}, asynchronously.<p>
   * The file is opened for both reading and writing. If the file does not already exist and
   * {@code createNew} is {@code true} it will be created with the permissions as specified by {@code perms}, otherwise
   * the operation will fail.
   * Write operations will not automatically flush to storage.
   * The handler will be called when the operation completes or an error occurs.
   */
  public void open(String path, String perms, boolean createNew, AsyncResultHandler<AsyncFile> handler) {
    openInternal(path, perms, createNew, handler).run();
  }

  /**
   * Synchronous version of {@link #open(String, String, boolean, AsyncResultHandler)}
   */
  public AsyncFile openSync(String path, String perms, boolean createNew) throws Exception {
    return openInternal(path, perms, createNew, null).action();
  }

  /**
   * Open the file represented by {@code path}, asynchronously.<p>
   * If {@code read} is {@code true} the file will be opened for reading. If {@code write} is {@code true} the file
   * will be opened for writing.<p>
   * If the file does not already exist and
   * {@code createNew} is {@code true} it will be created with the permissions as specified by {@code perms}, otherwise
   * the operation will fail.<p>
   * Write operations will not automatically flush to storage.
   * The handler will be called when the operation completes or an error occurs.
   */
  public void open(String path, String perms, boolean read, boolean write, boolean createNew, AsyncResultHandler<AsyncFile> handler) {
    openInternal(path, perms, read, write, createNew, handler).run();
  }

  /**
   * Synchronous version of {@link #open(String, String, boolean, boolean, boolean, AsyncResultHandler)}
   */
  public AsyncFile openSync(String path, String perms, boolean read, boolean write, boolean createNew) throws Exception {
    return openInternal(path, perms, read, write, createNew, null).action();
  }

  /**
   * Open the file represented by {@code path}, asynchronously.<p>
   * If {@code read} is {@code true} the file will be opened for reading. If {@code write} is {@code true} the file
   * will be opened for writing.<p>
   * If the file does not already exist and
   * {@code createNew} is {@code true} it will be created with the permissions as specified by {@code perms}, otherwise
   * the operation will fail.<p>
   * If {@code flush} is {@code true} then all writes will be automatically flushed through OS buffers to the underlying
   * storage on each write.<p>
   * The handler will be called when the operation completes or an error occurs.
   */
  public void open(String path, String perms, boolean read, boolean write, boolean createNew,
                   boolean flush, AsyncResultHandler<AsyncFile> handler) {
    openInternal(path, perms, read, write, createNew, flush, handler).run();
  }

  /**
   * Synchronous version of {@link #open(String, String, boolean, boolean, boolean, boolean, AsyncResultHandler)}
   */
  public AsyncFile openSync(String path, String perms, boolean read, boolean write, boolean createNew, boolean flush) throws Exception {
    return openInternal(path, perms, read, write, createNew, flush, null).action();
  }

  /**
   * Creates an empty file with the specified {@code path}, asynchronously.<p>
   * The handler will be called when the operation completes or an error occurs.
   */
  public void createFile(String path, AsyncResultHandler<Void> handler) {
    createFileInternal(path, handler).run();
  }

  /**
   * Synchronous version of {@link #createFile(String, AsyncResultHandler)}
   */
  public void createFileSync(String path) throws Exception {
    createFileInternal(path, null).action();
  }

  /**
   * Creates an empty file with the specified {@code path} and permissions {@code perms}, asynchronously.<p>
   * The handler will be called when the operation completes or an error occurs.
   */
  public void createFile(String path, String perms, AsyncResultHandler<Void> handler) {
    createFileInternal(path, perms, handler).run();
  }

  /**
   * Synchronous version of {@link #createFile(String, String, AsyncResultHandler)}
   */
  public void createFileSync(String path, String perms) throws Exception {
    createFileInternal(path, perms, null).action();
  }

  /**
   * Determines whether the file as specified by the path {@code path} exists, asynchronously.<p>
   * The handler will be called when the operation completes or an error occurs.
   */
  public void exists(String path, AsyncResultHandler<Boolean> handler) {
    existsInternal(path, handler).run();
  }

  /**
   * Synchronous version of {@link #exists(String, AsyncResultHandler)}
   */
  public boolean existsSync(String path) throws Exception {
    return existsInternal(path, null).action();
  }

  /**
   * Returns properties of the file-system being used by the specified {@code path}, asynchronously.<p>
   * The handler will be called when the operation completes or an error occurs.
   */
  public void fsProps(String path, AsyncResultHandler<FileSystemProps> handler) {
    fsPropsInternal(path, handler).run();
  }

  /**
   * Synchronous version of {@link #fsProps(String, AsyncResultHandler)}
   */
  public FileSystemProps fsPropsSync(String path) throws Exception {
    return fsPropsInternal(path, null).action();
  }

  private BlockingAction<Void> copyInternal(String from, String to, AsyncResultHandler<Void> handler) {
    return copyInternal(from, to, false, handler);
  }

  private BlockingAction<Void> copyInternal(String from, String to, final boolean recursive, AsyncResultHandler<Void> handler) {
    
    final Path source = Paths.get(from);
    final Path target = Paths.get(to);
    return new BlockingAction<Void>(vertx, handler) {
      public Void action() throws Exception {
        try {
          if (recursive) {
            Files.walkFileTree(source, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
                new SimpleFileVisitor<Path>() {
                  public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                      throws IOException {
                    Path targetDir = target.resolve(source.relativize(dir));
                    try {
                      Files.copy(dir, targetDir);
                    } catch (FileAlreadyExistsException e) {
                      if (!Files.isDirectory(targetDir)) {
                        throw e;
                      }
                    }
                    return FileVisitResult.CONTINUE;
                  }

                  @Override
                  public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                      throws IOException {
                    Files.copy(file, target.resolve(source.relativize(file)));
                    return FileVisitResult.CONTINUE;
                  }
                });
          } else {
            Files.copy(source, target);
          }
        } catch (FileAlreadyExistsException e) {
          throw new FileSystemException("File already exists " + e.getMessage());
        }
        return null;
      }
    };
  }

  private BlockingAction<Void> moveInternal(String from, String to, AsyncResultHandler<Void> handler) {
    
    //TODO atomic moves - but they have different semantics, e.g. on Linux if target already exists it is overwritten
    final Path source = Paths.get(from);
    final Path target = Paths.get(to);
    return new BlockingAction<Void>(vertx, handler) {
      public Void action() throws Exception {
        try {
          Files.move(source, target);
        } catch (FileAlreadyExistsException e) {
          throw new FileSystemException("Failed to move between " + source + " and " + target + ". Target already exists");
        } catch (AtomicMoveNotSupportedException e) {
          throw new FileSystemException("Atomic move not supported between " + source + " and " + target);
        }
        return null;
      }
    };
  }

  private BlockingAction<Void> truncateInternal(final String path, final long len, AsyncResultHandler<Void> handler) {
     
     return new BlockingAction<Void>(vertx, handler) {
       public Void action() throws Exception {
         if (len < 0) {
           throw new FileSystemException("Cannot truncate file to size < 0");
         }
         if (!Files.exists(Paths.get(path))) {
           throw new FileSystemException("Cannot truncate file " + path + ". Does not exist");
         }

         RandomAccessFile raf = null;
         try {
           raf = new RandomAccessFile(path, "rw");
           raf.getChannel().truncate(len);
         } catch (FileNotFoundException e) {
           throw new FileSystemException("Cannot open file " + path + ". Either it is a directory or you don't have permission to change it");
         } finally {
           if (raf != null) raf.close();
         }
         return null;
       }
     };
  }

  private BlockingAction<Void> chmodInternal(String path, String perms, AsyncResultHandler<Void> handler) {
    return chmodInternal(path, perms, null, handler);
  }

  private BlockingAction<Void> chmodInternal(String path, String perms, String dirPerms, AsyncResultHandler<Void> handler) {
    
    final Path target = Paths.get(path);
    final Set<PosixFilePermission> permissions = PosixFilePermissions.fromString(perms);
    final Set<PosixFilePermission> dirPermissions = dirPerms == null ? null : PosixFilePermissions.fromString(dirPerms);
    return new BlockingAction<Void>(vertx, handler) {
      public Void action() throws Exception {
        try {
          if (dirPermissions != null) {
            Files.walkFileTree(target, new SimpleFileVisitor<Path>() {
              public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                //The directory entries typically have different permissions to the files, e.g. execute permission
                //or can't cd into it
                Files.setPosixFilePermissions(dir, dirPermissions);
                return FileVisitResult.CONTINUE;
              }

              public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.setPosixFilePermissions(file, permissions);
                return FileVisitResult.CONTINUE;
              }
            });
          } else {
            Files.setPosixFilePermissions(target, permissions);
          }
        } catch (SecurityException e) {
          throw new FileSystemException("Accessed denied for chmod on " + target);
        }
        return null;
      }
    };
  }

  private BlockingAction<FileProps> propsInternal(String path, AsyncResultHandler<FileProps> handler) {
    return props(path, true, handler);
  }

  private BlockingAction<FileProps> lpropsInternal(String path, AsyncResultHandler<FileProps> handler) {
    return props(path, false, handler);
  }

  private BlockingAction<FileProps> props(String path, final boolean followLinks, AsyncResultHandler<FileProps> handler) {
    
    final Path target = Paths.get(path);
    return new BlockingAction<FileProps>(vertx, handler) {
      public FileProps action() throws Exception {
        try {
          BasicFileAttributes attrs;
          if (followLinks) {
            attrs = Files.readAttributes(target, BasicFileAttributes.class);
          } else {
            attrs = Files.readAttributes(target, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
          }
          return new FileProps(attrs);
        } catch (NoSuchFileException e) {
          throw new FileSystemException("No such file: " + target);
        }
      }
    };
  }

  private BlockingAction<Void> linkInternal(String link, String existing, AsyncResultHandler<Void> handler) {
    return link(link, existing, false, handler);
  }

  private BlockingAction<Void> symlinkInternal(String link, String existing, AsyncResultHandler<Void> handler) {
    return link(link, existing, true, handler);
  }

  private BlockingAction<Void> link(String link, String existing, final boolean symbolic, AsyncResultHandler<Void> handler) {
    
    final Path source = Paths.get(link);
    final Path target = Paths.get(existing);
    return new BlockingAction<Void>(vertx, handler) {
      public Void action() throws Exception {
        try {
          if (symbolic) {
            Files.createSymbolicLink(source, target);
          } else {
            Files.createLink(source, target);
          }
        } catch (FileAlreadyExistsException e) {
          throw new FileSystemException("Cannot create link, file already exists: " + source);
        }
        return null;
      }
    };
  }

  private BlockingAction<Void> unlinkInternal(String link, AsyncResultHandler<Void> handler) {
    return deleteInternal(link, handler);
  }

  private BlockingAction<String> readSymlinkInternal(String link, AsyncResultHandler<String> handler) {
    
    final Path source = Paths.get(link);
    return new BlockingAction<String>(vertx, handler) {
      public String action() throws Exception {
        try {
          return Files.readSymbolicLink(source).toString();
        } catch (NotLinkException e) {
          throw new FileSystemException("Cannot read " + source + " it's not a symbolic link");
        }
      }
    };
  }

  private BlockingAction<Void> deleteInternal(String path, AsyncResultHandler<Void> handler) {
    return deleteInternal(path, false, handler);
  }

  private BlockingAction<Void> deleteInternal(String path, final boolean recursive, AsyncResultHandler<Void> handler) {
    
    final Path source = Paths.get(path);
    return new BlockingAction<Void>(vertx, handler) {
      public Void action() throws Exception {
        if (recursive) {
          Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
              Files.delete(file);
              return FileVisitResult.CONTINUE;
            }
            public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException {
              if (e == null) {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
              } else {
                throw e;
              }
            }
          });
        } else {
          try {
            Files.delete(source);
          } catch (NoSuchFileException e) {
            throw new FileSystemException("Cannot delete file, it does not exist: " + source);
          } catch (DirectoryNotEmptyException e) {
            throw new FileSystemException("Cannot delete directory, it is not empty: " + source + ". Use recursive delete");
          }
        }
        return null;
      }
    };
  }

  private BlockingAction<Void> mkdirInternal(String path, AsyncResultHandler<Void> handler) {
    return mkdirInternal(path, null, false, handler);
  }

  private BlockingAction<Void> mkdirInternal(String path, boolean createParents, AsyncResultHandler<Void> handler) {
    return mkdirInternal(path, null, createParents, handler);
  }

  private BlockingAction<Void> mkdirInternal(String path, String perms, AsyncResultHandler<Void> handler) {
    return mkdirInternal(path, perms, false, handler);
  }

  private BlockingAction<Void> mkdirInternal(String path, final String perms, final boolean createParents, AsyncResultHandler<Void> handler) {
    
    final Path source = Paths.get(path);
    final FileAttribute<?> attrs = perms == null ? null : PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(perms));
    return new BlockingAction<Void>(vertx, handler) {
      public Void action() throws Exception {
        try {
          if (createParents) {
            if (attrs != null) {
              Files.createDirectories(source, attrs);
            } else {
              Files.createDirectories(source);
            }
          } else {
            if (attrs != null) {
              Files.createDirectory(source, attrs);
            } else {
              Files.createDirectory(source);
            }
          }
        } catch (FileAlreadyExistsException e) {
          throw new FileSystemException("Cannot create directory: " + source + ". It already exists");
        } catch (NoSuchFileException e) {
          throw new FileSystemException("Canot create directory: " + source + " it has parents");
        }
        return null;
      }
    };
  }

  private BlockingAction<String[]> readDirInternal(String path, AsyncResultHandler<String[]> handler) {
    return readDirInternal(path, null, handler);
  }

  private BlockingAction<String[]> readDirInternal(final String path, final String filter, AsyncResultHandler<String[]> handler) {
    
    return new BlockingAction<String[]>(vertx, handler) {
      public String[] action() throws Exception {
        File file = new File(path);
        if (!file.exists()) {
          throw new FileSystemException("Cannot read directory " + path + ". Does not exist");
        }
        if (!file.isDirectory()) {
          throw new FileSystemException("Cannot read directory " + path + ". It's not a directory");
        } else {
          FilenameFilter fnFilter;
          if (filter != null) {
            fnFilter = new FilenameFilter() {
              public boolean accept(File dir, String name) {
                return Pattern.matches(filter, name);
              }
            };
          } else {
            fnFilter = null;
          }
          File[] files;
          if (fnFilter == null) {
            files = file.listFiles();
          } else {
            files = file.listFiles(fnFilter);
          }
          String[] ret = new String[files.length];
          int i = 0;
          for (File f : files) {
            ret[i++] = f.getCanonicalPath();
          }
          return ret;
        }
      }
    };
  }

  private BlockingAction<Buffer> readFileInternal(final String path, AsyncResultHandler<Buffer> handler) {
    
    return new BlockingAction<Buffer>(vertx, handler) {
      public Buffer action() throws Exception {
        Path target = Paths.get(path);
        byte[] bytes = Files.readAllBytes(target);
        Buffer buff = new Buffer(bytes);
        return buff;
      }
    };
  }

  private BlockingAction<Void> writeFileInternal(final String path, final Buffer data, AsyncResultHandler<Void> handler) {
    
    return new BlockingAction<Void>(vertx, handler) {
      public Void action() throws Exception {
        Path target = Paths.get(path);
        Files.write(target, data.getBytes());
        return null;
      }
    };
  }

  private BlockingAction<AsyncFile> openInternal(String path, AsyncResultHandler<AsyncFile> handler) {
    return openInternal(path, null, true, true, true, false, handler);
  }

  private BlockingAction<AsyncFile> openInternal(String path, String perms, AsyncResultHandler<AsyncFile> handler) {
    return openInternal(path, perms, true, true, true, false, handler);
  }

  private BlockingAction<AsyncFile> openInternal(String path, String perms, boolean createNew, AsyncResultHandler<AsyncFile> handler) {
    return openInternal(path, perms, true, true, createNew, false, handler);
  }

  private BlockingAction<AsyncFile> openInternal(String path, String perms, boolean read, boolean write, boolean createNew, AsyncResultHandler<AsyncFile> handler) {
    return openInternal(path, perms, read, write, createNew, false, handler);
  }

  private BlockingAction<AsyncFile> openInternal(final String path, final String perms, final boolean read, final boolean write, final boolean createNew,
                   final boolean flush, AsyncResultHandler<AsyncFile> handler) {
    return new BlockingAction<AsyncFile>(vertx, handler) {
      public AsyncFile action() throws Exception {
        return doOpen(path, perms, read, write, createNew, flush, context);
      }
    };
  }

  private AsyncFile doOpen(String path, String perms, boolean read, boolean write, boolean createNew,
                           boolean flush, Context context) throws Exception {
    return new AsyncFile(vertx, path, perms, read, write, createNew, flush, context);
  }

  private BlockingAction<Void> createFileInternal(String path, AsyncResultHandler<Void> handler) {
    return createFileInternal(path, null, handler);
  }

  private BlockingAction<Void> createFileInternal(final String path, final String perms, AsyncResultHandler<Void> handler) {
    
    final FileAttribute<?> attrs = perms == null ? null : PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(perms));
    return new BlockingAction<Void>(vertx, handler) {
      public Void action() throws Exception {
        try {
          Path target = Paths.get(path);
          if (attrs != null) {
            Files.createFile(target, attrs);
          } else {
            Files.createFile(target);
          }
        } catch (FileAlreadyExistsException e) {
          throw new FileSystemException("Cannot create link, file already exists: " + path);
        }
        return null;
      }
    };
  }

  private BlockingAction<Boolean> existsInternal(final String path, AsyncResultHandler<Boolean> handler) {
    
    return new BlockingAction<Boolean>(vertx, handler) {
      public Boolean action() throws Exception {
        File file = new File(path);
        return file.exists();
      }
    };
  }

  private BlockingAction<FileSystemProps> fsPropsInternal(final String path, AsyncResultHandler<FileSystemProps> handler) {
    
    return new BlockingAction<FileSystemProps>(vertx, handler) {
      public FileSystemProps action() throws Exception {
        Path target = Paths.get(path);
        FileStore fs = Files.getFileStore(target);
        return new FileSystemProps(fs.getTotalSpace(), fs.getUnallocatedSpace(), fs.getUsableSpace());
      }
    };
  }
}
