/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.parsetools.impl;

import io.netty.buffer.Unpooled;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.Arguments;
import io.vertx.core.parsetools.RecordParser;
import io.vertx.core.streams.ReadStream;

import java.util.Objects;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author <a href="mailto:larsdtimm@gmail.com">Lars Timm</a>
 */
public class RecordParserImpl implements RecordParser {

  // Empty and unmodifiable
  private static final Buffer EMPTY_BUFFER = Buffer.buffer(Unpooled.EMPTY_BUFFER);

  private Buffer buff = EMPTY_BUFFER;
  private int pos;            // Current position in buffer
  private int start;          // Position of beginning of current record
  private int delimPos;       // Position of current match in delimiter array

  private boolean delimited;
  private byte[] delim;
  private int recordSize;
  private int maxRecordSize;
  private long demand = Long.MAX_VALUE;
  private Handler<Buffer> eventHandler;
  private Handler<Void> endHandler;
  private Handler<Throwable> exceptionHandler;
  private boolean parsing;
  private boolean streamEnded;

  private final ReadStream<Buffer> stream;

  private RecordParserImpl(ReadStream<Buffer> stream) {
    this.stream = stream;
  }

  public void setOutput(Handler<Buffer> output) {
    Objects.requireNonNull(output, "output");
    eventHandler = output;
  }

  /**
   * Helper method to convert a latin-1 String to an array of bytes for use as a delimiter
   * Please do not use this for non latin-1 characters
   *
   * @param str  the string
   * @return The byte[] form of the string
   */
  public static Buffer latin1StringToBytes(String str) {
    byte[] bytes = new byte[str.length()];
    for (int i = 0; i < str.length(); i++) {
      char c = str.charAt(i);
      bytes[i] = (byte) (c & 0xFF);
    }
    return Buffer.buffer(bytes);
  }

  /**
   * Create a new {@code RecordParser} instance, initially in delimited mode, and where the delimiter can be represented
   * by the String {@code} delim endcoded in latin-1 . Don't use this if your String contains other than latin-1 characters.
   * <p>
   * {@code output} Will receive whole records which have been parsed.
   *
   * @param delim  the initial delimiter string
   * @param output  handler that will receive the output
   */
  public static RecordParser newDelimited(String delim, ReadStream<Buffer> stream, Handler<Buffer> output) {
    return newDelimited(latin1StringToBytes(delim), stream, output);
  }

  /**
   * Create a new {@code RecordParser} instance, initially in delimited mode, and where the delimiter can be represented
   * by the {@code buffer} delim.
   * <p>
   * {@code output} Will receive whole records which have been parsed.
   *
   * @param delim  the initial delimiter buffer
   * @param output  handler that will receive the output
   */
  public static RecordParser newDelimited(Buffer delim, ReadStream<Buffer> stream, Handler<Buffer> output) {
    RecordParserImpl ls = new RecordParserImpl(stream);
    ls.handler(output);
    ls.delimitedMode(delim);
    return ls;
  }

  /**
   * Create a new {@code RecordParser} instance, initially in fixed size mode, and where the record size is specified
   * by the {@code size} parameter.
   * <p>
   * {@code output} Will receive whole records which have been parsed.
   *
   * @param size  the initial record size
   * @param output  handler that will receive the output
   */
  public static RecordParser newFixed(int size, ReadStream<Buffer> stream, Handler<Buffer> output) {
    Arguments.require(size > 0, "Size must be > 0");
    RecordParserImpl ls = new RecordParserImpl(stream);
    ls.handler(output);
    ls.fixedSizeMode(size);
    return ls;
  }

  /**
   * Flip the parser into delimited mode, and where the delimiter can be represented
   * by the String {@code delim} encoded in latin-1 . Don't use this if your String contains other than latin-1 characters.
   * <p>
   * This method can be called multiple times with different values of delim while data is being parsed.
   *
   * @param delim  the new delimeter
   */
  public void delimitedMode(String delim) {
    delimitedMode(latin1StringToBytes(delim));
  }

  /**
   * Flip the parser into delimited mode, and where the delimiter can be represented
   * by the delimiter {@code delim}.
   * <p>
   * This method can be called multiple times with different values of delim while data is being parsed.
   *
   * @param delim  the new delimiter
   */
  public void delimitedMode(Buffer delim) {
    Objects.requireNonNull(delim, "delim");
    delimited = true;
    this.delim = delim.getBytes();
    delimPos = 0;
  }

