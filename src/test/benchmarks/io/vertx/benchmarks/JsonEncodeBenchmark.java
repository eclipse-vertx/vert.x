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
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;
import java.net.URL;
import java.util.Map;

/**
 * @author Thomas Segismont
 */
@State(Scope.Thread)
public class JsonEncodeBenchmark extends BenchmarkBase {
  
  private JsonObject small;
  private JsonObject wide;
  private JsonObject deep;
  private ObjectMapper jacksonMapper;

  @Setup
  public void setup() {
    ClassLoader classLoader = getClass().getClassLoader();
    small = loadJson(classLoader.getResource("small_bench.json"));
    wide = loadJson(classLoader.getResource("wide_bench.json"));
    deep = loadJson(classLoader.getResource("deep_bench.json"));
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
  public void viaStringSmall(Blackhole blackhole) throws Exception {
    viaString(small, blackhole);
  }

  @Benchmark
  public void viaStringJacksonSmall(Blackhole blackhole) throws Exception {
    viaStringJackson(small, blackhole);
  }

  @Benchmark
  public void viaStringWide(Blackhole blackhole) throws Exception {
    viaString(wide, blackhole);
  }

  @Benchmark
  public void viaStringJacksonWide(Blackhole blackhole) throws Exception {
    viaStringJackson(wide, blackhole);
  }

  @Benchmark
  public void viaStringDeep(Blackhole blackhole) throws Exception {
    viaString(deep, blackhole);
  }

  @Benchmark
  public void viaStringJacksonDeep(Blackhole blackhole) throws Exception {
    viaStringJackson(deep, blackhole);
  }

  private void viaString(JsonObject jsonObject, Blackhole blackhole) throws Exception {
    Buffer buffer = Buffer.buffer();
    String strJson = jsonObject.encode();
    byte[] encoded = strJson.getBytes(CharsetUtil.UTF_8);
    buffer.appendInt(encoded.length);
    Buffer buff = Buffer.buffer(encoded);
    buffer.appendBuffer(buff);
    blackhole.consume(buffer);
  }

  private void viaStringJackson(JsonObject jsonObject, Blackhole blackhole) throws Exception {
    Buffer buffer = Buffer.buffer();
    String strJson = jacksonMapper.writeValueAsString(jsonObject);
    byte[] encoded = strJson.getBytes(CharsetUtil.UTF_8);
    buffer.appendInt(encoded.length);
    Buffer buff = Buffer.buffer(encoded);
    buffer.appendBuffer(buff);
    blackhole.consume(buffer);
  }

  @Benchmark
  public void directSmall(Blackhole blackhole) throws Exception {
    direct(small, blackhole);
  }

  @Benchmark
  public void directJacksonSmall(Blackhole blackhole) throws Exception {
    directJackson(small, blackhole);
  }

  @Benchmark
  public void directDeep(Blackhole blackhole) throws Exception {
    direct(deep, blackhole);
  }

  @Benchmark
  public void directJacksonDeep(Blackhole blackhole) throws Exception {
    directJackson(deep, blackhole);
  }

  @Benchmark
  public void directWide(Blackhole blackhole) throws Exception {
    direct(wide, blackhole);
  }

  @Benchmark
  public void directJacksonWide(Blackhole blackhole) throws Exception {
    directJackson(wide, blackhole);
  }

  private void direct(JsonObject jsonObject, Blackhole blackhole) throws Exception {
    Buffer buffer = Buffer.buffer();
    Buffer encoded = jsonObject.toBuffer();
    buffer.appendInt(encoded.length());
    buffer.appendBuffer(encoded);
    blackhole.consume(buffer);
  }

  private void directJackson(JsonObject jsonObject, Blackhole blackhole) throws Exception {
    Buffer buffer = Buffer.buffer();
    Buffer encoded = Buffer.buffer(jacksonMapper.writeValueAsBytes(jsonObject));
    buffer.appendInt(encoded.length());
    buffer.appendBuffer(encoded);
    blackhole.consume(buffer);
  }
}
