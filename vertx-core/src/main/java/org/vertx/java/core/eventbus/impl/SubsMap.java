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

package org.vertx.java.core.eventbus.impl;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.net.impl.ServerID;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 *
 */
public interface SubsMap {

  void put(String subName, ServerID serverID, Handler<AsyncResult<Void>> completionHandler);

  void get(String subName, Handler<AsyncResult<ServerIDs>> completionHandler);

  void remove(String subName, ServerID serverID, Handler<AsyncResult<Void>> completionHandler);

  void removeAllForServerID(ServerID serverID, Handler<AsyncResult<Void>> completionHandler);
}
