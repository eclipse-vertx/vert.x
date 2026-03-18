/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.net.impl;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.logging.Logger;

import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ConnectRetry {

  public static <T> void connectWithRetries(Logger log,
                                        Supplier<Future<T>> supplier,
                                        ContextInternal context,
                                        Promise<T> promise,
                                        Duration reconnectInterval,
                                        int remainingAttempts) {
    Future<T> res = supplier.get();
    res.onComplete(ar -> {
      if (ar.succeeded()) {
        promise.complete(ar.result());
      } else {
        Throwable cause = ar.cause();
        // FileNotFoundException for domain sockets
        boolean connectError = cause instanceof ConnectException || cause instanceof FileNotFoundException;
        if (connectError && (remainingAttempts > 0 || remainingAttempts == -1)) {
          context.emit(v -> {
            log.debug("Failed to create connection. Will retry in " + reconnectInterval);
            // Set a timer to retry connection
            context.setTimer(reconnectInterval.toMillis(), tid ->
              connectWithRetries(
                log,
                supplier,
                context,
                promise,
                reconnectInterval,
                remainingAttempts == -1 ? remainingAttempts : remainingAttempts - 1)
            );
          });
        } else {
          promise.fail(cause);
        }
      }
    });
  }
}
