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

package io.vertx.core.file.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.FileProps;
import io.vertx.core.file.FileSystem;
import io.vertx.core.file.FileSystemException;
import io.vertx.core.file.FileSystemProps;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.Action;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileStore;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 *
 * This class is thread-safe
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class FileSystemImpl implements FileSystem {

  protected final VertxInternal vertx;

  public FileSystemImpl(VertxInternal vertx) {
    this.vertx = vertx;
  }

  public FileSystem copy(String from, String to, Handler<AsyncResult<Void>> handler) {
    copyInternal(from, to, handler).run();
    return this;
  }

  public FileSystem copyBlocking(String from, String to) {
    copyInternal(from, to, null).perform();
    return this;
  }

  public FileSystem copyRecursive(String from, String to, boolean recursive, Handler<AsyncResult<Void>> handler) {
    copyInternal(from, to, recursive, handler).run();
    return this;
  }

  public FileSystem copyRecursiveBlocking(String from, String to, boolean recursive) {
    copyInternal(from, to, recursive, null).perform();
    return this;
  }

  public FileSystem move(String from, String to, Handler<AsyncResult<Void>> handler) {
    moveInternal(from, to, handler).run();
    return this;
  }

  public FileSystem moveBlocking(String from, String to) {
    moveInternal(from, to, null).perform();
    return this;
  }

  public FileSystem truncate(String path, long len, Handler<AsyncResult<Void>> handler) {
    truncateInternal(path, len, handler).run();
    return this;
  }

  public FileSystem truncateBlocking(String path, long len) {
    truncateInternal(path, len, null).perform();
    return this;
  }

  public FileSystem chmod(String path, String perms, Handler<AsyncResult<Void>> handler) {
    chmodInternal(path, perms, handler).run();
    return this;
  }

  public FileSystem chmodBlocking(String path, String perms) {
    chmodInternal(path, perms, null).perform();
    return this;
  }

  public FileSystem chmodRecursive(String path, String perms, String dirPerms, Handler<AsyncResult<Void>> handler) {
    chmodInternal(path, perms, dirPerms, handler).run();
    return this;
  }

  public FileSystem chmodRecursiveBlocking(String path, String perms, String dirPerms) {
    chmodInternal(path, perms, dirPerms, null).perform();
    return this;
  }

  public FileSystem chown(String path, String user, String group, Handler<AsyncResult<Void>> handler) {
    chownInternal(path, user, group, handler).run();
    return this;
  }

  public FileSystem chownBlocking(String path, String user, String group) {
    chownInternal(path, user, group, null).perform();
    return this;
  }

  public FileSystem props(String path, Handler<AsyncResult<FileProps>> handler) {
    propsInternal(path, handler).run();
    return this;
  }

  public FileProps propsBlocking(String path) {
    return propsInternal(path, null).perform();
  }

  public FileSystem lprops(String path, Handler<AsyncResult<FileProps>> handler) {
    lpropsInternal(path, handler).run();
    return this;
  }

  public FileProps lpropsBlocking(String path) {
    return lpropsInternal(path, null).perform();
  }

  public FileSystem link(String link, String existing, Handler<AsyncResult<Void>> handler) {
    linkInternal(link, existing, handler).run();
    return this;
  }

  public FileSystem linkBlocking(String link, String existing) {
    linkInternal(link, existing, null).perform();
    return this;
  }

  public FileSystem symlink(String link, String existing, Handler<AsyncResult<Void>> handler) {
    symlinkInternal(link, existing, handler).run();
    return this;
  }

  public FileSystem symlinkBlocking(String link, String existing) {
    symlinkInternal(link, existing, null).perform();
    return this;
  }

  public FileSystem unlink(String link, Handler<AsyncResult<Void>> handler) {
    unlinkInternal(link, handler).run();
    return this;
  }

  public FileSystem unlinkBlocking(String link) {
    unlinkInternal(link, null).perform();
    return this;
  }

  public FileSystem readSymlink(String link, Handler<AsyncResult<String>> handler) {
    readSymlinkInternal(link, handler).run();
    return this;
  }

  public String readSymlinkBlocking(String link) {
    return readSymlinkInternal(link, null).perform();
  }

  public FileSystem delete(String path, Handler<AsyncResult<Void>> handler) {
    deleteInternal(path, handler).run();
    return this;
  }

  public FileSystem deleteBlocking(String path) {
    deleteInternal(path, null).perform();
    return this;
  }

  public FileSystem deleteRecursive(String path, boolean recursive, Handler<AsyncResult<Void>> handler) {
    deleteInternal(path, recursive, handler).run();
    return this;
  }

  public FileSystem deleteRecursiveBlocking(String path, boolean recursive) {
    deleteInternal(path, recursive, null).perform();
    return this;
  }

  public FileSystem mkdir(String path, Handler<AsyncResult<Void>> handler) {
    mkdirInternal(path, handler).run();
    return this;
  }

  public FileSystem mkdirBlocking(String path) {
    mkdirInternal(path, null).perform();
    return this;
  }

  public FileSystem mkdirs(String path, Handler<AsyncResult<Void>> handler) {
    mkdirInternal(path, true, handler).run();
    return this;
  }

  public FileSystem mkdirsBlocking(String path) {
    mkdirInternal(path, true, null).perform();
    return this;
  }

  public FileSystem mkdir(String path, String perms, Handler<AsyncResult<Void>> handler) {
    mkdirInternal(path, perms, handler).run();
    return this;
  }

  public FileSystem mkdirBlocking(String path, String perms) {
    mkdirInternal(path, perms, null).perform();
    return this;
  }

  public FileSystem mkdirs(String path, String perms, Handler<AsyncResult<Void>> handler) {
    mkdirInternal(path, perms, true, handler).run();
    return this;
  }

  public FileSystem mkdirsBlocking(String path, String perms) {
    mkdirInternal(path, perms, true, null).perform();
    return this;
  }

  public FileSystem readDir(String path, Handler<AsyncResult<List<String>>> handler) {
    readDirInternal(path, handler).run();
    return this;
  }

  public List<String> readDirBlocking(String path) {
    return readDirInternal(path, null).perform();
  }

  public FileSystem readDir(String path, String filter, Handler<AsyncResult<List<String>>> handler) {
    readDirInternal(path, filter, handler).run();
    return this;
  }

  public List<String> readDirBlocking(String path, String filter) {
    return readDirInternal(path, filter, null).perform();
  }

  public FileSystem readFile(String path, Handler<AsyncResult<Buffer>> handler) {
    readFileInternal(path, handler).run();
    return this;
  }

  public Buffer readFileBlocking(String path) {
    return readFileInternal(path, null).perform();
  }

  public FileSystem writeFile(String path, Buffer data, Handler<AsyncResult<Void>> handler) {
    writeFileInternal(path, data, handler).run();
    return this;
  }

  public FileSystem writeFileBlocking(String path, Buffer data) {
    writeFileInternal(path, data, null).perform();
    return this;
  }

  public FileSystem open(String path, OpenOptions options, Handler<AsyncResult<AsyncFile>> handler) {
    openInternal(path, options, handler).run();
    return this;
  }

  public AsyncFile openBlocking(String path, OpenOptions options) {
    return openInternal(path, options, null).perform();
  }

  public FileSystem createFile(String path, Handler<AsyncResult<Void>> handler) {
    createFileInternal(path, handler).run();
    return this;
  }

  public FileSystem createFileBlocking(String path) {
    createFileInternal(path, null).perform();
    return this;
  }

  public FileSystem createFile(String path, String perms, Handler<AsyncResult<Void>> handler) {
    createFileInternal(path, perms, handler).run();
    return this;
  }

  public FileSystem createFileBlocking(String path, String perms) {
    createFileInternal(path, perms, null).perform();
    return this;
  }

  public FileSystem exists(String path, Handler<AsyncResult<Boolean>> handler) {
    existsInternal(path, handler).run();
    return this;
  }

  public boolean existsBlocking(String path) {
    return existsInternal(path, null).perform();
  }

  public FileSystem fsProps(String path, Handler<AsyncResult<FileSystemProps>> handler) {
    fsPropsInternal(path, handler).run();
    return this;
  }

  public FileSystemProps fsPropsBlocking(String path) {
    return fsPropsInternal(path, null).perform();
  }

  private BlockingAction<Void> copyInternal(String from, String to, Handler<AsyncResult<Void>> handler) {
    return copyInternal(from, to, false, handler);
  }

  private BlockingAction<Void> copyInternal(String from, String to, boolean recursive, Handler<AsyncResult<Void>> handler) {
    Objects.requireNonNull(from);
    Objects.requireNonNull(to);
    return new BlockingAction<Void>(handler) {
      public Void perform() {
        try {
          Path source = vertx.resolveFile(from).toPath();
          Path target = vertx.resolveFile(to).toPath();
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
    Objects.requireNonNull(from);
    Objects.requireNonNull(to);
    return new BlockingAction<Void>(handler) {
      public Void perform() {
        try {
          Path source = vertx.resolveFile(from).toPath();
          Path target = vertx.resolveFile(to).toPath();
          Files.move(source, target);
        } catch (IOException e) {
          throw new FileSystemException(e);
        }
        return null;
      }
    };
  }

  private BlockingAction<Void> truncateInternal(String p, long len, Handler<AsyncResult<Void>> handler) {
    Objects.requireNonNull(p);
    return new BlockingAction<Void>(handler) {
      public Void perform() {
        RandomAccessFile raf = null;
        try {
          String path = vertx.resolveFile(p).getAbsolutePath();
          if (len < 0) {
            throw new FileSystemException("Cannot truncate file to size < 0");
          }
          if (!Files.exists(Paths.get(path))) {
            throw new FileSystemException("Cannot truncate file " + path + ". Does not exist");
          }
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
    Objects.requireNonNull(path);
    Set<PosixFilePermission> permissions = PosixFilePermissions.fromString(perms);
    Set<PosixFilePermission> dirPermissions = dirPerms == null ? null : PosixFilePermissions.fromString(dirPerms);
    return new BlockingAction<Void>(handler) {
      public Void perform() {
        try {
          Path target = vertx.resolveFile(path).toPath();
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
          throw new FileSystemException("Accessed denied for chmod on " + path);
        } catch (IOException e) {
          throw new FileSystemException(e);
        }
        return null;
      }
    };
  }

  protected BlockingAction<Void> chownInternal(String path, String user, String group, Handler<AsyncResult<Void>> handler) {
    Objects.requireNonNull(path);
    return new BlockingAction<Void>(handler) {
      public Void perform() {
        try {
          Path target = vertx.resolveFile(path).toPath();
          UserPrincipalLookupService service = target.getFileSystem().getUserPrincipalLookupService();
          UserPrincipal userPrincipal = user == null ? null : service.lookupPrincipalByName(user);
          GroupPrincipal groupPrincipal = group == null ? null : service.lookupPrincipalByGroupName(group);
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
          throw new FileSystemException("Accessed denied for chown on " + path);
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

  private BlockingAction<FileProps> props(String path, boolean followLinks, Handler<AsyncResult<FileProps>> handler) {
    Objects.requireNonNull(path);
    return new BlockingAction<FileProps>(handler) {
      public FileProps perform() {
        try {
          Path target = vertx.resolveFile(path).toPath();
          BasicFileAttributes attrs;
          if (followLinks) {
            attrs = Files.readAttributes(target, BasicFileAttributes.class);
          } else {
            attrs = Files.readAttributes(target, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
          }
          return new FilePropsImpl(attrs);
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

  private BlockingAction<Void> link(String link, String existing, boolean symbolic, Handler<AsyncResult<Void>> handler) {
    Objects.requireNonNull(link);
    Objects.requireNonNull(existing);
    return new BlockingAction<Void>(handler) {
      public Void perform() {
        try {
          Path source = vertx.resolveFile(link).toPath();
          Path target = vertx.resolveFile(existing).toPath();
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
    Objects.requireNonNull(link);
    return new BlockingAction<String>(handler) {
      public String perform() {
        try {
          Path source = vertx.resolveFile(link).toPath();
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

  private BlockingAction<Void> deleteInternal(String path, boolean recursive, Handler<AsyncResult<Void>> handler) {
    Objects.requireNonNull(path);
    return new BlockingAction<Void>(handler) {
      public Void perform() {
        try {
          Path source = vertx.resolveFile(path).toPath();
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

  protected BlockingAction<Void> mkdirInternal(String path, String perms, boolean createParents, Handler<AsyncResult<Void>> handler) {
    Objects.requireNonNull(path);
    FileAttribute<?> attrs = perms == null ? null : PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(perms));
    return new BlockingAction<Void>(handler) {
      public Void perform() {
        try {
          Path source = vertx.resolveFile(path).toPath();
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

  private BlockingAction<List<String>> readDirInternal(String path, Handler<AsyncResult<List<String>>> handler) {
    return readDirInternal(path, null, handler);
  }

  private BlockingAction<List<String>> readDirInternal(String p, String filter, Handler<AsyncResult<List<String>>> handler) {
    Objects.requireNonNull(p);
    return new BlockingAction<List<String>>(handler) {
      public List<String> perform() {
        try {
          File file = vertx.resolveFile(p);
          if (!file.exists()) {
            throw new FileSystemException("Cannot read directory " + file + ". Does not exist");
          }
          if (!file.isDirectory()) {
            throw new FileSystemException("Cannot read directory " + file + ". It's not a directory");
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
            List<String> ret = new ArrayList<>(files.length);
            for (File f : files) {
              ret.add(f.getCanonicalPath());
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
    Objects.requireNonNull(path);
    return new BlockingAction<Buffer>(handler) {
      public Buffer perform() {
        try {
          Path target = vertx.resolveFile(path).toPath();
          byte[] bytes = Files.readAllBytes(target);
          Buffer buff = Buffer.buffer(bytes);
          return buff;
        } catch (IOException e) {
          throw new FileSystemException(e);
        }
      }
    };
  }

  private BlockingAction<Void> writeFileInternal(String path, Buffer data, Handler<AsyncResult<Void>> handler) {
    Objects.requireNonNull(path);
    Objects.requireNonNull(data);
    return new BlockingAction<Void>(handler) {
      public Void perform() {
        try {
          Path target = vertx.resolveFile(path).toPath();
          Files.write(target, data.getBytes());
          return null;
        } catch (IOException e) {
          throw new FileSystemException(e);
        }
      }
    };
  }

  private BlockingAction<AsyncFile> openInternal(String p, OpenOptions options, Handler<AsyncResult<AsyncFile>> handler) {
    Objects.requireNonNull(p);
    Objects.requireNonNull(options);
    return new BlockingAction<AsyncFile>(handler) {
      String path = vertx.resolveFile(p).getAbsolutePath();
      public AsyncFile perform() {
        return doOpen(path, options, context);
      }
    };
  }

  protected AsyncFile doOpen(String path, OpenOptions options, ContextImpl context) {
    return new AsyncFileImpl(vertx, path, options, context);
  }

  private BlockingAction<Void> createFileInternal(String path, Handler<AsyncResult<Void>> handler) {
    return createFileInternal(path, null, handler);
  }

  protected BlockingAction<Void> createFileInternal(String p, String perms, Handler<AsyncResult<Void>> handler) {
    Objects.requireNonNull(p);
    FileAttribute<?> attrs = perms == null ? null : PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(perms));
    return new BlockingAction<Void>(handler) {
      public Void perform() {
        try {
          Path target = vertx.resolveFile(p).toPath();
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
    Objects.requireNonNull(path);
    return new BlockingAction<Boolean>(handler) {
      File file = vertx.resolveFile(path);
      public Boolean perform() {
        return file.exists();
      }
    };
  }

  private BlockingAction<FileSystemProps> fsPropsInternal(String path, Handler<AsyncResult<FileSystemProps>> handler) {
    Objects.requireNonNull(path);
    return new BlockingAction<FileSystemProps>(handler) {
      public FileSystemProps perform() {
        try {
          Path target = vertx.resolveFile(path).toPath();
          FileStore fs = Files.getFileStore(target);
          return new FileSystemPropsImpl(fs.getTotalSpace(), fs.getUnallocatedSpace(), fs.getUsableSpace());
        } catch (IOException e) {
          throw new FileSystemException(e);
        }
      }
    };
  }

  protected abstract class BlockingAction<T> implements Action<T> {

    private final Handler<AsyncResult<T>> handler;
    protected final ContextImpl context;

    public BlockingAction(Handler<AsyncResult<T>> handler) {
      this.handler = handler;
      this.context = vertx.getOrCreateContext();
    }
    /**
     * Run the blocking action using a thread from the worker pool.
     */
    public void run() {
      context.executeBlocking(this, true, handler);
    }
  }
}
