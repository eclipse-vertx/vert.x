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

package org.vertx.groovy.core.streams

/**
 * Represents a stream of data that can be read from.<p>
 *
 * Any class that implements this interface can be used by a {@link Pump} to pump data from it
 * to a {@link WriteStream}.<p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
interface ReadStream  {

  /**
   * Set a data handler. As data is read, the handler will be called with the data.
   */
  void dataHandler(Closure handler)

  /**
   * Pause the {@code ReadStream}. After calling this, the ReadStream will aim to send no more data to the {@code
   * dataHandler}
   */
  void pause()

  /**
   * Resume reading. If the {@code ReadStream} has been paused, reading will recommence on it.
   */
  void resume()

  /**
   * Set an exception handler.
   */
  void exceptionHandler(Closure handler)

  /**
   * Set an end handler. Once the stream has ended, and there is no more data to be read, this handler will be called.
   */
  void endHandler(Closure handler)

}