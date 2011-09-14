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

package org.nodex.java.addons.redis;

import org.nodex.java.core.composition.Composable;
import org.nodex.java.core.internal.NodexInternal;
import redis.clients.jedis.Jedis;

public class RedisConnection {
  private Jedis jedis;

  RedisConnection(Jedis jedis) {
    this.jedis = jedis;
  }

  public Composable set(final String key, final String value, final Runnable onComplete) {
    final Composable df = new Composable();
    NodexInternal.instance.executeInBackground(new Runnable() {
      public void run() {
        jedis.set(key, value);
        onComplete.run();
        df.complete();
      }
    });

    return df;
  }

  public Composable get(final String key, final ResultHandler resultHandler) {
    final Composable df = new Composable();
    NodexInternal.instance.executeInBackground(new Runnable() {
      public void run() {
        String val = jedis.get(key);
        resultHandler.onResult(val);
        df.complete();
      }
    });
    return df;
  }

  public void close() {
    NodexInternal.instance.executeInBackground(new Runnable() {
      public void run() {
        jedis.disconnect();
      }
    });
  }


}
