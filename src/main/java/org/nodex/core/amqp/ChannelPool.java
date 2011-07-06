package org.nodex.core.amqp;

import com.rabbitmq.client.ConnectionFactory;
import org.nodex.core.Callback;
import org.nodex.core.Nodex;
import org.nodex.core.composition.Completion;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

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
    client = Client.createClient();
  }

  private Client client;

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
  private volatile Connection connection;
  private Completion connected;

  private synchronized void createConnection() {
    if (connection == null) {
      client.connect(new Callback<Connection>() {
        public void onEvent(Connection conn) {
          connection = conn;
          connected.complete();
        }
      });
    }
  }

  public void getChannel(Callback<Channel> channelCallback) {
    if (connection == null) createConnection();
    //FIXME - for the demo we just get a new channel each time
    connection.createChannel(channelCallback);
  }

  public void returnChannel(Channel channel) {
    channel.close(null);
  }

}
