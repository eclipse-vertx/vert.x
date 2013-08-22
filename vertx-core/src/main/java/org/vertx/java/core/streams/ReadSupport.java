/*
 * Copyright 2013 the original author or authors.
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
package org.vertx.java.core.streams;

import org.vertx.java.core.Handler;

/**
 * Allows to set a {@link Handler} which is notified once data was read.
 * It also allows to pause reading and resume later.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public interface ReadSupport<T, M> extends ExceptionSupport<T> {

  /**
   * Set a data handler. As data is read, the handler will be called with the data.
   */
  T dataHandler(Handler<M> handler);

  /**
   * Pause the {@code ReadSupport}. While it's paused, no data will be sent to the {@code dataHandler}
   */
  T pause();

  /**
   * Resume reading. If the {@code ReadSupport} has been paused, reading will recommence on it.
   */
  T resume();
}
