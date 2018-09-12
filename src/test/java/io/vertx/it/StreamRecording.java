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

package io.vertx.it;

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
