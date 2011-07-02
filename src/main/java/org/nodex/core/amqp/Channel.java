package org.nodex.core.amqp;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import org.nodex.core.Callback;
import org.nodex.core.NoArgCallback;
import org.nodex.core.Nodex;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

/**
 * User: timfox
 * Date: 02/07/2011
 * Time: 07:25
 */
public class Channel {
  private com.rabbitmq.client.Channel channel;

  Channel(com.rabbitmq.client.Channel channel) {
    this.channel = channel;
  }

  public void publish(String exchange, String routingKey, String message) throws IOException {
    try {
      channel.basicPublish(exchange, routingKey, null, message.getBytes("UTF-8"));
    } catch (UnsupportedEncodingException willNeverHappen) {
    }
  }

  public void declare(final String queueName, final boolean durable, final boolean exclusive, final boolean autoDelete,
                      final NoArgCallback doneCallback) {
    Nodex.instance.executeInBackground(new Runnable() {
      public void run() {
        try {
          channel.queueDeclare(queueName, durable, exclusive, autoDelete, null);
          doneCallback.onEvent();
        } catch (IOException e) {
          //TODO handle exception by passing them back on callback
        }
      }
    });


  }

  public void subscribe(final String queueName, final boolean autoAck, final Callback<String> messageCallback) throws IOException {
    Nodex.instance.executeInBackground(new Runnable() {
      public void run() {
        try {
          channel.basicConsume(queueName, autoAck, "blah",
              new DefaultConsumer(channel) {
                @Override
                public void handleDelivery(String consumerTag,
                                           Envelope envelope,
                                           AMQP.BasicProperties properties,
                                           byte[] body)
                    throws IOException {
                  messageCallback.onEvent(new String(body, "UTF-8"));
                }
              });
        } catch (IOException e) {
          //TODO handle exception by passing them back on callback
        }
      }
    });
  }


  public void close(final NoArgCallback doneCallback) throws IOException {
    Nodex.instance.executeInBackground(new Runnable() {
      public void run() {
        try {
          channel.close();
          doneCallback.onEvent();
        } catch (IOException e) {
          //TODO handle exception by passing them back on callback
        }
      }
    });
  }

}
