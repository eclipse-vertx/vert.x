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

package org.nodex.java.addons.redis;

import java.util.LinkedList;
import java.util.Queue;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class TxReplyHandler implements ReplyHandler {

  final Queue<RedisDeferred<?>> deferreds = new LinkedList<>();

  RedisDeferred<?> endDeferred; // The Deferred corresponding to the EXEC/DISCARD

  boolean discarded;

  public void setReply(RedisReply reply) {

    if (reply.type == RedisReply.Type.ONE_LINE) {
      if (reply.line.equals("QUEUED")) {
        return;
      }
    }

    if (discarded) {
      for (RedisDeferred<?> deferred: deferreds) {
        deferred.setException(new RedisException("Transaction discarded"));
      }
      sendEnd();
    } else {
      RedisDeferred<?> deferred = deferreds.poll();
      if (deferred != null) {
        deferred.setReply(reply);
        if (deferreds.isEmpty()) {
          sendEnd();
        }
      } else {
        sendEnd();
      }
    }
  }

  private void sendEnd() {
    if (endDeferred != null) {
      endDeferred.setResult(null);
      endDeferred = null;
    } else {
      throw new IllegalStateException("Invalid tx response");
    }
  }
}
