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
package io.vertx.core.http.impl;

import io.netty.buffer.ByteBuf;
import io.vertx.core.Future;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.internal.http.ContentDecoder;
import io.vertx.core.internal.http.FlowController;

/**
 * Wraps a content decoder and reports its outcome with a {@link Future}, completed with the result of the decoder or
 * failed when the decoder fails or is closed before the end of the content. When the decoder fails, the decoder
 * is closed with the failure and the rest of the content is discarded. A failure of the decoder close is reported to
 * the context.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class SafeContentDecoder<T> implements ContentDecoder<Void> {

  private final ContentDecoder<T> decoder;
  private final ContextInternal context;
  private final PromiseInternal<T> promise;
  private FlowController flowController;
  private boolean failed;

  public SafeContentDecoder(ContentDecoder<T> decoder, ContextInternal context) {
    this.decoder = decoder;
    this.context = context;
    this.promise = context.promise();
  }

  public Future<T> future() {
    return promise.future();
  }

  @Override
  public void init(FlowController flowController) {
    this.flowController = flowController;
    decoder.init(flowController);
  }

  @Override
  public void handle(ByteBuf chunk) {
    if (!failed) {
      try {
        decoder.handle(chunk);
      } catch (Throwable err) {
        fail(err);
      }
    }
  }

  @Override
  public Void handleEnd() {
    if (!failed) {
      T result;
      try {
        result = decoder.handleEnd();
      } catch (Throwable err) {
        fail(err);
        return null;
      }
      promise.complete(result);
    }
    return null;
  }

  @Override
  public void handleClose(Throwable cause) {
    if (!failed) {
      if (cause != null) {
        promise.tryFail(cause);
      }
      close(cause);
    }
  }

  private void fail(Throwable err) {
    failed = true;
    flowController.resume();
    promise.fail(err);
    close(err);
  }

  private void close(Throwable cause) {
    try {
      decoder.handleClose(cause);
    } catch (Throwable err) {
      context.reportException(err);
    }
  }
}
