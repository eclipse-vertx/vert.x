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

package io.vertx.test.core;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.eventbus.ReplyFailure;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Thomas Segismont
 */
public class FaultToleranceVerticle extends AbstractVerticle {
  private static final Logger log = LoggerFactory.getLogger(FaultToleranceVerticle.class);

  private int id;
  private int numAddresses;

  @Override
  public void start() throws Exception {
    JsonObject config = config();
    id = config.getInteger("id");
    numAddresses = config.getInteger("addressesCount");
    List<Future> registrationFutures = new ArrayList<>(numAddresses);
    for (int i = 0; i < numAddresses; i++) {
      Future<Void> registrationFuture = Future.future();
      registrationFutures.add(registrationFuture);
      vertx.eventBus().consumer(createAddress(id, i), msg -> msg.reply("pong")).completionHandler(registrationFuture.completer());
    }
    Future<Void> registrationFuture = Future.future();
    registrationFutures.add(registrationFuture);
    vertx.eventBus().consumer("ping", this::ping).completionHandler(registrationFuture.completer());
    CompositeFuture.all(registrationFutures).setHandler(ar -> {
      if (ar.succeeded()) {
        vertx.eventBus().send("control", "start");
      }
    });
  }

  private void ping(Message<JsonArray> message) {
    JsonArray jsonArray = message.body();
    for (int i = 0; i < jsonArray.size(); i++) {
      int node = jsonArray.getInteger(i);
      for (int j = 0; j < numAddresses; j++) {
        vertx.eventBus().send(createAddress(node, j), "ping", ar -> {
          if (ar.succeeded()) {
            vertx.eventBus().send("control", "pong");
          } else {
            Throwable cause = ar.cause();
            if (cause instanceof ReplyException) {
              ReplyException replyException = (ReplyException) cause;
              if (replyException.failureType() == ReplyFailure.NO_HANDLERS) {
                vertx.eventBus().send("control", "noHandlers");
                return;
              }
            }
            log.error("Unexpected error during ping (id=" + id + ")", cause);
          }
        });
      }
    }
  }

  private String createAddress(int id, int i) {
    return "address-" + id + "-" + i;
  }
}
