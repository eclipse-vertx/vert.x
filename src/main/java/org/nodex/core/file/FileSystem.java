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

import org.nodex.core.BackgroundTask;
import org.nodex.core.BackgroundTaskWithResult;
import org.nodex.core.Completion;
import org.nodex.core.CompletionWithResult;
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

  public void copy(String from, String to, Completion completion) {
    copy(from, to, false, completion);
  }

  public void copy(String from, String to, final boolean recursive, Completion completion) {
    final Path source = Paths.get(from);
    final Path target = Paths.get(to);
    new BackgroundTask(completion) {
      public Object execute() throws Exception {
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

  public void move(String from, String to, Completion completion) {
    //TODO atomic moves - but they have different semantics, e.g. on Linux if target already exists it is overwritten
    final Path source = Paths.get(from);
    final Path target = Paths.get(to);
    new BackgroundTask(completion) {
      public Object execute() throws Exception {
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

  public void truncate(final String path, final long len, Completion completion) {
    new BackgroundTask(completion) {
      public Object execute() throws Exception {
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

  public void chmod(String path, String perms, Completion completion) {
    chmod(path, perms, null, completion);
  }

  /*
  Permissions is a String of the form rwxr-x---
  See http://download.oracle.com/javase/7/docs/api/java/nio/file/attribute/PosixFilePermissions.html createBuffer method
   */
  public void chmod(String path, String perms, String dirPerms, Completion completion) {
    final Path target = Paths.get(path);
    final Set<PosixFilePermission> permissions = PosixFilePermissions.fromString(perms);
    final Set<PosixFilePermission> dirPermissions = dirPerms == null ? null : PosixFilePermissions.fromString(dirPerms);
    new BackgroundTask(completion) {
      public Object execute() throws Exception {
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

  public void stat(String path,  CompletionWithResult<FileStats> completion) {
    stat(path, true, completion);
  }

  public void lstat(String path, CompletionWithResult<FileStats> completion) {
    stat(path, false, completion);
  }

  private void stat(String path, final boolean followLinks, CompletionWithResult<FileStats> completion) {
    final Path target = Paths.get(path);
    new BackgroundTaskWithResult<FileStats>(completion) {
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

  public void link(String link, String existing, Completion completion) {
    link(link, existing, false, completion);
  }

  public void symlink(String link, String existing, Completion completion) {
    link(link, existing, true, completion);
  }

  private void link(String link, String existing, final boolean symbolic, Completion completion) {
    final Path source = Paths.get(link);
    final Path target = Paths.get(existing);
    new BackgroundTask(completion) {
      public Object execute() throws Exception {
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

  public void unlink(String link, Completion completion) {
    delete(link, completion);
  }

  public void readSymlink(String link, CompletionWithResult<String> completion) {
    final Path source = Paths.get(link);
    new BackgroundTaskWithResult<String>(completion) {
      public String execute() throws Exception {
        try {
          return Files.readSymbolicLink(source).toString();
        } catch (NotLinkException e) {
          throw new FileSystemException("Cannot read " + source + " it's not a symbolic link");
        }
      }
    }.run();
  }

  public void delete(String path, Completion completion) {
    delete(path, false, completion);
  }

  public void delete(String path, final boolean recursive, Completion completion) {
    final Path source = Paths.get(path);
    new BackgroundTask(completion) {
      public Object execute() throws Exception {
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

  public void mkdir(String path, Completion completion) {
    mkdir(path, null, false, completion);
  }

  public void mkdir(String path, boolean createParents, Completion completion) {
    mkdir(path, null, createParents, completion);
  }

  public void mkdir(String path, String perms, Completion completion) {
    mkdir(path, perms, false, completion);
  }

  public void mkdir(String path, final String perms, final boolean createParents, Completion completion) {
    final Path source = Paths.get(path);
    final FileAttribute<?> attrs = perms == null ? null : PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(perms));
    new BackgroundTask(completion) {
      public Object execute() throws Exception {
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

  public void readDir(final String path, CompletionWithResult<String[]> completion) {
    readDir(path, null, completion);
  }

  public void readDir(final String path, final String filter, CompletionWithResult<String[]> completion) {
    new BackgroundTaskWithResult<String[]>(completion) {
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

  public void readFileAsString(final String path, final String encoding, final CompletionWithResult<String> completion) {
    readFile(path, new CompletionWithResult<Buffer>() {
      public void onCompletion(Buffer result) {
        String str = result.toString(encoding);
        completion.onCompletion(str);
      }

      public void onException(Exception e) {
        completion.onException(e);
      }
    });
  }

  public void readFile(final String path, CompletionWithResult<Buffer> completion) {
    new BackgroundTaskWithResult<Buffer>(completion) {
      public Buffer execute() throws Exception {
        Path target = Paths.get(path);
        byte[] bytes = Files.readAllBytes(target);
        Buffer buff = Buffer.createBuffer(bytes);
        return buff;
      }
    }.run();
  }

  public void writeStringToFile(String path, String str, String enc, Completion completion) {
    Buffer buff = Buffer.createBuffer(str, enc);
    writeFile(path, buff, completion);
  }

  public void writeFile(final String path, final Buffer data, Completion completion) {
    new BackgroundTask(completion) {
      public Object execute() throws Exception {
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
                   CompletionWithResult<AsyncFile> completion) {
    open(path, null, true, true, true, false, false, completion);
  }

  public void open(final String path, String perms,
                   CompletionWithResult<AsyncFile> completion) {
    open(path, perms, true, true, true, false, false, completion);
  }

  public void open(final String path, String perms, final boolean createNew,
                   CompletionWithResult<AsyncFile> completion) {
    open(path, perms, true, true, createNew, false, false, completion);
  }

  public void open(final String path, String perms, final boolean read, final boolean write, final boolean createNew,
                   CompletionWithResult<AsyncFile> completion) {
    open(path, perms, read, write, createNew, false, false, completion);
  }

  public void open(final String path, final String perms, final boolean read, final boolean write, final boolean createNew,
                   final boolean sync, final boolean syncMeta, CompletionWithResult<AsyncFile> completion) {
    final String contextID = Nodex.instance.getContextID();
    final Thread th = Thread.currentThread();
    new BackgroundTaskWithResult<AsyncFile>(completion) {
      public AsyncFile execute() throws Exception {
        return doOpen(path, perms, read, write, createNew, sync, syncMeta, contextID, th);
      }
    }.run();
  }

  private AsyncFile doOpen(final String path, String perms, final boolean read, final boolean write, final boolean createNew,
                            final boolean sync, final boolean syncMeta, final String contextID, final Thread th) throws Exception {
    return new AsyncFile(path, perms, read, write, createNew, sync, syncMeta, contextID, th);
  }

  //Create an empty file

  public void createFile(final String path, Completion completion) {
    createFile(path, null, completion);
  }

  public void createFile(final String path, final String perms, Completion completion) {
    final FileAttribute<?> attrs = perms == null ? null : PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(perms));
    new BackgroundTask(completion) {
      public Object execute() throws Exception {
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

  public void exists(final String path, CompletionWithResult<Boolean> completion) {
    new BackgroundTaskWithResult<Boolean>(completion) {
      public Boolean execute() throws Exception {
        File file = new File(path);
        return file.exists();
      }
    }.run();
  }

  public void getFSStats(final String path, CompletionWithResult<FileSystemStats> completion) {
    new BackgroundTaskWithResult<FileSystemStats>(completion) {
      public FileSystemStats execute() throws Exception {
        Path target = Paths.get(path);
        FileStore fs = Files.getFileStore(target);
        return new FileSystemStats(fs.getTotalSpace(), fs.getUnallocatedSpace(), fs.getUsableSpace());
      }
    }.run();
  }

}
