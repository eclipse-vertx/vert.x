package org.nodex.core.amqp;

import org.nodex.core.DoneHandler;
import org.nodex.core.composition.Completion;

/**
 * User: tim
 * Date: 06/07/11
 * Time: 07:23
 */
public class ChannelPool {
  public static ChannelPool createPool() {
    return new ChannelPool();
  }

  private ChannelPool() {
    client = AmqpClient.createClient();
  }

  private AmqpClient client;

  private int maxConnections = 10;

  public ChannelPool setHost(String host) {
    client.setHost(host);
    return this;
  }

  public ChannelPool setPort(int port) {
    client.setPort(port);
    return this;
  }

  public ChannelPool setUsername(String username) {
    client.setUsername(username);
    return this;
  }

  public ChannelPool setPassword(String password) {
    client.setPassword(password);
    return this;
  }

  public ChannelPool setVirtualHost(String virtualHost) {
    client.setVirtualHost(virtualHost);
    return this;
  }

  public ChannelPool setMaxConnections(int maxConnections) {
    this.maxConnections = maxConnections;
    return this;
  }

  //FIXME - for demo we just have one connection
  private volatile AmqpConnection connection;
  private Completion connected = new Completion();

  private synchronized void createConnection() {
    if (connection == null) {
      client.connect(new AmqpConnectHandler() {
        public void onConnect(AmqpConnection conn) {
          connection = conn;
          connected.complete();
        }
      });
    }
  }

  public void getChannel(final ChannelHandler channelHandler) {
    if (connection == null) createConnection();
    connected.onComplete(new DoneHandler() {
      public void onDone() {
        //FIXME - for the demo we just get a new channel each time
        //Also this is broken since if more than one call to getChannel comes in before connection is created
        //previous request will be overwritten
        connection.createChannel(channelHandler);
      }
    });
  }

  public void returnChannel(Channel channel) {
    channel.close(null);
  }

}
