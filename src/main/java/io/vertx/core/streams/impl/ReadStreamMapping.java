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

package io.vertx.core.streams.impl;

import io.vertx.core.Handler;
import io.vertx.core.streams.ReadStream;

import java.util.function.Function;

/**
 * read stream map transformation.
 *
 * @author <a href="https://wang007.github.io">wang007</a>
 */
public class ReadStreamMapping<T, R> implements ReadStream<R> {

  private final ReadStream<T> source;

  private final Function<T, R> mapping;

  public ReadStreamMapping(ReadStream<T> source, Function<T, R> mapping) {
    this.source = source;
    this.mapping = mapping;
  }

  @Override
  public ReadStream<R> exceptionHandler(Handler<Throwable> handler) {
    source.exceptionHandler(handler);
    return this;
  }

  @Override
  public ReadStream<R> handler(Handler<R> handler) {
    source.handler(new MappingHandler(handler));
    return this;
  }

  @Override
  public ReadStream<R> pause() {
    source.pause();
    return this;
  }

  @Override
  public ReadStream<R> resume() {
    source.resume();
    return this;
  }

  @Override
  public ReadStream<R> fetch(long amount) {
    source.fetch(amount);
    return this;
  }

  @Override
  public ReadStream<R> endHandler(Handler<Void> endHandler) {
    source.endHandler(endHandler);
    return this;
  }


  class MappingHandler implements Handler<T> {

    public final Handler<R> delete;

    public MappingHandler(Handler<R> delete) {
      this.delete = delete;
    }

    @Override
    public void handle(T event) {
      delete.handle(mapping.apply(event));
    }
  }

}
