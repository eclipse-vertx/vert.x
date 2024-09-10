/*
 * Copyright (c) 2011-2024 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.net.impl;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 10, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Fork(2)
public class HostAndPortBenchmark {

   @Param("192.168.0.1:8080")
   private String host;

   @Setup
   public void setup() {
   }


   @Benchmark
   public int parseIPv4Address() {
      String host =  this.host;
      return HostAndPortImpl.parseIPv4Address(host, 0, host.length());
   }

   @Benchmark
   public int parseHost() {
      String host =  this.host;
      return HostAndPortImpl.parseHost(host, 0, host.length());
   }

   @Benchmark
   public HostAndPortImpl parseAuthority() {
      return HostAndPortImpl.parseAuthority(host, -1);
   }

   @Benchmark
   public boolean isValidAuthority() {
      return HostAndPortImpl.isValidAuthority(host);
   }
}
