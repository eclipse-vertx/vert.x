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
import java.util.logging.Handler;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;

/**
 * A helper class registering "error" output.
 *
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class Recording {

  private ByteArrayOutputStream error = new ByteArrayOutputStream();
  private Handler handler = new StreamHandler(error, new SimpleFormatter());
  private Logger logger = LogManager.getLogManager().getLogger("");

  public Recording() throws IOException {
  }

  private void start() {
    error.reset();
    logger.addHandler(handler);
  }

  public void stop() {
    logger.removeHandler(handler);
  }

  public String get() {
    handler.flush();
    return error.toString();
  }

  public String execute(Runnable runnable) {
    start();
    String result;
    try {
      runnable.run();
      result = get();
    } finally {
      stop();
    }
    return result;
  }

}
