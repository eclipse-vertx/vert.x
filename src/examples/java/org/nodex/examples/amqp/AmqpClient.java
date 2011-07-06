package org.nodex.examples.amqp;

import com.rabbitmq.client.AMQP;
import org.nodex.core.Callback;
import org.nodex.core.NoArgCallback;
import org.nodex.core.amqp.AmqpMsgCallback;
import org.nodex.core.amqp.Channel;
import org.nodex.core.amqp.AmqpConnection;

/**
 * User: tim
 * Date: 04/07/11
 * Time: 10:35
 */
public class AmqpClient {

  private static String QUEUE_NAME = "my-queue";

  public static void main(String[] args) {
    org.nodex.core.amqp.AmqpClient.createClient().connect(new Callback<AmqpConnection>() {
      public void onEvent(AmqpConnection conn) {
        conn.createChannel(new Callback<Channel>() {
          public void onEvent(final Channel channel) {
            channel.declareQueue(QUEUE_NAME, false, true, true, new NoArgCallback() {
              public void onEvent() {
                channel.subscribe(QUEUE_NAME, true, new AmqpMsgCallback() {
                  public void onMessage(AMQP.BasicProperties props, byte[] body) {
                    System.out.println("Got message " + new String(body));
                  }
                });
                //Send some messages
                for (int i = 0; i < 10; i++) {
                  channel.publish("", QUEUE_NAME, null, "message " + i);
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
