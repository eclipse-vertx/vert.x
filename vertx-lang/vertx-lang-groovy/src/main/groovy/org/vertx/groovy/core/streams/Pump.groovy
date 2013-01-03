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
import org.vertx.java.core.Handler
import org.vertx.java.core.streams.Pump as JPump
import org.vertx.java.core.streams.ReadStream as JReadStream
import org.vertx.java.core.streams.WriteStream as JWriteStream

/**
 * Pumps data from a {@link ReadStream} to a {@link WriteStream} and performs flow control where necessary to
 * prevent the write stream from getting overloaded.<p>
 * Instances of this class read bytes from a {@link ReadStream} and write them to a {@link WriteStream}. If data
 * can be read faster than it can be written this could result in the write queue of the {@link WriteStream} growing
 * without bound, eventually causing it to exhaust all available RAM.<p>
 * To prevent this, after each write, instances of this class check whether the write queue of the {@link
 * WriteStream} is full, and if so, the {@link ReadStream} is paused, and a {@code drainHandler} is set on the
 * {@link WriteStream}. When the {@link WriteStream} has processed half of its backlog, the {@code drainHandler} will be
 * called, which results in the pump resuming the {@link ReadStream}.<p>
 * This class can be used to pump from any {@link ReadStream} to any {@link WriteStream},
 * e.g. from an {@link org.vertx.groovy.core.http.HttpServerRequest} to an {@link org.vertx.groovy.core.file.AsyncFile},
 * or from {@link org.vertx.groovy.core.net.NetSocket} to a {@link org.vertx.groovy.core.http.WebSocket}.<p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class Pump {

  private JPump jPump

  /**
   * Create a new {@code Pump} with the given {@code ReadStream} and {@code WriteStream}
   */
  static Pump createPump(ReadStream rs, WriteStream ws) {
    new Pump(rs, ws)
  }

  /**
   * Set the write queue max size to {@code maxSize}
   */
  void setWriteQueueMaxSize(int maxSize) {
    jPump.setWriteQueueMaxSize(maxSize)    
  }

  /**
   * Start the Pump. The Pump can be started and stopped multiple times.
   */
  void start() {
    jPump.start()    
  }

  /**
   * Stop the Pump. The Pump can be started and stopped multiple times.
   */
  void stop() {
    jPump.stop()    
  }

  /**
   * Return the total number of bytes pumped by this pump.
   */
  int getBytesPumped() {
    return jPump.getBytesPumped()    
  }
    private Pump(ReadStream rs, WriteStream ws) {
    jPump = new JPump(new JReadStream() {

      void dataHandler(Handler<org.vertx.java.core.buffer.Buffer> handler) {
        rs.dataHandler({handler.handle(it.toJavaBuffer())})
      }

      void pause() {
        rs.pause()
      }

      void resume() {
        rs.resume()
      }

      void exceptionHandler(Handler<Exception> handler) {
        rs.exceptionHandler({handler.handle(it)})
      }

      void endHandler(Handler<Void> endHandler) {
        rs.endHandler({endHandler.handle(null)})
      }

    },
    new JWriteStream() {

      void writeBuffer(org.vertx.java.core.buffer.Buffer data) {
        ws.writeBuffer(new Buffer(data))
      }

      void setWriteQueueMaxSize(int maxSize) {
        ws.writeQueueMaxSize(maxSize)
      }

      boolean writeQueueFull() {
        return ws.isWriteQueueFull()
      }

      void drainHandler(Handler<Void> handler) {
        ws.drainHandler({handler.handle(null) })
      }

      void exceptionHandler(Handler<Exception> handler) {
        ws.exceptionHandler({handler.handle(it)})
      }

    })
  }
}
