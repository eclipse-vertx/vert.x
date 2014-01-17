/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package org.vertx.java.core.file.impl;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.file.*;
import org.vertx.java.core.file.FileSystem;
import org.vertx.java.core.file.FileSystemException;
import org.vertx.java.core.impl.BlockingAction;
import org.vertx.java.core.impl.DefaultContext;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.*;
import java.nio.file.attribute.*;
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

  public FileSystem copy(String from, String to, Handler<AsyncResult<Void>> handler) {
    copyInternal(from, to, handler).run();
    return this;
  }

  public FileSystem copySync(String from, String to) {
    copyInternal(from, to, null).action();
    return this;
  }

  public FileSystem copy(String from, String to, boolean recursive, Handler<AsyncResult<Void>> handler) {
    copyInternal(from, to, recursive, handler).run();
    return this;
  }

  public FileSystem copySync(String from, String to, boolean recursive) {
    copyInternal(from, to, recursive, null).action();
    return this;
  }

  public FileSystem move(String from, String to, Handler<AsyncResult<Void>> handler) {
    moveInternal(from, to, handler).run();
    return this;
  }

  public FileSystem moveSync(String from, String to) {
    moveInternal(from, to, null).action();
    return this;
  }

  public FileSystem truncate(String path, long len, Handler<AsyncResult<Void>> handler) {
    truncateInternal(path, len, handler).run();
    return this;
  }

  public FileSystem truncateSync(String path, long len) {
    truncateInternal(path, len, null).action();
    return this;
  }

  public FileSystem chmod(String path, String perms, Handler<AsyncResult<Void>> handler) {
    chmodInternal(path, perms, handler).run();
    return this;
  }

  public FileSystem chmodSync(String path, String perms) {
    chmodInternal(path, perms, null).action();
    return this;
  }

  public FileSystem chmod(String path, String perms, String dirPerms, Handler<AsyncResult<Void>> handler) {
    chmodInternal(path, perms, dirPerms, handler).run();
    return this;
  }

  public FileSystem chmodSync(String path, String perms, String dirPerms) {
    chmodInternal(path, perms, dirPerms, null).action();
    return this;
  }

  public FileSystem chown(String path, String user, String group, Handler<AsyncResult<Void>> handler) {
    chownInternal(path, user, group, handler).run();
    return this;
  }

  public FileSystem chownSync(String path, String user, String group) {
    chownInternal(path, user, group, null).action();
    return this;
  }

  public FileSystem props(String path, Handler<AsyncResult<FileProps>> handler) {
    propsInternal(path, handler).run();
    return this;
  }

  public FileProps propsSync(String path) {
    return propsInternal(path, null).action();
  }

  public FileSystem lprops(String path, Handler<AsyncResult<FileProps>> handler) {
    lpropsInternal(path, handler).run();
    return this;
  }

  public FileProps lpropsSync(String path) {
    return lpropsInternal(path, null).action();
  }

  public FileSystem link(String link, String existing, Handler<AsyncResult<Void>> handler) {
    linkInternal(link, existing, handler).run();
    return this;
  }

  public FileSystem linkSync(String link, String existing) {
    linkInternal(link, existing, null).action();
    return this;
  }

  public FileSystem symlink(String link, String existing, Handler<AsyncResult<Void>> handler) {
    symlinkInternal(link, existing, handler).run();
    return this;
  }

  public FileSystem symlinkSync(String link, String existing) {
    symlinkInternal(link, existing, null).action();
    return this;
  }

  public FileSystem unlink(String link, Handler<AsyncResult<Void>> handler) {
    unlinkInternal(link, handler).run();
    return this;
  }

  public FileSystem unlinkSync(String link) {
    unlinkInternal(link, null).action();
    return this;
  }

  public FileSystem readSymlink(String link, Handler<AsyncResult<String>> handler) {
    readSymlinkInternal(link, handler).run();
    return this;
  }

  public String readSymlinkSync(String link) {
    return readSymlinkInternal(link, null).action();
  }

  public FileSystem delete(String path, Handler<AsyncResult<Void>> handler) {
    deleteInternal(path, handler).run();
    return this;
  }

  public FileSystem deleteSync(String path) {
    deleteInternal(path, null).action();
    return this;
  }

  public FileSystem delete(String path, boolean recursive, Handler<AsyncResult<Void>> handler) {
    deleteInternal(path, recursive, handler).run();
    return this;
  }

  public FileSystem deleteSync(String path, boolean recursive) {
    deleteInternal(path, recursive, null).action();
    return this;
  }

  public FileSystem mkdir(String path, Handler<AsyncResult<Void>> handler) {
    mkdirInternal(path, handler).run();
    return this;
  }

  public FileSystem mkdirSync(String path) {
    mkdirInternal(path, null).action();
    return this;
  }

  public FileSystem mkdir(String path, boolean createParents, Handler<AsyncResult<Void>> handler) {
    mkdirInternal(path, createParents, handler).run();
    return this;
  }

  public FileSystem mkdirSync(String path, boolean createParents) {
    mkdirInternal(path, createParents, null).action();
    return this;
  }

  public FileSystem mkdir(String path, String perms, Handler<AsyncResult<Void>> handler) {
    mkdirInternal(path, perms, handler).run();
    return this;
  }

  public FileSystem mkdirSync(String path, String perms) {
    mkdirInternal(path, perms, null).action();
    return this;
  }

  public FileSystem mkdir(String path, String perms, boolean createParents, Handler<AsyncResult<Void>> handler) {
    mkdirInternal(path, perms, createParents, handler).run();
    return this;
  }

  public FileSystem mkdirSync(String path, String perms, boolean createParents) {
    mkdirInternal(path, perms, createParents, null).action();
    return this;
  }

  public FileSystem readDir(String path, Handler<AsyncResult<String[]>> handler) {
    readDirInternal(path, handler).run();
    return this;
  }

  public String[] readDirSync(String path) {
    return readDirInternal(path, null).action();
  }

  public FileSystem readDir(String path, String filter, Handler<AsyncResult<String[]>> handler) {
    readDirInternal(path, filter, handler).run();
    return this;
  }

  public String[] readDirSync(String path, String filter) {
    return readDirInternal(path, filter, null).action();
  }

  public FileSystem readFile(String path, Handler<AsyncResult<Buffer>> handler) {
    readFileInternal(path, handler).run();
    return this;
  }

  public Buffer readFileSync(String path) {
    return readFileInternal(path, null).action();
  }

  public FileSystem writeFile(String path, Buffer data, Handler<AsyncResult<Void>> handler) {
    writeFileInternal(path, data, handler).run();
    return this;
  }

  public FileSystem writeFileSync(String path, Buffer data) {
    writeFileInternal(path, data, null).action();
    return this;
  }

  public FileSystem open(String path, Handler<AsyncResult<AsyncFile>> handler) {
    openInternal(path, handler).run();
    return this;
  }

  public AsyncFile openSync(String path) {
    return openInternal(path, null).action();
  }

  public FileSystem open(String path, String perms, Handler<AsyncResult<AsyncFile>> handler) {
    openInternal(path, perms, handler).run();
    return this;
  }

  public AsyncFile openSync(String path, String perms) {
    return openInternal(path, perms, null).action();
  }

  public FileSystem open(String path, String perms, boolean createNew, Handler<AsyncResult<AsyncFile>> handler) {
    openInternal(path, perms, createNew, handler).run();
    return this;
  }

  public AsyncFile openSync(String path, String perms, boolean createNew) {
    return openInternal(path, perms, createNew, null).action();
  }

  public FileSystem open(String path, String perms, boolean read, boolean write, boolean createNew, Handler<AsyncResult<AsyncFile>> handler) {
    openInternal(path, perms, read, write, createNew, handler).run();
    return this;
  }

  public AsyncFile openSync(String path, String perms, boolean read, boolean write, boolean createNew) {
    return openInternal(path, perms, read, write, createNew, null).action();
  }

  public FileSystem open(String path, String perms, boolean read, boolean write, boolean createNew,
                         boolean flush, Handler<AsyncResult<AsyncFile>> handler) {
    openInternal(path, perms, read, write, createNew, flush, handler).run();
    return this;
  }

  public AsyncFile openSync(String path, String perms, boolean read, boolean write, boolean createNew, boolean flush) {
    return openInternal(path, perms, read, write, createNew, flush, null).action();
  }

  public FileSystem createFile(String path, Handler<AsyncResult<Void>> handler) {
    createFileInternal(path, handler).run();
    return this;
  }

  public FileSystem createFileSync(String path) {
    createFileInternal(path, null).action();
    return this;
  }

  public FileSystem createFile(String path, String perms, Handler<AsyncResult<Void>> handler) {
    createFileInternal(path, perms, handler).run();
    return this;
  }

  public FileSystem createFileSync(String path, String perms) {
    createFileInternal(path, perms, null).action();
    return this;
  }

  public FileSystem exists(String path, Handler<AsyncResult<Boolean>> handler) {
    existsInternal(path, handler).run();
    return this;
  }

  public boolean existsSync(String path) {
    return existsInternal(path, null).action();
  }

  public FileSystem fsProps(String path, Handler<AsyncResult<FileSystemProps>> handler) {
    fsPropsInternal(path, handler).run();
    return this;
  }

  public FileSystemProps fsPropsSync(String path) {
    return fsPropsInternal(path, null).action();
  }

  private BlockingAction<Void> copyInternal(String from, String to, Handler<AsyncResult<Void>> handler) {
    return copyInternal(from, to, false, handler);
  }

  private BlockingAction<Void> copyInternal(String from, String to, final boolean recursive, Handler<AsyncResult<Void>> handler) {
    final Path source = PathAdjuster.adjust(vertx, Paths.get(from));
    final Path target = PathAdjuster.adjust(vertx, Paths.get(to));
    return new BlockingAction<Void>(vertx, handler) {
      public Void action() {
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
          throw new FileSystemException(e);
        }
        return null;
      }
    };
  }

  private BlockingAction<Void> moveInternal(String from, String to, Handler<AsyncResult<Void>> handler) {
    final Path source = PathAdjuster.adjust(vertx, Paths.get(from));
    final Path target = PathAdjuster.adjust(vertx, Paths.get(to));
    return new BlockingAction<Void>(vertx, handler) {
      public Void action() {
        try {
          Files.move(source, target);
        } catch (IOException e) {
          throw new FileSystemException(e);
        }
        return null;
      }
    };
  }

  private BlockingAction<Void> truncateInternal(String p, final long len, Handler<AsyncResult<Void>> handler) {
    final String path = PathAdjuster.adjust(vertx, p);
    return new BlockingAction<Void>(vertx, handler) {
      public Void action() {
        if (len < 0) {
          throw new FileSystemException("Cannot truncate file to size < 0");
        }
        if (!Files.exists(Paths.get(path))) {
          throw new FileSystemException("Cannot truncate file " + path + ". Does not exist");
        }
        RandomAccessFile raf = null;
        try {
          try {
            raf = new RandomAccessFile(path, "rw");
            raf.setLength(len);
          } finally {
            if (raf != null) raf.close();
          }
        } catch (IOException e) {
          throw new FileSystemException(e);
        }
        return null;
      }
    };
  }

  private BlockingAction<Void> chmodInternal(String path, String perms, Handler<AsyncResult<Void>> handler) {
    return chmodInternal(path, perms, null, handler);
  }

  protected BlockingAction<Void> chmodInternal(String path, String perms, String dirPerms, Handler<AsyncResult<Void>> handler) {
    final Path target = PathAdjuster.adjust(vertx, Paths.get(path));
    final Set<PosixFilePermission> permissions = PosixFilePermissions.fromString(perms);
    final Set<PosixFilePermission> dirPermissions = dirPerms == null ? null : PosixFilePermissions.fromString(dirPerms);
    return new BlockingAction<Void>(vertx, handler) {
      public Void action() {
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
        } catch (IOException e) {
          throw new FileSystemException(e);
        }
        return null;
      }
    };
  }

  protected BlockingAction<Void> chownInternal(String path, final String user, final String group, Handler<AsyncResult<Void>> handler) {
    final Path target = PathAdjuster.adjust(vertx, Paths.get(path));
    final UserPrincipalLookupService service = target.getFileSystem().getUserPrincipalLookupService();
    return new BlockingAction<Void>(vertx, handler) {
      public Void action() {

        try {
          final UserPrincipal userPrincipal = user == null ? null : service.lookupPrincipalByName(user);
          final GroupPrincipal groupPrincipal = group == null ? null : service.lookupPrincipalByGroupName(group);
          if (groupPrincipal != null) {
            PosixFileAttributeView view = Files.getFileAttributeView(target, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
            if (view == null) {
              throw new FileSystemException("Change group of file not supported");
            }
            view.setGroup(groupPrincipal);

          }
          if (userPrincipal != null) {
            Files.setOwner(target, userPrincipal);
          }
        } catch (SecurityException e) {
          throw new FileSystemException("Accessed denied for chown on " + target);
        } catch (IOException e) {
          throw new FileSystemException(e);
        }
        return null;
      }
    };
  }

  private BlockingAction<FileProps> propsInternal(String path, Handler<AsyncResult<FileProps>> handler) {
    return props(path, true, handler);
  }

  private BlockingAction<FileProps> lpropsInternal(String path, Handler<AsyncResult<FileProps>> handler) {
    return props(path, false, handler);
  }

  private BlockingAction<FileProps> props(String path, final boolean followLinks, Handler<AsyncResult<FileProps>> handler) {
    final Path target = PathAdjuster.adjust(vertx, Paths.get(path));
    return new BlockingAction<FileProps>(vertx, handler) {
      public FileProps action() {
        try {
          BasicFileAttributes attrs;
          if (followLinks) {
            attrs = Files.readAttributes(target, BasicFileAttributes.class);
          } else {
            attrs = Files.readAttributes(target, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
          }
          return new DefaultFileProps(attrs);
        } catch (IOException e) {
          throw new FileSystemException(e);
        }
      }
    };
  }

  private BlockingAction<Void> linkInternal(String link, String existing, Handler<AsyncResult<Void>> handler) {
    return link(link, existing, false, handler);
  }

  private BlockingAction<Void> symlinkInternal(String link, String existing, Handler<AsyncResult<Void>> handler) {
    return link(link, existing, true, handler);
  }

  private BlockingAction<Void> link(String link, String existing, final boolean symbolic, Handler<AsyncResult<Void>> handler) {
    final Path source = PathAdjuster.adjust(vertx, Paths.get(link));
    final Path target = PathAdjuster.adjust(vertx, Paths.get(existing));
    return new BlockingAction<Void>(vertx, handler) {
      public Void action() {
        try {
          if (symbolic) {
            Files.createSymbolicLink(source, target);
          } else {
            Files.createLink(source, target);
          }
        } catch (IOException e) {
          throw new FileSystemException(e);
        }
        return null;
      }
    };
  }

  private BlockingAction<Void> unlinkInternal(String link, Handler<AsyncResult<Void>> handler) {
    return deleteInternal(link, handler);
  }

  private BlockingAction<String> readSymlinkInternal(String link, Handler<AsyncResult<String>> handler) {
    final Path source = PathAdjuster.adjust(vertx, Paths.get(link));
    return new BlockingAction<String>(vertx, handler) {
      public String action() {
        try {
          return Files.readSymbolicLink(source).toString();
        } catch (IOException e) {
          throw new FileSystemException(e);
        }
      }
    };
  }

  private BlockingAction<Void> deleteInternal(String path, Handler<AsyncResult<Void>> handler) {
    return deleteInternal(path, false, handler);
  }

  private BlockingAction<Void> deleteInternal(String path, final boolean recursive, Handler<AsyncResult<Void>> handler) {
    final Path source = PathAdjuster.adjust(vertx, Paths.get(path));
    return new BlockingAction<Void>(vertx, handler) {
      public Void action() {
        try {
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
            Files.delete(source);
          }
        } catch (IOException e) {
          throw new FileSystemException(e);
        }
        return null;
      }
    };
  }

  private BlockingAction<Void> mkdirInternal(String path, Handler<AsyncResult<Void>> handler) {
    return mkdirInternal(path, null, false, handler);
  }

  private BlockingAction<Void> mkdirInternal(String path, boolean createParents, Handler<AsyncResult<Void>> handler) {
    return mkdirInternal(path, null, createParents, handler);
  }

  private BlockingAction<Void> mkdirInternal(String path, String perms, Handler<AsyncResult<Void>> handler) {
    return mkdirInternal(path, perms, false, handler);
  }

  protected BlockingAction<Void> mkdirInternal(String path, final String perms, final boolean createParents, Handler<AsyncResult<Void>> handler) {
    final Path source = PathAdjuster.adjust(vertx, Paths.get(path));
    final FileAttribute<?> attrs = perms == null ? null : PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(perms));
    return new BlockingAction<Void>(vertx, handler) {
      public Void action() {
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
        } catch (IOException e) {
          throw new FileSystemException(e);
        }
        return null;
      }
    };
  }

  private BlockingAction<String[]> readDirInternal(String path, Handler<AsyncResult<String[]>> handler) {
    return readDirInternal(path, null, handler);
  }

  private BlockingAction<String[]> readDirInternal(String p, final String filter, Handler<AsyncResult<String[]>> handler) {
    final String path = PathAdjuster.adjust(vertx, p);
    return new BlockingAction<String[]>(vertx, handler) {
      public String[] action() {
        try {
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
        } catch (IOException e) {
          throw new FileSystemException(e);
        }
      }
    };
  }

  private BlockingAction<Buffer> readFileInternal(String path, Handler<AsyncResult<Buffer>> handler) {
    final Path target = PathAdjuster.adjust(vertx, Paths.get(path));
    return new BlockingAction<Buffer>(vertx, handler) {
      public Buffer action() {
        try {
          byte[] bytes = Files.readAllBytes(target);
          Buffer buff = new Buffer(bytes);
          return buff;
        } catch (IOException e) {
          throw new FileSystemException(e);
        }
      }
    };
  }

  private BlockingAction<Void> writeFileInternal(String path, final Buffer data, Handler<AsyncResult<Void>> handler) {
    final Path target = PathAdjuster.adjust(vertx, Paths.get(path));
    return new BlockingAction<Void>(vertx, handler) {
      public Void action() {
        try {
          Files.write(target, data.getBytes());
          return null;
        } catch (IOException e) {
          throw new FileSystemException(e);
        }
      }
    };
  }

  private BlockingAction<AsyncFile> openInternal(String path, Handler<AsyncResult<AsyncFile>> handler) {
    return openInternal(path, null, true, true, true, false, handler);
  }

  private BlockingAction<AsyncFile> openInternal(String path, String perms, Handler<AsyncResult<AsyncFile>> handler) {
    return openInternal(path, perms, true, true, true, false, handler);
  }

  private BlockingAction<AsyncFile> openInternal(String path, String perms, boolean createNew, Handler<AsyncResult<AsyncFile>> handler) {
    return openInternal(path, perms, true, true, createNew, false, handler);
  }

  private BlockingAction<AsyncFile> openInternal(String path, String perms, boolean read, boolean write, boolean createNew, Handler<AsyncResult<AsyncFile>> handler) {
    return openInternal(path, perms, read, write, createNew, false, handler);
  }

  private BlockingAction<AsyncFile> openInternal(String p, final String perms, final boolean read, final boolean write, final boolean createNew,
                                                 final boolean flush, Handler<AsyncResult<AsyncFile>> handler) {
    final String path = PathAdjuster.adjust(vertx, p);
    return new BlockingAction<AsyncFile>(vertx, handler) {
      public AsyncFile action() {
        return doOpen(path, perms, read, write, createNew, flush, context);
      }
    };
  }

  protected AsyncFile doOpen(String path, String perms, boolean read, boolean write, boolean createNew,
                             boolean flush, DefaultContext context) {
    return new DefaultAsyncFile(vertx, path, perms, read, write, createNew, flush, context);
  }

  private BlockingAction<Void> createFileInternal(String path, Handler<AsyncResult<Void>> handler) {
    return createFileInternal(path, null, handler);
  }

  protected BlockingAction<Void> createFileInternal(String p, final String perms, Handler<AsyncResult<Void>> handler) {
    final String path = PathAdjuster.adjust(vertx, p);
    final FileAttribute<?> attrs = perms == null ? null : PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(perms));
    return new BlockingAction<Void>(vertx, handler) {
      public Void action() {
        try {
          Path target = Paths.get(path);
          if (attrs != null) {
            Files.createFile(target, attrs);
          } else {
            Files.createFile(target);
          }
        } catch (IOException e) {
          throw new FileSystemException(e);
        }
        return null;
      }
    };
  }

  private BlockingAction<Boolean> existsInternal(String path, Handler<AsyncResult<Boolean>> handler) {
    final File file = new File(PathAdjuster.adjust(vertx, path));
    return new BlockingAction<Boolean>(vertx, handler) {
      public Boolean action() {
        return file.exists();
      }
    };
  }

  private BlockingAction<FileSystemProps> fsPropsInternal(String path, Handler<AsyncResult<FileSystemProps>> handler) {
    final Path target = PathAdjuster.adjust(vertx, Paths.get(path));
    return new BlockingAction<FileSystemProps>(vertx, handler) {
      public FileSystemProps action() {
        try {
          FileStore fs = Files.getFileStore(target);
          return new DefaultFileSystemProps(fs.getTotalSpace(), fs.getUnallocatedSpace(), fs.getUsableSpace());
        } catch (IOException e) {
          throw new FileSystemException(e);
        }
      }
    };
  }
}
