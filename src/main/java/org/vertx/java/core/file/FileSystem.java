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

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.BlockingAction;
import org.vertx.java.core.CompletionHandler;
import org.vertx.java.core.Context;
import org.vertx.java.core.Deferred;
import org.vertx.java.core.Future;
import org.vertx.java.core.Handler;
import org.vertx.java.core.SynchronousAction;
import org.vertx.java.core.VertxInternal;
import org.vertx.java.core.buffer.Buffer;

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
 * <p>Represents the file-system and contains a broad set of operations for manipulating files.</p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class FileSystem {

  public static FileSystem instance = new FileSystem();

  private FileSystem() {
  }

  private SynchronousAction<Void> copyDeferred(String from, String to) {
    return copyDeferred(from, to, false);
  }

  /**
   * Copy a file from the path {@code from} to path {@code to}, asynchronously.<p>
   * The copy will fail if the destination if the destination already exists.<p>
   * The actual copy will happen asynchronously.
   * @return a Future representing the future result of the action.
   */
  public Future<Void> copy(String from, String to) {
    return copyDeferred(from, to).execute();
  }

  public void copySync(String from, String to) throws Exception {
    copyDeferred(from, to).action();
  }

  private SynchronousAction<Void> copyDeferred(String from, String to, final boolean recursive) {
    checkContext();
    final Path source = Paths.get(from);
    final Path target = Paths.get(to);
    return new BlockingAction<Void>() {
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

  /**
   * Copy a file from the path {@code from} to path {@code to}, asynchronously.<p>
   * If {@code recursive} is {@code true} and {@code from} represents a directory, then the directory and its contents
   * will be copied recursively to the destination {@code to}.<p>
   * The copy will fail if the destination if the destination already exists.<p>
   * @return a Future representing the future result of the action.
   */
  public Future<Void> copy(String from, String to, boolean recursive) {
    return copyDeferred(from, to, recursive).execute();
  }

  public void copy2(String from, String to, boolean recursive, final Handler<AsyncResult<Void>> handler) {
    Future<Void> fut = copyDeferred(from, to, recursive).execute();
    wrapHandler(fut, handler);
  }

  private <T> void wrapHandler(Future<T> fut, final Handler<AsyncResult<T>> handler) {
    fut.handler(new CompletionHandler<T>() {
      public void handle(Future<T> event) {
        if (event.succeeded()) {
          handler.handle(new AsyncResult<T>(event.result()));
        } else {
          handler.handle(new AsyncResult<T>(event.exception()));
        }
      }
    });
  }

  public void copySync(String from, String to, boolean recursive) throws Exception {
    copyDeferred(from, to, recursive).action();
  }

  private SynchronousAction<Void> moveDeferred(String from, String to) {
    checkContext();
    //TODO atomic moves - but they have different semantics, e.g. on Linux if target already exists it is overwritten
    final Path source = Paths.get(from);
    final Path target = Paths.get(to);
    return new BlockingAction<Void>() {
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

  /**
   * Move a file from the path {@code from} to path {@code to}, asynchronously.<p>
   * The move will fail if the destination already exists.<p>
   * The actual move will happen asynchronously.
   * @return a Future representing the future result of the action.
   */
  public Future<Void> move(String from, String to) {
    return moveDeferred(from, to).execute();
  }

  public void moveSync(String from, String to) throws Exception {
    moveDeferred(from, to).action();
  }

  private SynchronousAction<Void> truncateDeferred(final String path, final long len) {
    checkContext();
    return new BlockingAction<Void>() {
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

  /**
   * Truncate the file represented by {@code path} to length {@code len} in bytes, asynchronously.<p>
   * The operation will fail if the file does not exist or {@code len} is less than {@code zero}.
   * @return a Future representing the future result of the action.
   */
  public Future<Void> truncate(String path, long len) {
    return truncateDeferred(path, len).execute();
  }

  public void truncateSync(String path, long len) throws Exception {
    truncateDeferred(path, len).action();
  }

  private SynchronousAction<Void> chmodDeferred(String path, String perms) {
    return chmodDeferred(path, perms, null);
  }

  /**
   * Change the permissions on the file represented by {@code path} to {@code perms}, asynchronously.
   * The permission String takes the form rwxr-x--- as specified <a href="http://download.oracle.com/javase/7/docs/api/java/nio/file/attribute/PosixFilePermissions.html">here</a>.<p>
   * @return a Future representing the future result of the action.
   */
  public Future<Void> chmod(String path, String perms) {
    return chmodDeferred(path, perms).execute();
  }

  public void chmodSync(String path, String perms) throws Exception {
    chmodDeferred(path, perms).action();
  }

  private SynchronousAction<Void> chmodDeferred(String path, String perms, String dirPerms) {
    checkContext();
    final Path target = Paths.get(path);
    final Set<PosixFilePermission> permissions = PosixFilePermissions.fromString(perms);
    final Set<PosixFilePermission> dirPermissions = dirPerms == null ? null : PosixFilePermissions.fromString(dirPerms);
    return new BlockingAction<Void>() {
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

  /**
   * Change the permissions on the file represented by {@code path} to {@code perms}, asynchronously.
   * The permission String takes the form rwxr-x--- as specified in {<a href="http://download.oracle.com/javase/7/docs/api/java/nio/file/attribute/PosixFilePermissions.html">here</a>}.<p>
   * If the file is directory then all contents will also have their permissions changed recursively. Any directory permissions will
   * be set to {@code dirPerms}, whilst any normal file permissions will be set to {@code perms}.<p>
   * @return a Future representing the future result of the action.
   */
  public Future<Void> chmod(String path, String perms, String dirPerms) {
    return chmodDeferred(path, perms, dirPerms).execute();
  }


  public void chmodSync(String path, String perms, String dirPerms) throws Exception {
    chmodDeferred(path, perms, dirPerms).action();
  }


  private SynchronousAction<FileProps> propsDeferred(String path) {
    return props(path, true);
  }

  /**
   * Obtain properties for the file represented by {@code path}, asynchronously.
   * If the file is a link, the link will be followed.
   * @return a Future representing the future result of the action.
   */
  public Future<FileProps> props(String path) {
    return propsDeferred(path).execute();
  }

  public FileProps propsSync(String path) throws Exception {
    return propsDeferred(path).action();
  }

  private SynchronousAction<FileProps> lpropsDeferred(String path) {
    return props(path, false);
  }

  /**
   * Obtain properties for the link represented by {@code path}, asynchronously. The link will not be followed.
   * @return a Future representing the future result of the action.
   */
  public Future<FileProps> lprops(String path) {
    return lpropsDeferred(path).execute();
  }

  public FileProps lpropsSync(String path) throws Exception {
    return lpropsDeferred(path).action();
  }

  private SynchronousAction<FileProps> props(String path, final boolean followLinks) {
    checkContext();
    final Path target = Paths.get(path);
    return new BlockingAction<FileProps>() {
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

  private SynchronousAction<Void> linkDeferred(String link, String existing) {
    return link(link, existing, false);
  }

  /**
   * Create a hard link on the file system from {@code link} to {@code existing}, asynchronously.<p>
   * @return a Future representing the future result of the action.
   */
  public Future<Void> link(String link, String existing) {
    return linkDeferred(link, existing).execute();
  }

  public void linkSync(String link, String existing) throws Exception {
    linkDeferred(link, existing).action();
  }

  private SynchronousAction<Void> symlinkDeferred(String link, String existing) {
    return link(link, existing, true);
  }

  /**
   * Create a symbolic link on the file system from {@code link} to {@code existing}, asynchronously.<p>
   * @return a Future representing the future result of the action.
   */
  public Future<Void> symlink(String link, String existing) {
    return symlinkDeferred(link, existing).execute();
  }

  public void symlinkSync(String link, String existing) throws Exception {
    symlinkDeferred(link, existing).action();
  }

  private SynchronousAction<Void> link(String link, String existing, final boolean symbolic) {
    checkContext();
    final Path source = Paths.get(link);
    final Path target = Paths.get(existing);
    return new BlockingAction<Void>() {
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

  private SynchronousAction<Void> unlinkDeferred(String link) {
    return deleteDeferred(link);
  }

  /**
   * Unlinks the link on the file system represented by the path {@code link}, asynchronously.<p>
   * @return a Future representing the future result of the action.
   */
  public Future<Void> unlink(String link) {
    return unlinkDeferred(link).execute();
  }

  public void unlinkSync(String link) throws Exception {
    unlinkDeferred(link).action();
  }

  private SynchronousAction<String> readSymlinkDeferred(String link) {
    checkContext();
    final Path source = Paths.get(link);
    return new BlockingAction<String>() {
      public String action() throws Exception {
        try {
          return Files.readSymbolicLink(source).toString();
        } catch (NotLinkException e) {
          throw new FileSystemException("Cannot read " + source + " it's not a symbolic link");
        }
      }
    };
  }

  /**
   * Returns the path representing the file that the symbolic link specified by {@code link} points to, asynchronously.<p>
   * @return a Future representing the future result of the action.
   */
  public Future<String> readSymlink(String link) {
    return readSymlinkDeferred(link).execute();
  }

  public String readSymlinkSync(String link) throws Exception {
    return readSymlinkDeferred(link).action();
  }

  private SynchronousAction<Void> deleteDeferred(String path) {
    return deleteDeferred(path, false);
  }

  /**
   * Deletes the file represented by the specified {@code path}, asynchronously.<p>
   * @return a Future representing the future result of the action.
   */
  public Future<Void> delete(String path) {
    return deleteDeferred(path).execute();
  }

  public void deleteSync(String path) throws Exception {
    deleteDeferred(path).action();
  }

  private SynchronousAction<Void> deleteDeferred(String path, final boolean recursive) {
    checkContext();
    final Path source = Paths.get(path);
    return new BlockingAction<Void>() {
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

  /**
   * Deletes the file represented by the specified {@code path}, asynchronously.<p>
   * If the path represents a directory, then the directory and its contents will be deleted recursively.<p>
   * @return a Future representing the future result of the action.
   */
  public Future<Void> delete(String path, boolean recursive) {
    return deleteDeferred(path, recursive).execute();
  }

  public void deleteSync(String path, boolean recursive) throws Exception {
    deleteDeferred(path, recursive).action();
  }

  private SynchronousAction<Void> mkdirDeferred(String path) {
    return mkdirDeferred(path, null, false);
  }

  /**
   * Create the directory represented by {@code path}, asynchronously.<p>
   * The operation will fail if the directory already exists.<p>
   * @return a Future representing the future result of the action.
   */
  public Future<Void> mkdir(String path) {
    return mkdirDeferred(path).execute();
  }

  public void mkdirSync(String path) throws Exception {
    mkdirDeferred(path).action();
  }

  private SynchronousAction<Void> mkdirDeferred(String path, boolean createParents) {
    return mkdirDeferred(path, null, createParents);
  }

  /**
   * Create the directory represented by {@code path}, asynchronously.<p>
   * If {@code createParents} is set to {@code true} then any non-existent parent directories of the directory
   * will also be created.<p>
   * The operation will fail if the directory already exists.<p>
   * @return a Future representing the future result of the action.
   */
  public Future<Void> mkdir(String path, boolean createParents) {
    return mkdirDeferred(path, createParents).execute();
  }

  public void mkdirSync(String path, boolean createParents) throws Exception {
    mkdirDeferred(path, createParents).action();
  }

  private SynchronousAction<Void> mkdirDeferred(String path, String perms) {
    return mkdirDeferred(path, perms, false);
  }

  /**
   * Create the directory represented by {@code path}, asynchronously.<p>
   * The new directory will be created with permissions as specified by {@code perms}.
   * The permission String takes the form rwxr-x--- as specified in <a href="http://download.oracle.com/javase/7/docs/api/java/nio/file/attribute/PosixFilePermissions.html">here</a>.<p>
   * The operation will fail if the directory already exists.<p>
   * @return a Future representing the future result of the action.
   */
  public Future<Void> mkdir(String path, String perms) {
    return mkdirDeferred(path, perms).execute();
  }

  public void mkdirSync(String path, String perms) throws Exception {
    mkdirDeferred(path, perms).action();
  }

  private SynchronousAction<Void> mkdirDeferred(String path, final String perms, final boolean createParents) {
    checkContext();
    final Path source = Paths.get(path);
    final FileAttribute<?> attrs = perms == null ? null : PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(perms));
    return new BlockingAction<Void>() {
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

  /**
   * Create the directory represented by {@code path}, asynchronously.<p>
   * The new directory will be created with permissions as specified by {@code perms}.
   * The permission String takes the form rwxr-x--- as specified in <a href="http://download.oracle.com/javase/7/docs/api/java/nio/file/attribute/PosixFilePermissions.html">here</a>.<p>
   * If {@code createParents} is set to {@code true} then any non-existent parent directories of the directory
   * will also be created.<p>
   * The operation will fail if the directory already exists.<p>
   * @return a Future representing the future result of the action.
   */
  public Future<Void> mkdir(String path, String perms, boolean createParents) {
    return mkdirDeferred(path, perms, createParents).execute();
  }

  public void mkdirSync(String path, String perms, boolean createParents) throws Exception {
    mkdirDeferred(path, perms, createParents).action();
  }

  private SynchronousAction<String[]> readDirDeferred(String path) {
    return readDirDeferred(path, null);
  }

  /**
   * Read the contents of the directory specified by {@code path}, asynchronously
   * @return a Future representing the future result of the action.
   * The result is an array of String representing the paths of the files inside the directory.
   */
  public Future<String[]> readDir(String path) {
    return readDirDeferred(path).execute();
  }

  public String[] readDirSync(String path) throws Exception {
    return readDirDeferred(path).action();
  }

  private SynchronousAction<String[]> readDirDeferred(final String path, final String filter) {
    checkContext();
    return new BlockingAction<String[]>() {
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

  /**
   * Read the contents of the directory specified by {@code path}, asynchronously<p>
   * {@code filter} is a regular expression. If {@code filter} is specified then only the paths that match @{filter} will be returned.<p>
   * @return a Future representing the future result of the action.
   * The result is an array of String representing the paths of the files inside the directory.
   */
  public Future<String[]> readDir(String path, String filter) {
    return readDirDeferred(path, filter).execute();
  }

  public String[] readDirSync(String path, String filter) throws Exception {
    return readDirDeferred(path, filter).action();
  }

  private SynchronousAction<Buffer> readFileDeferred(final String path) {
    checkContext();
    return new BlockingAction<Buffer>() {
      public Buffer action() throws Exception {
        Path target = Paths.get(path);
        byte[] bytes = Files.readAllBytes(target);
        Buffer buff = Buffer.create(bytes);
        return buff;
      }
    };
  }

  /**
   * Reads the entire file as represented by the path {@code path} as a {@link Buffer}, asynchronously.<p>
   * Do not user this method to read very large files or you risk running out of available RAM.<p>
   * @return a Future representing the future result of the action.
   */
  public Future<Buffer> readFile(String path) {
    return readFileDeferred(path).execute();
  }

  public Buffer readFileSync(String path) throws Exception {
    return readFileDeferred(path).action();
  }

  private SynchronousAction<Void> writeFileDeferred(final String path, final Buffer data) {
    checkContext();
    return new BlockingAction<Void>() {
      public Void action() throws Exception {
        Path target = Paths.get(path);
        Files.write(target, data.getBytes());
        return null;
      }
    };
  }

  /**
   * Creates the file, and writes the specified {@code Buffer data} to the file represented by the path {@code path}, asynchronously.<p>
   * @return a Future representing the future result of the action.
   */
  public Future<Void> writeFile(String path, Buffer data) {
    return writeFileDeferred(path, data).execute();
  }

  public void writeFileSync(String path, Buffer data) throws Exception {
    writeFileDeferred(path, data).action();
  }

  public void lock() {
    //TODO
  }

  public void unlock() {
    //TODO
  }

  public void watchFile() {
    //TODO
  }

  public void unwatchFile() {
    //TODO
  }

  private SynchronousAction<AsyncFile> openDeferred(String path) {
    return openDeferred(path, null, true, true, true, false);
  }

  /**
   * Open the file represented by {@code path}, asynchronously.<p>
   * The file is opened for both reading and writing. If the file does not already exist it will be created.
   * Write operations will not automatically flush to storage.
   * @return a Future representing the future result of the action.
   */
  public Future<AsyncFile> open(String path) {
    return openDeferred(path).execute();
  }

  public AsyncFile openSync(String path) throws Exception {
    return openDeferred(path).action();
  }

  private SynchronousAction<AsyncFile> openDeferred(String path, String perms) {
    return openDeferred(path, perms, true, true, true, false);
  }

  /**
   * Open the file represented by {@code path}, asynchronously.<p>
   * The file is opened for both reading and writing. If the file does not already exist it will be created with the
   * permissions as specified by {@code perms}.
   * Write operations will not automatically flush to storage.
   * @return a Future representing the future result of the action.
   */
  public Future<AsyncFile> open(String path, String perms) {
    return openDeferred(path, perms).execute();
  }

  public AsyncFile openSync(String path, String perms) throws Exception {
    return openDeferred(path, perms).action();
  }

  private SynchronousAction<AsyncFile> openDeferred(String path, String perms, boolean createNew) {
    return openDeferred(path, perms, true, true, createNew, false);
  }

  /**
   * Open the file represented by {@code path}, asynchronously.<p>
   * The file is opened for both reading and writing. If the file does not already exist and
   * {@code createNew} is {@code true} it will be created with the permissions as specified by {@code perms}, otherwise
   * the operation will fail.
   * Write operations will not automatically flush to storage.
   * @return a Future representing the future result of the action.
   */
  public Future<AsyncFile> open(String path, String perms, boolean createNew) {
    return openDeferred(path, perms, createNew).execute();
  }

  public AsyncFile openSync(String path, String perms, boolean createNew) throws Exception {
    return openDeferred(path, perms, createNew).action();
  }

  private SynchronousAction<AsyncFile> openDeferred(String path, String perms, boolean read, boolean write, boolean createNew) {
    return openDeferred(path, perms, read, write, createNew, false);
  }

  /**
   * Open the file represented by {@code path}, asynchronously.<p>
   * If {@code read} is {@code true} the file will be opened for reading. If {@code write} is {@code true} the file
   * will be opened for writing.<p>
   * If the file does not already exist and
   * {@code createNew} is {@code true} it will be created with the permissions as specified by {@code perms}, otherwise
   * the operation will fail.<p>
   * Write operations will not automatically flush to storage.
   * @return a Future representing the future result of the action.
   */
  public Future<AsyncFile> open(String path, String perms, boolean read, boolean write, boolean createNew) {
    return openDeferred(path, perms, read, write, createNew).execute();
  }

  public AsyncFile openSync(String path, String perms, boolean read, boolean write, boolean createNew) throws Exception {
    return openDeferred(path, perms, read, write, createNew).action();
  }

  private SynchronousAction<AsyncFile> openDeferred(final String path, final String perms, final boolean read, final boolean write, final boolean createNew,
                   final boolean flush) {
    final Context ctx = VertxInternal.instance.getContext();
    if (ctx == null) {
      throw new IllegalStateException("Can't use file system from outside an event loop");
    }
    final Thread th = Thread.currentThread();
    return new BlockingAction<AsyncFile>() {
      public AsyncFile action() throws Exception {
        return doOpen(path, perms, read, write, createNew, flush, ctx, th);
      }
    };
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
   * @return a Future representing the future result of the action.
   */
  public Future<AsyncFile> open(String path, String perms, boolean read, boolean write, boolean createNew,
                   boolean flush) {
    return openDeferred(path, perms, read, write, createNew, flush).execute();
  }

  public AsyncFile openSync(String path, String perms, boolean read, boolean write, boolean createNew, boolean flush) throws Exception {
    return openDeferred(path, perms, read, write, createNew, flush).action();
  }

  private AsyncFile doOpen(String path, String perms, boolean read, boolean write, boolean createNew,
                           boolean flush, Context context,
                           Thread th) throws Exception {
    return new AsyncFile(path, perms, read, write, createNew, flush, context, th);
  }

  private SynchronousAction<Void> createFileDeferred(String path) {
    return createFileDeferred(path, null);
  }

  /**
   * Creates an empty file with the specified {@code path}, asynchronously.<p>
   * @return a Future representing the future result of the action.
   */
  public Future<Void> createFile(String path) {
    return createFileDeferred(path).execute();
  }

  public void createFileSync(String path) throws Exception {
    createFileDeferred(path).action();
  }

  private SynchronousAction<Void> createFileDeferred(final String path, final String perms) {
    checkContext();
    final FileAttribute<?> attrs = perms == null ? null : PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(perms));
    return new BlockingAction<Void>() {
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

  /**
   * Creates an empty file with the specified {@code path} and permissions {@code perms}, asynchronously.<p>
   * @return a Future representing the future result of the action.
   */
  public Future<Void> createFile(String path, String perms) {
    return createFileDeferred(path, perms).execute();
  }

  public void createFileSync(String path, String perms) throws Exception {
    createFileDeferred(path, perms).action();
  }

  private SynchronousAction<Boolean> existsDeferred(final String path) {
    checkContext();
    return new BlockingAction<Boolean>() {
      public Boolean action() throws Exception {
        File file = new File(path);
        return file.exists();
      }
    };
  }

  /**
   * Determines whether the file as specified by the path {@code path} exists, asynchronously.<p>
   * @return a Future representing the future result of the action.
   */
  public Future<Boolean> exists(String path) {
    return existsDeferred(path).execute();
  }

  public boolean existsSync(String path) throws Exception {
    return existsDeferred(path).action();
  }

  private SynchronousAction<FileSystemProps> fsPropsDeferred(final String path) {
    checkContext();
    return new BlockingAction<FileSystemProps>() {
      public FileSystemProps action() throws Exception {
        Path target = Paths.get(path);
        FileStore fs = Files.getFileStore(target);
        return new FileSystemProps(fs.getTotalSpace(), fs.getUnallocatedSpace(), fs.getUsableSpace());
      }
    };
  }

  /**
   * Returns properties of the file-system being used by the specified {@code path}, asynchronously.<p>
   * @return a Future representing the future result of the action.
   */
  public Future<FileSystemProps> fsProps(String path) {
    return fsPropsDeferred(path).execute();
  }

  public FileSystemProps fsPropsSync(String path) throws Exception {
    return fsPropsDeferred(path).action();
  }

  private void checkContext() {
    if (VertxInternal.instance.getContext() == null) {
      throw new IllegalStateException("Can't use file system outside an event loop");
    }
  }

}
