/*
 * Copyright 2011-2013 the original author or authors.
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
package org.vertx.java.tests.deploy;

import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.http.HttpServerRequest;

/**
 *
 */
public class RepoServer {
  private static final String webroot = "src/test/resources/";
  private final Vertx vertx;

  public RepoServer(final String module1, final String module2) {
    vertx = Vertx.newVertx();
    vertx.createHttpServer()
        .requestHandler(new Handler<HttpServerRequest>() {
          public void handle(final HttpServerRequest req) {
            System.out.println("HANDLING " + req.uri);
            if (req.uri.equals("/vertx-mods/mods/" + module2
                + "/mod.zip")) {
              System.out.println("HANDLING repo request");
              req.response.sendFile(webroot + "mod2.zip");
            } else if (req.uri.equals("http://vert-x.github.com/vertx-mods/mods/" + module1
                + "/mod.zip")) {
              req.response.sendFile(webroot + "mod1.zip");
            }
          }
        }).listen(9093, "localhost");
  }

}