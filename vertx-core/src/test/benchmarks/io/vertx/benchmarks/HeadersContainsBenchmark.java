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
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import static io.vertx.benchmarks.HeadersUtils.setBaseHeaders;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@State(Scope.Thread)
public class HeadersContainsBenchmark extends BenchmarkBase {

  private HttpHeaders nettySmallHeaders;
  private HeadersMultiMap vertxSmallHeaders;

  @Setup
  public void setup() {
    nettySmallHeaders = new DefaultHttpHeaders();
    vertxSmallHeaders = HeadersMultiMap.httpHeaders();
    setBaseHeaders(nettySmallHeaders, true, true);
    setBaseHeaders(vertxSmallHeaders, true, true);
  }

  @Benchmark
  public boolean nettySmallMatch() throws Exception {
    return nettySmallHeaders.contains(io.vertx.core.http.HttpHeaders.CONTENT_LENGTH.toString());
  }

  @Benchmark
  public boolean nettySmallMiss() throws Exception {
    return nettySmallHeaders.contains(io.vertx.core.http.HttpHeaders.CLOSE.toString());
  }

  @Benchmark
  public boolean nettySmallExactMatch() throws Exception {
    return nettySmallHeaders.contains(io.vertx.core.http.HttpHeaders.CONTENT_LENGTH);
  }

  @Benchmark
  public boolean nettySmallExactMiss() throws Exception {
    return nettySmallHeaders.contains(io.vertx.core.http.HttpHeaders.CLOSE);
  }

  @Benchmark
  public boolean vertxSmallMatch() throws Exception {
    return vertxSmallHeaders.contains(io.vertx.core.http.HttpHeaders.CONTENT_LENGTH.toString());
  }

  @Benchmark
  public boolean vertxSmallMiss() throws Exception {
    return vertxSmallHeaders.contains(io.vertx.core.http.HttpHeaders.CLOSE.toString());
  }

  @Benchmark
  public boolean vertxSmallExactMatch() throws Exception {
    return vertxSmallHeaders.contains(io.vertx.core.http.HttpHeaders.CONTENT_LENGTH);
  }

  @Benchmark
  public boolean vertxSmallExactMiss() throws Exception {
    return vertxSmallHeaders.contains(io.vertx.core.http.HttpHeaders.CLOSE);
  }
}
