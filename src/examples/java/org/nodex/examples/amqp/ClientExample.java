package org.nodex.examples.amqp;

import org.nodex.core.DoneHandler;
import org.nodex.core.amqp.AmqpConnectHandler;
import org.nodex.core.amqp.AmqpConnection;
import org.nodex.core.amqp.AmqpMsgCallback;
import org.nodex.core.amqp.AmqpProps;
import org.nodex.core.amqp.Channel;
import org.nodex.core.amqp.ChannelHandler;

import java.io.IOException;

/**
 * User: tim
 * Date: 04/07/11
 * Time: 10:35
 */
public class ClientExample {

  private static String QUEUE_NAME = "my-queue";

  public static void main(String[] args) throws IOException {
    org.nodex.core.amqp.AmqpClient.createClient().connect(new AmqpConnectHandler() {
      public void onConnect(AmqpConnection conn) {
        conn.createChannel(new ChannelHandler() {
          public void onCreate(final Channel channel) {
            channel.declareQueue(QUEUE_NAME, false, true, true, new DoneHandler() {
              public void onDone() {
                System.out.println("declared ok");
                channel.subscribe(QUEUE_NAME, true, new AmqpMsgCallback() {
                  public void onMessage(AmqpProps props, byte[] body) {
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

    System.out.println("Any key to exit");
    System.in.read();
  }
}
