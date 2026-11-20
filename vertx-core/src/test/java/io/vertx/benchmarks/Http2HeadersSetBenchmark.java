/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.benchmarks;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpVersion;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/**
 * Adds a few headers to an HTTP/2 {@link MultiMap}.
 */
@State(Scope.Thread)
@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
public class Http2HeadersSetBenchmark extends BenchmarkBase {

  @Param({"ascii", "string", "mixedCase"})
  public String names;

  private MultiMap headers;
  private CharSequence[] headerNames;
  private CharSequence[] headerValues;

  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  public static void consume(final MultiMap headers) {
  }

  @Setup
  public void setup() {
    headers = HttpHeaders.headers(HttpVersion.HTTP_2);
    headerNames = new CharSequence[] { HttpHeaders.CONTENT_TYPE, HttpHeaders.CONTENT_LENGTH, HttpHeaders.SERVER, HttpHeaders.DATE };
    headerValues = new CharSequence[] { HeadersUtils.TEXT_PLAIN_HEADER, HeadersUtils.CONTENT_LENGTH_HEADER, HeadersUtils.VERTX_HEADER, HeadersUtils.DATE_HEADER };
    for (int i = 0; i < headerNames.length; i++) {
      switch (names) {
        case "string":
          headerNames[i] = headerNames[i].toString();
          break;
        case "mixedCase":
          String s = headerNames[i].toString();
          headerNames[i] = Character.toUpperCase(s.charAt(0)) + s.substring(1);
          break;
      }
    }
  }

  @Benchmark
  public void add() {
    headers.clear();
    for (int i = 0; i < headerNames.length; i++) {
      headers.add(headerNames[i], headerValues[i]);
    }
    consume(headers);
  }
}
