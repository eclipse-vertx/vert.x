/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.file.impl;

import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.CopyOptions;
import io.vertx.core.file.FileProps;
import io.vertx.core.file.FileSystem;
import io.vertx.core.file.FileSystemException;
import io.vertx.core.file.FileSystemProps;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.CopyOption;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileStore;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
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
import java.util.HashSet;
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

  private static final CopyOptions DEFAULT_OPTIONS = new CopyOptions();

  protected final VertxInternal vertx;

  public FileSystemImpl(VertxInternal vertx) {
    this.vertx = vertx;
  }

  public FileSystem copy(String from, String to, Handler<AsyncResult<Void>> handler) {
    return copy(from, to, DEFAULT_OPTIONS, handler);
  }

  @Override
  public Future<Void> copy(String from, String to) {
    Promise<Void> promise = Promise.promise();
    copy(from, to, promise);
    return promise.future();
  }

  @Override
  public FileSystem copy(String from, String to, CopyOptions options, Handler<AsyncResult<Void>> handler) {
    copyInternal(from, to, options, handler).run();
    return this;
  }

  @Override
  public Future<Void> copy(String from, String to, CopyOptions options) {
    return copy(from, to, options);
  }

  public FileSystem copyBlocking(String from, String to) {
    copyInternal(from, to, DEFAULT_OPTIONS, null).perform();
    return this;
  }

  public FileSystem copyRecursive(String from, String to, boolean recursive, Handler<AsyncResult<Void>> handler) {
    copyRecursiveInternal(from, to, recursive, handler).run();
    return this;
  }

  @Override
  public Future<Void> copyRecursive(String from, String to, boolean recursive) {
    Promise<Void> promise = Promise.promise();
    copyRecursive(from, to, recursive, promise);
    return promise.future();
  }

  public FileSystem copyRecursiveBlocking(String from, String to, boolean recursive) {
    copyRecursiveInternal(from, to, recursive, null).perform();
    return this;
  }

  public FileSystem move(String from, String to, Handler<AsyncResult<Void>> handler) {
    return move(from, to, DEFAULT_OPTIONS, handler);
  }

  @Override
  public Future<Void> move(String from, String to) {
    Promise<Void> promise = Promise.promise();
    move(from, to, promise);
    return promise.future();
  }

  @Override
  public FileSystem move(String from, String to, CopyOptions options, Handler<AsyncResult<Void>> handler) {
    moveInternal(from, to, options, handler).run();
    return this;
  }

  @Override
  public Future<Void> move(String from, String to, CopyOptions options) {
    Promise<Void> promise = Promise.promise();
    move(from, to, options, promise);
    return promise.future();
  }

  public FileSystem moveBlocking(String from, String to) {
    moveInternal(from, to, DEFAULT_OPTIONS, null).perform();
    return this;
  }

  public FileSystem truncate(String path, long len, Handler<AsyncResult<Void>> handler) {
    truncateInternal(path, len, handler).run();
    return this;
  }

  @Override
  public Future<Void> truncate(String path, long len) {
    Promise<Void> promise = Promise.promise();
    truncate(path, len, promise);
    return promise.future();
  }

  public FileSystem truncateBlocking(String path, long len) {
    truncateInternal(path, len, null).perform();
    return this;
  }

  public FileSystem chmod(String path, String perms, Handler<AsyncResult<Void>> handler) {
    chmodInternal(path, perms, handler).run();
    return this;
  }

  @Override
  public Future<Void> chmod(String path, String perms) {
    Promise<Void> promise = Promise.promise();
    chmod(path, perms, promise);
    return promise.future();
  }

  public FileSystem chmodBlocking(String path, String perms) {
    chmodInternal(path, perms, null).perform();
    return this;
  }

  public FileSystem chmodRecursive(String path, String perms, String dirPerms, Handler<AsyncResult<Void>> handler) {
    chmodInternal(path, perms, dirPerms, handler).run();
    return this;
  }

  @Override
  public Future<Void> chmodRecursive(String path, String perms, String dirPerms) {
    Promise<Void> promise = Promise.promise();
    chmodRecursive(path, perms, dirPerms, promise);
    return promise.future();
  }

  public FileSystem chmodRecursiveBlocking(String path, String perms, String dirPerms) {
    chmodInternal(path, perms, dirPerms, null).perform();
    return this;
  }

  public FileSystem chown(String path, String user, String group, Handler<AsyncResult<Void>> handler) {
    chownInternal(path, user, group, handler).run();
    return this;
  }

  @Override
  public Future<Void> chown(String path, @Nullable String user, @Nullable String group) {
    Promise<Void> promise = Promise.promise();
    chown(path, user, group, promise);
    return promise.future();
  }

  public FileSystem chownBlocking(String path, String user, String group) {
    chownInternal(path, user, group, null).perform();
    return this;
  }

  public FileSystem props(String path, Handler<AsyncResult<FileProps>> handler) {
    propsInternal(path, handler).run();
    return this;
  }

  @Override
  public Future<FileProps> props(String path) {
    Promise<FileProps> promise = Promise.promise();
    props(path, promise);
    return promise.future();
  }

  public FileProps propsBlocking(String path) {
    return propsInternal(path, null).perform();
  }

  public FileSystem lprops(String path, Handler<AsyncResult<FileProps>> handler) {
    lpropsInternal(path, handler).run();
    return this;
  }

  @Override
  public Future<FileProps> lprops(String path) {
    Promise<FileProps> promise = Promise.promise();
    lprops(path, promise);
    return promise.future();
  }

  public FileProps lpropsBlocking(String path) {
    return lpropsInternal(path, null).perform();
  }

  public FileSystem link(String link, String existing, Handler<AsyncResult<Void>> handler) {
    linkInternal(link, existing, handler).run();
    return this;
  }

  @Override
  public Future<Void> link(String link, String existing) {
    Promise<Void> promise = Promise.promise();
    link(link, existing, promise);
    return promise.future();
  }

  public FileSystem linkBlocking(String link, String existing) {
    linkInternal(link, existing, null).perform();
    return this;
  }

  public FileSystem symlink(String link, String existing, Handler<AsyncResult<Void>> handler) {
    symlinkInternal(link, existing, handler).run();
    return this;
  }

  @Override
  public Future<Void> symlink(String link, String existing) {
    Promise<Void> promise = Promise.promise();
    symlink(link, existing, promise);
    return promise.future();
  }

  public FileSystem symlinkBlocking(String link, String existing) {
    symlinkInternal(link, existing, null).perform();
    return this;
  }

  public FileSystem unlink(String link, Handler<AsyncResult<Void>> handler) {
    unlinkInternal(link, handler).run();
    return this;
  }

  @Override
  public Future<Void> unlink(String link) {
    Promise<Void> promise = Promise.promise();
    unlink(link, promise);
    return promise.future();
  }

  public FileSystem unlinkBlocking(String link) {
    unlinkInternal(link, null).perform();
    return this;
  }

  public FileSystem readSymlink(String link, Handler<AsyncResult<String>> handler) {
    readSymlinkInternal(link, handler).run();
    return this;
  }

  @Override
  public Future<String> readSymlink(String link) {
    Promise<String> promise = Promise.promise();
    readSymlink(link, promise);
    return promise.future();
  }

  public String readSymlinkBlocking(String link) {
    return readSymlinkInternal(link, null).perform();
  }

  public FileSystem delete(String path, Handler<AsyncResult<Void>> handler) {
    deleteInternal(path, handler).run();
    return this;
  }

  @Override
  public Future<Void> delete(String path) {
    Promise<Void> promise = Promise.promise();
    delete(path, promise);
    return promise.future();
  }

  public FileSystem deleteBlocking(String path) {
    deleteInternal(path, null).perform();
    return this;
  }

  public FileSystem deleteRecursive(String path, boolean recursive, Handler<AsyncResult<Void>> handler) {
    deleteInternal(path, recursive, handler).run();
    return this;
  }

  @Override
  public Future<Void> deleteRecursive(String path, boolean recursive) {
    Promise<Void> promise = Promise.promise();
    deleteRecursive(path, recursive, promise);
    return promise.future();
  }

  public FileSystem deleteRecursiveBlocking(String path, boolean recursive) {
    deleteInternal(path, recursive, null).perform();
    return this;
  }

  public FileSystem mkdir(String path, Handler<AsyncResult<Void>> handler) {
    mkdirInternal(path, handler).run();
    return this;
  }

  @Override
  public Future<Void> mkdir(String path) {
    Promise<Void> promise = Promise.promise();
    mkdir(path, promise);
    return promise.future();
  }

  public FileSystem mkdirBlocking(String path) {
    mkdirInternal(path, null).perform();
    return this;
  }

  public FileSystem mkdirs(String path, Handler<AsyncResult<Void>> handler) {
    mkdirInternal(path, true, handler).run();
    return this;
  }

  @Override
  public Future<Void> mkdirs(String path) {
    Promise<Void> promise = Promise.promise();
    mkdirs(path, promise);
    return promise.future();
  }

  public FileSystem mkdirsBlocking(String path) {
    mkdirInternal(path, true, null).perform();
    return this;
  }

  public FileSystem mkdir(String path, String perms, Handler<AsyncResult<Void>> handler) {
    mkdirInternal(path, perms, handler).run();
    return this;
  }

  @Override
  public Future<Void> mkdir(String path, String perms) {
    Promise<Void> promise = Promise.promise();
    mkdir(path, perms, promise);
    return promise.future();
  }

  public FileSystem mkdirBlocking(String path, String perms) {
    mkdirInternal(path, perms, null).perform();
    return this;
  }

  public FileSystem mkdirs(String path, String perms, Handler<AsyncResult<Void>> handler) {
    mkdirInternal(path, perms, true, handler).run();
    return this;
  }

  @Override
  public Future<Void> mkdirs(String path, String perms) {
    Promise<Void> promise = Promise.promise();
    mkdirs(path, perms, promise);
    return promise.future();
  }

  public FileSystem mkdirsBlocking(String path, String perms) {
    mkdirInternal(path, perms, true, null).perform();
    return this;
  }

  public FileSystem readDir(String path, Handler<AsyncResult<List<String>>> handler) {
    readDirInternal(path, handler).run();
    return this;
  }

  @Override
  public Future<List<String>> readDir(String path) {
    Promise<List<String>> promise = Promise.promise();
    readDir(path, promise);
    return promise.future();
  }

  public List<String> readDirBlocking(String path) {
    return readDirInternal(path, null).perform();
  }

  public FileSystem readDir(String path, String filter, Handler<AsyncResult<List<String>>> handler) {
    readDirInternal(path, filter, handler).run();
    return this;
  }

  @Override
  public Future<List<String>> readDir(String path, String filter) {
    Promise<List<String>> promise = Promise.promise();
    readDir(path, filter, promise);
    return promise.future();
  }

  public List<String> readDirBlocking(String path, String filter) {
    return readDirInternal(path, filter, null).perform();
  }

  public FileSystem readFile(String path, Handler<AsyncResult<Buffer>> handler) {
    readFileInternal(path, handler).run();
    return this;
  }

  @Override
  public Future<Buffer> readFile(String path) {
    Promise<Buffer> promise = Promise.promise();
    readFile(path, promise);
    return promise.future();
  }

  public Buffer readFileBlocking(String path) {
    return readFileInternal(path, null).perform();
  }

  public FileSystem writeFile(String path, Buffer data, Handler<AsyncResult<Void>> handler) {
    writeFileInternal(path, data, handler).run();
    return this;
  }

  @Override
  public Future<Void> writeFile(String path, Buffer data) {
    Promise<Void> promise = Promise.promise();
    writeFile(path, data, promise);
    return promise.future();
  }

  public FileSystem writeFileBlocking(String path, Buffer data) {
    writeFileInternal(path, data, null).perform();
    return this;
  }

  public FileSystem open(String path, OpenOptions options, Handler<AsyncResult<AsyncFile>> handler) {
    openInternal(path, options, handler).run();
    return this;
  }

  @Override
  public Future<AsyncFile> open(String path, OpenOptions options) {
    Promise<AsyncFile> promise = Promise.promise();
    open(path, options, promise);
    return promise.future();
  }

  public AsyncFile openBlocking(String path, OpenOptions options) {
    return openInternal(path, options, null).perform();
  }

  public FileSystem createFile(String path, Handler<AsyncResult<Void>> handler) {
    createFileInternal(path, handler).run();
    return this;
  }

  @Override
  public Future<Void> createFile(String path) {
    Promise<Void> promise = Promise.promise();
    createFile(path, promise);
    return promise.future();
  }

  public FileSystem createFileBlocking(String path) {
    createFileInternal(path, null).perform();
    return this;
  }

  public FileSystem createFile(String path, String perms, Handler<AsyncResult<Void>> handler) {
    createFileInternal(path, perms, handler).run();
    return this;
  }

  @Override
  public Future<Void> createFile(String path, String perms) {
    Promise<Void> promise = Promise.promise();
    createFile(path, perms, promise);
    return promise.future();
  }

  public FileSystem createFileBlocking(String path, String perms) {
    createFileInternal(path, perms, null).perform();
    return this;
  }

  public FileSystem exists(String path, Handler<AsyncResult<Boolean>> handler) {
    existsInternal(path, handler).run();
    return this;
  }

  @Override
  public Future<Boolean> exists(String path) {
    Promise<Boolean> promise = Promise.promise();
    exists(path, promise);
    return promise.future();
  }

  public boolean existsBlocking(String path) {
    return existsInternal(path, null).perform();
  }

  public FileSystem fsProps(String path, Handler<AsyncResult<FileSystemProps>> handler) {
    fsPropsInternal(path, handler).run();
    return this;
  }

  @Override
  public Future<FileSystemProps> fsProps(String path) {
    Promise<FileSystemProps> promise = Promise.promise();
    fsProps(path, promise);
    return promise.future();
  }

  public FileSystemProps fsPropsBlocking(String path) {
    return fsPropsInternal(path, null).perform();
  }


  @Override
  public FileSystem createTempDirectory(String prefix, Handler<AsyncResult<String>> handler) {
    createTempDirectoryInternal(null, prefix, null, handler).run();
    return this;
  }

  @Override
  public Future<String> createTempDirectory(String prefix) {
    Promise<String> promise = Promise.promise();
    createTempDirectory(prefix, promise);
    return promise.future();
  }

  @Override
  public String createTempDirectoryBlocking(String prefix) {
    return createTempDirectoryInternal(null, prefix, null, null).perform();
  }

  @Override
  public FileSystem createTempDirectory(String prefix, String perms, Handler<AsyncResult<String>> handler) {
    createTempDirectoryInternal(null, prefix, perms, handler).run();
    return this;
  }

  @Override
  public Future<String> createTempDirectory(String prefix, String perms) {
    Promise<String> promise = Promise.promise();
    createTempDirectory(prefix, perms, promise);
    return promise.future();
  }

  @Override
  public String createTempDirectoryBlocking(String prefix, String perms) {
    return createTempDirectoryInternal(null, prefix, perms, null).perform();
  }

  @Override
  public FileSystem createTempDirectory(String dir, String prefix, String perms, Handler<AsyncResult<String>> handler) {
    createTempDirectoryInternal(dir, prefix, perms, handler).run();
    return this;
  }

  @Override
  public Future<String> createTempDirectory(String dir, String prefix, String perms) {
    Promise<String> promise = Promise.promise();
    createTempDirectory(dir, prefix, perms, promise);
    return promise.future();
  }

  @Override
  public String createTempDirectoryBlocking(String dir, String prefix, String perms) {
    return createTempDirectoryInternal(dir, prefix, perms, null).perform();
  }

  @Override
  public FileSystem createTempFile(String prefix, String suffix, Handler<AsyncResult<String>> handler) {
    createTempFileInternal(null, prefix, suffix, null, handler).run();
    return this;
  }

  @Override
  public Future<String> createTempFile(String prefix, String suffix) {
    Promise<String> promise = Promise.promise();
    createTempFile(prefix, suffix, promise);
    return promise.future();
  }

  @Override
  public String createTempFileBlocking(String prefix, String suffix) {
    return createTempFileInternal(null, prefix, suffix, null, null).perform();
  }

  @Override
  public FileSystem createTempFile(String prefix, String suffix, String perms, Handler<AsyncResult<String>> handler) {
    createTempFileInternal(null, prefix, suffix, perms, handler).run();
    return this;
  }

  @Override
  public Future<String> createTempFile(String prefix, String suffix, String perms) {
    Promise<String> promise = Promise.promise();
    createTempFile(prefix, suffix, perms, promise);
    return promise.future();
  }

  @Override
  public String createTempFileBlocking(String prefix, String suffix, String perms) {
    return createTempFileInternal(null, prefix, suffix, perms, null).perform();
  }


  @Override
  public FileSystem createTempFile(String dir, String prefix, String suffix, String perms, Handler<AsyncResult<String>> handler) {
    createTempFileInternal(dir, prefix, suffix, perms, handler).run();
    return this;
  }

  @Override
  public Future<String> createTempFile(String dir, String prefix, String suffix, String perms) {
    Promise<String> promise = Promise.promise();
    createTempFile(dir, prefix, suffix, perms, promise);
    return promise.future();
  }

  @Override
  public String createTempFileBlocking(String dir, String prefix, String suffix, String perms) {
    return createTempFileInternal(dir, prefix, suffix, perms, null).perform();
  }

  private BlockingAction<Void> copyInternal(String from, String to, CopyOptions options, Handler<AsyncResult<Void>> handler) {
    Objects.requireNonNull(from);
    Objects.requireNonNull(to);
    Objects.requireNonNull(options);
    Set<CopyOption> copyOptionSet = toCopyOptionSet(options);
    CopyOption[] copyOptions = copyOptionSet.toArray(new CopyOption[copyOptionSet.size()]);
    return new BlockingAction<Void>(handler) {
      public Void perform() {
        try {
          Path source = vertx.resolveFile(from).toPath();
          Path target = vertx.resolveFile(to).toPath();
          Files.copy(source, target, copyOptions);
        } catch (IOException e) {
          throw new FileSystemException(e);
        }
        return null;
      }
    };
  }

  private BlockingAction<Void> copyRecursiveInternal(String from, String to, boolean recursive, Handler<AsyncResult<Void>> handler) {
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

  private BlockingAction<Void> moveInternal(String from, String to, CopyOptions options, Handler<AsyncResult<Void>> handler) {
    Objects.requireNonNull(from);
    Objects.requireNonNull(to);
    Objects.requireNonNull(options);
    Set<CopyOption> copyOptionSet = toCopyOptionSet(options);
    CopyOption[] copyOptions = copyOptionSet.toArray(new CopyOption[copyOptionSet.size()]);
    return new BlockingAction<Void>(handler) {
      public Void perform() {
        try {
          Path source = vertx.resolveFile(from).toPath();
          Path target = vertx.resolveFile(to).toPath();
          Files.move(source, target, copyOptions);
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
          delete(source, recursive);
        } catch (IOException e) {
          throw new FileSystemException(e);
        }
        return null;
      }
    };
  }

  public static void delete(Path path, boolean recursive) throws IOException {
    if (recursive) {
      Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
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
      Files.delete(path);
    }
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

  protected BlockingAction<String> createTempDirectoryInternal(String parentDir, String prefix, String perms, Handler<AsyncResult<String>> handler) {
    FileAttribute<?> attrs = perms == null ? null : PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(perms));
    return new BlockingAction<String>(handler) {
      public String perform() {
        try {
          Path tmpDir;
          if (parentDir != null) {
            Path dir = vertx.resolveFile(parentDir).toPath();
            if (attrs != null) {
              tmpDir = Files.createTempDirectory(dir, prefix, attrs);
            } else {
              tmpDir = Files.createTempDirectory(dir, prefix);
            }
          } else {
            if (attrs != null) {
              tmpDir = Files.createTempDirectory(prefix, attrs);
            } else {
              tmpDir = Files.createTempDirectory(prefix);
            }
          }
          return tmpDir.toFile().getAbsolutePath();
        } catch (IOException e) {
          throw new FileSystemException(e);
        }
      }
    };
  }

  protected BlockingAction<String> createTempFileInternal(String parentDir, String prefix, String suffix, String perms, Handler<AsyncResult<String>> handler) {
    FileAttribute<?> attrs = perms == null ? null : PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(perms));
    return new BlockingAction<String>(handler) {
      public String perform() {
        try {
          Path tmpFile;
          if (parentDir != null) {
            Path dir = vertx.resolveFile(parentDir).toPath();
            if (attrs != null) {
              tmpFile = Files.createTempFile(dir, prefix, suffix, attrs);
            } else {
              tmpFile = Files.createTempFile(dir, prefix, suffix);
            }
          } else {
            if (attrs != null) {
              tmpFile = Files.createTempFile(prefix, suffix, attrs);
            } else {
              tmpFile = Files.createTempFile(prefix, suffix);
            }
          }
          return tmpFile.toFile().getAbsolutePath();
        } catch (IOException e) {
          throw new FileSystemException(e);
        }
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
      public AsyncFile perform() {
        String path = vertx.resolveFile(p).getAbsolutePath();
        return doOpen(path, options, context);
      }
    };
  }

  protected AsyncFile doOpen(String path, OpenOptions options, ContextInternal context) {
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

  protected abstract class BlockingAction<T> implements Handler<Promise<T>> {

    private final Handler<AsyncResult<T>> handler;
    protected final ContextInternal context;

    public BlockingAction(Handler<AsyncResult<T>> handler) {
      this.handler = handler;
      this.context = vertx.getOrCreateContext();
    }
    /**
     * Run the blocking action using a thread from the worker pool.
     */
    public void run() {
      context.executeBlockingInternal(this, handler);
    }

    @Override
    public void handle(Promise<T> fut) {
      try {
        T result = perform();
        fut.complete(result);
      } catch (Exception e) {
        fut.fail(e);
      }
    }

    public abstract T perform();

  }

  // Visible for testing
  static Set<CopyOption> toCopyOptionSet(CopyOptions copyOptions) {
    Set<CopyOption> copyOptionSet = new HashSet<>();
    if (copyOptions.isReplaceExisting()) copyOptionSet.add(StandardCopyOption.REPLACE_EXISTING);
    if (copyOptions.isCopyAttributes()) copyOptionSet.add(StandardCopyOption.COPY_ATTRIBUTES);
    if (copyOptions.isAtomicMove()) copyOptionSet.add(StandardCopyOption.ATOMIC_MOVE);
    if (copyOptions.isNofollowLinks()) copyOptionSet.add(LinkOption.NOFOLLOW_LINKS);
    return copyOptionSet;
  }
}
