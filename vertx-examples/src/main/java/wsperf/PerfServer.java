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
import org.vertx.java.core.http.ServerWebSocket;
import org.vertx.java.core.streams.Pump;
import org.vertx.java.platform.Verticle;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class PerfServer extends Verticle {

  private static final int BUFF_SIZE = 32 * 1024;

  public void start() throws Exception {
    vertx.createHttpServer().setReceiveBufferSize(BUFF_SIZE).setSendBufferSize(BUFF_SIZE).setAcceptBacklog(32000).
        websocketHandler(new Handler<ServerWebSocket>() {
      public void handle(ServerWebSocket ws) {
        //System.out.println("connected " + ++count);
        Pump.createPump(ws, ws, BUFF_SIZE).start();
      }
    }).listen(8080, "localhost");
  }
}
