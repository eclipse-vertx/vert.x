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

package org.vertx.java.examples.webapp;

import org.vertx.java.core.Handler;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.sockjs.AppConfig;
import org.vertx.java.core.sockjs.SockJSServer;
import org.vertx.java.deploy.Verticle;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class WebServer extends Verticle implements Handler<HttpServerRequest> {

  public void start() throws Exception {
    HttpServer server = vertx.createHttpServer();
    server.setSSL(true).setKeyStorePath("server-keystore.jks").
                        setKeyStorePassword("wibble");
    server.requestHandler(this);

    SockJSServer sjsServer = vertx.createSockJSServer(server);
    JsonArray permitted = container.getConfig().getArray("permitted");
    sjsServer.bridge(new AppConfig().setPrefix("/eventbus"), permitted);

    server.listen(8080, "localhost");
  }

  public void handle(HttpServerRequest req) {
    if (req.path.equals("/")) {
      req.response.sendFile("web/index.html");
    } else if (!req.path.contains("..")) {
      req.response.sendFile("web/" + req.path);
    } else {
      req.response.statusCode = 404;
      req.response.end();
    }
  }
}