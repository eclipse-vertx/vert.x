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
import io.vertx.core.json.JsonObject;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Thomas Segismont
 */
@State(Scope.Thread)
public class JsonDecodeBenchmark extends BenchmarkBase {

  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  public static void consume(final JsonObject jsonObject) {
  }

  private Buffer small;
  private Buffer large;
  private ObjectMapper jacksonMapper;

  @Setup
  public void setup() {
    ClassLoader classLoader = getClass().getClassLoader();
    small = loadJsonAsBuffer(classLoader.getResource("small.json"));
    large = loadJsonAsBuffer(classLoader.getResource("large.json"));
    jacksonMapper = new ObjectMapper();
  }

  private Buffer loadJsonAsBuffer(URL url) {
    try {
      Buffer encoded = Buffer.buffer(String.join("", Files.readAllLines(Paths.get(url.toURI()))));
      return Buffer.buffer().appendInt(encoded.length()).appendBuffer(encoded);
    } catch (Exception e) {
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

  private void viaString(Buffer buffer) throws Exception {
    int pos = 0;
    int length = buffer.getInt(pos);
    pos += 4;
    byte[] encoded = buffer.getBytes(pos, pos + length);
    String str = new String(encoded, CharsetUtil.UTF_8);
    consume(new JsonObject(str));
  }

  private void viaStringJackson(Buffer buffer) throws Exception {
    int pos = 0;
    int length = buffer.getInt(pos);
    pos += 4;
    byte[] encoded = buffer.getBytes(pos, pos + length);
    String str = new String(encoded, CharsetUtil.UTF_8);
    Map decoded = jacksonMapper.readValue(str, Map.class);
    consume(new JsonObject(decoded));
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

  private void direct(Buffer buffer) throws Exception {
    int pos = 0;
    int length = buffer.getInt(pos);
    pos += 4;
    consume(new JsonObject(buffer.slice(pos, pos + length)));
  }

  private void directJackson(Buffer buffer) throws Exception {
    int pos = 0;
    int length = buffer.getInt(pos);
    pos += 4;
    Buffer slice = buffer.slice(pos, pos + length);
    Map decoded = jacksonMapper.readValue(slice.toString(), Map.class);
    consume(new JsonObject(decoded));
  }
}
