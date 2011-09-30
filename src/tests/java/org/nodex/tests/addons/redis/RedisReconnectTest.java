/*
 * Copyright 2011 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.nodex.tests.addons.redis;

import org.nodex.java.addons.redis.RedisConnection;
import org.nodex.java.addons.redis.RedisPool;
import org.nodex.java.core.Handler;
import org.nodex.java.core.Nodex;
import org.nodex.tests.core.TestBase;
import org.testng.annotations.Test;

/**
 *
 * TODO
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class RedisReconnectTest extends TestBase {

  @Test
  public void test1() throws Exception {

    Nodex.instance.go(new Runnable() {
      public void run() {
        RedisPool pool = new RedisPool();

        pool.setMaxPoolSize(1);

        final RedisConnection conn = pool.connection();

        conn.ping().execute();

        Nodex.instance.setTimer(10000, new Handler<Long>() {
          public void handle(Long timerID) {
            System.out.println("Timer fired");
            conn.ping().execute();
          }
        });
      }
    });

    System.out.println("Sleeping");
    Thread.sleep(30000);
    System.out.println("Done");

  }
}
