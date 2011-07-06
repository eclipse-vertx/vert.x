package org.nodex.examples.composition;

import org.nodex.core.Callback;
import org.nodex.core.NoArgCallback;
import org.nodex.core.amqp.Channel;
import org.nodex.core.buffer.Buffer;
import org.nodex.core.composition.Completion;
import org.nodex.core.composition.Composer;
import org.nodex.core.composition.Deferred;
import org.nodex.core.http.HttpCallback;
import org.nodex.core.http.Request;
import org.nodex.core.http.Response;
import org.nodex.core.net.Server;
import org.nodex.core.redis.Connection;
import org.nodex.core.stomp.MessageCallback;
import org.nodex.core.stomp.StompServer;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * User: tim
 * Date: 05/07/11
 * Time: 15:58
 */
public class CompositionExample {
  public static void main(String[] args) throws Exception {
    new CompositionExample().run();

    System.out.println("Any key to exit");
    System.in.read();
  }

  private CompositionExample() {
  }

  private void run() {
  //  example1();
    example2();
  }

//  private void example1() {
//    //First a simple example - chain some actions one after another
//
//    Composer composer = Composer.compose();
//
//    final AtomicInteger total = new AtomicInteger(0);
//
//    //TODO should be able to just pass a Runnable in!
//
//    composer.then(new Deferred(new NoArgCallback() {
//      public void onEvent() {
//        System.out.println("In Deffered1");
//        total.incrementAndGet();
//      }
//    })).then(new Deferred(new NoArgCallback() {
//      public void onEvent() {
//        System.out.println("In Deffered2");
//
//      }
//    })).run();
//  }

  private void example2() {
    final Composer composer = Composer.compose();

    //FIXME - do this in two stages:
    //Stage1 - start all the servers and create the clients we need

        /*

        STOMP server maintains order queue
        Redis maintains prices
        Rabbit is warehouse server

        1) Click on web page, to "check prices and availability"
        2) Send message containing item_id to STOMP
        3) Consume message from STOMP
        4) Call Redis to get prices, and rabbit to check availability in PARALLEL
        5) When both have returned, format web page and return to user

        Will need a rabbit worker which consumes from the availability queue and returns a random result


         */

    final String availabilityQueue = "availability";
    final String availabilityResponse = "availability-response";
    final String stompTopic = "availability-request";

    // Create Stomp Server
    Server stomp = StompServer.createServer().listen(8181);

    //Create
//    org.nodex.core.stomp.Client.connect(8181, new Callback<org.nodex.core.stomp.Connection>() {
//      public void onEvent(org.nodex.core.stomp.Connection conn) {

    // Create HTTP server
    org.nodex.core.http.Server server = org.nodex.core.http.Server.createServer(new Callback<org.nodex.core.http.Connection>() {
      public void onEvent(final org.nodex.core.http.Connection conn) {
        conn.request(new HttpCallback() {
          public void onRequest(Request req, Response resp) {
            //Send a message to STOMP with the item
            String item = req.uri;

          }
        });
      }
    }).listen(8080);


    // Create Rabbit worker which checks availability of item
    org.nodex.core.amqp.Client amqpClient = org.nodex.core.amqp.Client.createClient();
    amqpClient.connect(new Callback<org.nodex.core.amqp.Connection>() {
      public void onEvent(org.nodex.core.amqp.Connection conn) {
        conn.createChannel(new Callback<Channel>() {
          public void onEvent(final Channel ch) {
            ch.declareQueue(availabilityQueue, false, false, true, new NoArgCallback() {
              public void onEvent() {
//                ch.subscribe(availabilityQueue, true, new Callback<String>() {
//                  public void onEvent(String msg) {
//                    System.out.println("Checking availability of item " + msg);
//                    int availability = (int) (10 * Math.random());
//                    //ch.publish("", availabilityResponse, String.valueOf(availability));
//                  }
//                });
              }
            });
          }
        });
      }
    });

    //Create redis client and a connection

    final Completion redisConnected = new Completion();
    final AtomicReference<org.nodex.core.redis.Connection> redisConnection = new AtomicReference<Connection>();

    org.nodex.core.redis.Client.createClient().connect(6379, "localhost", new Callback<org.nodex.core.redis.Connection>() {
      public void onEvent(org.nodex.core.redis.Connection conn) {
        redisConnection.set(conn);
        redisConnected.complete();
      }
    });

    // Create STOMP subscriber

    //FIXME - the AMQP and STOMP ways of conneting and creating clients are inconsisent

    org.nodex.core.stomp.Client.connect(8181, new Callback<org.nodex.core.stomp.Connection>() {
      public void onEvent(org.nodex.core.stomp.Connection conn) {
         conn.subscribe(stompTopic, new MessageCallback() {
           public void onMessage(Map<String, String> headers,  Buffer body) {
             String item = body.toString();
             System.out.println("Received request to check availability and price for " + item);

             final AtomicReference<String> price = new AtomicReference<String>(null);

             //Make sure redis is connected then send a price request to redis and an availability request to
             //rabbit in parallel
             Composer comp = Composer.compose();
             comp.then(redisConnected)  // First make sure redis has connected (since this is done async too)
                 .parallel(redisConnection.get().get(item, new Callback<String>() {
                   public void onEvent(String value) {
                     price.set(value);
                   }
                 }))
                 .then(null);
           }
         });
      }
    });

  }
}
