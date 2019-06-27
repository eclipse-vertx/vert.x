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

package io.vertx.benchmarks;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.util.CharsetUtil;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.io.IOException;
import java.net.URL;
import java.util.Map;

/**
 * @author Thomas Segismont
 */
@State(Scope.Thread)
public class JsonEncodeBenchmark extends BenchmarkBase {

  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  public static void consume(final Buffer buffer) {
  }

  private JsonObject small;
  private JsonObject large;
  private ObjectMapper jacksonMapper;

  @Setup
  public void setup() {
    ClassLoader classLoader = getClass().getClassLoader();
    small = loadJson(classLoader.getResource("small.json"));
    large = loadJson(classLoader.getResource("large.json"));
    jacksonMapper = new ObjectMapper();

    com.fasterxml.jackson.databind.module.SimpleModule module = new com.fasterxml.jackson.databind.module.SimpleModule();

    // custom types
    module.addSerializer(JsonObject.class, new com.fasterxml.jackson.databind.JsonSerializer<JsonObject>() {
      @Override
      public void serialize(JsonObject value, JsonGenerator jgen, com.fasterxml.jackson.databind.SerializerProvider serializerProvider) throws IOException {
        jgen.writeObject(value.getMap());
      }
    });
    module.addSerializer(JsonArray.class, new com.fasterxml.jackson.databind.JsonSerializer<JsonArray>() {
        @Override
        public void serialize(JsonArray value, JsonGenerator jgen, com.fasterxml.jackson.databind.SerializerProvider serializerProvider) throws IOException {
          jgen.writeObject(value.getList());
        }
    });

    this.jacksonMapper.registerModule(module);
  }

  private JsonObject loadJson(URL url) {
    try {
      return new JsonObject(new ObjectMapper().readValue(url, Map.class));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Benchmark
  public void viaStringSmall() throws Exception {
    viaString(small);
  }

  @Benchmark
  public void viaStringLarge() throws Exception {
    viaString(large);
  }

  @Benchmark
  public void viaStringJacksonSmall() throws Exception {
    viaStringJackson(small);
  }

  @Benchmark
  public void viaStringJacksonLarge() throws Exception {
    viaStringJackson(large);
  }

  private void viaString(JsonObject jsonObject) throws Exception {
    Buffer buffer = Buffer.buffer();
    String strJson = jsonObject.encode();
    byte[] encoded = strJson.getBytes(CharsetUtil.UTF_8);
    buffer.appendInt(encoded.length);
    Buffer buff = Buffer.buffer(encoded);
    buffer.appendBuffer(buff);
    consume(buffer);
  }

  private void viaStringJackson(JsonObject jsonObject) throws Exception {
    Buffer buffer = Buffer.buffer();
    String strJson = jacksonMapper.writeValueAsString(jsonObject);
    byte[] encoded = strJson.getBytes(CharsetUtil.UTF_8);
    buffer.appendInt(encoded.length);
    Buffer buff = Buffer.buffer(encoded);
    buffer.appendBuffer(buff);
    consume(buffer);
  }

  @Benchmark
  public void directSmall() throws Exception {
    direct(small);
  }

  @Benchmark
  public void directLarge() throws Exception {
    direct(large);
  }

  @Benchmark
  public void directJacksonSmall() throws Exception {
    directJackson(small);
  }

  @Benchmark
  public void directJacksonLarge() throws Exception {
    directJackson(large);
  }

  private void direct(JsonObject jsonObject) throws Exception {
    Buffer buffer = Buffer.buffer();
    Buffer encoded = jsonObject.toBuffer();
    buffer.appendInt(encoded.length());
    buffer.appendBuffer(encoded);
    consume(buffer);
  }

  private void directJackson(JsonObject jsonObject) throws Exception {
    Buffer buffer = Buffer.buffer();
    Buffer encoded = Buffer.buffer(jacksonMapper.writeValueAsBytes(jsonObject));
    buffer.appendInt(encoded.length());
    buffer.appendBuffer(encoded);
    consume(buffer);
  }
}
