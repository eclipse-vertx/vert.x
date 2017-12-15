/*
 * Copyright (c) 2011-2017 The original author or authors
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
package io.vertx.benchmarks;

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

  @Setup
  public void setup() {
    ClassLoader classLoader = getClass().getClassLoader();
    small = loadJson(classLoader.getResource("small.json"));
    large = loadJson(classLoader.getResource("large.json"));
  }

  private JsonObject loadJson(URL url) {
    try {
      return new JsonObject(Json.mapper.readValue(url, Map.class));
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

  private void viaString(JsonObject jsonObject) throws Exception {
    Buffer buffer = Buffer.buffer();
    String strJson = jsonObject.encode();
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

  private void direct(JsonObject jsonObject) throws Exception {
    Buffer buffer = Buffer.buffer();
    Buffer encoded = jsonObject.toBuffer();
    buffer.appendInt(encoded.length());
    buffer.appendBuffer(encoded);
    consume(buffer);
  }
}
