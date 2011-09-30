/*
 * Copyright 2011 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.nodex.java.examples.redis;

import org.nodex.java.addons.redis.RedisConnection;
import org.nodex.java.addons.redis.RedisPool;
import org.nodex.java.core.CompletionHandler;
import org.nodex.java.core.Future;
import org.nodex.java.core.Handler;
import org.nodex.java.core.NodexMain;
import org.nodex.java.core.buffer.Buffer;
import org.nodex.java.core.http.HttpServer;
import org.nodex.java.core.http.HttpServerRequest;

public class RedisExample extends NodexMain {
  public static void main(String[] args) throws Exception {
    new RedisExample().run();

    System.out.println("Hit enter to exit");
    System.in.read();
  }

  public void go() throws Exception {

    final RedisPool pool = new RedisPool();
    final Buffer key = Buffer.create("my_count");

    new HttpServer().requestHandler(new Handler<HttpServerRequest>() {
      public void handle(final HttpServerRequest req) {
        if (req.uri.equals("/")) {
          final RedisConnection conn = pool.connection();
          conn.incr(key).handler(new CompletionHandler<Integer>() {
            public void handle(Future<Integer> future) {
              Buffer content = Buffer.create("<html><body><h1>Hit count is " + future.result() + "</h1></body></html>");
              req.response.putHeader("Content-Type", "text/html; charset=UTF-8");
              req.response.putHeader("Content-Length", String.valueOf(content.length()));
              req.response.write(content).end();
              conn.close();
            }
          }).execute();
        } else {
          req.response.statusCode = 404;
          req.response.end();
        }
      }
    }).listen(8080);
  }
}
