package wsperf;

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

import org.vertx.java.core.Handler;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.WebSocket;
import org.vertx.java.platform.Verticle;

public class ConnectClient extends Verticle {

  private HttpClient client;

  // Number of connections to create
  private static final int CONNS = 1000;


  public ConnectClient() {
  }

  int connectCount;

  public void start() {
    System.out.println("Starting perf client");
    client = vertx.createHttpClient().setPort(8080).setHost("localhost").setMaxPoolSize(CONNS);
    for (int i = 0; i < CONNS; i++) {
      System.out.println("connecting ws");
      client.connectWebsocket("/someuri", new Handler<WebSocket>() {
        public void handle(WebSocket ws) {
          System.out.println("ws connected: " + ++connectCount);
        }
      });
    }
  }

}
