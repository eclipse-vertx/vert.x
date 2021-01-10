package io.vertx.core.parsetools.impl;

import io.netty.buffer.Unpooled;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.Arguments;
import io.vertx.core.parsetools.LengthFieldParser;
import io.vertx.core.streams.ReadStream;

/**
 * @author <a href="mailto:emad.albloushi@gmail.com">Emad Alblueshi</a>
 */

public class LengthFieldParserImpl implements LengthFieldParser {

  private final Buffer EMPTY_BUFFER = Buffer.buffer(Unpooled.EMPTY_BUFFER);

  private long demand = Long.MAX_VALUE;
  private Handler<Buffer> eventHandler;
  private Handler<Void> endHandler;
  private Handler<Throwable> exceptionHandler;
  private final ReadStream<Buffer> stream;
  private boolean ended;

  private Buffer acc = EMPTY_BUFFER;
  private long frameLength;
  private final int length;
  private final int offset;
  private final int max;

  public LengthFieldParserImpl(int length, int offset, int max, ReadStream<Buffer> stream) {
    Arguments.require(length == 1 || length == 2 || length == 3 || length == 4 || length == 8,
      "Field length must be 1, 2, 3, 4, or 8");
    Arguments.require(offset >= 0, "Field offset must be >= 0");
    Arguments.require(max > 0, "Max frame length must be > 0");
    this.length = length;
    this.offset = offset;
    this.max = max;
    this.stream = stream;
  }


  @Override
  public LengthFieldParser exceptionHandler(Handler<Throwable> handler) {
    exceptionHandler = handler;
    return this;
  }

  @Override
  public LengthFieldParser handler(Handler<Buffer> handler) {
    eventHandler = handler;
    if (stream != null) {
      if (handler != null) {
        stream.endHandler(v -> end());
        stream.exceptionHandler(ex -> {
          if (exceptionHandler != null) {
            exceptionHandler.handle(ex);
          }
        });
        stream.handler(this);
      } else {
        stream.handler(null);
        stream.endHandler(null);
        stream.exceptionHandler(null);
      }
    }
    return this;
  }

  @Override
  public LengthFieldParser pause() {
    demand = 0L;
    return this;
  }

  @Override
  public LengthFieldParser resume() {
    return fetch(Long.MAX_VALUE);
  }

  @Override
  public LengthFieldParser fetch(long amount) {
    Arguments.require(amount > 0L, "Fetch amount must be > 0L");
    demand += amount;
    if (demand < 0L) {
      demand = Long.MAX_VALUE;
    }
    handleParsing();
    return this;
  }

  @Override
  public LengthFieldParser endHandler(Handler<Void> handler) {
    endHandler = handler;
    return this;
  }

  @Override
  public void handle(Buffer buffer) {
    if (acc.length() == 0) {
      acc = buffer;
    } else {
      acc.appendBuffer(buffer);
    }
    handleParsing();
  }

  void end() {
    ended = true;
    handleParsing();
  }

  void clear() {
    frameLength = 0;
    acc = EMPTY_BUFFER;
  }

  void handleEvent(Buffer event) {
    if (demand != Long.MAX_VALUE) {
      demand--;
    }
    Handler<Buffer> handler = this.eventHandler;
    if (handler != null) {
      handler.handle(event);
    }
  }

  private void handleParsing() {
    try {
      while (true) {
        boolean next = (acc.length() >= length + offset + frameLength);
        if (!next) {
          if (ended) {
            if (endHandler != null) {
              endHandler.handle(null);
            }
            return;
          }
          break;
        } else {
          if (demand > 0L) {
            handleFieldFrame();
          } else {
            break;
          }
        }
      }
      if (demand == 0L) {
        if (stream != null) {
          stream.pause();
        }
      } else {
        if (stream != null) {
          stream.resume();
        }
      }
    } catch (Exception e) {
      if (exceptionHandler != null) {
        exceptionHandler.handle(e);
      } else {
        throw e;
      }
    }
  }

  void handleFieldFrame() {
    if (length == 1) {
      frameLength = acc.getByte(offset);
    } else if (length == 2) {
      frameLength = acc.getShort(offset);
    } else if (length == 3) {
      frameLength = acc.getMedium(offset);
    } else if(length == 4) {
      frameLength = acc.getInt(offset);
    } else if(length == 8) {
      frameLength = acc.getLong(offset);
    }
    if(frameLength > max || frameLength < 0) {
      try {
        String err = frameLength < 0 ? "Frame length is corrupted < 0"
          : "Frame length is too large current: " + frameLength + " max: " + max;
        IllegalStateException ex = new IllegalStateException(err);
        if (exceptionHandler != null) {
          exceptionHandler.handle(ex);
        } else {
          throw ex;
        }
      } finally {
        clear();
      }
    } else {
      int len = length + offset + (int) frameLength;
      if(acc.length() >= len) {
        Buffer event = acc.getBuffer(length + offset, len);
        handleEvent(event);
        acc = acc.getBuffer(len, acc.length());
      }
    }
  }
}
