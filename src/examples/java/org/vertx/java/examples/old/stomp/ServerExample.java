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

package org.vertx.java.examples.old.stomp;

import org.vertx.java.core.net.NetServer;
import org.vertx.java.old.stomp.StompServer;

public class ServerExample {
  public static void main(String[] args) throws Exception {
    NetServer server = StompServer.createServer().listen(8181, "localhost");

    System.out.println("Any key to exit");
    System.in.read();
    server.close();
  }
}
