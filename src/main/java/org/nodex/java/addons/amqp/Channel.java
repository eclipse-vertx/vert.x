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

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import org.nodex.java.core.composition.Composable;
import org.nodex.java.core.internal.NodexInternal;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Channel {

  private com.rabbitmq.client.Channel channel;

  Channel(com.rabbitmq.client.Channel channel) {
    this.channel = channel;
  }

  public void publish(final String exchange, final String routingKey, AmqpProps props, final byte[] body) {
    try {
      if (props == null) {
        props = new AmqpProps();
      }
      AMQP.BasicProperties aprops = props.toBasicProperties();
      channel.basicPublish(exchange, routingKey, aprops, body);
    } catch (IOException e) {
      //TODO handle exceptionHandler by passing them back on callback
      e.printStackTrace();
    }
  }

  public void publish(final String exchange, final String routingKey, final AmqpProps props, final String message) {
    try {
      publish(exchange, routingKey, props, message.getBytes("UTF-8"));
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }
  }

  public void declareQueue(final String queueName, final boolean durable, final boolean exclusive, final boolean autoDelete,
                           final Runnable doneCallback) {
    NodexInternal.instance.executeInBackground(new Runnable() {
      public void run() {
        try {
          channel.queueDeclare(queueName, durable, exclusive, autoDelete, null);
          doneCallback.run();
        } catch (IOException e) {
          //TODO handle exceptionHandler by passing them back on callback
          e.printStackTrace();
        }
      }
    });
  }

  public void subscribe(final String queueName, final boolean autoAck, final AmqpMsgCallback messageCallback) {
    NodexInternal.instance.executeInBackground(new Runnable() {
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
                  AmqpProps props = properties == null ? null : new AmqpProps(properties);
                  messageCallback.onMessage(props, body);
                }
              });
        } catch (IOException e) {
          //TODO handle exceptionHandler by passing them back on callback
          e.printStackTrace();
        }
      }
    });
  }

  private Map<String, AmqpMsgCallback> callbacks = new ConcurrentHashMap();
  private volatile String responseQueue;
  private Composable responseQueueSetup = new Composable();

  private synchronized void createResponseQueue() {
    if (responseQueue == null) {
      final String queueName = UUID.randomUUID().toString();
      declareQueue(queueName, false, true, true, new Runnable() {
        public void run() {
          responseQueue = queueName;
          responseQueueSetup.complete(); //Queue is now set up
          subscribe(queueName, true, new AmqpMsgCallback() {
            public void onMessage(AmqpProps props, byte[] body) {
              String cid = props.correlationId;
              if (cid == null) {
                //TODO better error reporting
                System.err.println("No correlation id");
              } else {
                AmqpMsgCallback cb = callbacks.get(cid);
                if (cb == null) {
                  System.err.println("No callback for correlation id");
                } else {
                  cb.onMessage(props, body);
                }
              }
            }
          });
        }
      });
    }
  }

  // Request-response pattern

  public Composable request(final String exchange, final String routingKey, final AmqpProps props, final String body, final AmqpMsgCallback responseCallback) {
    try {
      return request(exchange, routingKey, props, body == null ? null : body.getBytes("UTF-8"), responseCallback);
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
      return null;
    }
  }

  public Composable request(final String exchange, final String routingKey, final AmqpProps props, final byte[] body, final AmqpMsgCallback responseCallback) {
    final AmqpProps theProps = props == null ? new AmqpProps() : props;
    if (responseQueue == null) createResponseQueue();
    //We make sure we don't actually send until the response queue has been setup, this is done by using a
    //Composable
    final Composable c = new Composable();
    responseQueueSetup.onComplete(new Runnable() {
      public void run() {
        AmqpMsgCallback cb = new AmqpMsgCallback() {
          public void onMessage(AmqpProps props, byte[] body) {
            responseCallback.onMessage(props, body);
            c.complete();
          }
        };
        String cid = UUID.randomUUID().toString();
        theProps.correlationId = cid;
        theProps.replyTo = responseQueue;
        callbacks.put(cid, cb);
        publish(exchange, routingKey, theProps, body);
      }
    });
    return c;
  }


  public void close(final Runnable doneCallback) {
    NodexInternal.instance.executeInBackground(new Runnable() {
      public void run() {
        try {
          channel.close();
          doneCallback.run();
        } catch (IOException e) {
          //TODO handle exceptionHandler by passing them back on callback
          e.printStackTrace();
        }
      }
    });
  }


}
