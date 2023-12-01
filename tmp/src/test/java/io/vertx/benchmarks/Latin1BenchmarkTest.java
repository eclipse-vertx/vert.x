package io.vertx.benchmarks;

import org.junit.Test;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertArrayEquals;

/**
 Task:
  Convert Latin1 String to byte[]
 Solutions:
  1) manual for-loop to copy chars into byte array
  2) {@link String#getBytes(int, int, byte[], int)}
  3) {@link String#getBytes(Charset)} with {@link StandardCharsets#ISO_8859_1}

 Benchmark them ^!

 @see io.vertx.core.parsetools.impl.RecordParserImpl#latin1StringToBytes(String)
 @author Andrej Fink
*/
@Warmup(iterations = 20, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 2, timeUnit = TimeUnit.SECONDS)
@Threads(1)
@BenchmarkMode(Mode.Throughput) // Mode.AverageTime
@Fork(value = 1, jvmArgs = {
  "-XX:+UseParallelGC",
  "-XX:+UseCompressedOops",
  "-XX:+DoEscapeAnalysis"
})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
public class Latin1BenchmarkTest {

  static final List<String> SAMPLES = new ArrayList<>(1000);
  static {
    SAMPLES.add("Wenn Fliegen hinter Fliegen fliegen, fliegen Fliegen Fliegen nach. 42!$");
    SAMPLES.add("");
    SAMPLES.add(" ");
    SAMPLES.add("\u0000");

    for (int i=1; i<1000; i++){
      byte[] bytes = new byte[i];
      ThreadLocalRandom.current().nextBytes(bytes);
      String s = new String(bytes, StandardCharsets.ISO_8859_1);// random Latin1 String
      SAMPLES.add(s);
    }
  }

  public static byte[] latinBytesV1a (String str){
    byte[] bytes = new byte[str.length()];
    for (int i = 0; i < str.length(); i++){
      char c = str.charAt(i);
      bytes[i] = (byte) (c & 0xFF);
    }
    return bytes;
  }

  public static byte[] latinBytesV1b (String str){
    int len = str.length();
    byte[] bytes = new byte[len];
    for (int i = 0; i < str.length(); i++){
      char c = str.charAt(i);
      bytes[i] = (byte)(c & 0xFF);
    }
    return bytes;
  }

  public static byte[] latinBytesV2 (String str){
    int len = str.length();
    byte[] bytes = new byte[len];
    str.getBytes(0, len, bytes, 0);
    return bytes;
  }

  public static byte[] latinBytesV3 (String str){
    return str.getBytes(StandardCharsets.ISO_8859_1);
  }


  @Test public void same (){
    String s = SAMPLES.get(0);
    assertArrayEquals(latinBytesV1a(s), latinBytesV1b(s));
    assertArrayEquals(latinBytesV1a(s), latinBytesV2(s));
    assertArrayEquals(latinBytesV1a(s), latinBytesV3(s));
  }


  @Benchmark
  public int testBaseline (){
    AtomicInteger antiRemoval = new AtomicInteger();
    SAMPLES.forEach(s->{
      byte[] bytes = new byte[s.length()];
      antiRemoval.addAndGet(bytes.length);
    });
    return antiRemoval.get();
  }

  @Benchmark
  public int testLatinBytesV1a (){
    AtomicInteger antiRemoval = new AtomicInteger();
    SAMPLES.forEach(s->{
      antiRemoval.addAndGet(latinBytesV1a(s).length);
    });
    return antiRemoval.get();
  }

  @Benchmark
  public int testLatinBytesV1b (){
    AtomicInteger antiRemoval = new AtomicInteger();
    SAMPLES.forEach(s->{
      antiRemoval.addAndGet(latinBytesV1b(s).length);
    });
    return antiRemoval.get();
  }

  @Benchmark
  public int testLatinBytesV2 (){
    AtomicInteger antiRemoval = new AtomicInteger();
    SAMPLES.forEach(s->{
      antiRemoval.addAndGet(latinBytesV2(s).length);
    });
    return antiRemoval.get();
  }

  @Benchmark
  public int testLatinBytesV3 (){
    AtomicInteger antiRemoval = new AtomicInteger();
    SAMPLES.forEach(s->{
      antiRemoval.addAndGet(latinBytesV3(s).length);
    });
    return antiRemoval.get();
  }

  public static void main (String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
      .include(Latin1BenchmarkTest.class.getSimpleName())
      .build();

    new Runner(opt).run();
  }
}
