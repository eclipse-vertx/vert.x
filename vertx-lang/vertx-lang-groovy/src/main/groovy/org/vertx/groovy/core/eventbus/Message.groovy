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

package org.vertx.groovy.core.eventbus

import org.vertx.java.core.eventbus.Message as JMessage
import org.vertx.java.core.json.JsonObject

/*
* Represents a message delivered to a handler
* @author <a href="http://tfox.org">Tim Fox</a>
*/
class Message {

  /**
   * The body of the message
   */
  def body

  private JMessage jMessage

  Message(JMessage jMessage) {
    if (jMessage.body instanceof JsonObject) {
      this.body = jMessage.body.toMap()
    } else {
      this.body = jMessage.body
    }
    this.jMessage = jMessage
  }
  
  String getReplyAddress() { return jMessage.replyAddress }
  void setReplyAddress(String address) { jMessage.replyAddress = address }

  /**
 * Reply to this message. If the message was sent specifying a reply handler, that handler will be
 * called when it has received a reply. If the message wasn't sent specifying a receipt handler
 * this method does nothing.
 * @param message The reply message
   @param replyHandler Optional reply handler, so you can get a reply to your reply
 */
  void reply(message, Closure replyHandler = null) {
    message = EventBus.convertMessage(message)
    jMessage.reply(message, EventBus.wrapHandler(replyHandler))
  }
}
