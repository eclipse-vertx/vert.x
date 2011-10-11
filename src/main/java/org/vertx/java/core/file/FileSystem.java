/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.core.file;

import org.vertx.java.core.BlockingAction;
import org.vertx.java.core.Deferred;
import org.vertx.java.core.Future;
import org.vertx.java.core.Vertx;
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

  /**
   * The same as {@link #copy(String, String)} but the copy does not start until the {@link Deferred#execute} method
   * is called on the Deferred instance returned by this method.
   * @return a Deferred representing the as-yet unexecuted action.
   */
  public Deferred<Void> copyDeferred(String from, String to) {
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

  /**
   * The same as {@link #copy(String, String, boolean)} but the copy does not start until the {@link Deferred#execute} method
   * is called on the Deferred instance returned by this method.
   * @return a Deferred representing the as-yet unexecuted action.
   */
  public Deferred<Void> copyDeferred(String from, String to, final boolean recursive) {
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
  public Future<Void> copy(String from, String to, final boolean recursive) {
    return copyDeferred(from, to, recursive).execute();
  }

  /**
   * The same as {@link #move(String, String)} but the move does not start until the {@link Deferred#execute} method
   * is called on the Deferred instance returned by this method.
   * @return a Deferred representing the as-yet unexecuted action.
   */
  public Deferred<Void> moveDeferred(String from, String to) {
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

  /**
   * The same as {@link #truncate(String, long)} but the truncate does not start until the {@link Deferred#execute} method
   * is called on the Deferred instance returned by this method.
   * @return a Deferred representing the as-yet unexecuted action.
   */
  public Deferred<Void> truncateDeferred(final String path, final long len) {
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
  public Future<Void> truncate(final String path, final long len) {
    return truncateDeferred(path, len).execute();
  }

  /**
   * The same as {@link #chmod(String, String)} but the chmod does not start until the {@link Deferred#execute} method
   * is called on the Deferred instance returned by this method.
   * @return a Deferred representing the as-yet unexecuted action.
   */
  public Deferred<Void> chmodDeferred(String path, String perms) {
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

  /**
   * The same as {@link #chmod(String, String, String)} but the chmod does not start until the {@link Deferred#execute} method
   * is called on the Deferred instance returned by this method.
   * @return a Deferred representing the as-yet unexecuted action.
   */
  public Deferred<Void> chmodDeferred(String path, String perms, String dirPerms) {
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


  /**
   * The same as {@link #props(String)} but the props does not start until the {@link Deferred#execute} method
   * is called on the Deferred instance returned by this method.
   * @return a Deferred representing the as-yet unexecuted action.
   */
  public Deferred<FileProps> propsDeferred(String path) {
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

  /**
   * The same as {@link #lprops(String)} but the lprops does not start until the {@link Deferred#execute} method
   * is called on the Deferred instance returned by this method.
   * @return a Deferred representing the as-yet unexecuted action.
   */
  public Deferred<FileProps> lpropsDeferred(String path) {
    return props(path, false);
  }

  /**
   * Obtain properties for the link represented by {@code path}, asynchronously. The link will not be followed.
   * @return a Future representing the future result of the action.
   */
  public Future<FileProps> lprops(String path) {
    return lpropsDeferred(path).execute();
  }

  private Deferred<FileProps> props(String path, final boolean followLinks) {
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

  /**
   * The same as {@link #link(String, String)} but the link does not start until the {@link Deferred#execute} method
   * is called on the Deferred instance returned by this method.
   * @return a Deferred representing the as-yet unexecuted action.
   */
  public Deferred<Void> linkDeferred(String link, String existing) {
    return link(link, existing, false);
  }

  /**
   * Create a hard link on the file system from {@code link} to {@code existing}, asynchronously.<p>
   * @return a Future representing the future result of the action.
   */
  public Future<Void> link(String link, String existing) {
    return linkDeferred(link, existing).execute();
  }

  /**
   * The same as {@link #symlink(String, String)} but the symlink does not start until the {@link Deferred#execute} method
   * is called on the Deferred instance returned by this method.
   * @return a Deferred representing the as-yet unexecuted action.
   */
  public Deferred<Void> symlinkDeferred(String link, String existing) {
    return link(link, existing, true);
  }

  /**
   * Create a symbolic link on the file system from {@code link} to {@code existing}, asynchronously.<p>
   * @return a Future representing the future result of the action.
   */
  public Future<Void> symlink(String link, String existing) {
    return symlinkDeferred(link, existing).execute();
  }

  private Deferred<Void> link(String link, String existing, final boolean symbolic) {
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

  /**
   * The same as {@link #unlink(String)} but the unlink does not start until the {@link Deferred#execute} method
   * is called on the Deferred instance returned by this method.
   * @return a Deferred representing the as-yet unexecuted action.
   */
  public Deferred<Void> unlinkDeferred(String link) {
    return deleteDeferred(link);
  }

  /**
   * Unlinks the link on the file system represented by the path {@code link}, asynchronously.<p>
   * @return a Future representing the future result of the action.
   */
  public Future<Void> unlink(String link) {
    return unlinkDeferred(link).execute();
  }

  /**
   * The same as {@link #readSymlink(String)} but the read does not start until the {@link Deferred#execute} method
   * is called on the Deferred instance returned by this method.
   * @return a Deferred representing the as-yet unexecuted action.
   */
  public Deferred<String> readSymlinkDeferred(String link) {
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

  /**
   * The same as {@link #delete(String)} but the delete does not start until the {@link Deferred#execute} method
   * is called on the Deferred instance returned by this method.
   * @return a Deferred representing the as-yet unexecuted action.
   */
  public Deferred<Void> deleteDeferred(String path) {
    return deleteDeferred(path, false);
  }

  /**
   * Deletes the file represented by the specified {@code path}, asynchronously.<p>
   * @return a Future representing the future result of the action.
   */
  public Future<Void> delete(String path) {
    return deleteDeferred(path).execute();
  }

  /**
   * The same as {@link #delete(String, boolean)} but the delete does not start until the {@link Deferred#execute} method
   * is called on the Deferred instance returned by this method.
   * @return a Deferred representing the as-yet unexecuted action.
   */
  public Deferred<Void> deleteDeferred(String path, final boolean recursive) {
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
  public Future<Void> delete(String path, final boolean recursive) {
    return deleteDeferred(path, recursive).execute();
  }

  /**
   * The same as {@link #mkdir(String)} but the mkdir does not start until the {@link Deferred#execute} method
   * is called on the Deferred instance returned by this method.
   * @return a Deferred representing the as-yet unexecuted action.
   */
  public Deferred<Void> mkdirDeferred(String path) {
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

  /**
   * The same as {@link #mkdir(String, boolean)} but the mkdir does not start until the {@link Deferred#execute} method
   * is called on the Deferred instance returned by this method.
   * @return a Deferred representing the as-yet unexecuted action.
   */
  public Deferred<Void> mkdirDeferred(String path, boolean createParents) {
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

  /**
   * The same as {@link #mkdir(String, String)} but the mkdir does not start until the {@link Deferred#execute} method
   * is called on the Deferred instance returned by this method.
   * @return a Deferred representing the as-yet unexecuted action.
   */
  public Deferred<Void> mkdirDeferred(String path, String perms) {
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

  /**
   * The same as {@link #mkdir(String, String, boolean)} but the mkdir does not start until the {@link Deferred#execute} method
   * is called on the Deferred instance returned by this method.
   * @return a Deferred representing the as-yet unexecuted action.
   */
  public Deferred<Void> mkdirDeferred(String path, final String perms, final boolean createParents) {
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
  public Future<Void> mkdir(String path, final String perms, final boolean createParents) {
    return mkdirDeferred(path, perms, createParents).execute();
  }

  /**
   * The same as {@link #readDir(String)} but the read does not start until the {@link Deferred#execute} method
   * is called on the Deferred instance returned by this method.
   * @return a Deferred representing the as-yet unexecuted action.
   */
  public Deferred<String[]> readDirDeferred(final String path) {
    return readDirDeferred(path, null);
  }

  /**
   * Read the contents of the directory specified by {@code path}, asynchronously
   * @return a Future representing the future result of the action.
   * The result is an array of String representing the paths of the files inside the directory.
   */
  public Future<String[]> readDir(final String path) {
    return readDirDeferred(path).execute();
  }

  /**
   * The same as {@link #readDir(String, String)} but the read does not start until the {@link Deferred#execute} method
   * is called on the Deferred instance returned by this method.
   * @return a Deferred representing the as-yet unexecuted action.
   */
  public Deferred<String[]> readDirDeferred(final String path, final String filter) {
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
  public Future<String[]> readDir(final String path, final String filter) {
    return readDirDeferred(path, filter).execute();
  }

  /**
   * The same as {@link #readFile(String)} but the read does not start until the {@link Deferred#execute} method
   * is called on the Deferred instance returned by this method.
   * @return a Deferred representing the as-yet unexecuted action.
   */
  public Deferred<Buffer> readFileDeferred(final String path) {
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
  public Future<Buffer> readFile(final String path) {
    return readFileDeferred(path).execute();
  }

  /**
   * The same as {@link #writeFile(String, Buffer)} but the write does not start until the {@link Deferred#execute} method
   * is called on the Deferred instance returned by this method.
   * @return a Deferred representing the as-yet unexecuted action.
   */
  public Deferred<Void> writeFileDeferred(final String path, final Buffer data) {
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
  public Future<Void> writeFile(final String path, final Buffer data) {
    return writeFileDeferred(path, data).execute();
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

  /**
   * The same as {@link #open(String)} but the open does not start until the {@link Deferred#execute} method
   * is called on the Deferred instance returned by this method.
   * @return a Deferred representing the as-yet unexecuted action.
   */
  public Deferred<AsyncFile> openDeferred(final String path) {
    return openDeferred(path, null, true, true, true, false);
  }

  /**
   * Open the file represented by {@code path}, asynchronously.<p>
   * The file is opened for both reading and writing. If the file does not already exist it will be created.
   * Write operations will not automatically flush to storage.
   * @return a Future representing the future result of the action.
   */
  public Future<AsyncFile> open(final String path) {
    return openDeferred(path).execute();
  }

  /**
   * The same as {@link #open(String, String)} but the open does not start until the {@link Deferred#execute} method
   * is called on the Deferred instance returned by this method.
   * @return a Deferred representing the as-yet unexecuted action.
   */
  public Deferred<AsyncFile> openDeferred(final String path, String perms) {
    return openDeferred(path, perms, true, true, true, false);
  }

  /**
   * Open the file represented by {@code path}, asynchronously.<p>
   * The file is opened for both reading and writing. If the file does not already exist it will be created with the
   * permissions as specified by {@code perms}.
   * Write operations will not automatically flush to storage.
   * @return a Future representing the future result of the action.
   */
  public Future<AsyncFile> open(final String path, String perms) {
    return openDeferred(path, perms).execute();
  }

  /**
   * The same as {@link #open(String, String, boolean)} but the open does not start until the {@link Deferred#execute} method
   * is called on the Deferred instance returned by this method.
   * @return a Deferred representing the as-yet unexecuted action.
   */
  public Deferred<AsyncFile> openDeferred(final String path, String perms, final boolean createNew) {
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
  public Future<AsyncFile> open(final String path, String perms, final boolean createNew) {
    return openDeferred(path, perms, createNew).execute();
  }

  /**
   * The same as {@link #open(String, String, boolean, boolean, boolean)} but the open does not start until the {@link Deferred#execute} method
   * is called on the Deferred instance returned by this method.
   * @return a Deferred representing the as-yet unexecuted action.
   */
  public Deferred<AsyncFile> openDeferred(final String path, String perms, final boolean read, final boolean write, final boolean createNew) {
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
  public Future<AsyncFile> open(final String path, String perms, final boolean read, final boolean write, final boolean createNew) {
    return openDeferred(path, perms, read, write, createNew).execute();
  }

  /**
   * The same as {@link #open(String, String, boolean, boolean, boolean, boolean)} but the open does not start until the {@link Deferred#execute} method
   * is called on the Deferred instance returned by this method.
   * @return a Deferred representing the as-yet unexecuted action.
   */
  public Deferred<AsyncFile> openDeferred(final String path, final String perms, final boolean read, final boolean write, final boolean createNew,
                   final boolean flush) {
    final long contextID = Vertx.instance.getContextID();
    final Thread th = Thread.currentThread();
    return new BlockingAction<AsyncFile>() {
      public AsyncFile action() throws Exception {
        return doOpen(path, perms, read, write, createNew, flush, contextID, th);
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
  public Future<AsyncFile> open(final String path, final String perms, final boolean read, final boolean write, final boolean createNew,
                   final boolean flush) {
    return openDeferred(path, perms, read, write, createNew, flush).execute();
  }

  private AsyncFile doOpen(final String path, String perms, final boolean read, final boolean write, final boolean createNew,
                           final boolean flush, final long contextID,
                           final Thread th) throws Exception {
    return new AsyncFile(path, perms, read, write, createNew, flush, contextID, th);
  }

  /**
   * The same as {@link #createFile(String)} but the create does not start until the {@link Deferred#execute} method
   * is called on the Deferred instance returned by this method.
   * @return a Deferred representing the as-yet unexecuted action.
   */
  public Deferred<Void> createFileDeferred(final String path) {
    return createFileDeferred(path, null);
  }

  /**
   * Creates an empty file with the specified {@code path}, asynchronously.<p>
   * @return a Future representing the future result of the action.
   */
  public Future<Void> createFile(final String path) {
    return createFileDeferred(path).execute();
  }

  /**
   * The same as {@link #createFile(String, String)} but the create does not start until the {@link Deferred#execute} method
   * is called on the Deferred instance returned by this method.
   * @return a Deferred representing the as-yet unexecuted action.
   */
  public Deferred<Void> createFileDeferred(final String path, final String perms) {
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
  public Future<Void> createFile(final String path, final String perms) {
    return createFileDeferred(path, perms).execute();
  }

  /**
   * The same as {@link #exists(String)} but the operation does not start until the {@link Deferred#execute} method
   * is called on the Deferred instance returned by this method.
   * @return a Deferred representing the as-yet unexecuted action.
   */
  public Deferred<Boolean> existsDeferred(final String path) {
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
  public Future<Boolean> exists(final String path) {
    return existsDeferred(path).execute();
  }

  /**
   * The same as {@link #getFSProps(String)} but the check does not start until the {@link Deferred#execute} method
   * is called on the Deferred instance returned by this method.
   * @return a Deferred representing the as-yet unexecuted action.
   */
  public Deferred<FileSystemProps> getFSPropsDeferred(final String path) {
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
  public Future<FileSystemProps> getFSProps(final String path) {
    return getFSPropsDeferred(path).execute();
  }

}
