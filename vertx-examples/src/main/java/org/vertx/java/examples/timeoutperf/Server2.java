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
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.RouteMatcher;
import org.vertx.java.deploy.Verticle;

public class Server2 extends Verticle {

    @Override
    public void start() throws Exception {
        HttpServer server = vertx.createHttpServer();
      server.setAcceptBacklog(1000000);
        RouteMatcher route = new RouteMatcher();
        route.get("/", new Handler<HttpServerRequest>() {
            @Override
            public void handle(final HttpServerRequest req) {
                vertx.setTimer(100, new Handler<Long>() {
                    @Override
                    public void handle(Long timerId) {
                        req.response.statusCode = 200;
                        req.response.end();
                    }
                });
            }
        });
        server.requestHandler(route);
        server.listen(8080);
    }

}
