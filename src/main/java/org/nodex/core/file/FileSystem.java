/*
 * Copyright 2002-2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.nodex.core.file;

import org.nodex.core.BlockingTask;
import org.nodex.core.CompletionHandler;
import org.nodex.core.Nodex;
import org.nodex.core.buffer.Buffer;

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

public class FileSystem {

  public static FileSystem instance = new FileSystem();

  private FileSystem() {
  }

  public void copy(String from, String to, CompletionHandler<Void> completionHandler) {
    copy(from, to, false, completionHandler);
  }

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

  public void chmod(String path, String perms, CompletionHandler<Void> completionHandler) {
    chmod(path, perms, null, completionHandler);
  }

  /*
  Permissions is a String of the form rwxr-x---
  See http://download.oracle.com/javase/7/docs/api/java/nio/file/attribute/PosixFilePermissions.html create method
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

  public void stat(String path,  CompletionHandler<FileStats> completionHandler) {
    stat(path, true, completionHandler);
  }

  public void lstat(String path, CompletionHandler<FileStats> completionHandler) {
    stat(path, false, completionHandler);
  }

  private void stat(String path, final boolean followLinks, CompletionHandler<FileStats> completionHandler) {
    final Path target = Paths.get(path);
    new BlockingTask<FileStats>(completionHandler) {
      public FileStats execute() throws Exception {
        try {
          BasicFileAttributes attrs;
          if (followLinks) {
            attrs = Files.readAttributes(target, BasicFileAttributes.class);
          } else {
            attrs = Files.readAttributes(target, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
          }
          return new FileStats(attrs);
        } catch (NoSuchFileException e) {
          throw new FileSystemException("No such file: " + target);
        }
      }
    }.run();
  }

  public void link(String link, String existing, CompletionHandler<Void> completionHandler) {
    link(link, existing, false, completionHandler);
  }

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

  public void unlink(String link, CompletionHandler<Void> completionHandler) {
    delete(link, completionHandler);
  }

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

  public void delete(String path, CompletionHandler<Void> completionHandler) {
    delete(path, false, completionHandler);
  }

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

  public void mkdir(String path, CompletionHandler<Void> completionHandler) {
    mkdir(path, null, false, completionHandler);
  }

  public void mkdir(String path, boolean createParents, CompletionHandler<Void> completionHandler) {
    mkdir(path, null, createParents, completionHandler);
  }

  public void mkdir(String path, String perms, CompletionHandler<Void> completionHandler) {
    mkdir(path, perms, false, completionHandler);
  }

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

  public void readDir(final String path, CompletionHandler<String[]> completionHandler) {
    readDir(path, null, completionHandler);
  }

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
          for (File f: files) {
            ret[i++] = f.getCanonicalPath();
          }
          return ret;
        }
      }
    }.run();
  }

  // Read and write entire files in one go

  public void readFileAsString(final String path, final String encoding, final CompletionHandler<String> completionHandler) {
    readFile(path, new CompletionHandler<Buffer>() {
      public void onCompletion(Buffer result) {
        String str = result.toString(encoding);
        completionHandler.onCompletion(str);
      }

      public void onException(Exception e) {
        completionHandler.onException(e);
      }
    });
  }

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

  public void writeStringToFile(String path, String str, String enc, CompletionHandler<Void> completionHandler) {
    Buffer buff = Buffer.create(str, enc);
    writeFile(path, buff, completionHandler);
  }

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

  // Close and open

  public void open(final String path,
                   CompletionHandler<AsyncFile> completionHandler) {
    open(path, null, true, true, true, false, false, completionHandler);
  }

  public void open(final String path, String perms,
                   CompletionHandler<AsyncFile> completionHandler) {
    open(path, perms, true, true, true, false, false, completionHandler);
  }

  public void open(final String path, String perms, final boolean createNew,
                   CompletionHandler<AsyncFile> completionHandler) {
    open(path, perms, true, true, createNew, false, false, completionHandler);
  }

  public void open(final String path, String perms, final boolean read, final boolean write, final boolean createNew,
                   CompletionHandler<AsyncFile> completionHandler) {
    open(path, perms, read, write, createNew, false, false, completionHandler);
  }

  public void open(final String path, final String perms, final boolean read, final boolean write, final boolean createNew,
                   final boolean sync, final boolean syncMeta, CompletionHandler<AsyncFile> completionHandler) {
    final long contextID = Nodex.instance.getContextID();
    final Thread th = Thread.currentThread();
    new BlockingTask<AsyncFile>(completionHandler) {
      public AsyncFile execute() throws Exception {
        return doOpen(path, perms, read, write, createNew, sync, syncMeta, contextID, th);
      }
    }.run();
  }

  private AsyncFile doOpen(final String path, String perms, final boolean read, final boolean write, final boolean createNew,
                            final boolean sync, final boolean syncMeta, final long contextID,
                            final Thread th) throws Exception {
    return new AsyncFile(path, perms, read, write, createNew, sync, syncMeta, contextID, th);
  }

  //Create an empty file

  public void createFile(final String path, CompletionHandler<Void> completionHandler) {
    createFile(path, null, completionHandler);
  }

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

  public void exists(final String path, CompletionHandler<Boolean> completionHandler) {
    new BlockingTask<Boolean>(completionHandler) {
      public Boolean execute() throws Exception {
        File file = new File(path);
        return file.exists();
      }
    }.run();
  }

  public void getFSStats(final String path, CompletionHandler<FileSystemStats> completionHandler) {
    new BlockingTask<FileSystemStats>(completionHandler) {
      public FileSystemStats execute() throws Exception {
        Path target = Paths.get(path);
        FileStore fs = Files.getFileStore(target);
        return new FileSystemStats(fs.getTotalSpace(), fs.getUnallocatedSpace(), fs.getUsableSpace());
      }
    }.run();
  }

}
