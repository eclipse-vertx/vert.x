package org.nodex.examples.amqp;

import org.nodex.core.Callback;
import org.nodex.core.NoArgCallback;
import org.nodex.core.amqp.Channel;
import org.nodex.core.amqp.Client;
import org.nodex.core.amqp.Connection;

/**
 * User: tim
 * Date: 04/07/11
 * Time: 10:35
 */
public class ClientExample {

  private static String QUEUE_NAME = "my-queue";

  public static void main(String[] args) {
    Client.createClient().connect(new Callback<Connection>() {
      public void onEvent(Connection conn) {
        conn.createChannel(new Callback<Channel>() {
          public void onEvent(final Channel channel) {
            channel.declare(QUEUE_NAME, false, true, true, new NoArgCallback() {
              public void onEvent() {
                channel.subscribe(QUEUE_NAME, true, new Callback<String>() {
                  public void onEvent(String msg) {
                    System.out.println("Got message " + msg);
                  }
                });
                //Send some messages
                for (int i = 0; i < 10; i++) {
                  channel.publish("", QUEUE_NAME, "message " + i);
                }
                System.out.println("Sent messages");
              }
            });
          }
        });
      }
    });
  }
}
