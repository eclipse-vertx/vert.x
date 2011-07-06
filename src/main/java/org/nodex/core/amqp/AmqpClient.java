package org.nodex.core.amqp;

import com.rabbitmq.client.ConnectionFactory;
import org.nodex.core.Callback;
import org.nodex.core.Nodex;

import java.io.IOException;

/**
 * User: timfox
 * Date: 02/07/2011
 * Time: 07:12
 */
public class AmqpClient {
  public static AmqpClient createClient() {
    return new AmqpClient();
  }

  private AmqpClient() {
    cf = new ConnectionFactory();
  }

  private ConnectionFactory cf;

  public AmqpClient setHost(String host) {
    cf.setHost(host);
    return this;
  }

  public AmqpClient setPort(int port) {
    cf.setPort(port);
    return this;
  }

  public AmqpClient setUsername(String username) {
    cf.setUsername(username);
    return this;
  }

  public AmqpClient setPassword(String password) {
    cf.setPassword(password);
    return this;
  }

  public AmqpClient setVirtualHost(String virtualHost) {
    cf.setVirtualHost(virtualHost);
    return this;
  }

  public void connect(final Callback<AmqpConnection> connectCallback) {
    Nodex.instance.executeInBackground(new Runnable() {
      public void run() {
        try {
          connectCallback.onEvent(new AmqpConnection(cf.newConnection()));
        } catch (IOException e) {
          //TODO handle exception by passing them back on callback
          e.printStackTrace();
        }
      }
    });
  }
}
