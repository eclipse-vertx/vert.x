package io.vertx.core.parsetools;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.parsetools.impl.LengthFieldParserImpl;
import io.vertx.core.streams.ReadStream;

/**
 *  Parser for length field frame protocols
 * * <p/>
 *  The parser handles length fields byte(1), short(2), medium(3), int(4) and long(8) in the {@link Buffer}
 *  with length field offset to skip explicit length while parsing.
 * * <p/>
 *  the default maximum frame length is {@link Integer#MAX_VALUE} and can be changed explicitly to have more control of
 *  the parsed frame length.
 * * <p/>
 *  The {@link #exceptionHandler(Handler)} is called when the frame exceeds the maximum length
 *  or less than zero.
 * * <p/>
 * @author <a href="mailto:emad.albloushi@gmail.com">Emad Alblueshi</a>
 */

@VertxGen
public interface LengthFieldParser extends ReadStream<Buffer>, Handler<Buffer> {


  /**
   * Create a new {@code LengthFieldFrameParser} instance.
   *
   * @param length the length of the field : byte(1), short(2), medium(3), int(4) or long(8)
   */
  static LengthFieldParser newParser(int length) {
    return new LengthFieldParserImpl(length, 0, Integer.MAX_VALUE, null);
  }

  /**
   * Create a new {@code LengthFieldFrameParser} instance.
   *
   * @param length the length of the field : byte(1), short(2), medium(3), int(4) or long(8)
   * @param stream the wrapped of read stream
   */
  static LengthFieldParser newParser(int length, ReadStream<Buffer> stream) {
    return new LengthFieldParserImpl(length, 0, Integer.MAX_VALUE, stream);
  }

  /**
   * Create a new {@code LengthFieldFrameParser} instance.
   *
   * @param length the length of the field : byte(1), short(2), medium(3), int(4) or long(8)
   * @param offset the offset of the field
   * @param stream the wrapped of read stream
   */
  static LengthFieldParser newParser(int length, int offset, ReadStream<Buffer> stream) {
    return new LengthFieldParserImpl(length, offset, Integer.MAX_VALUE, stream);
  }

  /**
   * Create a new {@code LengthFieldFrameParser} instance.
   *
   * @param length the length of the field : byte(1), short(2), medium(3), int(4) or long(8)
   * @param offset the offset of the field
   * @param max the maximum length of the frame
   * @param stream the wrapped of read stream
   */
  static LengthFieldParser newParser(int length, int offset, int max, ReadStream<Buffer> stream) {
    return new LengthFieldParserImpl(length, offset, max, stream);
  }

  /**
   * Create a new {@code LengthFieldFrameParser} instance.
   *
   * @param length the length of the field : byte(1), short(2), medium(3), int(4) or long(8)
   * @param offset the offset of the field
   * @param max the maximum length of the frame
   */
  static LengthFieldParser newParser(int length, int offset, int max) {
    return new LengthFieldParserImpl(length, offset, max, null);
  }

  /**
   * Create a new {@code LengthFieldFrameParser} instance.
   *
   * @param length the length of the field : byte(1), short(2), medium(3), int(4) or long(8)
   * @param offset the offset of the field
   */
  static LengthFieldParser newParser(int length, int offset) {
    return new LengthFieldParserImpl(length, offset, Integer.MAX_VALUE, null);
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
