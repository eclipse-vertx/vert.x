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

import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.impl.CodecManager;
import io.vertx.core.eventbus.impl.codecs.IterableMessageCodec;
import io.vertx.core.eventbus.impl.codecs.JsonArrayMessageCodec;
import io.vertx.core.json.JsonArray;
import org.openjdk.jmh.annotations.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @author Josef Pfleger
 */
@State(Scope.Thread)
public class IterableMessageCodecBenchmark extends BenchmarkBase {

  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  public static void consume(final Iterable<String> data) {
  }

  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  public static void consume(final JsonArray data) {
  }

  private JsonArrayMessageCodec jsonArrayCodec;
  private IterableMessageCodec iterableCodec;

  private List<String> iterableData;
  private JsonArray jsonData;

  @Setup
  public void setup() {
    jsonArrayCodec = new JsonArrayMessageCodec();
    iterableCodec = new IterableMessageCodec(new CodecManager());

    jsonData = new JsonArray();
    iterableData = new ArrayList<>();
    for(int i = 0; i < 10; i++) {
      String uuid = UUID.randomUUID().toString();
      iterableData.add(uuid);
      jsonData.add(uuid);
    }
  }

  @Benchmark
  public void jsonArrayCodec() {
    Buffer buffer = Buffer.buffer();
    jsonArrayCodec.encodeToWire(buffer, jsonData);
    consume(jsonArrayCodec.decodeFromWire(0, buffer));
  }

  @Benchmark
  public void jsonArrayCodecWithConversion() {
    JsonArray o = new JsonArray();
    iterableData.forEach(o::add);
    Buffer buffer = Buffer.buffer();
    jsonArrayCodec.encodeToWire(buffer, o);
    consume(jsonArrayCodec.decodeFromWire(0, buffer));
  }

  @Benchmark
  public void iterableCodec() {
    Buffer buffer = Buffer.buffer();
    iterableCodec.encodeToWire(buffer, iterableData);
    consume(iterableCodec.decodeFromWire(0, buffer));
  }
}
