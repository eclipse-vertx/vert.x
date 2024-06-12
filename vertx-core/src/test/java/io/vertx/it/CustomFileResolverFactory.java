/*
 * Copyright (c) 2011-2021 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.it;

import io.vertx.core.VertxOptions;
import io.vertx.core.spi.FileResolverFactory;
import io.vertx.core.spi.file.FileResolver;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static io.vertx.it.CustomExecutorServiceFactory.NUM;

public class CustomFileResolverFactory implements FileResolverFactory {

  @Override
  public FileResolver resolver(VertxOptions options) {
    return new CustomFileResolver();
  }
}
