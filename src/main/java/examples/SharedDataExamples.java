/*
 * Copyright 2014 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package examples;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.*;
import io.vertx.core.shareddata.*;

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

    sd.<String, String>getClusterWideMap("mymap", res -> {
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
