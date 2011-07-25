package org.nodex.core.redis;

import org.nodex.core.DoneHandler;
import org.nodex.core.Nodex;
import org.nodex.core.composition.Completion;
import redis.clients.jedis.Jedis;

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

  public Completion set(final String key, final String value, final DoneHandler onComplete) {
    final Completion df = new Completion();
    Nodex.instance.executeInBackground(new Runnable() {
      public void run() {
        jedis.set(key, value);
        onComplete.onDone();
        df.complete();
      }
    });

    return df;
  }

  public Completion get(final String key, final ResultHandler resultHandler) {
    final Completion df = new Completion();
    Nodex.instance.executeInBackground(new Runnable() {
      public void run() {
        String val = jedis.get(key);
        resultHandler.onResult(val);
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
