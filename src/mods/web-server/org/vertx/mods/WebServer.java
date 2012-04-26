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

package org.vertx.mods;

import org.vertx.java.core.Handler;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.sockjs.SockJSServer;
import org.vertx.java.deploy.Verticle;

import java.io.File;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class WebServer extends Verticle implements Handler<HttpServerRequest> {

  private String webRootPrefix;
  private String indexPage;

  public void start() throws Exception {
    HttpServer server = vertx.createHttpServer();

    JsonObject conf = container.getConfig();

    if (conf.getBoolean("ssl", false)) {
      server.setSSL(true).setKeyStorePassword(conf.getString("key_store_password", "wibble"))
                         .setKeyStorePath(conf.getString("key_store_path", "server-keystore.jks"));
    }

    server.requestHandler(this);

    boolean bridge = conf.getBoolean("bridge", false);
    if (bridge) {
      SockJSServer sjsServer = vertx.createSockJSServer(server);
      JsonArray permitted = container.getConfig().getArray("permitted");

      sjsServer.bridge(conf.getObject("sjs_config", new JsonObject().putString("prefix", "/eventbus")), permitted,
                       conf.getString("users_collection", "users"), conf.getString("persistor_address", "vertx.persistor"));
    }

    String webRoot = conf.getString("web_root", "web");
    String index = conf.getString("index_page", "index.html");
    webRootPrefix = webRoot + File.separator;
    indexPage = webRootPrefix + index;

    server.listen((Integer)conf.getNumber("port", 80), conf.getString("host", "0.0.0.0"));
  }

  public void handle(HttpServerRequest req) {
    if (req.path.equals("/")) {
      req.response.sendFile(indexPage);
    } else if (!req.path.contains("..")) {
      req.response.sendFile(webRootPrefix + req.path);
    } else {
      req.response.statusCode = 404;
      req.response.end();
    }
  }
}
