package org.nodex.core.amqp;

import org.nodex.core.DoneHandler;
import org.nodex.core.Nodex;

import java.io.IOException;

/**
 * User: timfox
 * Date: 02/07/2011
 * Time: 07:22
 */
public class AmqpConnection {

  private com.rabbitmq.client.Connection conn;

  AmqpConnection(com.rabbitmq.client.Connection conn) {
    this.conn = conn;
  }

  public void createChannel(final ChannelHandler channelHandler) {
    Nodex.instance.executeInBackground(new Runnable() {
      public void run() {
        try {
          channelHandler.onCreate(new Channel(conn.createChannel()));
        } catch (IOException e) {
          //TODO handle exception by passing them back on callback
          e.printStackTrace();
        }
      }
    });
  }

  public void close(final DoneHandler doneCallback) {
    Nodex.instance.executeInBackground(new Runnable() {
      public void run() {
        try {
          conn.close();
          //FIXME - again this is sync
          doneCallback.onDone();
        } catch (IOException e) {
          //TODO handle exception by passing them back on callback
          e.printStackTrace();
        }
      }
    });
  }
}
