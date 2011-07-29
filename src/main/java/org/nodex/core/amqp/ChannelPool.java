/*
 * Copyright 2002-2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.nodex.core.amqp;

import org.nodex.core.composition.Completion;

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
    connected.onComplete(new Runnable() {
      public void run() {
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
