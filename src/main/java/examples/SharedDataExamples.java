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
import io.vertx.core.buffer.Buffer;
import io.vertx.core.shareddata.*;

/**
 * Created by tim on 19/01/15.
 */
public class SharedDataExamples {

  public void localMap(Vertx vertx) {
    SharedData sharedData = vertx.sharedData();

    LocalMap<String, String> map1 = sharedData.getLocalMap("mymap1");

    map1.put("foo", "bar"); // Strings are immutable so no need to copy

    LocalMap<String, Buffer> map2 = sharedData.getLocalMap("mymap2");

    map2.put("eek", Buffer.buffer().appendInt(123)); // This buffer will be copied before adding to map

    // Then... in another part of your application:

    map1 = sharedData.getLocalMap("mymap1");

    String val = map1.get("foo");

    map2 = sharedData.getLocalMap("mymap2");

    Buffer buff = map2.get("eek");
  }

  public void asyncMap(Vertx vertx) {
    SharedData sharedData = vertx.sharedData();

    sharedData.
      <String, String>getAsyncMap("mymap")
      .onComplete(res -> {
        if (res.succeeded()) {
          AsyncMap<String, String> map = res.result();
        } else {
          // Something went wrong!
        }
      });
  }

  public void localAsyncMap(Vertx vertx) {
    SharedData sharedData = vertx.sharedData();

    sharedData.
      <String, String>getLocalAsyncMap("mymap")
      .onComplete(res -> {
        if (res.succeeded()) {
          // Local-only async map
          AsyncMap<String, String> map = res.result();
        } else {
          // Something went wrong!
        }
      });
  }

  public void example3(AsyncMap<String, String> map) {
    map
      .put("foo", "bar")
      .onComplete(resPut -> {
        if (resPut.succeeded()) {
          // Successfully put the value
        } else {
          // Something went wrong!
        }
      });
  }

  public void example4(AsyncMap<String, String> map) {
    map
      .get("foo")
      .onComplete(resGet -> {
        if (resGet.succeeded()) {
          // Successfully got the value
          Object val = resGet.result();
        } else {
          // Something went wrong!
        }
      });
  }

  public void lock(Vertx vertx) {
    SharedData sharedData = vertx.sharedData();

    sharedData
      .getLock("mylock")
      .onComplete(res -> {
        if (res.succeeded()) {
          // Got the lock!
          Lock lock = res.result();

          // 5 seconds later we release the lock so someone else can get it

          vertx.setTimer(5000, tid -> lock.release());

        } else {
          // Something went wrong
        }
      });
  }

  private Future<String> getAsyncString() {
    throw new UnsupportedOperationException();
  }

  public void withLock(Vertx vertx) {
    SharedData sharedData = vertx.sharedData();

    Future<String> res = sharedData.withLock("mylock", () -> {
      // Obtained the lock!
      Future<String> future = getAsyncString();
      // It will be released upon completion of this future
      return future;
    });
  }

  public void lockWithTimeout(Vertx vertx) {
    SharedData sharedData = vertx.sharedData();

    sharedData
      .getLockWithTimeout("mylock", 10000)
      .onComplete(res -> {
        if (res.succeeded()) {
          // Got the lock!
          Lock lock = res.result();

        } else {
          // Failed to get lock
        }
      });
  }

  public void localLock(Vertx vertx) {
    SharedData sharedData = vertx.sharedData();

    sharedData
      .getLocalLock("mylock")
      .onComplete(res -> {
        if (res.succeeded()) {
          // Local-only lock
          Lock lock = res.result();

          // 5 seconds later we release the lock so someone else can get it

          vertx.setTimer(5000, tid -> lock.release());

        } else {
          // Something went wrong
        }
      });
  }

  public void counter(Vertx vertx) {
    SharedData sharedData = vertx.sharedData();

    sharedData
      .getCounter("mycounter")
      .onComplete(res -> {
        if (res.succeeded()) {
          Counter counter = res.result();
        } else {
          // Something went wrong!
        }
      });
  }

  public void localCounter(Vertx vertx) {
    SharedData sharedData = vertx.sharedData();

    sharedData
      .getLocalCounter("mycounter")
      .onComplete(res -> {
        if (res.succeeded()) {
          // Local-only counter
          Counter counter = res.result();
        } else {
          // Something went wrong!
        }
      });
  }
}