  /**
   * Flip the parser into fixed size mode, where the record size is specified by {@code size} in bytes.
   * <p>
   * This method can be called multiple times with different values of size while data is being parsed.
   *
   * @param size  the new record size
   */
  public void fixedSizeMode(int size) {
    Arguments.require(size > 0, "Size must be > 0");
    delimited = false;
    recordSize = size;
  }

  /**
   * Set the maximum allowed size for a record when using the delimited mode.
   * The delimiter itself does not count for the record size.
   * <p>
   * If a record is longer than specified, an {@link IllegalStateException} will be thrown.
   *
   * @param size the maximum record size
   * @return  a reference to this, so the API can be used fluently
   */
  public RecordParser maxRecordSize(int size) {
    Arguments.require(size > 0, "Size must be > 0");
    maxRecordSize = size;
    return this;
  }

  private void handleParsing() {
    if (parsing) {
      return;
    }
    parsing = true;
    try {
      do {
        if (demand > 0L) {
          int next;
          if (delimited) {
            next = parseDelimited();
          } else {
            next = parseFixed();
          }
          if (next == -1) {
            if (streamEnded) {
              if (buff.length() == 0) {
                break;
              }
              next = buff.length();
            } else {
              ReadStream<Buffer> s = stream;
              if (s != null) {
                s.resume();
              }
              if (streamEnded) {
                continue;
              }
              break;
            }
          }
          if (demand != Long.MAX_VALUE) {
            demand--;
          }
          Buffer event = buff.getBuffer(start, next);
          start = pos;
          Handler<Buffer> handler = eventHandler;
          if (handler != null) {
            handler.handle(event);
          }
          if (streamEnded) {
            break;
          }
        } else {
          // Should use a threshold ?
          ReadStream<Buffer> s = stream;
          if (s != null) {
            s.pause();
          }
          break;
        }
      } while (true);
      int len = buff.length();
      if (start == len) {
        buff = EMPTY_BUFFER;
      } else if (start > 0) {
        buff = buff.getBuffer(start, len);
      }
      pos -= start;
      start = 0;
      if (streamEnded) {
        end();
      }
    } finally {
      parsing = false;
    }
  }

  private int parseDelimited() {
    int len = buff.length();
    for (; pos < len; pos++) {
      if (buff.getByte(pos) == delim[delimPos]) {
        delimPos++;
        if (delimPos == delim.length) {
          pos++;
          delimPos = 0;
          return pos - delim.length;
        }
      } else {
        if (delimPos > 0) {
          pos -= delimPos;
          delimPos = 0;
        }
      }
    }
    return -1;
  }

  private int parseFixed() {
    int len = buff.length();
    if (len - start >= recordSize) {
      int end = start + recordSize;
      pos = end;
      return end;
    }
    return -1;
  }

  /**
   * This method is called to provide the parser with data.
   *
   * @param buffer  a chunk of data
   */
  public void handle(Buffer buffer) {
    if (buff.length() == 0) {
      buff = buffer;
    } else {
      buff.appendBuffer(buffer);
    }
    handleParsing();
    if (buff != null && maxRecordSize > 0 && buff.length() > maxRecordSize) {
      IllegalStateException ex = new IllegalStateException("The current record is too long");
      if (exceptionHandler != null) {
        exceptionHandler.handle(ex);
      } else {
        throw ex;
      }
    }
  }

  private void end() {
    Handler<Void> handler = endHandler;
    if (handler != null) {
      handler.handle(null);
    }
  }

  @Override
  public RecordParser exceptionHandler(Handler<Throwable> handler) {
    exceptionHandler = handler;
    return this;
  }

  @Override
  public RecordParser handler(Handler<Buffer> handler) {
    eventHandler = handler;
    if (stream != null) {
      if (handler != null) {
        stream.endHandler(v -> {
          streamEnded = true;
          handleParsing();
        });
        stream.exceptionHandler(err -> {
          if (exceptionHandler != null) {
            exceptionHandler.handle(err);
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
  public RecordParser pause() {
    demand = 0L;
    return this;
  }

  @Override
  public RecordParser fetch(long amount) {
    Arguments.require(amount > 0, "Fetch amount must be > 0");
    demand += amount;
    if (demand < 0L) {
      demand = Long.MAX_VALUE;
    }
    handleParsing();
    return this;
  }

  @Override
  public RecordParser resume() {
    return fetch(Long.MAX_VALUE);
  }

  @Override
  public RecordParser endHandler(Handler<Void> handler) {
    endHandler = handler;
    return this;
  }
}
