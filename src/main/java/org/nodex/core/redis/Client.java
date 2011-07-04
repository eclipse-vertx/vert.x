package org.nodex.core.redis;

import org.nodex.core.Callback;
import org.nodex.core.Nodex;
import redis.clients.jedis.Jedis;

import java.io.IOException;

/**
 * User: tim
 * Date: 04/07/11
 * Time: 13:00
 */
public class Client {
  public static Client createClient() {
    return new Client();
  }

  private Client() {
  }

  public void connect(int port, String host, final Callback<Connection> connectCallback) {
    final Jedis jedis = new Jedis(host, port);
    Nodex.instance.executeInBackground(new Runnable() {
      public void run() {
        System.out.println("Connecting jedis");
        jedis.connect();
        System.out.println("Connected ok");
        connectCallback.onEvent(new Connection(jedis));
      }
    });
  }
}
