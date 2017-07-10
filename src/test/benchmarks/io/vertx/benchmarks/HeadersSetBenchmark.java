/*
 * Copyright (c) 2011-2013 The original author or authors
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
