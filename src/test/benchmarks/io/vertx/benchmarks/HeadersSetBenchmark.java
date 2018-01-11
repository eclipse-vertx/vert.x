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

import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import io.vertx.core.http.impl.headers.VertxHttpHeaders;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import static io.vertx.benchmarks.HeadersUtils.setBaseHeaders;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@State(Scope.Thread)
public class HeadersSetBenchmark extends BenchmarkBase {

  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  public static void consume(final HttpHeaders headers) {
  }

  private HttpHeaders nettySmallHeaders;
  private VertxHttpHeaders vertxSmallHeaders;

  @Setup
  public void setup() {
    nettySmallHeaders = new DefaultHttpHeaders();
    vertxSmallHeaders = new VertxHttpHeaders();
  }

  @Benchmark
  public void nettySmall() throws Exception {
    nettySmallHeaders.clear();
    setBaseHeaders(nettySmallHeaders);
    consume(nettySmallHeaders);
  }

  @Benchmark
  public void vertxSmall() throws Exception {
    vertxSmallHeaders.clear();
    setBaseHeaders(vertxSmallHeaders);
    consume(vertxSmallHeaders);
  }
}
