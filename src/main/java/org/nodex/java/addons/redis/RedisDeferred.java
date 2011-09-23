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

import org.nodex.java.core.DeferredAction;
import org.nodex.java.core.buffer.Buffer;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
abstract class RedisDeferred<T> extends DeferredAction<T> implements ReplyHandler {

  static enum DeferredType {
     VOID, BOOLEAN, INTEGER, BULK, MULTI_BULK, DOUBLE
  }

  static enum TxCommandType {
    MULTI, EXEC, DISCARD, OTHER
  }

  final DeferredType type;
  TxCommandType commandType = TxCommandType.OTHER;

  RedisDeferred(DeferredType type) {
    this.type = type;
  }

  public void setReply(final RedisReply reply) {
    if (reply.type == RedisReply.Type.ERROR) {
      setException(new RedisException(reply.error));
    } else {
      //If transacted the user should ignore the result, the EXEC will give the correct results
      switch (type) {
        case VOID: {
          setResult(null);
          break;
        }
        case BOOLEAN: {
          ((RedisDeferred<Boolean>)this).setResult(reply.intResult == 1);
          break;
        }
        case INTEGER: {
          ((RedisDeferred<Integer>)this).setResult(reply.intResult);
          break;
        }
        case DOUBLE: {
          ((RedisDeferred<Double>)this).setResult(Double.valueOf(reply.bulkResult.toString()));
          break;
        }
        case BULK: {
          ((RedisDeferred<Buffer>)this).setResult(reply.bulkResult);
          break;
        }
        case MULTI_BULK: {
          ((RedisDeferred<Buffer[]>)this).setResult(reply.multiBulkResult);
          break;
        }
      }
    }
  }
}
