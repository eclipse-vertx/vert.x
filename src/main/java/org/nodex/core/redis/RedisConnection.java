package org.nodex.core.redis;

import org.nodex.core.Callback;
import org.nodex.core.NoArgCallback;
import org.nodex.core.Nodex;
import redis.clients.jedis.Jedis;
import org.nodex.core.composition.Completion;

/**
 * User: tim
 * Date: 04/07/11
 * Time: 13:03
 */
public class RedisConnection {
  private Jedis jedis;

  RedisConnection(Jedis jedis) {
    this.jedis = jedis;
  }

  public Completion set(final String key, final String value, final NoArgCallback onComplete) {
    final Completion df = new Completion();
    Nodex.instance.executeInBackground(new Runnable() {
      public void run() {
        jedis.set(key, value);
        onComplete.onEvent();
        df.complete();
      }
    });

    return df;
  }

  public Completion get(final String key, final Callback<String> resultCallback) {
    final Completion df = new Completion();
    Nodex.instance.executeInBackground(new Runnable() {
      public void run() {
        String val = jedis.get(key);
        resultCallback.onEvent(val);
        df.complete();
      }
    });
    return df;
  }

  public void close() {
    Nodex.instance.executeInBackground(new Runnable() {
      public void run() {
        jedis.disconnect();
      }
    });
  }


}
