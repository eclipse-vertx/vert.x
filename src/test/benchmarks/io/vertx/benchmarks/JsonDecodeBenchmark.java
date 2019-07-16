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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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
  private String smallString;
  private String wideString;
  private String deepString;
  private ObjectMapper jacksonMapper;
  private JsonFactory jsonFactory;

  @Setup
  public void setup() {
    small = loadJsonAsBuffer("small_bench.json");
    wide = loadJsonAsBuffer("wide_bench.json");
    deep = loadJsonAsBuffer("deep_bench.json");
    smallString = small.toString();
    wideString = wide.toString();
    deepString = deep.toString();
    jacksonMapper = new ObjectMapper();
    jsonFactory = new JsonFactory();
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
  public void viaStringSmall(Blackhole blackhole) {
    viaString(smallString, blackhole);
  }

  @Benchmark
  public void viaStringSmallJackson(Blackhole blackhole) throws Exception {
    viaStringJackson(smallString, blackhole);
  }

  @Benchmark
  public void viaStringSmallBaseline(Blackhole blackhole) throws Exception {
    viaStringBaseline(smallString, blackhole);
  }

  @Benchmark
  public void viaStringWide(Blackhole blackhole) {
    viaString(wideString, blackhole);
  }

  @Benchmark
  public void viaStringWideJackson(Blackhole blackhole) throws Exception {
    viaStringJackson(wideString, blackhole);
  }

  @Benchmark
  public void viaStringDeep(Blackhole blackhole) {
    viaString(deepString, blackhole);
  }

  @Benchmark
  public void viaStringDeepJackson(Blackhole blackhole) throws Exception {
    viaStringJackson(deepString, blackhole);
  }

  private void viaString(String str, Blackhole blackhole) {
    blackhole.consume(new JsonObject(str));
  }

  private void viaStringJackson(String str, Blackhole blackhole) throws Exception {
    Map decoded = jacksonMapper.readValue(str, Map.class);
    blackhole.consume(new JsonObject(decoded));
  }

  private void viaStringBaseline(String str, Blackhole blackhole) throws Exception {
    JsonParser parser = jsonFactory.createParser(str);
    int val = 0;
    JsonToken token;
    while ((token = parser.nextToken()) != null) {
      val += token.hashCode();
    }
    blackhole.consume(val);
  }

  @Benchmark
  public void directSmall(Blackhole blackhole) {
    direct(small, blackhole);
  }

  @Benchmark
  public void directSmallJackson(Blackhole blackhole) {
    directJackson(small, blackhole);
  }

  @Benchmark
  public void directWide(Blackhole blackhole) {
    direct(wide, blackhole);
  }

  @Benchmark
  public void directWideJackson(Blackhole blackhole) {
    directJackson(wide, blackhole);
  }

  @Benchmark
  public void directDeep(Blackhole blackhole) {
    direct(deep, blackhole);
  }

  @Benchmark
  public void directDeepJackson(Blackhole blackhole) {
    directJackson(deep, blackhole);
  }

  private void direct(Buffer buffer, Blackhole blackhole) {
    blackhole.consume(new JsonObject(buffer));
  }

  private void directJackson(Buffer buffer, Blackhole blackhole) {
    blackhole.consume(new JsonObject(buffer));
  }
}
