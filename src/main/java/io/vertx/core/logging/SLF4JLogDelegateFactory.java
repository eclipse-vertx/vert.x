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

package io.vertx.core.logging;

import io.vertx.core.spi.logging.LogDelegate;
import io.vertx.core.spi.logging.LogDelegateFactory;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.NOPLoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

/*
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class SLF4JLogDelegateFactory implements LogDelegateFactory {

  static {
    // Check we have a valid ILoggerFactory
    // Replace the error stream since SLF4J will actually log the classloading error
    // when no implementation is available
    PrintStream err = System.err;
    try {
      System.setErr(new PrintStream(new ByteArrayOutputStream()));
      LoggerFactory.getILoggerFactory();
    } finally {
      System.setErr(err);
    }
  }

  @Override
  public boolean isAvailable() {
    // SLF might be available on the classpath but without configuration
    ILoggerFactory fact = LoggerFactory.getILoggerFactory();
    return !(fact instanceof NOPLoggerFactory);
  }

  public LogDelegate createDelegate(final String clazz) {
    return new SLF4JLogDelegate(clazz);
  }
}
