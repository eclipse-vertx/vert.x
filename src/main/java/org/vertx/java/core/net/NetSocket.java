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

package org.vertx.java.core.net;

import org.jboss.netty.channel.Channel;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.impl.Context;
import org.vertx.java.core.net.impl.ConnectionBase;
import org.vertx.java.core.streams.ReadStream;
import org.vertx.java.core.streams.WriteStream;

/**
 * Represents a socket-like interface to a TCP or SSL connection on either the
 * client or the server side.
 * <p>
 * Instances of this class is created on the client side by an {@link NetClient}
 * when a connection to a server is made, or on the server side by a {@link NetServer}
 * when a server accepts a connection.
 * <p>
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class NetSocket extends ConnectionBase implements ReadStream, WriteStream {

  /**
   * When a {@code NetSocket} is created it automatically registers an event handler with the event bus, the ID of that
   * handler is given by {@code writeHandlerID}.<p>
   * Given this ID, a different event loop can send a buffer to that event handler using the event bus and
   * that buffer will be received by this instance in its own event loop and writing to the underlying connection. This
   * allows you to write data to other connections which are owned by different event loops.
   */
  public final String writeHandlerID;

  protected NetSocket(Channel channel, String writeHandlerID, Context context, Thread th) {
    super(channel, context, th);
    this.writeHandlerID = writeHandlerID;
  }

  /**
   * Write a {@link Buffer} to the connection.
   */
  public abstract void writeBuffer(Buffer data);

  /**
   * Write a {@link Buffer} to the request body.<p>
   * @return A reference to this, so multiple method calls can be chained.
   */
  public abstract NetSocket write(Buffer data);

  /**
   * Write a {@link String} to the connection, encoded in UTF-8.<p>
   * @return A reference to this, so multiple method calls can be chained.
   */
  public abstract NetSocket write(String str);

  /**
   * Write a {@link String} to the connection, encoded using the encoding {@code enc}.<p>
   * @return A reference to this, so multiple method calls can be chained.
   */
  public abstract NetSocket write(String str, String enc);

  /**
   * Write a {@link Buffer} to the connection. The {@code doneHandler} is called after the buffer is actually written to the wire.<p>
   * @return A reference to this, so multiple method calls can be chained.
   */
  public abstract NetSocket write(Buffer data, Handler<Void> doneHandler);

  /**
   * Write a {@link String} to the connection, encoded in UTF-8. The {@code doneHandler} is called after the buffer is actually written to the wire.<p>
   * @return A reference to this, so multiple method calls can be chained.
   */
  public abstract NetSocket write(String str, Handler<Void> doneHandler);

  /**
   * Write a {@link String} to the connection, encoded with encoding {@code enc}. The {@code doneHandler} is called after the buffer is actually written to the wire.<p>
   * @return A reference to this, so multiple method calls can be chained.
   */
  public abstract NetSocket write(String str, String enc, Handler<Void> doneHandler);

  /**
   * Specify a data handler for the connection. As data is read from the connection the handler will be called.
   */
  public abstract void dataHandler(Handler<Buffer> dataHandler) ;

  /**
   * Specify an end handler for the connection. This will be called when the connection has ended, i.e. when it has been closed.
   */
  public abstract void endHandler(Handler<Void> endHandler);

  /**
   * This method sets a drain handler {@code drainHandler} on the connection. The drain handler will be called when write queue is no longer
   * full and it is safe to write to it again.<p>
   * The drain handler is actually called when the write queue size reaches <b>half</b> the write queue max size to prevent thrashing.
   * This method is used as part of a flow control strategy, e.g. it is used by the {@link org.vertx.java.core.streams.Pump} class to pump data
   * between different streams.
   */
  public abstract void drainHandler(Handler<Void> drainHandler);

  /**
   * Tell the kernel to stream a file as specified by {@code filename} directly from disk to the outgoing connection, bypassing userspace altogether
   * (where supported by the underlying operating system. This is a very efficient way to stream files.<p>
   */
  public abstract void sendFile(String filename);


}

