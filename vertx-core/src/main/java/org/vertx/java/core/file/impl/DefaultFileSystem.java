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

package org.vertx.java.core.file.impl;

import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.file.*;
import org.vertx.java.core.file.FileSystem;
import org.vertx.java.core.file.FileSystemException;
import org.vertx.java.core.impl.BlockingAction;
import org.vertx.java.core.impl.Context;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DefaultFileSystem implements FileSystem {

  @SuppressWarnings("unused")
	private static final Logger log = LoggerFactory.getLogger(DefaultFileSystem.class);

  protected final VertxInternal vertx;

  public DefaultFileSystem(VertxInternal vertx) {
    this.vertx = vertx;
  }

  public void copy(String from, String to, AsyncResultHandler<Void> handler) {
    copyInternal(from, to, handler).run();
  }

  public void copySync(String from, String to) throws Exception {
    copyInternal(from, to, null).action();
  }

  public void copy(String from, String to, boolean recursive, AsyncResultHandler<Void> handler) {
    copyInternal(from, to, recursive, handler).run();
  }

  public void copySync(String from, String to, boolean recursive) throws Exception {
    copyInternal(from, to, recursive, null).action();
  }

  public void move(String from, String to, AsyncResultHandler<Void> handler) {
    moveInternal(from, to, handler).run();
  }

  public void moveSync(String from, String to) throws Exception {
    moveInternal(from, to, null).action();
  }

  public void truncate(String path, long len, AsyncResultHandler<Void> handler) {
    truncateInternal(path, len, handler).run();
  }

  public void truncateSync(String path, long len) throws Exception {
    truncateInternal(path, len, null).action();
  }

  public void chmod(String path, String perms, AsyncResultHandler<Void> handler) {
    chmodInternal(path, perms, handler).run();
  }

  public void chmodSync(String path, String perms) throws Exception {
    chmodInternal(path, perms, null).action();
  }

  public void chmod(String path, String perms, String dirPerms, AsyncResultHandler<Void> handler) {
    chmodInternal(path, perms, dirPerms, handler).run();
  }

  public void chmodSync(String path, String perms, String dirPerms) throws Exception {
    chmodInternal(path, perms, dirPerms, null).action();
  }

  public void props(String path, AsyncResultHandler<FileProps> handler) {
    propsInternal(path, handler).run();
  }

  public FileProps propsSync(String path) throws Exception {
    return propsInternal(path, null).action();
  }

  public void lprops(String path, AsyncResultHandler<FileProps> handler) {
    lpropsInternal(path, handler).run();
  }

  public FileProps lpropsSync(String path) throws Exception {
    return lpropsInternal(path, null).action();
  }

  public void link(String link, String existing, AsyncResultHandler<Void> handler) {
    linkInternal(link, existing, handler).run();
  }

  public void linkSync(String link, String existing) throws Exception {
    linkInternal(link, existing, null).action();
  }

  public void symlink(String link, String existing, AsyncResultHandler<Void> handler) {
    symlinkInternal(link, existing, handler).run();
  }

  public void symlinkSync(String link, String existing) throws Exception {
    symlinkInternal(link, existing, null).action();
  }

  public void unlink(String link, AsyncResultHandler<Void> handler) {
    unlinkInternal(link, handler).run();
  }

  public void unlinkSync(String link) throws Exception {
    unlinkInternal(link, null).action();
  }

  public void readSymlink(String link, AsyncResultHandler<String> handler) {
    readSymlinkInternal(link, handler).run();
  }

  public String readSymlinkSync(String link) throws Exception {
    return readSymlinkInternal(link, null).action();
  }

  public void delete(String path, AsyncResultHandler<Void> handler) {
    deleteInternal(path, handler).run();
  }

  public void deleteSync(String path) throws Exception {
    deleteInternal(path, null).action();
  }

  public void delete(String path, boolean recursive, AsyncResultHandler<Void> handler) {
    deleteInternal(path, recursive, handler).run();
  }

  public void deleteSync(String path, boolean recursive) throws Exception {
    deleteInternal(path, recursive, null).action();
  }

  public void mkdir(String path, AsyncResultHandler<Void> handler) {
    mkdirInternal(path, handler).run();
  }

  public void mkdirSync(String path) throws Exception {
    mkdirInternal(path, null).action();
  }

  public void mkdir(String path, boolean createParents, AsyncResultHandler<Void> handler) {
    mkdirInternal(path, createParents, handler).run();
  }

  public void mkdirSync(String path, boolean createParents) throws Exception {
    mkdirInternal(path, createParents, null).action();
  }

  public void mkdir(String path, String perms, AsyncResultHandler<Void> handler) {
    mkdirInternal(path, perms, handler).run();
  }

  public void mkdirSync(String path, String perms) throws Exception {
    mkdirInternal(path, perms, null).action();
  }

  public void mkdir(String path, String perms, boolean createParents, AsyncResultHandler<Void> handler) {
    mkdirInternal(path, perms, createParents, handler).run();
  }

  public void mkdirSync(String path, String perms, boolean createParents) throws Exception {
    mkdirInternal(path, perms, createParents, null).action();
  }

  public void readDir(String path, AsyncResultHandler<String[]> handler) {
    readDirInternal(path, handler).run();
  }

  public String[] readDirSync(String path) throws Exception {
    return readDirInternal(path, null).action();
  }

  public void readDir(String path, String filter, AsyncResultHandler<String[]> handler) {
    readDirInternal(path, filter, handler).run();
  }

  public String[] readDirSync(String path, String filter) throws Exception {
    return readDirInternal(path, filter, null).action();
  }

  public void readFile(String path, AsyncResultHandler<Buffer> handler) {
    readFileInternal(path, handler).run();
  }

  public Buffer readFileSync(String path) throws Exception {
    return readFileInternal(path, null).action();
  }

  public void writeFile(String path, Buffer data, AsyncResultHandler<Void> handler) {
    writeFileInternal(path, data, handler).run();
  }

  public void writeFileSync(String path, Buffer data) throws Exception {
    writeFileInternal(path, data, null).action();
  }

  public void open(String path, AsyncResultHandler<AsyncFile> handler) {
    openInternal(path, handler).run();
  }

  public AsyncFile openSync(String path) throws Exception {
    return openInternal(path, null).action();
  }

  public void open(String path, String perms, AsyncResultHandler<AsyncFile> handler) {
    openInternal(path, perms, handler).run();
  }

  public AsyncFile openSync(String path, String perms) throws Exception {
    return openInternal(path, perms, null).action();
  }

  public void open(String path, String perms, boolean createNew, AsyncResultHandler<AsyncFile> handler) {
    openInternal(path, perms, createNew, handler).run();
  }

  public AsyncFile openSync(String path, String perms, boolean createNew) throws Exception {
    return openInternal(path, perms, createNew, null).action();
  }

  public void open(String path, String perms, boolean read, boolean write, boolean createNew, AsyncResultHandler<AsyncFile> handler) {
    openInternal(path, perms, read, write, createNew, handler).run();
  }

  public AsyncFile openSync(String path, String perms, boolean read, boolean write, boolean createNew) throws Exception {
    return openInternal(path, perms, read, write, createNew, null).action();
  }

  public void open(String path, String perms, boolean read, boolean write, boolean createNew,
                   boolean flush, AsyncResultHandler<AsyncFile> handler) {
    openInternal(path, perms, read, write, createNew, flush, handler).run();
  }

  public AsyncFile openSync(String path, String perms, boolean read, boolean write, boolean createNew, boolean flush) throws Exception {
    return openInternal(path, perms, read, write, createNew, flush, null).action();
  }

  public void createFile(String path, AsyncResultHandler<Void> handler) {
    createFileInternal(path, handler).run();
  }

  public void createFileSync(String path) throws Exception {
    createFileInternal(path, null).action();
  }

  public void createFile(String path, String perms, AsyncResultHandler<Void> handler) {
    createFileInternal(path, perms, handler).run();
  }

  public void createFileSync(String path, String perms) throws Exception {
    createFileInternal(path, perms, null).action();
  }

  public void exists(String path, AsyncResultHandler<Boolean> handler) {
    existsInternal(path, handler).run();
  }

  public boolean existsSync(String path) throws Exception {
    return existsInternal(path, null).action();
  }

  public void fsProps(String path, AsyncResultHandler<FileSystemProps> handler) {
    fsPropsInternal(path, handler).run();
  }

  public FileSystemProps fsPropsSync(String path) throws Exception {
    return fsPropsInternal(path, null).action();
  }

  private BlockingAction<Void> copyInternal(String from, String to, AsyncResultHandler<Void> handler) {
    return copyInternal(from, to, false, handler);
  }

  private BlockingAction<Void> copyInternal(String from, String to, final boolean recursive, AsyncResultHandler<Void> handler) {
    final Path source = PathAdjuster.adjust(vertx, Paths.get(from));
    final Path target = PathAdjuster.adjust(vertx, Paths.get(to));
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
    final Path source = PathAdjuster.adjust(vertx, Paths.get(from));
    final Path target = PathAdjuster.adjust(vertx, Paths.get(to));
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

  private BlockingAction<Void> truncateInternal(String p, final long len, AsyncResultHandler<Void> handler) {
     final String path = PathAdjuster.adjust(vertx, p);
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

  protected BlockingAction<Void> chmodInternal(String path, String perms, String dirPerms, AsyncResultHandler<Void> handler) {
    final Path target = PathAdjuster.adjust(vertx, Paths.get(path));
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
    final Path target = PathAdjuster.adjust(vertx, Paths.get(path));
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
    final Path source = PathAdjuster.adjust(vertx, Paths.get(link));
    final Path target = PathAdjuster.adjust(vertx, Paths.get(existing));
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
    final Path source = PathAdjuster.adjust(vertx, Paths.get(link));
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
    final Path source = PathAdjuster.adjust(vertx, Paths.get(path));
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

  protected BlockingAction<Void> mkdirInternal(String path, final String perms, final boolean createParents, AsyncResultHandler<Void> handler) {
    final Path source = PathAdjuster.adjust(vertx, Paths.get(path));
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

  private BlockingAction<String[]> readDirInternal(String p, final String filter, AsyncResultHandler<String[]> handler) {
    final String path = PathAdjuster.adjust(vertx, p);
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

  private BlockingAction<Buffer> readFileInternal(String path, AsyncResultHandler<Buffer> handler) {
    final Path target = PathAdjuster.adjust(vertx, Paths.get(path));
    return new BlockingAction<Buffer>(vertx, handler) {
      public Buffer action() throws Exception {
        byte[] bytes = Files.readAllBytes(target);
        Buffer buff = new Buffer(bytes);
        return buff;
      }
    };
  }

  private BlockingAction<Void> writeFileInternal(String path, final Buffer data, AsyncResultHandler<Void> handler) {
    final Path target = PathAdjuster.adjust(vertx, Paths.get(path));
    return new BlockingAction<Void>(vertx, handler) {
      public Void action() throws Exception {
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

  private BlockingAction<AsyncFile> openInternal(String p, final String perms, final boolean read, final boolean write, final boolean createNew,
                   final boolean flush, AsyncResultHandler<AsyncFile> handler) {
    final String path = PathAdjuster.adjust(vertx, p);
    return new BlockingAction<AsyncFile>(vertx, handler) {
      public AsyncFile action() throws Exception {
        return doOpen(path, perms, read, write, createNew, flush, context);
      }
    };
  }

  protected AsyncFile doOpen(String path, String perms, boolean read, boolean write, boolean createNew,
                           boolean flush, Context context) throws Exception {
    return new DefaultAsyncFile(vertx, path, perms, read, write, createNew, flush, context);
  }

  private BlockingAction<Void> createFileInternal(String path, AsyncResultHandler<Void> handler) {
    return createFileInternal(path, null, handler);
  }

  protected BlockingAction<Void> createFileInternal(String p, final String perms, AsyncResultHandler<Void> handler) {
    final String path = PathAdjuster.adjust(vertx, p);
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

  private BlockingAction<Boolean> existsInternal(String path, AsyncResultHandler<Boolean> handler) {
    final File file = new File(PathAdjuster.adjust(vertx, path));
    return new BlockingAction<Boolean>(vertx, handler) {
      public Boolean action() throws Exception {
        return file.exists();
      }
    };
  }

  private BlockingAction<FileSystemProps> fsPropsInternal(String path, AsyncResultHandler<FileSystemProps> handler) {
    final Path target = PathAdjuster.adjust(vertx, Paths.get(path));
    return new BlockingAction<FileSystemProps>(vertx, handler) {
      public FileSystemProps action() throws Exception {
        FileStore fs = Files.getFileStore(target);
        return new FileSystemProps(fs.getTotalSpace(), fs.getUnallocatedSpace(), fs.getUsableSpace());
      }
    };
  }
}
