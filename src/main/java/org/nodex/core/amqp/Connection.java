package org.nodex.core.amqp;

import org.nodex.core.Callback;
import org.nodex.core.NoArgCallback;
import org.nodex.core.Nodex;

import java.io.IOException;

/**
 * User: timfox
 * Date: 02/07/2011
 * Time: 07:22
 */
public class Connection {

  private com.rabbitmq.client.Connection conn;

  Connection(com.rabbitmq.client.Connection conn) {
    this.conn = conn;
  }

  public void createChannel(final Callback<Channel> channelCallback) throws IOException {
     Nodex.instance.executeInBackground(new Runnable() {
      public void run() {
        try {
          channelCallback.onEvent(new Channel(conn.createChannel()));
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
          conn.close();
          //FIXME - again this is sync
          doneCallback.onEvent();
        } catch (IOException e) {
          //TODO handle exception by passing them back on callback
        }
      }
    });
  }
}
