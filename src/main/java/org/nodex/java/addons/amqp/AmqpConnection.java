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

import org.nodex.java.core.NodexInternal;

import java.io.IOException;

public class AmqpConnection {

  private com.rabbitmq.client.Connection conn;

  AmqpConnection(com.rabbitmq.client.Connection conn) {
    this.conn = conn;
  }

  public void createChannel(final ChannelHandler channelHandler) {
    NodexInternal.instance.executeInBackground(new Runnable() {
      public void run() {
        try {
          channelHandler.onCreate(new Channel(conn.createChannel()));
        } catch (IOException e) {
          //TODO handle exceptionHandler by passing them back on callback
          e.printStackTrace();
        }
      }
    });
  }

  public void close(final Runnable doneCallback) {
    NodexInternal.instance.executeInBackground(new Runnable() {
      public void run() {
        try {
          conn.close();
          //FIXME - again this is sync
          doneCallback.run();
        } catch (IOException e) {
          //TODO handle exceptionHandler by passing them back on callback
          e.printStackTrace();
        }
      }
    });
  }
}
