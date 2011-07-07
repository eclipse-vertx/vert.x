package org.nodex.examples.redis;

import org.nodex.core.DoneHandler;
import org.nodex.core.redis.RedisClient;
import org.nodex.core.redis.RedisConnectHandler;
import org.nodex.core.redis.RedisConnection;
import org.nodex.core.redis.ResultHandler;

/**
 * User: tim
 * Date: 04/07/11
 * Time: 13:15
 */
public class ClientExample {
  public static void main(String[] args) throws Exception {
    RedisClient.createClient().connect(6379, "localhost", new RedisConnectHandler() {
      public void onConnect(final RedisConnection conn) {
        conn.set("foo", "bar", new DoneHandler() {
          public void onDone() {
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
