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

package io.vertx.core.parsetools;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.parsetools.impl.LengthFieldParserImpl;
import io.vertx.core.streams.ReadStream;

/**
 * A parser that splits the received {@link io.vertx.core.buffer.Buffer} dynamically by the value of the length field in
 * the frame.
 *  <p/>
 *  It is convenient when you parse a binary data which has an header field byte(1), short(2), medium(3),
 *  int(4) or long(8) that represents the length of the frame.
 *  <p/>
 *  The parser supports length field offset to specify explicit position while parsing.
 *  <p/>
 *  The user can configure the parser to skip all fields that precedes the frame body or to receive the whole frame
 *  conveniently.
 *  <p/>
 *  The default maximum frame length is {@link Integer#MAX_VALUE} if not specified and can be changed explicitly.
 *  <p/>
 *  The {@link #exceptionHandler(Handler)} is called when the frame body exceeds the maximum length
 *  or the length is invalid "less or equal to zero".
 *  <p/>
 * @author <a href="mailto:emad.albloushi@gmail.com">Emad Alblueshi</a>
 */

@VertxGen
public interface LengthFieldParser extends ReadStream<Buffer>, Handler<Buffer> {
  /**
   * Create a new {@code LengthFieldParser} instance.
   *
   * @param length the length of the field : byte(1), short(2), medium(3), int(4) or long(8)
   * @param offset the offset of the field
   * @param skip if set to {@code true}, only the frame body is emitted by the parse, if {@code false}, the whole frame is emitted
   */
  static LengthFieldParser newParser(int length, int offset, boolean skip) {
    return new LengthFieldParserImpl(length, offset, skip, Integer.MAX_VALUE, null);
  }
  /**
   * Create a new {@code LengthFieldParser} instance.
   *
   * @param length the length of the field : byte(1), short(2), medium(3), int(4) or long(8)
   * @param offset the offset of the field
   * @param skip if set to {@code true}, only the frame body is emitted by the parse, if {@code false}, the whole frame is emitted
   * @param max the maximum length of the frame body
   */
  static LengthFieldParser newParser(int length, int offset, boolean skip, int max) {
    return new LengthFieldParserImpl(length, offset, skip, max, null);
  }
  /**
   * Create a new {@code LengthFieldParser} instance.
   *
   * @param length the length of the field : byte(1), short(2), medium(3), int(4) or long(8)
   * @param offset the offset of the field
   * @param skip if set to {@code true}, only the frame body is emitted by the parse, if {@code false}, the whole frame is emitted
   * @param stream the wrapped of read stream
   */
  static LengthFieldParser newParser(int length, int offset, boolean skip, ReadStream<Buffer> stream) {
    return new LengthFieldParserImpl(length, offset, skip, Integer.MAX_VALUE, stream);
  }
  /**
   * Create a new {@code LengthFieldParser} instance.
   *
   * @param length the length of the field : byte(1), short(2), medium(3), int(4) or long(8)
   * @param offset the offset of the field
   * @param skip if set to {@code true}, only the frame body is emitted by the parse, if {@code false}, the whole frame is emitted
   * @param max the maximum length of the frame body
   * @param stream the wrapped of read stream
   */
  static LengthFieldParser newParser(int length, int offset, boolean skip, int max, ReadStream<Buffer> stream) {
    return new LengthFieldParserImpl(length, offset, skip, max, stream);
  }

  @Override
  LengthFieldParser pause();

  @Override
  LengthFieldParser resume();

  @Override
  LengthFieldParser fetch(long amount);

  @Fluent
  LengthFieldParser endHandler(Handler<Void> endHandler);

  @Fluent
  LengthFieldParser handler(Handler<Buffer> handler);

  @Fluent
  LengthFieldParser exceptionHandler(Handler<Throwable> handler);

}
