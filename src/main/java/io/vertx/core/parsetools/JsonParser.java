/*
 * Copyright (c) 2011-2017 The original author or authors
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

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.parsetools.impl.JsonParserImpl;
import io.vertx.core.streams.ReadStream;

/**
 * A parser class which allows to incrementally parse json elements and emit json parse events instead of parsing a json
 * element fully. This parser is convenient for parsing large json structures.
 * <p/>
 * The parser can also parse entire object or array when it is convenient, for instance a very large array
 * of small objects can be parsed efficiently by handling array <i>start</i>/<i>end</i> and <i>object</i>
 * events.
 * <p/>
 * Whenever the parser fails to parse or process the stream, the {@link #exceptionHandler(Handler)} is called with
 * the cause of the failure and the current handling stops. After such event, the parser should not handle data
 * anymore.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface JsonParser extends Handler<Buffer>, ReadStream<JsonEvent> {

  /**
   * Create a new {@code JsonParser} instance.
   */
  static JsonParser newParser() {
    return new JsonParserImpl(null);
  }

  /**
   * Create a new {@code JsonParser} instance.
   */
  static JsonParser newParser(ReadStream<Buffer> stream) {
    return new JsonParserImpl(stream);
  }

  /**
   * Handle a {@code Buffer}, pretty much like calling {@link #handle(Object)}.
   *
   * @return  a reference to this, so the API can be used fluently
   */
  @Fluent
  JsonParser write(Buffer buffer);

  /**
   * End the stream, this must be called after all the json stream has been processed.
   */
  void end();

  /**
   * Flip the parser to emit a stream of events for each new json object.
   *
   * @return  a reference to this, so the API can be used fluently
   */
  @Fluent
  JsonParser objectEventMode();

  /**
   * Flip the parser to emit a single value event for each new json object.
   * </p>
   * Json object currently streamed won't be affected.
   *
   * @return  a reference to this, so the API can be used fluently
   */
  @Fluent
  JsonParser objectValueMode();

  /**
   * Flip the parser to emit a stream of events for each new json array.
   *
   * @return  a reference to this, so the API can be used fluently
   */
  @Fluent
  JsonParser arrayEventMode();

  /**
   * Flip the parser to emit a single value event for each new json array.
   * </p>
   * Json array currently streamed won't be affected.
   *
   * @return  a reference to this, so the API can be used fluently
   */
  @Fluent
  JsonParser arrayValueMode();

  @Override
  JsonParser pause();

  @Override
  JsonParser resume();

  @Fluent
  JsonParser endHandler(Handler<Void> endHandler);

  @Fluent
  JsonParser handler(Handler<JsonEvent> handler);

  @Fluent
  JsonParser exceptionHandler(Handler<Throwable> handler);

}
