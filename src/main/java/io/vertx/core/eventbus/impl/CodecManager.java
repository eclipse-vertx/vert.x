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

package io.vertx.core.eventbus.impl;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.eventbus.impl.codecs.*;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class CodecManager {

  // The standard message codecs
  public static final MessageCodec<String, String> PING_MESSAGE_CODEC = new PingMessageCodec();
  public static final MessageCodec<String, String> NULL_MESSAGE_CODEC = new NullMessageCodec();
  public static final MessageCodec<String, String> STRING_MESSAGE_CODEC = new StringMessageCodec();
  public static final MessageCodec<Buffer, Buffer> BUFFER_MESSAGE_CODEC = new BufferMessageCodec();
  public static final MessageCodec<JsonObject, JsonObject> JSON_OBJECT_MESSAGE_CODEC = new JsonObjectMessageCodec();
  public static final MessageCodec<JsonArray, JsonArray> JSON_ARRAY_MESSAGE_CODEC = new JsonArrayMessageCodec();
  public static final MessageCodec<byte[], byte[]> BYTE_ARRAY_MESSAGE_CODEC = new ByteArrayMessageCodec();
  public static final MessageCodec<Integer, Integer> INT_MESSAGE_CODEC = new IntMessageCodec();
  public static final MessageCodec<Long, Long> LONG_MESSAGE_CODEC = new LongMessageCodec();
  public static final MessageCodec<Float, Float> FLOAT_MESSAGE_CODEC = new FloatMessageCodec();
  public static final MessageCodec<Double, Double> DOUBLE_MESSAGE_CODEC = new DoubleMessageCodec();
  public static final MessageCodec<Boolean, Boolean> BOOLEAN_MESSAGE_CODEC = new BooleanMessageCodec();
  public static final MessageCodec<Short, Short> SHORT_MESSAGE_CODEC = new ShortMessageCodec();
  public static final MessageCodec<Character, Character> CHAR_MESSAGE_CODEC = new CharMessageCodec();
  public static final MessageCodec<Byte, Byte> BYTE_MESSAGE_CODEC = new ByteMessageCodec();
  public static final MessageCodec<ReplyException, ReplyException> REPLY_EXCEPTION_MESSAGE_CODEC = new ReplyExceptionMessageCodec();

  private final MessageCodec[] systemCodecs;
  private final ConcurrentMap<String, MessageCodec> codecsByName = new ConcurrentHashMap<>();
  private final ConcurrentMap<Class, MessageCodec> codecsByClass = new ConcurrentHashMap<>();

  public CodecManager() {
    MessageCodec[] codecs = {NULL_MESSAGE_CODEC, PING_MESSAGE_CODEC, STRING_MESSAGE_CODEC, BUFFER_MESSAGE_CODEC, JSON_OBJECT_MESSAGE_CODEC, JSON_ARRAY_MESSAGE_CODEC,
      BYTE_ARRAY_MESSAGE_CODEC, INT_MESSAGE_CODEC, LONG_MESSAGE_CODEC, FLOAT_MESSAGE_CODEC, DOUBLE_MESSAGE_CODEC,
      BOOLEAN_MESSAGE_CODEC, SHORT_MESSAGE_CODEC, CHAR_MESSAGE_CODEC, BYTE_MESSAGE_CODEC, REPLY_EXCEPTION_MESSAGE_CODEC};
    Arrays.sort(codecs, Comparator.comparingInt(MessageCodec::systemCodecID));
    this.systemCodecs = codecs;
  }

  public MessageCodec lookupCodec(Object body, String codecName) {
    if (codecName != null) {
      MessageCodec codec;
      codec = codecsByName.get(codecName);
      if (codec == null) {
        throw new IllegalArgumentException("No message codec for name: " + codecName);
      }
      return codec;
    } else {
      if (body == null) {
        return NULL_MESSAGE_CODEC;
      } else {
        Class<?> clazz = body.getClass();
        if (clazz == String.class) {
          return STRING_MESSAGE_CODEC;
        } else if (Buffer.class.isAssignableFrom(clazz)) {
          return BUFFER_MESSAGE_CODEC;
        } else if (clazz == JsonObject.class) {
          return JSON_OBJECT_MESSAGE_CODEC;
        } else if (clazz == JsonArray.class) {
          return JSON_ARRAY_MESSAGE_CODEC;
        } else if (clazz == byte[].class) {
          return BYTE_ARRAY_MESSAGE_CODEC;
        } else if (clazz == Integer.class) {
          return INT_MESSAGE_CODEC;
        } else if (clazz == Long.class) {
          return LONG_MESSAGE_CODEC;
        } else if (clazz == Float.class) {
          return FLOAT_MESSAGE_CODEC;
        } else if (clazz == Double.class) {
          return DOUBLE_MESSAGE_CODEC;
        } else if (clazz == Boolean.class) {
          return BOOLEAN_MESSAGE_CODEC;
        } else if (clazz == Short.class) {
          return SHORT_MESSAGE_CODEC;
        } else if (clazz == Character.class) {
          return CHAR_MESSAGE_CODEC;
        } else if (clazz == Byte.class) {
          return BYTE_MESSAGE_CODEC;
        } else if (ReplyException.class.isAssignableFrom(clazz)) {
          return codecsByClass.getOrDefault(clazz, REPLY_EXCEPTION_MESSAGE_CODEC);
        } else {
          MessageCodec codec = resolveCodec(clazz);
          if (codec == null) {
            throw new IllegalArgumentException("No message codec for type: " + clazz);
          }
          return codec;
        }
      }
    }
  }

  private MessageCodec resolveCodec(Class<?> clazz) {
    MessageCodec codec;
    codec = codecsByClass.get(clazz);
    if (codec == null) {
      Class<?> parent = clazz.getSuperclass();
      if (parent != Object.class && parent != null) {
        codec = resolveCodec(parent);
      }
    }
    return codec;
  }

  public MessageCodec getCodec(String codecName) {
    return codecsByName.get(codecName);
  }

  public <T> void registerCodec(Class<T> clazz, MessageCodec<T, ?> codec) {
    Objects.requireNonNull(codec, "codec");
    Objects.requireNonNull(codec.name(), "code.name()");
    checkSystemCodec(codec);
    if (codecsByName.containsKey(codec.name())) {
      throw new IllegalStateException("Already a codec registered with name " + codec.name());
    }
    if (clazz != null) {
      if (clazz == Object.class) {
        throw new IllegalArgumentException("Registering a default codec for class java.lang.Object is not supported");
      }
      if (codecsByClass.putIfAbsent(clazz, codec) != null) {
        throw new IllegalStateException("Already a default codec registered for class " + clazz);
      }
    }
    codecsByName.put(codec.name(), codec);
  }

  public void unregisterCodec(String name) {
    Objects.requireNonNull(name);
    codecsByName.remove(name);
  }

  public void unregisterCodec(Class<?> clazz) {
    Objects.requireNonNull(clazz);
    MessageCodec<?, ?> codec = codecsByClass.remove(clazz);
    if (codec != null) {
      codecsByName.remove(codec.name());
    }
  }

  public MessageCodec[] systemCodecs() {
    return systemCodecs;
  }

  private void checkSystemCodec(MessageCodec codec) {
    if (codec.systemCodecID() != -1) {
      throw new IllegalArgumentException("Can't register a system codec");
    }
  }
}
