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

import org.vertx.groovy.core.buffer.Buffer

/**
 *
 * Represents a stream of data that can be written to.<p>
 *
 * Any class that implements this interface can be used by a {@link Pump} to pump data from a {@code ReadStream}
 * to it.<p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
interface WriteStream {

  /**
   * Write some data to the stream. The data is put on an internal write queue, and the write actually happens
   * asynchronously. To avoid running out of memory by putting too much on the write queue,
   * check the {@link #writeQueueFull} method before writing. This is done automatically if using a {@link Pump}.
   */
  void writeBuffer(Buffer data)

  /**
   * Set the maximum size of the write queue to {@code maxSize}. You will still be able to write to the stream even
   * if there is more than {@code maxSize} bytes in the write queue. This is used as an indicator by classes such as
   * {@code Pump} to provide flow control.
   */
  void setWriteQueueMaxSize(int maxSize)

  /**
   * This will return {@code true} if there are more bytes in the write queue than the value set using {@link #setWriteQueueMaxSize}
   */
  boolean writeQueueFull()

  /**
   * Set a drain handler on the stream. If the write queue is full, then the handler will be called when the write
   * queue has been reduced to maxSize / 2. See {@link org.vertx.groovy.core.streams.Pump} for an example of this being used.
   */
  void drainHandler(Closure handler)

  /**
   * Set an exception handler on the stream
   */
  void exceptionHandler(Closure handler)

}