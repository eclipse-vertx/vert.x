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

package examples;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Examples of the Future / CompletionStage interoperability.
 *
 * @author <a href="https://julien.ponge.org/">Julien Ponge</a>
 */
public class CompletionStageInteropExamples {

  public void toCS(Vertx vertx) {
    Future<String> future = vertx.createDnsClient().lookup("vertx.io");
    future.toCompletionStage().whenComplete((ip, err) -> {
      if (err != null) {
        System.err.println("Could not resolve vertx.io");
        err.printStackTrace();
      } else {
        System.out.println("vertx.io => " + ip);
      }
    });
  }

  private Future<String> storeInDb(String key, String value) {
    return Future.succeededFuture("Yo");
  }

  public void fromCS(Vertx vertx, CompletionStage<String> completionStage) {
    Future.fromCompletionStage(completionStage, vertx.getOrCreateContext())
      .flatMap(str -> {
        String key = UUID.randomUUID().toString();
        return storeInDb(key, str);
      })
      .onSuccess(str -> {
        System.out.println("We have a result: " + str);
      })
      .onFailure(err -> {
        System.err.println("We have a problem");
        err.printStackTrace();
      });
  }
}
