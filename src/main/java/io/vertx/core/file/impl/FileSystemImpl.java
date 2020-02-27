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

  @Override
  public FileSystem copy(String from, String to, Handler<AsyncResult<Void>> handler) {
    return copy(from, to, DEFAULT_OPTIONS, handler);
  }

  @Override
  public Future<Void> copy(String from, String to) {
    return copy(from, to, DEFAULT_OPTIONS);
  }

  @Override
  public FileSystem copy(String from, String to, CopyOptions options, Handler<AsyncResult<Void>> handler) {
    copy(from, to, options).setHandler(handler);
    return this;
  }

  @Override
  public Future<Void> copy(String from, String to, CopyOptions options) {
    return copyInternal(from, to, false, options).run();
  }

  @Override
  public FileSystem copyBlocking(String from, String to) {
    copyInternal(from, to, false, DEFAULT_OPTIONS).perform();
    return this;
  }

  @Override
  public FileSystem copyRecursive(String from, String to, Handler<AsyncResult<Void>> handler) {
    copyRecursive(from, to).setHandler(handler);
    return this;
  }

  @Override
  public Future<Void> copyRecursive(String from, String to) {
    return copyInternal(from, to, true, DEFAULT_OPTIONS).run();
  }

  @Override
  public FileSystem copyRecursiveBlocking(String from, String to) {
    copyInternal(from, to, true, DEFAULT_OPTIONS).perform();
    return this;
  }

  @Override
  public FileSystem move(String from, String to, Handler<AsyncResult<Void>> handler) {
    return move(from, to, DEFAULT_OPTIONS, handler);
  }

  @Override
  public Future<Void> move(String from, String to) {
    return move(from, to, DEFAULT_OPTIONS);
  }

  @Override
  public FileSystem move(String from, String to, CopyOptions options, Handler<AsyncResult<Void>> handler) {
    move(from, to, options).setHandler(handler);
    return this;
  }

  @Override
  public Future<Void> move(String from, String to, CopyOptions options) {
    return moveInternal(from, to, options).run();
  }

  @Override
  public FileSystem moveBlocking(String from, String to) {
    moveInternal(from, to, DEFAULT_OPTIONS).perform();
    return this;
  }

  @Override
  public FileSystem truncate(String path, long len, Handler<AsyncResult<Void>> handler) {
    truncate(path, len).setHandler(handler);
    return this;
  }

  @Override
  public Future<Void> truncate(String path, long len) {
    return truncateInternal(path, len).run();
  }

  @Override
  public FileSystem truncateBlocking(String path, long len) {
    truncateInternal(path, len).perform();
    return this;
  }

  @Override
  public FileSystem chmod(String path, String perms, Handler<AsyncResult<Void>> handler) {
    chmod(path, perms).setHandler(handler);
    return this;
  }

  @Override
  public Future<Void> chmod(String path, String perms) {
    return chmodInternal(path, perms).run();
  }

  @Override
  public FileSystem chmodBlocking(String path, String perms) {
    chmodInternal(path, perms).perform();
    return this;
  }

  @Override
  public FileSystem chmodRecursive(String path, String perms, String dirPerms, Handler<AsyncResult<Void>> handler) {
    chmodRecursive(path, perms, dirPerms).setHandler(handler);
    return this;
  }

  @Override
  public Future<Void> chmodRecursive(String path, String perms, String dirPerms) {
    return chmodInternal(path, perms, dirPerms).run();
  }

  @Override
  public FileSystem chmodRecursiveBlocking(String path, String perms, String dirPerms) {
    chmodInternal(path, perms, dirPerms).perform();
    return this;
  }

  @Override
  public FileSystem chown(String path, String user, String group, Handler<AsyncResult<Void>> handler) {
    chown(path, user, group).setHandler(handler);
    return this;
  }

  @Override
  public Future<Void> chown(String path, @Nullable String user, @Nullable String group) {
    return chownInternal(path, user, group).run();
  }

  @Override
  public FileSystem chownBlocking(String path, String user, String group) {
    chownInternal(path, user, group).perform();
    return this;
  }

  @Override
  public FileSystem props(String path, Handler<AsyncResult<FileProps>> handler) {
    props(path).setHandler(handler);
    return this;
  }

  @Override
  public Future<FileProps> props(String path) {
    return propsInternal(path).run();
  }

  @Override
  public FileProps propsBlocking(String path) {
    return propsInternal(path).perform();
  }

  @Override
  public FileSystem lprops(String path, Handler<AsyncResult<FileProps>> handler) {
    lprops(path).setHandler(handler);
    return this;
  }

  @Override
  public Future<FileProps> lprops(String path) {
    return lpropsInternal(path).run();
  }

  @Override
  public FileProps lpropsBlocking(String path) {
    return lpropsInternal(path).perform();
  }

  @Override
  public FileSystem link(String link, String existing, Handler<AsyncResult<Void>> handler) {
    link(link, existing).setHandler(handler);
    return this;
  }

  @Override
  public Future<Void> link(String link, String existing) {
    return linkInternal(link, existing).run();
  }

  @Override
  public FileSystem linkBlocking(String link, String existing) {
    linkInternal(link, existing).perform();
    return this;
  }

  @Override
  public FileSystem symlink(String link, String existing, Handler<AsyncResult<Void>> handler) {
    symlink(link, existing).setHandler(handler);
    return this;
  }

  @Override
  public Future<Void> symlink(String link, String existing) {
    return symlinkInternal(link, existing).run();
  }

  @Override
  public FileSystem symlinkBlocking(String link, String existing) {
    symlinkInternal(link, existing).perform();
    return this;
  }

  @Override
  public FileSystem unlink(String link, Handler<AsyncResult<Void>> handler) {
    unlink(link).setHandler(handler);
    return this;
  }

  @Override
  public Future<Void> unlink(String link) {
    return unlinkInternal(link).run();
  }

  @Override
  public FileSystem unlinkBlocking(String link) {
    unlinkInternal(link).perform();
    return this;
  }

  @Override
  public FileSystem readSymlink(String link, Handler<AsyncResult<String>> handler) {
    readSymlink(link).setHandler(handler);
    return this;
  }

  @Override
  public Future<String> readSymlink(String link) {
    return readSymlinkInternal(link).run();
  }

  @Override
  public String readSymlinkBlocking(String link) {
    return readSymlinkInternal(link).perform();
  }

  @Override
  public FileSystem delete(String path, Handler<AsyncResult<Void>> handler) {
    delete(path).setHandler(handler);
    return this;
  }

  @Override
  public Future<Void> delete(String path) {
    return deleteInternal(path).run();
  }

  @Override
  public FileSystem deleteBlocking(String path) {
    deleteInternal(path).perform();
    return this;
  }

  @Override
  public FileSystem deleteRecursive(String path, Handler<AsyncResult<Void>> handler) {
    deleteRecursive(path).setHandler(handler);
    return this;
  }

  @Override
  public Future<Void> deleteRecursive(String path) {
    return deleteInternal(path, true).run();
  }

  @Override
  public FileSystem deleteRecursiveBlocking(String path) {
    deleteInternal(path, true).perform();
    return this;
  }

  @Override
  public FileSystem mkdir(String path, Handler<AsyncResult<Void>> handler) {
    mkdir(path).setHandler(handler);
    return this;
  }

  @Override
  public Future<Void> mkdir(String path) {
    return mkdirInternal(path).run();
  }

  @Override
  public FileSystem mkdirBlocking(String path) {
    mkdirInternal(path).perform();
    return this;
  }

  @Override
  public FileSystem mkdirs(String path, Handler<AsyncResult<Void>> handler) {
    mkdirs(path).setHandler(handler);
    return this;
  }

  @Override
  public Future<Void> mkdirs(String path) {
    return mkdirInternal(path, true).run();
  }

  @Override
  public FileSystem mkdirsBlocking(String path) {
    mkdirInternal(path, true).perform();
    return this;
  }

  @Override
  public FileSystem mkdir(String path, String perms, Handler<AsyncResult<Void>> handler) {
    mkdir(path, perms).setHandler(handler);
    return this;
  }

  @Override
  public Future<Void> mkdir(String path, String perms) {
    return mkdirInternal(path, perms).run();
  }

  @Override
  public FileSystem mkdirBlocking(String path, String perms) {
    mkdirInternal(path, perms).perform();
    return this;
  }

  @Override
  public FileSystem mkdirs(String path, String perms, Handler<AsyncResult<Void>> handler) {
    mkdirs(path, perms).setHandler(handler);
    return this;
  }

  @Override
  public Future<Void> mkdirs(String path, String perms) {
    return mkdirInternal(path, perms, true).run();
  }

  @Override
  public FileSystem mkdirsBlocking(String path, String perms) {
    mkdirInternal(path, perms, true).perform();
    return this;
  }

  @Override
  public FileSystem readDir(String path, Handler<AsyncResult<List<String>>> handler) {
    readDir(path).setHandler(handler);
    return this;
  }

  @Override
  public Future<List<String>> readDir(String path) {
    return readDirInternal(path).run();
  }

  @Override
  public List<String> readDirBlocking(String path) {
    return readDirInternal(path).perform();
  }

  @Override
  public FileSystem readDir(String path, String filter, Handler<AsyncResult<List<String>>> handler) {
    readDir(path, filter).setHandler(handler);
    return this;
  }

  @Override
  public Future<List<String>> readDir(String path, String filter) {
    return readDirInternal(path, filter).run();
  }

  @Override
  public List<String> readDirBlocking(String path, String filter) {
    return readDirInternal(path, filter).perform();
  }

  @Override
  public FileSystem readFile(String path, Handler<AsyncResult<Buffer>> handler) {
    readFile(path).setHandler(handler);
    return this;
  }

  @Override
  public Future<Buffer> readFile(String path) {
    return readFileInternal(path).run();
  }

  @Override
  public Buffer readFileBlocking(String path) {
    return readFileInternal(path).perform();
  }

  @Override
  public FileSystem writeFile(String path, Buffer data, Handler<AsyncResult<Void>> handler) {
    writeFile(path, data).setHandler(handler);
    return this;
  }

  @Override
  public Future<Void> writeFile(String path, Buffer data) {
    return writeFileInternal(path, data).run();
  }

  @Override
  public FileSystem writeFileBlocking(String path, Buffer data) {
    writeFileInternal(path, data).perform();
    return this;
  }

  @Override
  public FileSystem open(String path, OpenOptions options, Handler<AsyncResult<AsyncFile>> handler) {
    open(path, options).setHandler(handler);
    return this;
  }

  @Override
  public Future<AsyncFile> open(String path, OpenOptions options) {
    return openInternal(path, options).run();
  }

  @Override
  public AsyncFile openBlocking(String path, OpenOptions options) {
    return openInternal(path, options).perform();
  }

  @Override
  public FileSystem createFile(String path, Handler<AsyncResult<Void>> handler) {
    createFile(path).setHandler(handler);
    return this;
  }

  @Override
  public Future<Void> createFile(String path) {
    return createFileInternal(path).run();
  }

  @Override
  public FileSystem createFileBlocking(String path) {
    createFileInternal(path).perform();
    return this;
  }

  @Override
  public FileSystem createFile(String path, String perms, Handler<AsyncResult<Void>> handler) {
    createFile(path, perms).setHandler(handler);
    return this;
  }

  @Override
  public Future<Void> createFile(String path, String perms) {
    return createFileInternal(path, perms).run();
  }

  @Override
  public FileSystem createFileBlocking(String path, String perms) {
    createFileInternal(path, perms).perform();
    return this;
  }

  @Override
  public FileSystem exists(String path, Handler<AsyncResult<Boolean>> handler) {
    exists(path).setHandler(handler);
    return this;
  }

  @Override
  public Future<Boolean> exists(String path) {
    return existsInternal(path).run();
  }

  @Override
  public boolean existsBlocking(String path) {
    return existsInternal(path).perform();
  }

  @Override
  public FileSystem fsProps(String path, Handler<AsyncResult<FileSystemProps>> handler) {
    fsProps(path).setHandler(handler);
    return this;
  }

  @Override
  public Future<FileSystemProps> fsProps(String path) {
    return fsPropsInternal(path).run();
  }

  @Override
  public FileSystemProps fsPropsBlocking(String path) {
    return fsPropsInternal(path).perform();
  }

  @Override
  public FileSystem createTempDirectory(String prefix, Handler<AsyncResult<String>> handler) {
    createTempDirectory(prefix).setHandler(handler);
    return this;
  }

  @Override
  public Future<String> createTempDirectory(String prefix) {
    return createTempDirectoryInternal(null, prefix, null).run();
  }

  @Override
  public String createTempDirectoryBlocking(String prefix) {
    return createTempDirectoryInternal(null, prefix, null).perform();
  }

  @Override
  public FileSystem createTempDirectory(String prefix, String perms, Handler<AsyncResult<String>> handler) {
    createTempDirectory(prefix, perms).setHandler(handler);
    return this;
  }

  @Override
  public Future<String> createTempDirectory(String prefix, String perms) {
    return createTempDirectoryInternal(null, prefix, perms).run();
  }

  @Override
  public String createTempDirectoryBlocking(String prefix, String perms) {
    return createTempDirectoryInternal(null, prefix, perms).perform();
  }

  @Override
  public FileSystem createTempDirectory(String dir, String prefix, String perms, Handler<AsyncResult<String>> handler) {
    createTempDirectory(dir, prefix, perms).setHandler(handler);
    return this;
  }

  @Override
  public Future<String> createTempDirectory(String dir, String prefix, String perms) {
    return createTempDirectoryInternal(dir, prefix, perms).run();
  }

  @Override
  public String createTempDirectoryBlocking(String dir, String prefix, String perms) {
    return createTempDirectoryInternal(dir, prefix, perms).perform();
  }

  @Override
  public FileSystem createTempFile(String prefix, String suffix, Handler<AsyncResult<String>> handler) {
    createTempFile(prefix, suffix).setHandler(handler);
    return this;
  }

  @Override
  public Future<String> createTempFile(String prefix, String suffix) {
    return createTempFileInternal(null, prefix, suffix, null).run();
  }

  @Override
  public String createTempFileBlocking(String prefix, String suffix) {
    return createTempFileInternal(null, prefix, suffix, null).perform();
  }

  @Override
  public FileSystem createTempFile(String prefix, String suffix, String perms, Handler<AsyncResult<String>> handler) {
    createTempFile(prefix, suffix, perms).setHandler(handler);
    return this;
  }

  @Override
  public Future<String> createTempFile(String prefix, String suffix, String perms) {
    return createTempFileInternal(null, prefix, suffix, perms).run();
  }

  @Override
  public String createTempFileBlocking(String prefix, String suffix, String perms) {
    return createTempFileInternal(null, prefix, suffix, perms).perform();
  }


  @Override
  public FileSystem createTempFile(String dir, String prefix, String suffix, String perms, Handler<AsyncResult<String>> handler) {
    createTempFile(dir, prefix, suffix, perms).setHandler(handler);
    return this;
  }

  @Override
  public Future<String> createTempFile(String dir, String prefix, String suffix, String perms) {
    return createTempFileInternal(dir, prefix, suffix, perms).run();
  }

  @Override
  public String createTempFileBlocking(String dir, String prefix, String suffix, String perms) {
    return createTempFileInternal(dir, prefix, suffix, perms).perform();
  }

  private BlockingAction<Void> copyInternal(String from, String to, boolean recursive, CopyOptions options) {
    Objects.requireNonNull(from);
    Objects.requireNonNull(to);
    Objects.requireNonNull(options);
    return new BlockingAction<Void>() {
      public Void perform() {
        try {
          Path source = vertx.resolveFile(from).toPath();
          Path target = vertx.resolveFile(to).toPath();
          copy(source, target, recursive, options);
        } catch (IOException e) {
          throw new FileSystemException(e);
        }
        return null;
      }
    };
  }


  private static void copy(Path source, Path target, boolean recursive, CopyOptions options) throws IOException {
    Set<CopyOption> copyOptionSet = toCopyOptionSet(options);
    CopyOption[] copyOptions = copyOptionSet.toArray(new CopyOption[copyOptionSet.size()]);
    if (recursive) {
      Files.walkFileTree(source, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
        new SimpleFileVisitor<Path>() {
          public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
            throws IOException {
            Path targetDir = target.resolve(source.relativize(dir));
            try {
              Files.copy(dir, targetDir, copyOptions);
            } catch (FileAlreadyExistsException e) {
              if (!Files.isDirectory(targetDir)) {
                throw e;
              }
            }
            return FileVisitResult.CONTINUE;
          }

          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
            throws IOException {
            Files.copy(file, target.resolve(source.relativize(file)), copyOptions);
            return FileVisitResult.CONTINUE;
          }
        });
    } else {
      Files.copy(source, target, copyOptions);
    }
  }

  private BlockingAction<Void> moveInternal(String from, String to, CopyOptions options) {
    Objects.requireNonNull(from);
    Objects.requireNonNull(to);
    Objects.requireNonNull(options);
    Set<CopyOption> copyOptionSet = toCopyOptionSet(options);
    CopyOption[] copyOptions = copyOptionSet.toArray(new CopyOption[copyOptionSet.size()]);
    return new BlockingAction<Void>() {
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

  private BlockingAction<Void> truncateInternal(String p, long len) {
    Objects.requireNonNull(p);
    return new BlockingAction<Void>() {
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

  private BlockingAction<Void> chmodInternal(String path, String perms) {
    return chmodInternal(path, perms, null);
  }

  protected BlockingAction<Void> chmodInternal(String path, String perms, String dirPerms) {
    Objects.requireNonNull(path);
    Set<PosixFilePermission> permissions = PosixFilePermissions.fromString(perms);
    Set<PosixFilePermission> dirPermissions = dirPerms == null ? null : PosixFilePermissions.fromString(dirPerms);
    return new BlockingAction<Void>() {
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

  protected BlockingAction<Void> chownInternal(String path, String user, String group) {
    Objects.requireNonNull(path);
    return new BlockingAction<Void>() {
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

  private BlockingAction<FileProps> propsInternal(String path) {
    return props(path, true);
  }

  private BlockingAction<FileProps> lpropsInternal(String path) {
    return props(path, false);
  }

  private BlockingAction<FileProps> props(String path, boolean followLinks) {
    Objects.requireNonNull(path);
    return new BlockingAction<FileProps>() {
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

  private BlockingAction<Void> linkInternal(String link, String existing) {
    return link(link, existing, false);
  }

  private BlockingAction<Void> symlinkInternal(String link, String existing) {
    return link(link, existing, true);
  }

  private BlockingAction<Void> link(String link, String existing, boolean symbolic) {
    Objects.requireNonNull(link);
    Objects.requireNonNull(existing);
    return new BlockingAction<Void>() {
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

  private BlockingAction<Void> unlinkInternal(String link) {
    return deleteInternal(link);
  }

  private BlockingAction<String> readSymlinkInternal(String link) {
    Objects.requireNonNull(link);
    return new BlockingAction<String>() {
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

  private BlockingAction<Void> deleteInternal(String path) {
    return deleteInternal(path, false);
  }

  private BlockingAction<Void> deleteInternal(String path, boolean recursive) {
    Objects.requireNonNull(path);
    return new BlockingAction<Void>() {
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

  private BlockingAction<Void> mkdirInternal(String path) {
    return mkdirInternal(path, null, false);
  }

  private BlockingAction<Void> mkdirInternal(String path, boolean createParents) {
    return mkdirInternal(path, null, createParents);
  }

  private BlockingAction<Void> mkdirInternal(String path, String perms) {
    return mkdirInternal(path, perms, false);
  }

  protected BlockingAction<Void> mkdirInternal(String path, String perms, boolean createParents) {
    Objects.requireNonNull(path);
    FileAttribute<?> attrs = perms == null ? null : PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(perms));
    return new BlockingAction<Void>() {
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

  protected BlockingAction<String> createTempDirectoryInternal(String parentDir, String prefix, String perms) {
    FileAttribute<?> attrs = perms == null ? null : PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(perms));
    return new BlockingAction<String>() {
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

  protected BlockingAction<String> createTempFileInternal(String parentDir, String prefix, String suffix, String perms) {
    FileAttribute<?> attrs = perms == null ? null : PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(perms));
    return new BlockingAction<String>() {
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

  private BlockingAction<List<String>> readDirInternal(String path) {
    return readDirInternal(path, null);
  }

  private BlockingAction<List<String>> readDirInternal(String p, String filter) {
    Objects.requireNonNull(p);
    return new BlockingAction<List<String>>() {
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

  private BlockingAction<Buffer> readFileInternal(String path) {
    Objects.requireNonNull(path);
    return new BlockingAction<Buffer>() {
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

  private BlockingAction<Void> writeFileInternal(String path, Buffer data) {
    Objects.requireNonNull(path);
    Objects.requireNonNull(data);
    return new BlockingAction<Void>() {
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

  private BlockingAction<AsyncFile> openInternal(String p, OpenOptions options) {
    Objects.requireNonNull(p);
    Objects.requireNonNull(options);
    return new BlockingAction<AsyncFile>() {
      public AsyncFile perform() {
        String path = vertx.resolveFile(p).getAbsolutePath();
        return doOpen(path, options, context);
      }
    };
  }

  protected AsyncFile doOpen(String path, OpenOptions options, ContextInternal context) {
    return new AsyncFileImpl(vertx, path, options, context);
  }

  private BlockingAction<Void> createFileInternal(String path) {
    return createFileInternal(path, null);
  }

  protected BlockingAction<Void> createFileInternal(String p, String perms) {
    Objects.requireNonNull(p);
    FileAttribute<?> attrs = perms == null ? null : PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(perms));
    return new BlockingAction<Void>() {
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

  private BlockingAction<Boolean> existsInternal(String path) {
    Objects.requireNonNull(path);
    return new BlockingAction<Boolean>() {
      File file = vertx.resolveFile(path);
      public Boolean perform() {
        return file.exists();
      }
    };
  }

  private BlockingAction<FileSystemProps> fsPropsInternal(String path) {
    Objects.requireNonNull(path);
    return new BlockingAction<FileSystemProps>() {
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

    protected final ContextInternal context;

    public BlockingAction() {
      this.context = vertx.getOrCreateContext();
    }

    /**
     * Run the blocking action using a thread from the worker pool.
     */
    public Future<T> run() {
      return context.executeBlockingInternal(this);
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
