package org.nodex.examples.composition;

import org.nodex.core.amqp.AmqpClient;
import org.nodex.core.amqp.AmqpConnectHandler;
import org.nodex.core.amqp.AmqpConnection;
import org.nodex.core.amqp.AmqpMsgCallback;
import org.nodex.core.amqp.AmqpProps;
import org.nodex.core.amqp.Channel;
import org.nodex.core.amqp.ChannelHandler;
import org.nodex.core.amqp.ChannelPool;
import org.nodex.core.buffer.Buffer;
import org.nodex.core.buffer.DataHandler;
import org.nodex.core.composition.Completion;
import org.nodex.core.composition.Composer;
import org.nodex.core.file.FileSystem;
import org.nodex.core.http.HttpRequestHandler;
import org.nodex.core.http.HttpServer;
import org.nodex.core.http.HttpServerConnectHandler;
import org.nodex.core.http.HttpServerConnection;
import org.nodex.core.http.HttpServerRequest;
import org.nodex.core.http.HttpServerResponse;
import org.nodex.core.redis.RedisClient;
import org.nodex.core.redis.RedisConnectHandler;
import org.nodex.core.redis.RedisConnection;
import org.nodex.core.redis.ResultHandler;
import org.nodex.core.stomp.StompClient;
import org.nodex.core.stomp.StompConnectHandler;
import org.nodex.core.stomp.StompConnection;
import org.nodex.core.stomp.StompMsgCallback;
import org.nodex.core.stomp.StompServer;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * User: tim
 * Date: 05/07/11
 * Time: 15:58
 * <p/>
 * This somewhat elaborate example shows an interaction including HTTP, STOMP, AMQP and Redis
 * <p/>
 * Summary: We have a website which allows the user to check the price and stock count for inventory items.
 * <p/>
 * It contains the following components In real life these would probably be running on different nodes.
 * <p/>
 * 1) HTTP server. We create an HTTP server which serves the index.html page from disk, and responds to HTTP requests for an item.
 * when it receives a request it uses the request-response pattern to send that request in an AMQP message to a queue
 * and sets a handler for the response.
 * When the response returns it formats the price and stock count in the html page returned to the browser.
 * <p/>
 * 2) AMQP consumer. We create an AMQP consumer that consumes from the AMQP queue, and then does two things in parallel
 * a) Call redis to get the price for the item
 * b) Send a STOMP message, using the request-response pattern to a STOMP destination to request the stock count
 * When both a) and b) asynchronously complete, we return an AMQP message as the response to the HTTP server that made
 * the request
 * <p/>
 * 3) A redis server
 * <p/>
 * 4) A STOMP server
 * <p/>
 * 5) STOMP consumer. We create a STOMP consumer that subscribes to the STOMP destination, calculates a stock count and
 * sends that back in a response message
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
    HttpServer.createServer(new HttpServerConnectHandler() {
      public void onConnect(final HttpServerConnection conn) {
        conn.request(new HttpRequestHandler() {
          public void onRequest(HttpServerRequest req, final HttpServerResponse resp) {
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
                  ch.request("", AMQP_QUEUE, props, (String) null, new AmqpMsgCallback() {
                    public void onMessage(AmqpProps respProps, byte[] bod) {
                      //We get a response back with the price and number of items in stock
                      int price = (Integer) respProps.headers.get("price");
                      int stock = (Integer) respProps.headers.get("stock");
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

  private void amqpWorker() {

    //First we need to set up the redis and Stomp connections (and add some reference data)

    final AtomicReference<RedisConnection> redisConn = new AtomicReference<RedisConnection>();
    final Completion redisConnected = new Completion();

    //Create redis connection
    RedisClient.createClient().connect(6379, "localhost", new RedisConnectHandler() {
      public void onConnect(final RedisConnection conn) {
        //We need to add a little reference data for this prices
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
    final Completion stompConnected = new Completion();

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
                    Completion redisGet = redisConn.get(item, new ResultHandler() {
                      public void onResult(String value) {
                        price.set(Integer.parseInt(value));
                      }
                    });

                    Map<String, String> headers = new HashMap<String, String>();
                    headers.put("item", item);

                    // Get stock from STOMP worker
                    final AtomicInteger stock = new AtomicInteger(0);
                    Completion responseReturned = stompConn.request(STOMP_DESTINATION, headers, null, new StompMsgCallback() {
                      public void onMessage(Map<String, String> headers, Buffer body) {
                        int st = Integer.valueOf(headers.get("stock"));
                        stock.set(st);
                      }
                    });

                    comp.when(redisGet, responseReturned)         // Execute redis get and stomp request/response in parallel
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
