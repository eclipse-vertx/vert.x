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
public class Client {
  public static Client createClient() {
    return new Client();
  }

  private Client() {
    cf = new ConnectionFactory();
  }

  private ConnectionFactory cf;

  public Client setHost(String host) {
    cf.setHost(host);
    return this;
  }

  public Client setPort(int port) {
    cf.setPort(port);
    return this;
  }

  public Client setUsername(String username) {
    cf.setUsername(username);
    return this;
  }

  public Client setPassword(String password) {
    cf.setPassword(password);
    return this;
  }

  public Client setVirtualHost(String virtualHost) {
    cf.setVirtualHost(virtualHost);
    return this;
  }

  public void connect(final Callback<Connection> connectCallback) {
    Nodex.instance.executeInBackground(new Runnable() {
      public void run() {
        try {
          connectCallback.onEvent(new Connection(cf.newConnection()));
        } catch (IOException e) {
          //TODO handle exception by passing them back on callback
          e.printStackTrace();
        }
      }
    });
  }
}
