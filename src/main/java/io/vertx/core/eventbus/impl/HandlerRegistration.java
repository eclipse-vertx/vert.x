package io.vertx.core.eventbus.impl;

import io.vertx.core.*;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.eventbus.ReplyFailure;
import io.vertx.core.eventbus.impl.clustered.ClusteredMessage;
import io.vertx.core.impl.Arguments;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.spi.metrics.EventBusMetrics;
import io.vertx.core.streams.ReadStream;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;

/*
 * This class is optimised for performance when used on the same event loop it was created on.
 * However it can be used safely from other threads.
 *
 * The internal state is protected using the synchronized keyword. If always used on the same event loop, then
 * we benefit from biased locking which makes the overhead of synchronized near zero.
 */
public class HandlerRegistration<T> implements MessageConsumer<T>, Handler<Message<T>> {

  private static final Logger log = LoggerFactory.getLogger(HandlerRegistration.class);

  public static final int DEFAULT_MAX_BUFFERED_MESSAGES = 1000;

  private final Vertx vertx;
  private final EventBusMetrics metrics;
  private final EventBusImpl eventBus;
  private final String address;
  private final String repliedAddress;
  private final boolean localOnly;
  private final Handler<AsyncResult<Message<T>>> asyncResultHandler;
  private long timeoutID = -1;
  private boolean registered;
  private Handler<Message<T>> handler;
  private AsyncResult<Void> result;
  private Handler<AsyncResult<Void>> completionHandler;
  private Handler<Void> endHandler;
  private Handler<Message<T>> discardHandler;
  private int maxBufferedMessages = DEFAULT_MAX_BUFFERED_MESSAGES;
  private final Queue<Message<T>> pending = new ArrayDeque<>(8);
  private boolean paused;
  private Object metric;

  public HandlerRegistration(Vertx vertx, EventBusMetrics metrics, EventBusImpl eventBus, String address,
                             String repliedAddress, boolean localOnly,
                             Handler<AsyncResult<Message<T>>> asyncResultHandler, long timeout) {
    this.vertx = vertx;
    this.metrics = metrics;
    this.eventBus = eventBus;
    this.address = address;
    this.repliedAddress = repliedAddress;
    this.localOnly = localOnly;
    this.asyncResultHandler = asyncResultHandler;
    if (timeout != -1) {
      timeoutID = vertx.setTimer(timeout, tid -> {
        metrics.replyFailure(address, ReplyFailure.TIMEOUT);
        sendAsyncResultFailure(ReplyFailure.TIMEOUT, "Timed out after waiting " + timeout + "(ms) for a reply. address: " + address);
      });
    }
  }

  @Override
  public synchronized MessageConsumer<T> setMaxBufferedMessages(int maxBufferedMessages) {
    Arguments.require(maxBufferedMessages >= 0, "Max buffered messages cannot be negative");
    while (pending.size() > maxBufferedMessages) {
      pending.poll();
    }
    this.maxBufferedMessages = maxBufferedMessages;
    return this;
  }

  @Override
  public synchronized int getMaxBufferedMessages() {
    return maxBufferedMessages;
  }

  @Override
  public String address() {
    return address;
  }

  @Override
  public synchronized void completionHandler(Handler<AsyncResult<Void>> completionHandler) {
    Objects.requireNonNull(completionHandler);
    if (result != null) {
      AsyncResult<Void> value = result;
      vertx.runOnContext(v -> completionHandler.handle(value));
    } else {
      this.completionHandler = completionHandler;
    }
  }

  @Override
  public synchronized void unregister() {
    unregister(false);
  }

  @Override
  public synchronized void unregister(Handler<AsyncResult<Void>> completionHandler) {
    Objects.requireNonNull(completionHandler);
    doUnregister(completionHandler, false);
  }

  public void unregister(boolean callEndHandler) {
    doUnregister(null, callEndHandler);
  }

  public void sendAsyncResultFailure(ReplyFailure failure, String msg) {
    unregister();
    asyncResultHandler.handle(Future.failedFuture(new ReplyException(failure, msg)));
  }

  private void doUnregister(Handler<AsyncResult<Void>> completionHandler, boolean callEndHandler) {
    if (timeoutID != -1) {
      vertx.cancelTimer(timeoutID);
    }
    if (endHandler != null && callEndHandler) {
      Handler<Void> theEndHandler = endHandler;
      Handler<AsyncResult<Void>> handler = completionHandler;
      completionHandler = ar -> {
        theEndHandler.handle(null);
        if (handler != null) {
          handler.handle(ar);
        }
      };
    }
    if (registered) {
      registered = false;
      eventBus.removeRegistration(address, this, completionHandler);
    } else {
      callCompletionHandlerAsync(completionHandler);
    }
    registered = false;
  }

