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

import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import io.vertx.core.http.impl.HttpUtils;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

import static io.vertx.benchmarks.HeadersUtils.setBaseHeaders;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@State(Scope.Thread)
@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 400, timeUnit = TimeUnit.MILLISECONDS)
public class HeadersSetBenchmark extends BenchmarkBase {

  @Param({"true", "false"})
  public boolean validate;

  @Param({"true", "false"})
  public boolean asciiNames;

  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  public static void consume(final HttpHeaders headers) {
  }

  private HttpHeaders nettySmallHeaders;
  private HeadersMultiMap vertxSmallHeaders;

  @Setup
  public void setup() {
    nettySmallHeaders = new DefaultHttpHeaders(validate);
    vertxSmallHeaders = new HeadersMultiMap(validate? HttpUtils::validateHeader : null);
  }

  @Benchmark
  public void nettySmall() {
    nettySmallHeaders.clear();
    setBaseHeaders(nettySmallHeaders, asciiNames, true);
    consume(nettySmallHeaders);
  }

  @Benchmark
  public void vertxSmall() {
    vertxSmallHeaders.clear();
    setBaseHeaders(vertxSmallHeaders, asciiNames, true);
    consume(vertxSmallHeaders);
  }
}
