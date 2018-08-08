/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
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
import io.vertx.core.parsetools.RecordParser;
import io.vertx.core.queue.Queue;
import io.vertx.core.streams.ReadStream;

import java.util.Objects;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author <a href="mailto:larsdtimm@gmail.com">Lars Timm</a>
 */
public class RecordParserImpl implements RecordParser {

  private Buffer buff;
  private int pos;            // Current position in buffer
  private int start;          // Position of beginning of current record
  private int delimPos;       // Position of current match in delimiter array
  private boolean reset;      // Allows user to toggle mode / change delim when records are emitted

  private boolean delimited;
  private byte[] delim;
  private int recordSize;
  private Handler<Void> endHandler;
  private Handler<Throwable> exceptionHandler;

  private final ReadStream<Buffer> stream;
  private final Queue<Buffer> pending;

  private RecordParserImpl(ReadStream<Buffer> stream, Handler<Buffer> output) {
    this.stream = stream;
    this.pending = Queue
      .<Buffer>queue()
      .handler(output)
      .writableHandler(v -> {
        stream.resume();
      });
  }

  public void setOutput(Handler<Buffer> output) {
    Objects.requireNonNull(output, "output");
    this.pending.handler(output);
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
    RecordParserImpl ls = new RecordParserImpl(stream, output);
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
    RecordParserImpl ls = new RecordParserImpl(stream, output);
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
    reset = true;
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
    reset = true;
  }

  private void handleParsing() {
    int len = buff.length();
    do {
      reset = false;
      if (delimited) {
        parseDelimited();
      } else {
        parseFixed();
      }
    } while (reset);

    if (start == len) {
      //Nothing left
      buff = null;
      pos = 0;
    } else {
      buff = buff.getBuffer(start, len);
      pos = buff.length();
    }
    start = 0;
  }

  private void handleEvent(Buffer event) {
    if (!pending.add(event) && stream != null) {
      stream.pause();
    }
  }

  private void parseDelimited() {
    int len = buff.length();
    for (; pos < len && !reset; pos++) {
      if (buff.getByte(pos) == delim[delimPos]) {
        delimPos++;
        if (delimPos == delim.length) {
          Buffer ret = buff.getBuffer(start, pos - delim.length + 1);
          start = pos + 1;
          delimPos = 0;
          handleEvent(ret);
        }
      } else {
        if (delimPos > 0) {
          pos -= delimPos;
          delimPos = 0;
        }
      }
    }
  }

  private void parseFixed() {
    int len = buff.length();
    while (len - start >= recordSize && !reset) {
      int end = start + recordSize;
      Buffer ret = buff.getBuffer(start, end);
      start = end;
      pos = start - 1;
      handleEvent(ret);
    }
  }

  /**
   * This method is called to provide the parser with data.
   *
   * @param buffer  a chunk of data
   */
  public void handle(Buffer buffer) {
    if (buff == null) {
      buff = buffer;
    } else {
      buff.appendBuffer(buffer);
    }
    handleParsing();
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
    pending.handler(handler);
    if (stream != null) {
      if (handler != null) {
        stream.endHandler(v -> end());
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
    pending.pause();
    return this;
  }

  @Override
  public RecordParser fetch(long amount) {
    pending.take(amount);
    return this;
  }

  @Override
  public RecordParser resume() {
    pending.resume();
    return this;
  }

  @Override
  public RecordParser endHandler(Handler<Void> handler) {
    endHandler = handler;
    return this;
  }
}
