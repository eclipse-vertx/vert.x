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

package io.vertx.core.parsetools;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.parsetools.impl.RecordParserImpl;
import io.vertx.core.streams.ReadStream;

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
 * @author <a href="mailto:larsdtimm@gmail.com">Lars Timm</a>
 */
@VertxGen
public interface RecordParser extends Handler<Buffer>, ReadStream<Buffer> {

  void setOutput(Handler<Buffer> output);

  /**
   * Like {@link #newDelimited(String)} but set the {@code output} that will receive whole records
   * which have been parsed.
   *
   * @param delim  the initial delimiter string
   * @param output  handler that will receive the output
   */
  static RecordParser newDelimited(String delim, Handler<Buffer> output) {
    return RecordParserImpl.newDelimited(delim, null, output);
  }

  /**
   * Like {@link #newDelimited(String)} but wraps the {@code stream}. The {@code stream} handlers will be set/unset
   * when the {@link #handler(Handler)} is set.
   * <p/>
   * The {@code pause()}/{@code resume()} operations are propagated to the {@code stream}.
   *
   * @param delim  the initial delimiter string
   * @param stream  the wrapped stream
   */
  static RecordParser newDelimited(String delim, ReadStream<Buffer> stream) {
    return RecordParserImpl.newDelimited(delim, stream, null);
  }

  /**
   * Create a new {@code RecordParser} instance, initially in delimited mode, and where the delimiter can be represented
   * by the String {@code} delim endcoded in latin-1 . Don't use this if your String contains other than latin-1 characters.
   * <p>
   * {@code output} Will receive whole records which have been parsed.
   *
   * @param delim  the initial delimiter string
   */
  static RecordParser newDelimited(String delim) {
    return RecordParserImpl.newDelimited(delim, null, null);
  }

  /**
   * Create a new {@code RecordParser} instance, initially in delimited mode, and where the delimiter can be represented
   * by the {@code Buffer} delim.
   * <p>
   *
   * @param delim  the initial delimiter buffer
   */
  static RecordParser newDelimited(Buffer delim) {
    return RecordParserImpl.newDelimited(delim,null,  null);
  }

  /**
   * Like {@link #newDelimited(Buffer)} but set the {@code output} that will receive whole records
   * which have been parsed.
   *
   * @param delim  the initial delimiter buffer
   * @param output  handler that will receive the output
   */
   static RecordParser newDelimited(Buffer delim, Handler<Buffer> output) {
     return RecordParserImpl.newDelimited(delim, null, output);
   }

  /**
   * Like {@link #newDelimited(Buffer)} but wraps the {@code stream}. The {@code stream} handlers will be set/unset
   * when the {@link #handler(Handler)} is set.
   * <p/>
   * The {@code pause()}/{@code resume()} operations are propagated to the {@code stream}.
   *
   * @param delim  the initial delimiter buffer
   * @param stream  the wrapped stream
   */
  static RecordParser newDelimited(Buffer delim, ReadStream<Buffer> stream) {
    return RecordParserImpl.newDelimited(delim, stream, null);
  }

  /**
   * Create a new {@code RecordParser} instance, initially in fixed size mode, and where the record size is specified
   * by the {@code size} parameter.
   * <p>
   * {@code output} Will receive whole records which have been parsed.
   *
   * @param size  the initial record size
   */
  static RecordParser newFixed(int size) {
    return RecordParserImpl.newFixed(size, null, null);
  }

  /**
   * Like {@link #newFixed(int)} but set the {@code output} that will receive whole records
   * which have been parsed.
   *
   * @param size  the initial record size
   * @param output  handler that will receive the output
   */
  static RecordParser newFixed(int size, Handler<Buffer> output) {
    return RecordParserImpl.newFixed(size, null, output);
  }

  /**
   * Like {@link #newFixed(int)} but wraps the {@code stream}. The {@code stream} handlers will be set/unset
   * when the {@link #handler(Handler)} is set.
   * <p/>
   * The {@code pause()}/{@code resume()} operations are propagated to the {@code stream}.
   *
   * @param size  the initial record size
   * @param stream  the wrapped stream
   */
  static RecordParser newFixed(int size, ReadStream<Buffer> stream) {
    return RecordParserImpl.newFixed(size, stream, null);
  }

  /**
   * Flip the parser into delimited mode, and where the delimiter can be represented
   * by the String {@code delim} encoded in latin-1 . Don't use this if your String contains other than latin-1 characters.
   * <p>
   * This method can be called multiple times with different values of delim while data is being parsed.
   *
   * @param delim  the new delimeter
   */
  void delimitedMode(String delim);

  /**
   * Flip the parser into delimited mode, and where the delimiter can be represented
   * by the delimiter {@code delim}.
   * <p>
   * This method can be called multiple times with different values of delim while data is being parsed.
   *
   * @param delim  the new delimiter
   */
  void delimitedMode(Buffer delim);

  /**
   * Flip the parser into fixed size mode, where the record size is specified by {@code size} in bytes.
   * <p>
   * This method can be called multiple times with different values of size while data is being parsed.
   *
   * @param size  the new record size
   */
  void fixedSizeMode(int size);

  /**
   * Set the maximum allowed size for a record when using the delimited mode.
   * The delimiter itself does not count for the record size.
   * <p>
   * If a record is longer than specified, an {@link IllegalStateException} will be thrown.
   *
   * @param size the maximum record size
   * @return  a reference to this, so the API can be used fluently
   */
  @Fluent
  RecordParser maxRecordSize(int size);

  /**
   * This method is called to provide the parser with data.
   *
   * @param buffer  a chunk of data
   */
  void handle(Buffer buffer);

  @Override
  RecordParser exceptionHandler(Handler<Throwable> handler);

  @Override
  RecordParser handler(Handler<Buffer> handler);

  @Override
  RecordParser pause();

  @Override
  RecordParser fetch(long amount);

  @Override
  RecordParser resume();

  @Override
  RecordParser endHandler(Handler<Void> endHandler);
}
