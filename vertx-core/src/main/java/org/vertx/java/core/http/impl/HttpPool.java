package org.vertx.java.core.http.impl;

import org.vertx.java.core.Handler;
import org.vertx.java.core.impl.DefaultContext;

/*
 * Copyright 2013 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 */
public interface HttpPool {

  void setMaxPoolSize(int size);

  int getMaxPoolSize();

  void close();

  void getConnection(Handler<ClientConnection> handler, Handler<Throwable> connectionExceptionHandler, DefaultContext context);

  void connectionClosed();

  void returnConnection(ClientConnection conn);

}
