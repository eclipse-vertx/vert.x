package org.nodex.examples.composition;

import org.nodex.core.DoneHandler;
import org.nodex.core.amqp.*;
import org.nodex.core.buffer.Buffer;
import org.nodex.core.buffer.DataHandler;
import org.nodex.core.composition.Completion;
import org.nodex.core.composition.Composer;
import org.nodex.core.composition.Deferred;
import org.nodex.core.file.FileSystem;
import org.nodex.core.http.*;
import org.nodex.core.redis.RedisClient;
import org.nodex.core.redis.RedisConnectHandler;
import org.nodex.core.redis.RedisConnection;
import org.nodex.core.redis.ResultHandler;
import org.nodex.core.stomp.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * User: tim
 * Date: 05/07/11
 * Time: 15:58
 *
 * This somewhat elaborate example shows an interaction including HTTP, STOMP, AMQP and Redis
 *
 * Summary: We have a website which allows the user to check the price and stock count for inventory items.
 *
 * It contains the following components In real life these would probably be running on different nodes.
 *
 * 1) HTTP server. We create an HTTP server which serves the index.html page from disk, and responds to HTTP requests for an item.
 * when it receives a request it uses the request-response pattern to send that request in an AMQP message to a queue
 * and sets a handler for the response.
 * When the response returns it formats the price and stock count in the html page returned to the browser.
 *
 * 2) AMQP consumer. We create an AMQP consumer that consumes from the AMQP queue, and then does two things in parallel
 * a) Call redis to get the price for the item
 * b) Send a STOMP message, using the request-response pattern to a STOMP destination to request the stock count
 * When both a) and b) asynchronously complete, we return an AMQP message as the response to the HTTP server that made
 * the request
 *
 * 3) A redis server
 *
 * 4) A STOMP server
 *
 * 5) STOMP consumer. We create a STOMP consumer that subscribes to the STOMP destination, calculates a stock count and
 * sends that back in a response message
 *
 */
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
    HttpServer.createServer(new HttpConnectHandler() {
      public void onConnect(final HttpConnection conn) {
        conn.request(new HttpCallback() {
          public void onRequest(HttpRequest req, final HttpResponse resp) {
            System.out.println("Request uri is " + req.uri);
            if (req.uri.equals("/")) {
              System.out.println("Serving index page");
              //Serve the main page
              FileSystem.instance.readFile("index.html", new DataHandler() {
                public void onData(Buffer data) {
                  resp.write(data);
                  resp.end();
                }
              });
            } else if (req.uri.startsWith("/submit")) {
              //We have received a request for price/stock information, so we send a message to Rabbit with the name of the item
              final String item = req.getParam("item");
              chPool.getChannel(new ChannelHandler() {
                public void onCreate(final Channel ch) {
                  AmqpProps props = new AmqpProps();
                  props.headers.put("item", item);
                  ch.request("", AMQP_QUEUE, props, (String)null, new AmqpMsgCallback() {
                    public void onMessage(AmqpProps respProps, byte[] bod) {
                      //We get a response back with the price and number of items in stock
                      int price = (Integer)respProps.headers.get("price");
                      int stock = (Integer)respProps.headers.get("stock");
                      String content = "<html><body>Price is: " + price + "<br>Stock is: " + stock + "</body></html>";
                      resp.write(content, "UTF-8").end();
                    }
                  });
                }
              });
            }
          }
        });
      }
    }).listen(8080);
  }

  /*
  The AMQP worker consumes from the queue and then calls redis to get the price for the item, and does a request/response
   from the STOMP queue to get the stock availability of the item. This is done in parallel.
   When both results are in, it sends back a message with both results
   */
  private void amqpWorker() {

    final AtomicReference<RedisConnection> redisConn = new AtomicReference<RedisConnection>();
    final Completion redisConnected = new Completion();

    //Create redis connection
    RedisClient.createClient().connect(6379, "localhost", new RedisConnectHandler() {
      public void onConnect(final RedisConnection conn) {
        //We need to add a little reference data for this prices
        conn.set("bicycle", "125", new DoneHandler() {
          public void onDone() {
            conn.set("aardvark", "333", new DoneHandler() {
              public void onDone() {
                redisConn.set(conn);
                redisConnected.complete();
              }
            });
          }
        });
      }
    });

    final AtomicReference<StompConnection> stompConn = new AtomicReference<StompConnection>();
    final Completion stompConnected = new Completion();

    //Create STOMP connection
    StompClient.connect(8181, new StompConnectHandler() {
      public void onConnect(StompConnection conn) {
        stompConn.set(conn);
        stompConnected.complete();
      }
    });

    // Create and start the worker
    AmqpClient.createClient().connect(new AmqpConnectHandler() {
      public void onConnect(AmqpConnection conn) {
        conn.createChannel(new ChannelHandler() {
          public void onCreate(final Channel ch) {
            //Declare the queue
            ch.declareQueue(AMQP_QUEUE, false, true, true, new DoneHandler() {
              public void onDone() {
                ch.subscribe(AMQP_QUEUE, true, new AmqpMsgCallback() {
                public void onMessage(final AmqpProps props, byte[] body) {
                  final String item = props.headers.get("item").toString();
                  Composer comp = Composer.compose();

                  // Get price from redis
                  final AtomicInteger price = new AtomicInteger(0);
                  Completion redisGet = redisConn.get().get(item, new ResultHandler() {
                    public void onResult(String value) {
                      price.set(Integer.parseInt(value));
                    }
                  });

                  Map<String, String> headers = new HashMap<String, String>();
                  headers.put("item", item);

                  // Get stock from STOMP worker
                  final AtomicInteger stock = new AtomicInteger(0);
                  Completion responseReturned = stompConn.get().request(STOMP_DESTINATION, headers, null, new StompMsgCallback() {
                    public void onMessage(Map<String, String> headers, Buffer body) {
                      int st = Integer.valueOf(headers.get("stock"));
                      stock.set(st);
                    }
                  });

                  comp.when(redisConnected, stompConnected)     // First make sure we are connected to redis and stomp
                      .when(redisGet, responseReturned)         // Then execute redis get and stomp request/response in parallel
                      .when(new Deferred(new DoneHandler() {    // Then send back a response with the price and stock
                        public void onDone() {
                          props.headers.put("price", price.get());
                          props.headers.put("stock", stock.get());
                          ch.publish("", props.replyTo, props, (byte[]) null);
                        }
                      }))
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
            headers.put("stock", String.valueOf((int)(10 * Math.random())));
            conn.send(headers.get("reply-to"), headers, null);
          }
        });
      }
    });
  }


}
