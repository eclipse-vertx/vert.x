/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.nodex.java.examples.old.amqp;

public class ClientExample {
//
//  private static String QUEUE_NAME = "my-queue";
//
//  public static void main(String[] args) throws IOException {
//    org.nodex.java.addons.old.amqp.AmqpClient.createClient().connect(new AmqpConnectHandler() {
//      public void onConnect(AmqpConnection conn) {
//        conn.createChannel(new ChannelHandler() {
//          public void onCreate(final Channel channel) {
//            channel.declareQueue(QUEUE_NAME, false, true, true, new Runnable() {
//              public void run() {
//                System.out.println("declared ok");
//                channel.subscribe(QUEUE_NAME, true, new AmqpMsgCallback() {
//                  public void onMessage(AmqpProps props, byte[] body) {
//                    System.out.println("Got message " + new String(body));
//                  }
//                });
//                //Send some messages
//                for (int i = 0; i < 10; i++) {
//                  channel.publish("", QUEUE_NAME, null, "message " + i);
//                }
//                System.out.println("Sent messages");
//              }
//            });
//          }
//        });
//      }
//    });
//
//    System.out.println("Any key to exit");
//    System.in.read();
//  }
}
