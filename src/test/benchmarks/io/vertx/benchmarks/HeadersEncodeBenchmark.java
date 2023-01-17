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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.EmptyHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import static io.vertx.benchmarks.HeadersUtils.setBaseHeaders;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@State(Scope.Thread)
public class HeadersEncodeBenchmark extends BenchmarkBase {

  @Param({"true", "false"})
  public boolean asciiNames;

  @Param({"true", "false"})
  public boolean asciiValues;

  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  public static void consume(final ByteBuf buf) {
  }

  static class PublicEncoder extends HttpResponseEncoder {

    // Make it public
    @Override
    public void encodeHeaders(HttpHeaders headers, ByteBuf buf) {
      super.encodeHeaders(headers, buf);
    }
  }

  private PublicEncoder encoder;
  private ByteBuf byteBuf;
  private HttpHeaders emptyHeaders;
  private HttpHeaders nettySmallHeaders;
  private HttpHeaders vertxSmallHeaders;

  @Setup
  public void setup() {
    byteBuf = Unpooled.buffer(1024);
    encoder = new PublicEncoder();
    emptyHeaders = EmptyHttpHeaders.INSTANCE;
    nettySmallHeaders = new DefaultHttpHeaders();
    vertxSmallHeaders = HeadersMultiMap.httpHeaders();
    setBaseHeaders(nettySmallHeaders, asciiNames, asciiValues);
    setBaseHeaders(vertxSmallHeaders, asciiNames, asciiValues);
  }

  @Benchmark
  public void baseline() throws Exception {
    byteBuf.resetWriterIndex();
    encoder.encodeHeaders(emptyHeaders, byteBuf);
    consume(byteBuf);
  }

  @Benchmark
  public void nettySmall() throws Exception {
    byteBuf.resetWriterIndex();
    encoder.encodeHeaders(nettySmallHeaders, byteBuf);
    consume(byteBuf);
  }

  @Benchmark
  public void vertxSmall() throws Exception {
    byteBuf.resetWriterIndex();
    encoder.encodeHeaders(vertxSmallHeaders, byteBuf);
    consume(byteBuf);
  }
}
