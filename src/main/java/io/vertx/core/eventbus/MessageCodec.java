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

package io.vertx.core.eventbus;

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.impl.codecs.LocalShareableCodec;
import io.vertx.core.shareddata.Shareable;

import java.util.function.Function;

/**
 * A message codec allows a custom message type to be marshalled across the event bus.
 * <p>
 * Usually the event bus only allows a certain set of message types to be sent across the event bus, including primitive types,
 * boxed primitive types, {@code byte[]}, {@link io.vertx.core.json.JsonObject}, {@link io.vertx.core.json.JsonArray},
 * {@link io.vertx.core.buffer.Buffer}.
 * <p>
 * By specifying a message codec you can pass any other type across the event bus, e.g. POJOs.
 * <p>
 * With a message codec the type sent <i>does not</i> have to be the same as the type received, e.g. you could send
 * a {@code Buffer} and have it be received as a {@code JsonObject}.
 * <p>
 *
 * <b>Instances of this class must be thread-safe as they may be used concurrently by different threads.</b>
 *
 * @param <S> the type of the message being sent
 * @param <R> the type of the message being received.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface MessageCodec<S, R> {

  /**
   * Called by Vert.x when marshalling a message to the wire.
   *
   * @param buffer  the message should be written into this buffer
   * @param s  the message that is being sent
   */
  void encodeToWire(Buffer buffer, S s);

  /**
   * Called by Vert.x when a message is decoded from the wire.
   *
   * @param pos  the position in the buffer where the message should be read from.
   * @param buffer  the buffer to read the message from
   * @return  the read message
   */
  R decodeFromWire(int pos, Buffer buffer);

  /**
   * If a message is sent <i>locally</i> across the event bus, this method is called to transform the message from
   * the sent type S to the received type R
   *
   * @param s  the sent message
   * @return  the transformed message
   */
  R transform(S s);

  /**
   * The codec name. Each codec must have a unique name. This is used to identify a codec when sending a message and
   * for unregistering codecs.
   *
   * @return the name
   */
  String name();

  /**
   * Used to identify system codecs. Should always return -1 for a user codec.
   *
   * @return -1 for a user codec.
   */
  byte systemCodecID();

  /**
   * Create a new message codec for sending {@link Shareable} objects to local consumers.
   * <p>
   * When sending messages to local consumers, you might not want to serialize the message.
   * Using this method, you can create a new codec that is able to send {@link Shareable} objects to local consumers
   * without performing any serialization: the method {@link Shareable#copy()} will be invoked instead.
   * <p>
   * Note: when sending to non-local consumers this codec will fail. Make sure you specify another codec for remote messages.
   *
   * @param clazz the shareable class
   * @return a reference to this, so the API can be used fluently
   */
  @GenIgnore
  @SuppressWarnings("unchecked")
  static <T extends Shareable> MessageCodec<T, T> localCodec(Class<? extends T> clazz) {
    return new LocalShareableCodec<T>("local." + clazz.getSimpleName(), v -> (T) v.copy());
  }

  /**
   * Like {@link #localCodec(Class)}, but specifying the copy function manually.
   */
  @GenIgnore
  static <T> MessageCodec<T, T> localCodec(Class<T> clazz, Function<T, T> copy) {
    return new LocalShareableCodec<T>("local." + clazz.getSimpleName(), copy);
  }

}
