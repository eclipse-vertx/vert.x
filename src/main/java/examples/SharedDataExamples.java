/*
 * Copyright (c) 2014 Red Hat, Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package examples;

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

    sharedData.<String, String>getAsyncMap("mymap", res -> {
      if (res.succeeded()) {
        AsyncMap<String, String> map = res.result();
      } else {
        // Something went wrong!
      }
    });
  }

  public void localAsyncMap(Vertx vertx) {
    SharedData sharedData = vertx.sharedData();

    sharedData.<String, String>getLocalAsyncMap("mymap", res -> {
      if (res.succeeded()) {
        // Local-only async map
        AsyncMap<String, String> map = res.result();
      } else {
        // Something went wrong!
      }
    });
  }

  public void example3(AsyncMap<String, String> map) {
    map.put("foo", "bar", resPut -> {
      if (resPut.succeeded()) {
        // Successfully put the value
      } else {
        // Something went wrong!
      }
    });
  }

  public void example4(AsyncMap<String, String> map) {
    map.get("foo", resGet -> {
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

    sharedData.getLock("mylock", res -> {
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

  public void lockWithTimeout(Vertx vertx) {
    SharedData sharedData = vertx.sharedData();

    sharedData.getLockWithTimeout("mylock", 10000, res -> {
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

    sharedData.getLocalLock("mylock", res -> {
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

    sharedData.getCounter("mycounter", res -> {
      if (res.succeeded()) {
        Counter counter = res.result();
      } else {
        // Something went wrong!
      }
    });
  }

  public void localCounter(Vertx vertx) {
    SharedData sharedData = vertx.sharedData();

    sharedData.getLocalCounter("mycounter", res -> {
      if (res.succeeded()) {
        // Local-only counter
        Counter counter = res.result();
      } else {
        // Something went wrong!
      }
    });
  }
}
