/*
 *  Copyright (c) 2011-2015 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *       The Eclipse Public License is available at
 *       http://www.eclipse.org/legal/epl-v10.html
 *
 *       The Apache License v2.0 is available at
 *       http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.logging;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.logging.LogManager;

/**
 * A helper class registering "error" output.
 *
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class StreamRecording {
  private static final PrintStream ORIGINAL_ERR = System.err;

  private ByteArrayOutputStream error = new ByteArrayOutputStream();

  public StreamRecording() throws IOException {
    // Clear and reload as the stream may have been cached.
    LogManager.getLogManager().reset();
    LogManager.getLogManager().readConfiguration();
  }

  public void start() {
    error.reset();
    System.setErr(new PrintStream(error));
  }

  public void stop() {
    if (System.err != ORIGINAL_ERR) {
      System.setErr(ORIGINAL_ERR);
    }
  }

  public String get() {
    try {
      error.flush();
    } catch (IOException e) {
      // Ignore it.
    }
    return error.toString();
  }

  public void terminate() {
    if (System.err != ORIGINAL_ERR) {
      System.setErr(ORIGINAL_ERR);
    }
  }

  public String execute(Runnable runnable) {
    start();
    runnable.run();
    String result = get();
    stop();
    return result;
  }

}