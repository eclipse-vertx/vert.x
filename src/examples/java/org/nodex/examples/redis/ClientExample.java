/*
 * Copyright 2002-2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.nodex.examples.redis;

import org.nodex.mods.redis.RedisClient;
import org.nodex.mods.redis.RedisConnectHandler;
import org.nodex.mods.redis.RedisConnection;
import org.nodex.mods.redis.ResultHandler;

public class ClientExample {
  public static void main(String[] args) throws Exception {
    RedisClient.createClient().connect(6379, "localhost", new RedisConnectHandler() {
      public void onConnect(final RedisConnection conn) {
        conn.set("foo", "bar", new Runnable() {
          public void run() {
            conn.get("foo", new ResultHandler() {
              public void onResult(String val) {
                System.out.println("Value of key foo is " + val);
              }
            });
          }
        });
      }
    });

    System.out.println("Any key to exit");
    System.in.read();
  }
}
