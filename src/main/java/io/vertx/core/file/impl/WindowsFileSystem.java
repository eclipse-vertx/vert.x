/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.file.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.Objects;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author Juergen Donnerstag
 */
public class WindowsFileSystem extends FileSystemImpl {

  private static final Logger log = LoggerFactory.getLogger(WindowsFileSystem.class);

  public WindowsFileSystem(final VertxInternal vertx) {
    super(vertx);
  }

  private static void logInternal(final String perms) {
    if (perms != null && log.isDebugEnabled()) {
      log.debug("You are running on Windows and POSIX style file permissions are not supported");
    }
  }

  @Override
  protected BlockingAction<Void> chmodInternal(String path, String perms, String dirPerms,
                                               Handler<AsyncResult<Void>> handler) {
    Objects.requireNonNull(path);
    Objects.requireNonNull(perms);
    logInternal(perms);
    logInternal(dirPerms);
    if (log.isDebugEnabled()) {
      log.debug("You are running on Windows and POSIX style file permissions are not supported!");
    }
    return new BlockingAction<Void>(handler) {
      @Override
      public Void perform() {
        return null;
      }
    };
  }

  @Override
  protected BlockingAction<Void> mkdirInternal(String path, final String perms, final boolean createParents,
                                               Handler<AsyncResult<Void>> handler) {
    logInternal(perms);
    return super.mkdirInternal(path, null, createParents, handler);
  }

  @Override
  protected AsyncFile doOpen(String path, OpenOptions options,
                             ContextInternal context) {
    logInternal(options.getPerms());
    return new AsyncFileImpl(vertx, path, options, context);
  }

  @Override
  protected BlockingAction<Void> createFileInternal(String p, final String perms, Handler<AsyncResult<Void>> handler) {
    logInternal(perms);
    return super.createFileInternal(p, null, handler);
  }

  @Override
  protected BlockingAction<Void> chownInternal(String path, String user, String group, Handler<AsyncResult<Void>> handler) {
    if (group != null && log.isDebugEnabled()) {
      log.debug("You are running on Windows and POSIX style file ownership is not supported");
    }
    return super.chownInternal(path, user, group, handler);
  }
}
