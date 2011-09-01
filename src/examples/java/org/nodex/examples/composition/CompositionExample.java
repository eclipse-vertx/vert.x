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

package org.nodex.examples.composition;

import org.nodex.core.CompletionHandler;
import org.nodex.core.EventHandler;
import org.nodex.core.buffer.Buffer;
import org.nodex.core.composition.Composable;
import org.nodex.core.composition.Composer;
import org.nodex.core.file.FileSystem;
import org.nodex.core.http.HttpServer;
import org.nodex.core.http.HttpServerRequest;
import org.nodex.mods.amqp.AmqpClient;
import org.nodex.mods.amqp.AmqpConnectHandler;
import org.nodex.mods.amqp.AmqpConnection;
import org.nodex.mods.amqp.AmqpMsgCallback;
import org.nodex.mods.amqp.AmqpProps;
import org.nodex.mods.amqp.Channel;
import org.nodex.mods.amqp.ChannelHandler;
import org.nodex.mods.amqp.ChannelPool;
import org.nodex.mods.redis.RedisClient;
import org.nodex.mods.redis.RedisConnectHandler;
import org.nodex.mods.redis.RedisConnection;
import org.nodex.mods.redis.ResultHandler;
import org.nodex.mods.stomp.StompClient;
import org.nodex.mods.stomp.StompConnectHandler;
import org.nodex.mods.stomp.StompConnection;
import org.nodex.mods.stomp.StompMsgCallback;
import org.nodex.mods.stomp.StompServer;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class CompositionExample {
  public static void main(String[] args) throws Exception {
    new CompositionExample().run();

    System.out.println("Any key to exit");
    System.in.read();
  }

  private CompositionExample() {
  }

  private static final String AMQP_QUEUE = "availability";
  private static final String STOMP_DESTINATION = "availability-request";

  private void run() {
    stompServer();
    httpServer();
    amqpWorker();
    stompWorker();
  }

  private void stompServer() {
    StompServer.createServer().listen(8181);
  }

  private void httpServer() {
    final ChannelPool chPool = ChannelPool.createPool();
    new HttpServer().requestHandler(new EventHandler<HttpServerRequest>() {
      public void onEvent(final HttpServerRequest req) {
        System.out.println("Request uri is " + req.uri);
        if (req.uri.equals("/")) {
          System.out.println("Serving index page");
          //Serve the main page
          FileSystem.instance.readFile("index.html", new CompletionHandler<Buffer>() {
            public void onCompletion(Buffer data) {
              req.response.write(data);
              req.response.end();
            }

            public void onException(Exception e) {
            }
          });
        } else if (req.uri.startsWith("/submit")) {
          //We have received a requestHandler for price/stock information, so we send a message to Rabbit with the name of the item
          final String item = req.getParam("item");
          chPool.getChannel(new ChannelHandler() {
            public void onCreate(final Channel ch) {
              AmqpProps props = new AmqpProps();
              props.headers.put("item", item);
              ch.request("", AMQP_QUEUE, props, (String) null, new AmqpMsgCallback() {
                public void onMessage(AmqpProps respProps, byte[] bod) {
                  //We get a response back with the price and number of items in stock
                  int price = (Integer) respProps.headers.get("price");
                  int stock = (Integer) respProps.headers.get("stock");
                  String content = "<html><body>Price is: " + price + "<br>Stock is: " + stock + "</body></html>";
                   req.response.write(content, "UTF-8").end();
                }
              });
            }
          });
        }
      }
    }).listen(8080);
  }

  private void amqpWorker() {

    //First we need to set up the redis and Stomp connections (and add some reference dataHandler)

    final AtomicReference<RedisConnection> redisConn = new AtomicReference<RedisConnection>();
    final Composable redisConnected = new Composable();

    //Create redis connection
    RedisClient.createClient().connect(6379, "localhost", new RedisConnectHandler() {
      public void onConnect(final RedisConnection conn) {
        //We need to add a little reference dataHandler for this prices
        conn.set("bicycle", "125", new Runnable() {
          public void run() {
            conn.set("aardvark", "333", new Runnable() {
              public void run() {
                redisConn.set(conn);
                redisConnected.complete();
              }
            });
          }
        });
      }
    });

    final AtomicReference<StompConnection> stompConn = new AtomicReference<StompConnection>();
    final Composable stompConnected = new Composable();

    //Create STOMP connection
    StompClient.connect(8181, new StompConnectHandler() {
      public void onConnect(StompConnection conn) {
        stompConn.set(conn);
        stompConnected.complete();
      }
    });

    // Once the connections are setup (asynchronously) we can start the worker
    new Composer().when(redisConnected, stompConnected).
        when(new Runnable() {
          public void run() {
            setupConnections(redisConn.get(), stompConn.get());
          }
        }).end();
  }

  private void setupConnections(final RedisConnection redisConn, final StompConnection stompConn) {

    // Create and start the worker
    AmqpClient.createClient().connect(new AmqpConnectHandler() {
      public void onConnect(AmqpConnection conn) {
        conn.createChannel(new ChannelHandler() {
          public void onCreate(final Channel ch) {
            //Declare the queue
            ch.declareQueue(AMQP_QUEUE, false, true, true, new Runnable() {
              public void run() {
                ch.subscribe(AMQP_QUEUE, true, new AmqpMsgCallback() {
                  public void onMessage(final AmqpProps props, byte[] body) {
                    final String item = props.headers.get("item").toString();
                    Composer comp = new Composer();

                    // Get price from redis
                    final AtomicInteger price = new AtomicInteger(0);
                    Composable redisGet = redisConn.get(item, new ResultHandler() {
                      public void onResult(String value) {
                        price.set(Integer.parseInt(value));
                      }
                    });

                    Map<String, String> headers = new HashMap<String, String>();
                    headers.put("item", item);

                    // Get stock from STOMP worker
                    final AtomicInteger stock = new AtomicInteger(0);
                    Composable responseReturned = stompConn.request(STOMP_DESTINATION, headers, null, new StompMsgCallback() {
                      public void onMessage(Map<String, String> headers, Buffer body) {
                        int st = Integer.valueOf(headers.get("stock"));
                        stock.set(st);
                      }
                    });

                    comp.when(redisGet, responseReturned)         // Execute redis get and stomp requestHandler/response in parallel
                        .when(new Runnable() {                 // Then send back a response with the price and stock
                          public void run() {
                            props.headers.put("price", price.get());
                            props.headers.put("stock", stock.get());
                            ch.publish("", props.replyTo, props, (byte[]) null);
                          }
                        })
                        .end();
                  }
                });
              }
            });
          }
        });
      }
    });
  }

  /*
  The STOMP worker consumes from the price queue and sends back the number of items in stock for the item
   */
  private void stompWorker() {
    StompClient.connect(8181, new StompConnectHandler() {
      public void onConnect(final StompConnection conn) {
        conn.subscribe(STOMP_DESTINATION, new StompMsgCallback() {
          public void onMessage(Map<String, String> headers, Buffer body) {
            System.out.println("Sending back number of items in stock for item " + headers.get("item"));
            headers.put("stock", String.valueOf((int) (10 * Math.random())));
            conn.send(headers.get("reply-to"), headers, null);
          }
        });
      }
    });
  }


}
