/*
 * Copyright (c) 2011-2014 The original author or authors
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

package io.vertx.core.eventbus;

import io.vertx.core.buffer.Buffer;

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
}
