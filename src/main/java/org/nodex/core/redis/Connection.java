package org.nodex.core.redis;

import org.nodex.core.Callback;
import org.nodex.core.NoArgCallback;
import org.nodex.core.Nodex;
import redis.clients.jedis.Jedis;

/**
 * User: tim
 * Date: 04/07/11
 * Time: 13:03
 */
public class Connection {
  private Jedis jedis;

  Connection(Jedis jedis) {
    this.jedis = jedis;
  }

  public void set(final String key, final String value, final NoArgCallback onComplete) {
    Nodex.instance.executeInBackground(new Runnable() {
      public void run() {
        jedis.set(key, value);
        onComplete.onEvent();
      }
    });
  }

  public void get(final String key, final Callback<String> resultCallback) {
    Nodex.instance.executeInBackground(new Runnable() {
      public void run() {
        String val = jedis.get(key);
        resultCallback.onEvent(val);
      }
    });
  }

  public void close() {
    Nodex.instance.executeInBackground(new Runnable() {
      public void run() {
        jedis.disconnect();
      }
    });
  }


}
