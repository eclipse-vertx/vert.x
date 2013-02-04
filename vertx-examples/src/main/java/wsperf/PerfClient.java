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
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.WebSocket;
import org.vertx.java.platform.Verticle;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

public class PerfClient extends Verticle {

  private HttpClient client;

  // Number of connections to create
  private static final int CONNS = 1;

  private int statsCount;

  private EventBus eb;

  private static final int STR_LENGTH = 8 * 1024;

  private static final int STATS_BATCH = 1024 * 1024;

  private static final int BUFF_SIZE = 32 * 1024;

  private String message;

  public PerfClient() {
    StringBuilder sb = new StringBuilder(STR_LENGTH);
    for (int i = 0; i < STR_LENGTH; i++) {
      sb.append('X');
    }
    message = sb.toString();
  }

  int connectCount;

  Queue<WebSocket> websockets = new LinkedList<>();

  Set<WebSocket> wss = new HashSet<>();

  private void connect(final int count) {
    client.connectWebsocket("/echo/websocket", new Handler<WebSocket>() {
      public void handle(final WebSocket ws) {
        connectCount++;

        ws.setWriteQueueMaxSize(BUFF_SIZE);
        ws.dataHandler(new Handler<Buffer>() {
          public void handle(Buffer data) {
            if (!wss.contains(ws)) {
              wss.add(ws);
              if (wss.size() == CONNS) {
                System.out.println("Received data on all conns");
              }
            }
            int len = data.length();
            statsCount += len;
            if (statsCount > STATS_BATCH) {
              eb.send("rate-counter", statsCount);
              statsCount = 0;
            }
          }
        });

        websockets.add(ws);
        if (connectCount == CONNS) {
          startWebSocket();
        }
      }
    });
    if (count + 1 < CONNS) {
      vertx.runOnLoop(new SimpleHandler() {
        public void handle() {
          connect(count + 1);
        }
      });
    }
  }

  private void startWebSocket() {
    WebSocket ws = websockets.poll();
    writeWebSocket(ws);
    if (!websockets.isEmpty()) {
      vertx.runOnLoop(new SimpleHandler() {
        public void handle() {
          startWebSocket();
        }
      });
    }

  }

  public void start() {
    System.out.println("Starting perf client");
    eb = vertx.eventBus();
    client = vertx.createHttpClient().setPort(8080).setHost("localhost").setMaxPoolSize(CONNS);
    client.setReceiveBufferSize(BUFF_SIZE);
    client.setSendBufferSize(BUFF_SIZE);
    client.setConnectTimeout(60000);
    client.setBossThreads(4);
    connect(0);
  }

  private void writeWebSocket(final WebSocket ws) {
    if (!ws.writeQueueFull()) {
      //ws.writeTextFrame(message);
      ws.writeBinaryFrame(new Buffer(message));
      vertx.runOnLoop(new SimpleHandler() {
        public void handle() {
          writeWebSocket(ws);
        }
      });
    } else {
      // Flow control
      ws.drainHandler(new SimpleHandler() {
        public void handle() {
          writeWebSocket(ws);
        }
      });
    }
  }

}
