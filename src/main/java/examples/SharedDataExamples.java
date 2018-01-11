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
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.Counter;
import io.vertx.core.shareddata.LocalMap;
import io.vertx.core.shareddata.Lock;
import io.vertx.core.shareddata.SharedData;

/**
 * Created by tim on 19/01/15.
 */
public class SharedDataExamples {

  public void example1(Vertx vertx) {

    SharedData sd = vertx.sharedData();

    LocalMap<String, String> map1 = sd.getLocalMap("mymap1");

    map1.put("foo", "bar"); // Strings are immutable so no need to copy

    LocalMap<String, Buffer> map2 = sd.getLocalMap("mymap2");

    map2.put("eek", Buffer.buffer().appendInt(123)); // This buffer will be copied before adding to map

    // Then... in another part of your application:

    map1 = sd.getLocalMap("mymap1");

    String val = map1.get("foo");

    map2 = sd.getLocalMap("mymap2");

    Buffer buff = map2.get("eek");
  }

  public void example2(Vertx vertx) {

    SharedData sd = vertx.sharedData();

    sd.<String, String>getAsyncMap("mymap", res -> {
      if (res.succeeded()) {
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

  public void example5(Vertx vertx, SharedData sd) {
    sd.getLock("mylock", res -> {
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

  public void example6(SharedData sd) {
    sd.getLockWithTimeout("mylock", 10000, res -> {
      if (res.succeeded()) {
        // Got the lock!
        Lock lock = res.result();

      } else {
        // Failed to get lock
      }
    });
  }

  public void example7(SharedData sd) {
    sd.getCounter("mycounter", res -> {
      if (res.succeeded()) {
        Counter counter = res.result();
      } else {
        // Something went wrong!
      }
    });
  }



}
