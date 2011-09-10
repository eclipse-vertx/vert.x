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

package org.nodex.java.core;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface Nodex {

  static Nodex instance = NodexInternal.instance;

  long setTimeout(long delay, EventHandler<Long> handler);

  long setPeriodic(long delay, EventHandler<Long> handler);

  boolean cancelTimeout(long id);

  <T> long registerHandler(EventHandler<T> handler);

  boolean unregisterHandler(long handlerID);

  <T> boolean sendToHandler(long actorID, T message);

  Long getContextID();

  void nextTick(EventHandler<Void> handler);

  void go(Runnable runnable);
}
