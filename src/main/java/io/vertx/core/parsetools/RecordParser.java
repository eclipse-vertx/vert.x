/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.parsetools;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.Arguments;

import java.util.Objects;

/**
 * A helper class which allows you to easily parse protocols which are delimited by a sequence of bytes, or fixed
 * size records.
 * <p>
 * Instances of this class take as input {@link io.vertx.core.buffer.Buffer} instances containing raw bytes,
 * and output records.
 * <p>
 * For example, if I had a simple ASCII text protocol delimited by '\n' and the input was the following:
 * <p>
 * <pre>
 * buffer1:HELLO\nHOW ARE Y
 * buffer2:OU?\nI AM
 * buffer3: DOING OK
 * buffer4:\n
 * </pre>
 * Then the output would be:<p>
 * <pre>
 * buffer1:HELLO
 * buffer2:HOW ARE YOU?
 * buffer3:I AM DOING OK
 * </pre>
 * Instances of this class can be changed between delimited mode and fixed size record mode on the fly as
 * individual records are read, this allows you to parse protocols where, for example, the first 5 records might
 * all be fixed size (of potentially different sizes), followed by some delimited records, followed by more fixed
 * size records.
 * <p>
 * Instances of this class can't currently be used for protocols where the text is encoded with something other than
 * a 1-1 byte-char mapping.
 * <p>
 * Please see the documentation for more information.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class RecordParser implements Handler<Buffer> {

  private Buffer buff;
  private int pos;            // Current position in buffer
  private int start;          // Position of beginning of current record
  private int delimPos;       // Position of current match in delimiter array
  private boolean reset;      // Allows user to toggle mode / change delim when records are emitted

  private boolean delimited;
  private byte[] delim;
  private int recordSize;
  private Handler<Buffer> output;

  private RecordParser(Handler<Buffer> output) {
    this.output = output;
  }

  public void setOutput(Handler<Buffer> output) {
    Objects.requireNonNull(output, "output");
    this.output = output;
  }

  /**
   * Helper method to convert a latin-1 String to an array of bytes for use as a delimiter
   * Please do not use this for non latin-1 characters
   *
   * @param str  the string
   * @return The byte[] form of the string
   */
  public static byte[] latin1StringToBytes(String str) {
    byte[] bytes = new byte[str.length()];
    for (int i = 0; i < str.length(); i++) {
      char c = str.charAt(i);
      bytes[i] = (byte) (c & 0xFF);
    }
    return bytes;
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
  public static RecordParser newDelimited(String delim, Handler<Buffer> output) {
    return newDelimited(latin1StringToBytes(delim), output);
  }

  /**
   * Create a new {@code RecordParser} instance, initially in delimited mode, and where the delimiter can be represented
   * by the {@code byte[]} delim.
   * <p>
   * {@code output} Will receive whole records which have been parsed.
   *
   * @param delim  the initial delimiter byte[]
   * @param output  handler that will receive the output
   */
  public static RecordParser newDelimited(byte[] delim, Handler<Buffer> output) {
    RecordParser ls = new RecordParser(output);
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
  public static RecordParser newFixed(int size, Handler<Buffer> output) {
    Arguments.require(size > 0, "Size must be > 0");
    RecordParser ls = new RecordParser(output);
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
  public void delimitedMode(byte[] delim) {
    Objects.requireNonNull(delim, "delim");
    delimited = true;
    this.delim = delim;
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

  private void parseDelimited() {
    int len = buff.length();
    for (; pos < len && !reset; pos++) {
      if (buff.getByte(pos) == delim[delimPos]) {
        delimPos++;
        if (delimPos == delim.length) {
          Buffer ret = buff.getBuffer(start, pos - delim.length + 1);
          start = pos + 1;
          delimPos = 0;
          output.handle(ret);
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
      output.handle(ret);
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
}
