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

package io.vertx.test.core;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.WriteStream;

/**
 * Send data aggressively to a write stream until it is closed. It will report any write failure when closing.
 * <p/>
 * This class must be used from the same thread to check that the stream close state (signaled by a write
 * exception) does not happen before the close event.
 */
public class CheckingSender {

  private static final Buffer data = Buffer.buffer("data");

  private final Context context;
  private final WriteStream<Buffer> stream;
  private Throwable error;
  private int countDown;

  public CheckingSender(Context context, WriteStream<Buffer> stream) {
    this(context, 1, stream);
  }

  public CheckingSender(Context context, int countDown, WriteStream<Buffer> stream) {
    this.context = context;
    this.stream = stream;
    this.countDown = countDown;
  }

  public void send() {
    if (Vertx.currentContext() == context) {
      if (countDown > 0) {
        try {
          stream.write(data);
        } catch (Exception e) {
          if (error == null) {
            error = e;
            return;
          }
        }
        context.owner().setTimer(1, id -> send());
      }
    } else {
      context.runOnContext(v -> send());
    }
  }

  public Throwable close() {
    countDown--;
    return error;
  }
}
