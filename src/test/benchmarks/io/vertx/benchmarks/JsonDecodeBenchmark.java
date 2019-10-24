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

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.jackson.DatabindCodec;
import io.vertx.core.json.jackson.JacksonCodec;
import io.vertx.core.spi.json.JsonCodec;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * @author Thomas Segismont
 * @author slinkydeveloper
 */
@State(Scope.Thread)
public class JsonDecodeBenchmark extends BenchmarkBase {

  private Buffer small;
  private Buffer wide;
  private Buffer deep;
  private String smallString;
  private String wideString;
  private String deepString;
  private JsonCodec jacksonCodec;
  private JsonCodec databindCodec;

  @Setup
  public void setup() {
    small = loadJsonAsBuffer("small_bench.json");
    wide = loadJsonAsBuffer("wide_bench.json");
    deep = loadJsonAsBuffer("deep_bench.json");
    smallString = small.toString();
    wideString = wide.toString();
    deepString = deep.toString();
    jacksonCodec = new JacksonCodec();
    databindCodec = new DatabindCodec();
  }

  private Buffer loadJsonAsBuffer(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    try (InputStream file = classLoader.getResourceAsStream(filename)) {
      String str = new BufferedReader(new InputStreamReader(file, StandardCharsets.UTF_8))
        .lines()
        .collect(Collectors.joining());
      Buffer encoded = Buffer.buffer(str);
      return Buffer.buffer().appendBuffer(encoded);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Benchmark
  public void smallStringJackson(Blackhole blackhole) {
    stringJackson(smallString, blackhole);
  }

  @Benchmark
  public void smallStringDatabind(Blackhole blackhole) throws Exception {
    stringDatabind(smallString, blackhole);
  }

  @Benchmark
  public void wideStringJackson(Blackhole blackhole) {
    stringJackson(wideString, blackhole);
  }

  @Benchmark
  public void wideStringDatabind(Blackhole blackhole) throws Exception {
    stringDatabind(wideString, blackhole);
  }

  @Benchmark
  public void deepStringJackson(Blackhole blackhole) {
    stringJackson(deepString, blackhole);
  }

  @Benchmark
  public void deepStringDatabind(Blackhole blackhole) throws Exception {
    stringDatabind(deepString, blackhole);
  }

  private void stringJackson(String str, Blackhole blackhole) {
    blackhole.consume(new JsonObject(str));
  }

  private void stringDatabind(String str, Blackhole blackhole) {
    blackhole.consume(databindCodec.fromString(str, JsonObject.class));
  }

  @Benchmark
  public void smallBufferJackson(Blackhole blackhole) {
    bufferJackson(small, blackhole);
  }

  @Benchmark
  public void smallBufferDatabind(Blackhole blackhole) throws Exception {
    bufferDatabind(small, blackhole);
  }

  @Benchmark
  public void wideBufferJackson(Blackhole blackhole) {
    bufferJackson(wide, blackhole);
  }

  @Benchmark
  public void wideBufferDatabind(Blackhole blackhole) throws Exception {
    bufferDatabind(wide, blackhole);
  }

  @Benchmark
  public void deepBufferJackson(Blackhole blackhole) {
    bufferJackson(deep, blackhole);
  }

  @Benchmark
  public void deepBufferDatabind(Blackhole blackhole) throws Exception {
    bufferDatabind(deep, blackhole);
  }

  private void bufferJackson(Buffer buffer, Blackhole blackhole) {
    blackhole.consume(new JsonObject(buffer));
  }

  private void bufferDatabind(Buffer buffer, Blackhole blackhole) throws Exception {
    blackhole.consume(jacksonCodec.fromBuffer(buffer, JsonObject.class));
  }
}
