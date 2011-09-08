/*
 * Copyright 2011 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.nodex.java.core.file;

import org.nodex.java.core.BlockingTask;
import org.nodex.java.core.Completion;
import org.nodex.java.core.CompletionHandler;
import org.nodex.java.core.Nodex;
import org.nodex.java.core.buffer.Buffer;

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
   * Copy a file from the path {@code from} to path {@code to}<p>
   * The copy will fail if the destination if the destination already exists.<p>
   * The actual copy will happen asynchronously, and the specified {@code completionHandler} will be called
   * when the operation is complete, or, if the operation fails.
   */
  public void copy(String from, String to, CompletionHandler<Void> completionHandler) {
    copy(from, to, false, completionHandler);
  }

  /**
   * Copy a file from the path {@code from} to path {@code to}<p>
   * If {@code recursive} is {@code true} and {@code from} represents a directory, then the directory and its contents
   * will be copied recursively to the destination {@code to}.<p>
   * The copy will fail if the destination if the destination already exists.<p>
   * The actual copy will happen asynchronously, and the specified {@code completionHandler} will be called
   * when the operation is complete, or, if the operation fails.
   */
  public void copy(String from, String to, final boolean recursive, CompletionHandler<Void> completionHandler) {
    final Path source = Paths.get(from);
    final Path target = Paths.get(to);
    new BlockingTask<Void>(completionHandler) {
      public Void execute() throws Exception {
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
    }.run();
  }

  /**
   * Move a file from the path {@code from} to path {@code to}<p>
   * The move will fail if the destination if the destination already exists.<p>
   * The actual move will happen asynchronously, and the specified {@code completionHandler} will be called
   * when the operation is complete, or, if the operation fails.
   */
  public void move(String from, String to, CompletionHandler<Void> completionHandler) {
    //TODO atomic moves - but they have different semantics, e.g. on Linux if target already exists it is overwritten
    final Path source = Paths.get(from);
    final Path target = Paths.get(to);
    new BlockingTask<Void>(completionHandler) {
      public Void execute() throws Exception {
        try {
          Files.move(source, target);
        } catch (FileAlreadyExistsException e) {
          throw new FileSystemException("Failed to move between " + source + " and " + target + ". Target already exists");
        } catch (AtomicMoveNotSupportedException e) {
          throw new FileSystemException("Atomic move not supported between " + source + " and " + target);
        }
        return null;
      }
    }.run();
  }

  /**
   * Truncate the file represented by {@code path} to length {@code len}, in bytes.<p>
   * The operation will fail if the file does not exist or {@code len} is less than {@code zero}.
   * The actual truncate will happen asynchronously, and the specified {@code completionHandler} will be called
   * when the operation is complete, or, if the operation fails.
   */
  public void truncate(final String path, final long len, CompletionHandler<Void> completionHandler) {
    new BlockingTask<Void>(completionHandler) {
      public Void execute() throws Exception {
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
    }.run();
  }

  /**
   * Change the permissions on the file represented by {@code path} to {@code perms}.
   * The permission String takes the form rwxr-x--- as specified <a href="http://download.oracle.com/javase/7/docs/api/java/nio/file/attribute/PosixFilePermissions.html">here</a>.<p>
   * The actual chmod will happen asynchronously, and the specified {@code completionHandler} will be called
   * when the operation is complete, or, if the operation fails.
   */
  public void chmod(String path, String perms, CompletionHandler<Void> completionHandler) {
    chmod(path, perms, null, completionHandler);
  }

  /**
   * Change the permissions on the file represented by {@code path} to {@code perms}.
   * The permission String takes the form rwxr-x--- as specified in {<a href="http://download.oracle.com/javase/7/docs/api/java/nio/file/attribute/PosixFilePermissions.html">here</a>}.<p>
   * If the file is directory then all contents will also have their permissions changed recursively. Any directory permissions will
   * be set to {@code dirPerms}, whilst any normal file permissions will be set to {@code perms}.<p>
   * The actual chmod will happen asynchronously, and the specified {@code completionHandler} will be called
   * when the operation is complete, or, if the operation fails.
   */
  public void chmod(String path, String perms, String dirPerms, CompletionHandler<Void> completionHandler) {
    final Path target = Paths.get(path);
    final Set<PosixFilePermission> permissions = PosixFilePermissions.fromString(perms);
    final Set<PosixFilePermission> dirPermissions = dirPerms == null ? null : PosixFilePermissions.fromString(dirPerms);
    new BlockingTask<Void>(completionHandler) {
      public Void execute() throws Exception {
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
    }.run();
  }

  /**
   * Obtain properties for the file represented by {@code path}. If the file is a link, the link will be followed.<p>
   * The actual properties will be obtained asynchronously
   * and the specified {@code completionHandler} will be called with the results, or, if the operation fails.
   */
  public void props(String path, CompletionHandler<FileProps> completionHandler) {
    props(path, true, completionHandler);
  }

  /**
   * Obtain properties for the link represented by {@code path}. The link will not be followed.<p>
   * The actual properties will be obtained asynchronously
   * and the specified {@code completionHandler} will be called with the results, or, if the operation fails.
   */
  public void lprops(String path, CompletionHandler<FileProps> completionHandler) {
    props(path, false, completionHandler);
  }

  private void props(String path, final boolean followLinks, CompletionHandler<FileProps> completionHandler) {
    final Path target = Paths.get(path);
    new BlockingTask<FileProps>(completionHandler) {
      public FileProps execute() throws Exception {
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
    }.run();
  }

  /**
   * Create a hard link on the file system from {@code link} to {@code existing}.<p>
   * The actual link will be created asynchronously and the specified {@code completionHandler} will be called
   * on completion, or, if the operation fails.
   */
  public void link(String link, String existing, CompletionHandler<Void> completionHandler) {
    link(link, existing, false, completionHandler);
  }

  /**
   * Create a symbolic link on the file system from {@code link} to {@code existing}.<p>
   * The actual link will be created asynchronously and the specified {@code completionHandler} will be called
   * on completion, or, if the operation fails.
   */
  public void symlink(String link, String existing, CompletionHandler<Void> completionHandler) {
    link(link, existing, true, completionHandler);
  }

  private void link(String link, String existing, final boolean symbolic, CompletionHandler<Void> completionHandler) {
    final Path source = Paths.get(link);
    final Path target = Paths.get(existing);
    new BlockingTask<Void>(completionHandler) {
      public Void execute() throws Exception {
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
    }.run();
  }

  /**
   * Unlinks the link on the file system represented by the path {@code link}.<p>
   * The actual unlink will be done asynchronously and the specified {@code completionHandler} will be called
   * on completion, or, if the operation fails.
   */
  public void unlink(String link, CompletionHandler<Void> completionHandler) {
    delete(link, completionHandler);
  }

  /**
   * Returns the path representing the file that the symbolic link specified by {@code link} points to.<p>
   * The actual read will be done asynchronously and the specified {@code completionHandler} will be called
   * with the result on completion, or, if the operation fails.
   */
  public void readSymlink(String link, CompletionHandler<String> completionHandler) {
    final Path source = Paths.get(link);
    new BlockingTask<String>(completionHandler) {
      public String execute() throws Exception {
        try {
          return Files.readSymbolicLink(source).toString();
        } catch (NotLinkException e) {
          throw new FileSystemException("Cannot read " + source + " it's not a symbolic link");
        }
      }
    }.run();
  }

  /**
   * Deletes the file represented by the specified {@code path}.<p>
   * The actual delete will be done asynchronously and the specified {@code completionHandler} will be called
   * on completion, or, if the operation fails.
   */
  public void delete(String path, CompletionHandler<Void> completionHandler) {
    delete(path, false, completionHandler);
  }

  /**
   * Deletes the file represented by the specified {@code path}.<p>
   * If the path represents a directory, then the directory and its contents will be deleted recursively.<p>
   * The actual delete will be done asynchronously and the specified {@code completionHandler} will be called
   * on completion, or, if the operation fails.
   */
  public void delete(String path, final boolean recursive, CompletionHandler<Void> completionHandler) {
    final Path source = Paths.get(path);
    new BlockingTask<Void>(completionHandler) {
      public Void execute() throws Exception {
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
    }.run();
  }

  /**
   * Create the directory represented by {@code path}.<p>
   * The operation will fail if the directory already exists.<p>
   * The actual create will be done asynchronously and the specified {@code completionHandler} will be called
   * on completion, or, if the operation fails.
   */
  public void mkdir(String path, CompletionHandler<Void> completionHandler) {
    mkdir(path, null, false, completionHandler);
  }

  /**
   * Create the directory represented by {@code path}.<p>
   * If {@code createParents} is set to {@code true} then any non-existent parent directories of the directory
   * will also be created.<p>
   * The operation will fail if the directory already exists.<p>
   * The actual create will be done asynchronously and the specified {@code completionHandler} will be called
   * on completion, or, if the operation fails.
   */
  public void mkdir(String path, boolean createParents, CompletionHandler<Void> completionHandler) {
    mkdir(path, null, createParents, completionHandler);
  }

  /**
   * Create the directory represented by {@code path}.<p>
   * The new directory will be created with permissions as specified by {@code perms}.
   * The permission String takes the form rwxr-x--- as specified in <a href="http://download.oracle.com/javase/7/docs/api/java/nio/file/attribute/PosixFilePermissions.html">here</a>.<p>
   * The operation will fail if the directory already exists.<p>
   * The actual create will be done asynchronously and the specified {@code completionHandler} will be called
   * on completion, or, if the operation fails.
   */
  public void mkdir(String path, String perms, CompletionHandler<Void> completionHandler) {
    mkdir(path, perms, false, completionHandler);
  }

  /**
   * Create the directory represented by {@code path}.<p>
   * The new directory will be created with permissions as specified by {@code perms}.
   * The permission String takes the form rwxr-x--- as specified in <a href="http://download.oracle.com/javase/7/docs/api/java/nio/file/attribute/PosixFilePermissions.html">here</a>.<p>
   * If {@code createParents} is set to {@code true} then any non-existent parent directories of the directory
   * will also be created.<p>
   * The operation will fail if the directory already exists.<p>
   * The actual create will be done asynchronously and the specified {@code completionHandler} will be called
   * on completion, or, if the operation fails.
   */
  public void mkdir(String path, final String perms, final boolean createParents, CompletionHandler<Void> completionHandler) {
    final Path source = Paths.get(path);
    final FileAttribute<?> attrs = perms == null ? null : PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(perms));
    new BlockingTask<Void>(completionHandler) {
      public Void execute() throws Exception {
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
    }.run();
  }

  /**
   * Read the contents of the directory specified by {@code path}.<p>
   * The result is an array of String representing the paths of the files inside the directory.<p>
   * The actual read will be done asynchronously and the specified {@code completionHandler} will be called
   * with the results on completion, or, if the operation fails.
   * @param path
   * @param completionHandler
   */
  public void readDir(final String path, CompletionHandler<String[]> completionHandler) {
    readDir(path, null, completionHandler);
  }

  /**
   * Read the contents of the directory specified by {@code path}.<p>
   * The result is an array of String representing the paths of the files inside the directory.<p>
   * If {@code filter} is specified then only the paths that match @{filter} will be returned.
   * {@code filter} is a regular expression.<p>
   * The actual read will be done asynchronously and the specified {@code completionHandler} will be called
   * with the results on completion, or, if the operation fails.
   * @param path
   * @param completionHandler
   */
  public void readDir(final String path, final String filter, CompletionHandler<String[]> completionHandler) {
    new BlockingTask<String[]>(completionHandler) {
      public String[] execute() throws Exception {
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
    }.run();
  }

  /**
   * Reads the entire file as represented by the path {@code path} as a String, using the encoding {@code enc}<p>
   * Do not user this method to read very large files or you risk running out of available RAM.<p>
   * The actual read will be done asynchronously and the specified {@code completionHandler} will be called
   * with the results on completion, or, if the operation fails.
   */
  public void readFileAsString(final String path, final String encoding, final CompletionHandler<String> completionHandler) {
    readFile(path, new CompletionHandler<Buffer>() {
      @Override
      public void onEvent(Completion<Buffer> completion) {
        if (completion.succeeded()) {
          completionHandler.onEvent(new Completion<>(completion.result.toString(encoding)));
        } else {
          completionHandler.onEvent(new Completion<String>(completion.exception));
        }
      }
    });
  }

  /**
   * Reads the entire file as represented by the path {@code path} as a {@link Buffer}.<p>
   * Do not user this method to read very large files or you risk running out of available RAM.<p>
   * The actual read will be done asynchronously and the specified {@code completionHandler} will be called
   * with the results on completion, or, if the operation fails.
   */
  public void readFile(final String path, CompletionHandler<Buffer> completionHandler) {
    new BlockingTask<Buffer>(completionHandler) {
      public Buffer execute() throws Exception {
        Path target = Paths.get(path);
        byte[] bytes = Files.readAllBytes(target);
        Buffer buff = Buffer.create(bytes);
        return buff;
      }
    }.run();
  }

  /**
   * Creates and writes the specified {@code String str} to the file represented by the path {@code path} using the encoding {@code enc}.<p>
   * The actual write will be done asynchronously and the specified {@code completionHandler} will be called
   * on completion, or, if the operation fails.
   */
  public void writeStringToFile(String path, String str, String enc, CompletionHandler<Void> completionHandler) {
    Buffer buff = Buffer.create(str, enc);
    writeFile(path, buff, completionHandler);
  }

  /**
   * Creates and writes the specified {@code Buffer data} to the file represented by the path {@code path}.<p>
   * The actual write will be done asynchronously and the specified {@code completionHandler} will be called
   * on completion, or, if the operation fails.
   */
  public void writeFile(final String path, final Buffer data, CompletionHandler<Void> completionHandler) {
    new BlockingTask<Void>(completionHandler) {
      public Void execute() throws Exception {
        Path target = Paths.get(path);
        Files.write(target, data.getBytes());
        return null;
      }
    }.run();
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
   * Open the file represented by {@code path}.<p>
   * The file is opened for both reading and writing. If the file does not already exist it will be created.
   * Write operation will not automatically flush to storage.<p>
   * The actual open will be done asynchronously and the specified {@code completionHandler} will be called
   * with the {@link AsyncFile} on completion, or, if the operation fails
   */
  public void open(final String path,
                   CompletionHandler<AsyncFile> completionHandler) {
    open(path, null, true, true, true, false, completionHandler);
  }

  /**
   * Open the file represented by {@code path}.<p>
   * The file is opened for both reading and writing. If the file does not already exist it will be created with the
   * permissions as specified by {@code perms}.
   * Write operation will not automatically flush to storage.<p>
   * The actual open will be done asynchronously and the specified {@code completionHandler} will be called
   * with the {@link AsyncFile} on completion, or, if the operation fails
   */
  public void open(final String path, String perms,
                   CompletionHandler<AsyncFile> completionHandler) {
    open(path, perms, true, true, true, false, completionHandler);
  }

  /**
   * Open the file represented by {@code path}.<p>
   * The file is opened for both reading and writing. If the file does not already exist and
   * {@code createNew} is {@code true} it will be created with the permissions as specified by {@code perms}, otherwise
   * the operation will fail.
   * Write operations will not automatically flush to storage.<p>
   * The actual open will be done asynchronously and the specified {@code completionHandler} will be called
   * with the {@link AsyncFile} on completion, or, if the operation fails
   */
  public void open(final String path, String perms, final boolean createNew,
                   CompletionHandler<AsyncFile> completionHandler) {
    open(path, perms, true, true, createNew, false, completionHandler);
  }

  /**
   * Open the file represented by {@code path}.<p>
   * If {@code read} is {@code true} the file will be opened for reading. If {@code write} is {@code true} the file
   * will be opened for writing.<p>
   * If the file does not already exist and
   * {@code createNew} is {@code true} it will be created with the permissions as specified by {@code perms}, otherwise
   * the operation will fail.<p>
   * Write operations will not automatically flush to storage.<p>
   * The actual open will be done asynchronously and the specified {@code completionHandler} will be called
   * with the {@link AsyncFile} on completion, or, if the operation fails
   */
  public void open(final String path, String perms, final boolean read, final boolean write, final boolean createNew,
                   CompletionHandler<AsyncFile> completionHandler) {
    open(path, perms, read, write, createNew, false, completionHandler);
  }

  /**
   * Open the file represented by {@code path}.<p>
   * If {@code read} is {@code true} the file will be opened for reading. If {@code write} is {@code true} the file
   * will be opened for writing.<p>
   * If the file does not already exist and
   * {@code createNew} is {@code true} it will be created with the permissions as specified by {@code perms}, otherwise
   * the operation will fail.<p>
   * If {@code flush} is {@code true} then all writes will be automatically flushed through OS buffers to the underlying
   * storage on each write.<p>
   * Write operations will not automatically flush to storage.<p>
   * The actual open will be done asynchronously and the specified {@code completionHandler} will be called
   * with the {@link AsyncFile} on completion, or, if the operation fails
   */
  public void open(final String path, final String perms, final boolean read, final boolean write, final boolean createNew,
                   final boolean flush, CompletionHandler<AsyncFile> completionHandler) {
    final long contextID = Nodex.instance.getContextID();
    final Thread th = Thread.currentThread();
    new BlockingTask<AsyncFile>(completionHandler) {
      public AsyncFile execute() throws Exception {
        return doOpen(path, perms, read, write, createNew, flush, contextID, th);
      }
    }.run();
  }

  private AsyncFile doOpen(final String path, String perms, final boolean read, final boolean write, final boolean createNew,
                           final boolean flush, final long contextID,
                           final Thread th) throws Exception {
    return new AsyncFile(path, perms, read, write, createNew, flush, contextID, th);
  }

  /**
   * Creates an empty file with the specified {@code path}.<p>
   * The actual creation will be done asynchronously and the specified {@code completionHandler} will be called
   * on completion, or, if the operation fails
   */
  public void createFile(final String path, CompletionHandler<Void> completionHandler) {
    createFile(path, null, completionHandler);
  }

  /**
   * Creates an empty file with the specified {@code path} and permissions {@code perms}<p>
   * The actual creation will be done asynchronously and the specified {@code completionHandler} will be called
   * on completion, or, if the operation fails
   */
  public void createFile(final String path, final String perms, CompletionHandler<Void> completionHandler) {
    final FileAttribute<?> attrs = perms == null ? null : PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(perms));
    new BlockingTask<Void>(completionHandler) {
      public Void execute() throws Exception {
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
    }.run();
  }

  /**
   * Determines whether the file as specified by the path {@code path} exists.<p>
   * The actual check will be done asynchronously and the specified {@code completionHandler} will be called
   * on completion with the result, or, if the operation fails
   */
  public void exists(final String path, CompletionHandler<Boolean> completionHandler) {
    new BlockingTask<Boolean>(completionHandler) {
      public Boolean execute() throws Exception {
        File file = new File(path);
        return file.exists();
      }
    }.run();
  }

  /**
   * Returns properties of the file-system being used by the specified {@code path}<p>
   * The actual properties are obtained asynchronously and the specified {@code completionHandler} will be called
   * on completion with the result, or, if the operation fails
   */
  public void getFSProps(final String path, CompletionHandler<FileSystemProps> completionHandler) {
    new BlockingTask<FileSystemProps>(completionHandler) {
      public FileSystemProps execute() throws Exception {
        Path target = Paths.get(path);
        FileStore fs = Files.getFileStore(target);
        return new FileSystemProps(fs.getTotalSpace(), fs.getUnallocatedSpace(), fs.getUsableSpace());
      }
    }.run();
  }

}
