/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.examples.timeoutperf;

import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.net.NetClient;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.deploy.Verticle;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class Client extends Verticle {

  private AtomicInteger count = new AtomicInteger(0);

  @Override
  public void start() throws Exception {
    NetClient client = vertx.createNetClient();
    for (int i = 0; i < 400; i++) {
      final long start = System.currentTimeMillis();
      client.connect(8080, new Handler<NetSocket>() {
        public void handle(NetSocket sock) {
          System.out.println("Connected: " + count.incrementAndGet() + " time: " + (System.currentTimeMillis() - start));
        }
      });
    }
  }

//  @Override
//  public void start() throws Exception {
//    client = vertx.createNetClient();
//    connect();
//  }
//
//  private void connect() {
//    final long start = System.currentTimeMillis();
//    client.connect(8080, new Handler<NetSocket>() {
//      public void handle(NetSocket sock) {
//        int c = count.incrementAndGet();
//        System.out.println("Connected: " + c + " time: " + (System.currentTimeMillis() - start));
//        if (c < 400) {
//          vertx.runOnLoop(new SimpleHandler() {
//            public void handle() {
//              connect();
//            }
//          });
//        }
//      }
//    });
//  }
}
