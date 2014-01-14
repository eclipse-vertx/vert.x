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

package org.vertx.java.core.json.impl;



import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.vertx.java.core.json.DecodeException;
import org.vertx.java.core.json.EncodeException;

import java.io.IOException;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class Json {

  private final static ObjectMapper mapper = new ObjectMapper();
  private final static ObjectMapper prettyMapper = new ObjectMapper();
  // extensions
  private static final SimpleModule EXTENSION;

  static {
    // Non-standard JSON but we allow C style comments in our JSON
    mapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
    // custom serializers
    EXTENSION = new SimpleModule("JSON Extensions for Vert.x");
    // serialize byte[] as Base64 Strings (same as used to be with Vert.x Json[Object|Array])
    EXTENSION.addSerializer(new JsonSerializer<byte[]>() {
      @Override
      public Class<byte[]> handledType() {
        return byte[].class;
      }

      @Override
      public void serialize(byte[] value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        if (value == null) {
          jgen.writeNull();
        } else {
          jgen.writeString(Base64.encodeBytes(value));
        }
      }
    });

    mapper.registerModule(EXTENSION);
    prettyMapper.registerModule(EXTENSION);
  }

  public static String encode(Object obj) throws EncodeException {
    try {
      return mapper.writeValueAsString(obj);
    }
    catch (Exception e) {
      throw new EncodeException("Failed to encode as JSON: " + e.getMessage());
    }
  }

  public static String encodePrettily(Object obj) throws EncodeException {
    try {
      return prettyMapper.writeValueAsString(obj);
    } catch (Exception e) {
      throw new EncodeException("Failed to encode as JSON: " + e.getMessage());
    }
  }

  @SuppressWarnings("unchecked")
  public static <T> T decodeValue(String str, Class<?> clazz) throws DecodeException {
    try {
      return (T)mapper.readValue(str, clazz);
    }
    catch (Exception e) {
      throw new DecodeException("Failed to decode:" + e.getMessage());
    }
  }

  static {
 	 	prettyMapper.configure(SerializationFeature.INDENT_OUTPUT, true);
  }

}
