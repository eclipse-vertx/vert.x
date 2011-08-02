/*
 * Copyright 2002-2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.nodex.core;

public interface Nodex {

  static Nodex instance = NodexInternal.instance;

  void setCoreThreadPoolSize(int size);

  int getCoreThreadPoolSize();

  void setBackgroundThreadPoolSize(int size);

  int getBackgroundThreadPoolSize();

  long setTimeout(long delay, Runnable handler);

  long setPeriodic(long delay, Runnable handler);

  boolean cancelTimeout(long id);

  <T> String registerActor(Actor<T> actor);

  boolean unregisterActor(String actorID);

  <T> boolean sendMessage(String actorID, T message);

  String getContextID();
}
