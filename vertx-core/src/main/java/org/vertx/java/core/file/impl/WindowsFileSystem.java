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
import org.vertx.java.core.file.AsyncFile;
import org.vertx.java.core.impl.BlockingAction;
import org.vertx.java.core.impl.DefaultContext;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author Juergen Donnerstag
 */
public class WindowsFileSystem extends DefaultFileSystem {

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
    logInternal(perms);
    logInternal(dirPerms);
    return new BlockingAction<Void>(vertx, handler) {
      @Override
      public Void action() {
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
  protected AsyncFile doOpen(String path, String perms, boolean read, boolean write, boolean createNew, boolean flush,
                             DefaultContext context) {
    logInternal(perms);
    return new DefaultAsyncFile(vertx, path, null, read, write, createNew, flush, context);
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
