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
import org.nodex.core.buffer.Buffer;
import org.nodex.core.buffer.DataHandler;
import org.nodex.core.streams.ReadStream;
import org.nodex.core.streams.WriteStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotLinkException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

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
        } catch (IOException e) {
          throw new FileSystemException(e.getMessage());
        }
        return null;
      }
    }.run();
  }

  public void move(String from, String to, Completion completion) {
    rename(from, to, completion);
  }

  public void rename(String from, String to, Completion completion) {
    final Path source = Paths.get(from);
    final Path target = Paths.get(to);
    new BackgroundTask(completion) {
      public Object execute() throws Exception {
        try {
          Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (FileAlreadyExistsException e) {
          throw new FileSystemException("Failed to move between " + source + " and " + target + ". Target already exists");
        } catch (AtomicMoveNotSupportedException e) {
          throw new FileSystemException("Atomic move not supported between " + source + " and " + target);
        }
        return null;
      }
    }.run();
  }

  public void truncate(final String path, final int len, Completion completion) {
    new BackgroundTask(completion) {
      public Object execute() throws Exception {
        if (len < 0) {
          throw new FileSystemException("Cannot truncate file to size < 0");
        }
        RandomAccessFile raf = null;
        try {
          raf = new RandomAccessFile(path, "rw");
          raf.getChannel().truncate(len);
        } catch (FileNotFoundException e) {
          throw new FileSystemException("Cannot open file " + path + ". Either it doesn't exist, is a directory or you don't have permission to change it");
        } finally {
          if (raf != null) raf.close();
        }
        return null;
      }
    }.run();
  }

  public void chmod(String path, String mode, Completion completion) {
    chmod(path, mode, false, completion);
  }

  public void chmod(String path, String mode, final boolean recursive, Completion completion) {
    final Path target = Paths.get(path);
    final Set<PosixFilePermission> permissions = new HashSet<PosixFilePermission>();
    //TODO interpret mode and set permissions appropriately
    new BackgroundTask(completion) {
      public Object execute() throws Exception {
        if (recursive) {
          Files.walkFileTree(target, new SimpleFileVisitor<Path>() {
           public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
             Files.setPosixFilePermissions(target, permissions);
             return FileVisitResult.CONTINUE;
           }
         });
        } else {
          try {
            Files.setPosixFilePermissions(target, permissions);
          } catch (SecurityException e) {
            throw new FileSystemException("Accessed denied for chmod on " + target);
          }
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
    new BackgroundTaskWithResult(completion) {
      public FileStats execute() throws Exception {
        BasicFileAttributes attrs;
        if (followLinks) {
          attrs = Files.readAttributes(target, BasicFileAttributes.class);
        } else {
          attrs = Files.readAttributes(target, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        }
        return new FileStats(attrs);
      }
    }.run();
  }

  public void fstat(int fd, StatsHandler handler) {
    //TODO - need fd map
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
    new BackgroundTaskWithResult(completion) {
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

  public void mkdir(String path, int mode, final boolean createParents, Completion completion) {
    final Path source = Paths.get(path);
    new BackgroundTask(completion) {
      public Object execute() throws Exception {
        //TODO attributes
        FileAttribute<?> attrs = null;
        try {
          if (createParents) {
            Files.createDirectories(source, attrs);
          } else {
            Files.createDirectory(source, attrs);
          }
        } catch (FileAlreadyExistsException e) {
          throw new FileSystemException("Cannot create directory: " + source + ". It already exists");
        }
        return null;
      }
    }.run();
  }

  public void readDir(String path, Runnable onCompletion) {

  }


  // Close and open

  public void close(int fd, Runnable onCompletion) {

  }

  public void open(String path, int flags, int mode, OpenHandler handler) {

  }

  // Random access

  public void write(int fd, Buffer buffer, int offset, int length, int position, Runnable onCompletion) {

  }

  public void read(int fd, Buffer buffer, int offset, int length, int position, Runnable onCompletion) {

  }

  // Read and write entire files (data will arrive in chunks)

  public void readFile(String path, String encoding, DataHandler handler) {

  }

  public void readFile(String path, DataHandler dataHandler) {
    //For now we just fake this
    try {
      File f = new File(path);
      byte[] bytes = new byte[(int) f.length()];
      FileInputStream fis = new FileInputStream(f);
      fis.read(bytes);
      fis.close();
      Buffer buff = Buffer.newWrapped(bytes);
      dataHandler.onData(buff);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public void writeFile(String path, String data, String encoding, Runnable onCompletion) {

  }

  public void writeFile(String path, Buffer data, Runnable onCompletion) {

  }

  public void watchFile() {

  }

  public void unwatchFile() {

  }

  public ReadStream createReadStream() {
    return null;
  }

  public WriteStream createWriteStream() {
    return null;
  }

    //Create an empty file
  public void createFile(String path) {

  }

  //Will be deleted on process exit
  public void createTempFile(String path) {

  }

  public boolean exists(String path) {
    return false;
  }

  public FileSystemStats getFSStats() {
    return null;
  }




}
