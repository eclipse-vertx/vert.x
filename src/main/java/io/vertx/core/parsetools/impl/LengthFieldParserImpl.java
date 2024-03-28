/*
 * Copyright (c) 2011-2021 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.parsetools.impl;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.Arguments;
import io.vertx.core.parsetools.LengthFieldParser;
import io.vertx.core.parsetools.RecordParser;
import io.vertx.core.streams.ReadStream;

/**
 * @author <a href="mailto:emad.albloushi@gmail.com">Emad Alblueshi</a>
 */

public class LengthFieldParserImpl implements LengthFieldParser {

  private Buffer acc = Buffer.buffer();
  private Handler<Throwable> exceptionHandler;
  private final RecordParser parser;

  private long frameLength = -1;
  private final int length;
  private final int offset;
  private final boolean skip;
  private final int max;

  public LengthFieldParserImpl(int length, int offset, boolean skip, int max, ReadStream<Buffer> stream) {
    Arguments.require(length == 1 || length == 2 || length == 3 || length == 4 || length == 8,
      "Field length must be 1, 2, 3, 4, or 8");
    Arguments.require(offset >= 0, "Field offset must be >= 0");
    Arguments.require(max > 0, "Max frame length must be > 0");
    this.length = length;
    this.offset = offset;
    this.skip = skip;
    this.max = max;
    this.parser = RecordParser.newFixed(length + offset, stream);

  }

  @Override
  public LengthFieldParser exceptionHandler(Handler<Throwable> handler) {
    if (handler != null) {
      exceptionHandler = handler;
    }
    return this;
  }

  @Override
  public LengthFieldParser handler(Handler<Buffer> handler) {
    if (handler == null) {
      if(parser != null) {
        parser.handler(null);
        parser.exceptionHandler(null);
        parser.endHandler(null);
      }
      return this;
    }
    parser.handler(buffer -> {
      try {
        if (frameLength == -1) {
          frameLength = handleFrameLength(buffer);
          if (frameLength > max || frameLength <= 0) {
            try {
              String err = frameLength <= 0 ? "Frame length : " + frameLength + " <= 0" :
                "Frame length is too large current: " + frameLength + " max: " + max;
              IllegalStateException ex = new IllegalStateException(err);
              if (exceptionHandler != null) {
                exceptionHandler.handle(ex);
              } else {
                throw ex;
              }
            } finally {
              frameLength = 0;
              this.handler(null);
            }
          } else {
            if (!skip) {
              // append length + offset
              acc.appendBuffer(buffer);
            }
            // next will get the content
            parser.fixedSizeMode((int) frameLength);
            fetch(1);
          }
        } else {
          if(!skip) {
            // sanity check
            if(buffer.length() > 0) {
              // append content
              acc.appendBuffer(buffer);
              buffer = acc.copy();
              acc = Buffer.buffer();
            }
          }
          handler.handle(buffer);
          // reset for next read
          parser.fixedSizeMode(length + offset);
          frameLength = -1;
        }
      } catch (Exception ex) {
        if (exceptionHandler != null) {
          exceptionHandler.handle(ex);
        } else {
          throw ex;
        }
      }
    });
    return this;
  }

  @Override
  public LengthFieldParser pause() {
    parser.pause();
    return this;
  }

  @Override
  public LengthFieldParser resume() {
    parser.resume();
    return this;
  }

  @Override
  public LengthFieldParser fetch(long amount) {
    parser.fetch(amount);
    return this;
  }

  @Override
  public LengthFieldParser endHandler(Handler<Void> handler) {
    parser.endHandler(handler);
    return this;
  }

  @Override
  public void handle(Buffer buffer) {
    parser.handle(buffer);
  }

  long handleFrameLength(Buffer buffer) {
    if (length == 1) {
      return buffer.getByte(offset);
    } else if (length == 2) {
      return buffer.getShort(offset);
    } else if (length == 3) {
      return buffer.getMedium(offset);
    } else if (length == 4) {
      return buffer.getInt(offset);
    } else {
      return buffer.getLong(offset);
    }
  }
}
