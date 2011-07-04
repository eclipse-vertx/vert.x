package org.nodex.examples.redis;

import org.nodex.core.Callback;
import org.nodex.core.NoArgCallback;
import org.nodex.core.redis.Client;
import org.nodex.core.redis.Connection;

/**
 * User: tim
 * Date: 04/07/11
 * Time: 13:15
 */
public class RedisClient {
  public static void main(String[] args) throws Exception {
    Client.createClient().connect(6379, "localhost", new Callback<Connection>() {
      public void onEvent(final Connection conn) {
        conn.set("foo", "bar", new NoArgCallback() {
          public void onEvent() {
            conn.get("foo", new Callback<String>() {
              public void onEvent(String val) {
                System.out.println("Value of key foo is " + val);
              }
            });
          }
        });
      }
    });
  }
}
