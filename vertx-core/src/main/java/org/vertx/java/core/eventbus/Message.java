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

package org.vertx.java.core.eventbus;

import org.vertx.java.core.Handler;

/**
 * Represents a message on the event bus.<p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class Message<T>  {

  protected Message() {
  }

  /**
   * The body of the message
   */
  public T body;

  /**
   * The reply address (if any)
   */
  public String replyAddress;

  /**
   * Same as {@code reply(T message)} but with an empty body
   */
  public void reply() {
    reply(null);
  }

  /**
   * Reply to this message. If the message was sent specifying a reply handler, that handler will be
   * called when it has received a reply. If the message wasn't sent specifying a receipt handler
   * this method does nothing.
   */
  public void reply(T message) {
    reply(message, null);
  }

  /**
   * The same as {@code reply(T message)} but you can specify handler for the reply - i.e.
   * to receive the reply to the reply.
   */
  public abstract void reply(T message, Handler<Message<T>> replyHandler);

}
