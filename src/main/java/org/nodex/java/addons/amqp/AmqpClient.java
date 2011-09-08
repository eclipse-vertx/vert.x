/*
 * Copyright 2011 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.nodex.java.addons.amqp;

import com.rabbitmq.client.ConnectionFactory;
import org.nodex.java.core.NodexInternal;

import java.io.IOException;

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

  public void connect(final AmqpConnectHandler connectHandler) {
    NodexInternal.instance.executeInBackground(new Runnable() {
      public void run() {
        try {
          connectHandler.onConnect(new AmqpConnection(cf.newConnection()));
        } catch (IOException e) {
          //TODO handle exceptionHandler by passing them back on callback
          e.printStackTrace();
        }
      }
    });
  }
}
