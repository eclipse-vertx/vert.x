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

package io.vertx.core.http;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.PfxOptions;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

/**
 * @author Thomas Segismont
 */
public class HttpServerCloseHookTest extends VertxTestBase {

  @Test
  public void deployHandlerShouldGetException() {
    vertx.deployVerticle(new TestVerticle(), onFailure(throwable -> {
      complete();
    }));
    await();
  }

  private static class TestVerticle extends AbstractVerticle {

    @Override
    public void start(Promise<Void> startPromise) {
      HttpServerOptions invalidOptions = new HttpServerOptions()
        .setSsl(true)
        .setPfxTrustOptions(new PfxOptions().setValue(Buffer.buffer("boom")));

      vertx.createHttpServer(invalidOptions).requestHandler(req -> {
        req.response().end("Hello World!");
      }).listen(8443, ar -> {
        if (ar.succeeded()) {
          startPromise.complete();
        } else {
          startPromise.fail(ar.cause());
        }
      });
    }
  }
}