  private void callCompletionHandlerAsync(Handler<AsyncResult<Void>> completionHandler) {
    if (completionHandler != null) {
      vertx.runOnContext(v -> completionHandler.handle(Future.succeededFuture()));
    }
  }

  public synchronized void setResult(AsyncResult<Void> result) {
    this.result = result;
    if (completionHandler != null) {
      if (result.succeeded()) {
        metric = metrics.handlerRegistered(address, repliedAddress);
      }
      Handler<AsyncResult<Void>> callback = completionHandler;
      vertx.runOnContext(v -> callback.handle(result));
    } else if (result.failed()) {
      log.error("Failed to propagate registration for handler " + handler + " and address " + address);
    } else {
      metric = metrics.handlerRegistered(address, repliedAddress);
    }
  }

  @Override
  public void handle(Message<T> message) {
    Handler<Message<T>> theHandler = null;
    synchronized (this) {
      if (paused) {
        if (pending.size() < maxBufferedMessages) {
          pending.add(message);
        } else {
          if (discardHandler != null) {
            discardHandler.handle(message);
          } else {
            log.warn("Discarding message as more than " + maxBufferedMessages + " buffered in paused consumer");
          }
        }
      } else {
        checkNextTick();
        if (metrics.isEnabled()) {
          boolean local = true;
          if (message instanceof ClusteredMessage) {
            // A bit hacky
            ClusteredMessage cmsg = (ClusteredMessage)message;
            if (cmsg.isFromWire()) {
              local = false;
            }
          }
          metrics.beginHandleMessage(metric, local);
        }
        theHandler = handler;
      }
    }
    // Handle the message outside the sync block
    // https://bugs.eclipse.org/bugs/show_bug.cgi?id=473714
    if (theHandler != null) {
      String creditsAddress = message.headers().get(MessageProducerImpl.CREDIT_ADDRESS_HEADER_NAME);
      if (creditsAddress != null) {
        eventBus.send(creditsAddress, 1);
      }
      handleMessage(theHandler, message);
    }
  }

  private void handleMessage(Handler<Message<T>> theHandler, Message<T> message) {
    try {
      theHandler.handle(message);
      metrics.endHandleMessage(metric, null);
    } catch (Exception e) {
      log.error("Failed to handleMessage", e);
      metrics.endHandleMessage(metric, e);
      throw e;
    }
  }

  /*
   * Internal API for testing purposes.
   */
  public synchronized void discardHandler(Handler<Message<T>> handler) {
    this.discardHandler = handler;
  }

  @Override
  public synchronized MessageConsumer<T> handler(Handler<Message<T>> handler) {
    this.handler = handler;
    if (this.handler != null && !registered) {
      registered = true;
      eventBus.addRegistration(address, this, repliedAddress != null, localOnly);
    } else if (this.handler == null && registered) {
      // This will set registered to false
      this.unregister();
    }
    return this;
  }

  @Override
  public ReadStream<T> bodyStream() {
    return new BodyReadStream<>(this);
  }

  @Override
  public synchronized boolean isRegistered() {
    return registered;
  }

  @Override
  public synchronized MessageConsumer<T> pause() {
    if (!paused) {
      paused = true;
    }
    return this;
  }

  @Override
  public synchronized MessageConsumer<T> resume() {
    if (paused) {
      paused = false;
      checkNextTick();
    }
    return this;
  }

  @Override
  public synchronized MessageConsumer<T> endHandler(Handler<Void> endHandler) {
    if (endHandler != null) {
      // We should use the HandlerHolder context to properly do this (needs small refactoring)
      Context endCtx = vertx.getOrCreateContext();
      this.endHandler = v1 -> endCtx.runOnContext(v2 -> endHandler.handle(null));
    } else {
      this.endHandler = null;
    }
    return this;
  }

  @Override
  public synchronized MessageConsumer<T> exceptionHandler(Handler<Throwable> handler) {
    return this;
  }

  private void checkNextTick() {
    // Check if there are more pending messages in the queue that can be processed next time around
    if (!pending.isEmpty()) {
      vertx.runOnContext(v -> {
        if (!paused) {
          Message<T> message = pending.poll();
          if (message != null) {
            HandlerRegistration.this.handle(message);
          }
        }
      });
    }
  }

  public Handler<Message<T>> getHandler() {
    return handler;
  }

  public Object getMetric() {
    return metric;
  }

}
