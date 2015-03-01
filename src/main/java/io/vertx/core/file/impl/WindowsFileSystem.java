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
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.impl.LoggerFactory;

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
                             ContextImpl context) {
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
