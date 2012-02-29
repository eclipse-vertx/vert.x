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

package org.vertx.java.old.redis;

import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.impl.Deferred;
import org.vertx.java.core.impl.DeferredAction;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
abstract class RedisDeferred<T> extends DeferredAction<T> implements ReplyHandler {

  private static final Logger log = LoggerFactory.getLogger(RedisDeferred.class);

  static enum DeferredType {
    VOID, BOOLEAN, INTEGER, BULK, MULTI_BULK, DOUBLE, STRING
  }

  static enum TxCommandType {
    MULTI, EXEC, DISCARD, OTHER
  }

  final DeferredType type;
  final RedisConnection rc;

  TxCommandType commandType = TxCommandType.OTHER;
  RedisReply reply;

  RedisDeferred(DeferredType type, RedisConnection rc) {
    this.type = type;
    this.rc = rc;
  }

  @Override
  public Deferred<T> execute() {
    if (rc.conn == null) {
      rc.addToPending(this);
    } else {
      if (!executed) {
        run();
        executed = true;
      }
    }
    return this;
  }

  void doHandleReply() {
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
        case STRING: {
          ((RedisDeferred<String>)this).setResult(reply.line);
          break;
        }
        case INTEGER: {
          ((RedisDeferred<Integer>)this).setResult(reply.intResult);
          break;
        }
        case DOUBLE: {
          if (reply.bulkResult != null) {
            ((RedisDeferred<Double>)this).setResult(Double.valueOf(reply.bulkResult.toString()));
          } else {
            setResult(null);
          }
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

  public void handleReply(final RedisReply reply) {
    this.reply = reply;
    try {
      doHandleReply();
    } catch (Exception e) {
      log.error("Failed to handle reply", e);
    }
  }
}