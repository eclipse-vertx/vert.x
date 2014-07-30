/*
 * Copyright 2014 Red Hat, Inc.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.file;

import io.vertx.codegen.annotations.Options;
import io.vertx.core.ServiceHelper;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.OpenOptionsFactory;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@Options
public interface OpenOptions {

  static OpenOptions options() {
    return factory.options();
  }

  static OpenOptions optionsFromJson(JsonObject json) {
    return factory.options(json);
  }

  String getPerms();

  OpenOptions setPerms(String perms);

  boolean isRead();

  OpenOptions setRead(boolean read);

  boolean isWrite();

  OpenOptions setWrite(boolean write);

  boolean isCreate();

  OpenOptions setCreate(boolean create);

  boolean isCreateNew();

  OpenOptions setCreateNew(boolean createNew);

  boolean isDeleteOnClose();

  OpenOptions setDeleteOnClose(boolean deleteOnClose);

  boolean isTruncateExisting();

  OpenOptions setTruncateExisting(boolean truncateExisting);

  boolean isSparse();

  OpenOptions setSparse(boolean sparse);

  boolean isSync();

  OpenOptions setSync(boolean sync);

  boolean isDSync();

  OpenOptions setDSync(boolean dsync);

  static final OpenOptionsFactory factory = ServiceHelper.loadFactory(OpenOptionsFactory.class);

}
