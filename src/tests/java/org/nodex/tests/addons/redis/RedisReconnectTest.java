/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
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
import org.nodex.java.core.CompletionHandler;
import org.nodex.java.core.Future;
import org.nodex.java.core.Handler;
import org.nodex.java.core.Nodex;
import org.nodex.java.core.buffer.Buffer;
import org.nodex.java.core.logging.Logger;
import org.nodex.tests.core.TestBase;
import org.testng.annotations.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 *
 * TODO
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class RedisReconnectTest extends TestBase {

  private static final Logger log = Logger.getLogger(RedisReconnectTest.class);

  @Test
  public void testConnectionFailure() throws Exception {

    final CountDownLatch latch = new CountDownLatch(1);

    Nodex.instance.go(new Runnable() {
      public void run() {
        RedisPool pool = new RedisPool();

        pool.setMaxPoolSize(1);
        pool.setReconnectAttempts(1000);

        final RedisConnection conn = pool.connection();

        log.info("Executing a set");
        conn.set(Buffer.create("key1"), Buffer.create("val1")).handler(new CompletionHandler<Void>() {
          public void handle(Future<Void> future) {
            log.info("Result of set returned");
          }
        }).execute();

        log.info("Now sleeping. Please kill redis");

        Nodex.instance.setTimer(10000, new Handler<Long>() {
          public void handle(Long timerID) {

            log.info("Executing a get");
            conn.get(Buffer.create("key1")).handler(new CompletionHandler<Buffer>() {
              public void handle(Future<Buffer> future) {
                log.info("Result of get returned " + future.result());

                conn.close();
                latch.countDown();
              }
            }).execute();
          }
        });
      }
    });

    assert(latch.await(1000000, TimeUnit.SECONDS));

  }

  @Test
  public void testConnectionFailureWhenInPool() throws Exception {

    final CountDownLatch latch = new CountDownLatch(1);

    Nodex.instance.go(new Runnable() {
      public void run() {
        final RedisPool pool = new RedisPool();

        pool.setMaxPoolSize(1);
        pool.setReconnectAttempts(1000000);

        RedisConnection conn1 = pool.connection();

        log.info("Executing a set");
        conn1.set(Buffer.create("key1"), Buffer.create("val1")).handler(new CompletionHandler<Void>() {
          public void handle(Future<Void> future) {
            log.info("Result of set returned");
          }
        }).execute();

        conn1.close();

        log.info("Now sleeping. Please kill redis");

        Nodex.instance.setTimer(10000, new Handler<Long>() {
          public void handle(Long timerID) {

            final RedisConnection conn2 = pool.connection();

            log.info("Executing a get");
            conn2.get(Buffer.create("key1")).handler(new CompletionHandler<Buffer>() {
              public void handle(Future<Buffer> future) {
                log.info("Result of get returned " + future.result());

                conn2.close();
                latch.countDown();
              }
            }).execute();
          }
        });
      }
    });

    assert(latch.await(100000, TimeUnit.SECONDS));

  }
}
