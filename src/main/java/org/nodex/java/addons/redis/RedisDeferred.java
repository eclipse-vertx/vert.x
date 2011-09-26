package org.nodex.java.addons.redis;

import org.nodex.java.core.Deferred;
import org.nodex.java.core.DeferredAction;
import org.nodex.java.core.Nodex;
import org.nodex.java.core.buffer.Buffer;
import org.nodex.java.core.internal.NodexInternal;

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
  final long contextID;
  final RedisConnection rc;

  TxCommandType commandType = TxCommandType.OTHER;
  RedisReply reply;

  RedisDeferred(DeferredType type, RedisConnection rc) {
    this.type = type;
    this.contextID = Nodex.instance.getContextID();
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

  public void handleReply(final RedisReply reply) {
    this.reply = reply;
    NodexInternal.instance.executeOnContext(contextID, new Runnable() {
      public void run() {
        try {
          doHandleReply();
        } catch (Exception e) {
          e.printStackTrace(System.err);
        }
      }
    });
  }

  public void handleReplyDirect(final RedisReply reply) {
    this.reply = reply;
    doHandleReply();
  }
}