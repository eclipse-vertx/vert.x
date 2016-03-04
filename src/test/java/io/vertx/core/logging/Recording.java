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
import java.util.logging.*;
import java.util.logging.Logger;

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

  private void stop() {
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
