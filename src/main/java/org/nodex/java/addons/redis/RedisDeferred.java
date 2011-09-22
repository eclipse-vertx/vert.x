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
import org.nodex.java.core.Nodex;
import org.nodex.java.core.buffer.Buffer;
import org.nodex.java.core.internal.NodexInternal;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
abstract class RedisDeferred<T> extends DeferredAction<T> {

  static enum DeferredType {
     VOID, BOOLEAN, INTEGER, BULK, MULTI_BULK, DOUBLE
  }

  final DeferredType type;
  final long contextID;

  RedisDeferred(DeferredType type) {
    this.type = type;
    this.contextID = Nodex.instance.getContextID();
  }

  void setReply(final RedisReply reply) {
    NodexInternal.instance.executeOnContext(contextID, new Runnable() {
      public void run() {
        if (reply.type == RedisReply.Type.ERROR) {
          setException(new RedisException(reply.error));
        } else {
          switch (type) {
            case VOID: {
              setResult(null);
              break;
            }
            case BOOLEAN: {
              ((RedisDeferred<Boolean>)RedisDeferred.this).setResult(reply.intResult == 1);
              break;
            }
            case INTEGER: {
              ((RedisDeferred<Integer>)RedisDeferred.this).setResult(reply.intResult);
              break;
            }
            case DOUBLE: {
              ((RedisDeferred<Double>)RedisDeferred.this).setResult(Double.valueOf(reply.bulkResult.toString()));
              break;
            }
            case BULK: {
              ((RedisDeferred<Buffer>)RedisDeferred.this).setResult(reply.bulkResult);
              break;
            }
            case MULTI_BULK: {
              ((RedisDeferred<Buffer[]>)RedisDeferred.this).setResult(reply.multiBulkResult);
              break;
            }
          }
        }
      }
    });
  }
}