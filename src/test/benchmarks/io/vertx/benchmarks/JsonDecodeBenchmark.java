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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.util.CharsetUtil;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
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
  private ObjectMapper jacksonMapper;

  @Setup
  public void setup() {
    small = loadJsonAsBuffer("small_bench.json");
    wide = loadJsonAsBuffer("wide_bench.json");
    deep = loadJsonAsBuffer("deep_bench.json");
    jacksonMapper = new ObjectMapper();
  }

  private Buffer loadJsonAsBuffer(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    try (InputStream file = classLoader.getResourceAsStream(filename)) {
      String str = new BufferedReader(new InputStreamReader(file, StandardCharsets.UTF_8))
        .lines()
        .collect(Collectors.joining());
      Buffer encoded = Buffer.buffer(str);
      return Buffer.buffer().appendInt(encoded.length()).appendBuffer(encoded);
    } catch (Exception e) {
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
  @BenchmarkMode(Mode.AverageTime)
  public void viaStringWide(Blackhole blackhole) throws Exception {
    viaString(wide, blackhole);
  }

  @Benchmark
  @BenchmarkMode(Mode.AverageTime)
  public void viaStringJacksonWide(Blackhole blackhole) throws Exception {
    viaStringJackson(wide, blackhole);
  }

  @Benchmark
  @BenchmarkMode(Mode.AverageTime)
  public void viaStringDeep(Blackhole blackhole) throws Exception {
    viaString(deep, blackhole);
  }

  @Benchmark
  @BenchmarkMode(Mode.AverageTime)
  public void viaStringJacksonDeep(Blackhole blackhole) throws Exception {
    viaStringJackson(deep, blackhole);
  }

  private void viaString(Buffer buffer, Blackhole blackhole) throws Exception {
    int pos = 0;
    int length = buffer.getInt(pos);
    pos += 4;
    byte[] encoded = buffer.getBytes(pos, pos + length);
    String str = new String(encoded, CharsetUtil.UTF_8);
    blackhole.consume(new JsonObject(str));
  }

  private void viaStringJackson(Buffer buffer, Blackhole blackhole) throws Exception {
    int pos = 0;
    int length = buffer.getInt(pos);
    pos += 4;
    byte[] encoded = buffer.getBytes(pos, pos + length);
    String str = new String(encoded, CharsetUtil.UTF_8);
    Map decoded = jacksonMapper.readValue(str, Map.class);
    blackhole.consume(new JsonObject(decoded));
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
  @BenchmarkMode(Mode.AverageTime)
  public void directWide(Blackhole blackhole) throws Exception {
    direct(wide, blackhole);
  }

  @Benchmark
  @BenchmarkMode(Mode.AverageTime)
  public void directJacksonWide(Blackhole blackhole) throws Exception {
    directJackson(wide, blackhole);
  }

  @Benchmark
  @BenchmarkMode(Mode.AverageTime)
  public void directDeep(Blackhole blackhole) throws Exception {
    direct(deep, blackhole);
  }

  @Benchmark
  @BenchmarkMode(Mode.AverageTime)
  public void directJacksonDeep(Blackhole blackhole) throws Exception {
    directJackson(deep, blackhole);
  }

  private void direct(Buffer buffer, Blackhole blackhole) throws Exception {
    int pos = 0;
    int length = buffer.getInt(pos);
    pos += 4;
    blackhole.consume(new JsonObject(buffer.slice(pos, pos + length)));
  }

  private void directJackson(Buffer buffer, Blackhole blackhole) throws Exception {
    int pos = 0;
    int length = buffer.getInt(pos);
    pos += 4;
    Buffer slice = buffer.slice(pos, pos + length);
    Map decoded = jacksonMapper.readValue(slice.toString(), Map.class);
    blackhole.consume(new JsonObject(decoded));
  }
}
