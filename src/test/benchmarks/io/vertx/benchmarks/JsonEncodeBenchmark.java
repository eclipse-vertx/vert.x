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

package io.vertx.benchmarks;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.jackson.DatabindCodec;
import io.vertx.core.json.jackson.JacksonCodec;
import io.vertx.core.spi.json.JsonCodec;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;
import java.net.URL;
import java.util.Map;

/**
 * @author Thomas Segismont
 * @author slinkydeveloper
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
public class JsonEncodeBenchmark extends BenchmarkBase {

  private JsonObject small;
  private JsonObject wide;
  private JsonObject deep;
  private JsonCodec jacksonCodec;
  private JsonCodec databindCodec;

  @Setup
  public void setup() {
    ClassLoader classLoader = getClass().getClassLoader();
    small = loadJson(classLoader.getResource("small_bench.json"));
    wide = loadJson(classLoader.getResource("wide_bench.json"));
    deep = loadJson(classLoader.getResource("deep_bench.json"));
    jacksonCodec = new JacksonCodec();
    databindCodec = new DatabindCodec();
  }

  private JsonObject loadJson(URL url) {
    try {
      return new JsonObject(new ObjectMapper().readValue(url, Map.class));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Benchmark
  public void smallStringJackson(Blackhole blackhole) throws Exception {
    stringJackson(small, blackhole);
  }

  @Benchmark
  public void smallStringDatabind(Blackhole blackhole) throws Exception {
    stringDatabind(small, blackhole);
  }

  @Benchmark
  public void wideStringJackson(Blackhole blackhole) throws Exception {
    stringJackson(wide, blackhole);
  }

  @Benchmark
  public void wideStringDatabind(Blackhole blackhole) throws Exception {
    stringDatabind(wide, blackhole);
  }

  @Benchmark
  public void deepStringJackson(Blackhole blackhole) throws Exception {
    stringJackson(deep, blackhole);
  }

  @Benchmark
  public void deepStringDatabind(Blackhole blackhole) throws Exception {
    stringDatabind(deep, blackhole);
  }

  private void stringJackson(JsonObject jsonObject, Blackhole blackhole) throws Exception {
    blackhole.consume(jsonObject.encode());
  }

  private void stringDatabind(JsonObject jsonObject, Blackhole blackhole) throws Exception {
    blackhole.consume(databindCodec.toString(jsonObject));
  }

  @Benchmark
  public void smallBufferJackson(Blackhole blackhole) throws Exception {
    bufferJackson(small, blackhole);
  }

  @Benchmark
  public void smallBufferDatabind(Blackhole blackhole) throws Exception {
    bufferDatabind(small, blackhole);
  }

  @Benchmark
  public void deepBufferJackson(Blackhole blackhole) throws Exception {
    bufferJackson(deep, blackhole);
  }

  @Benchmark
  public void deepBufferDatabind(Blackhole blackhole) throws Exception {
    bufferDatabind(deep, blackhole);
  }

  @Benchmark
  public void wideBufferJackson(Blackhole blackhole) throws Exception {
    bufferJackson(wide, blackhole);
  }

  @Benchmark
  public void wideBufferDatabind(Blackhole blackhole) throws Exception {
    bufferDatabind(wide, blackhole);
  }

  private void bufferJackson(JsonObject jsonObject, Blackhole blackhole) throws Exception {
    blackhole.consume(jsonObject.toBuffer());
  }

  private void bufferDatabind(JsonObject jsonObject, Blackhole blackhole) throws Exception {
    blackhole.consume(jacksonCodec.toBuffer(jsonObject));
  }
}
