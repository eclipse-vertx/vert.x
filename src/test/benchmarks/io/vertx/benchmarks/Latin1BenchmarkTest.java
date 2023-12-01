package io.vertx.benchmarks;

import org.junit.Test;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.charset.StandardCharsets.*;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 Task:
  Convert Latin1 String to byte[]
 Solutions:
  1) manual for-loop to copy chars into byte array
  2) {@link String#getBytes(int, int, byte[], int)}
  3) {@link String#getBytes(Charset)} with {@link StandardCharsets#ISO_8859_1}

 Benchmark them ^!

 Benchmark                               Mode  Cnt    Score   Error   Units
 Latin1BenchmarkTest.testBaseline       thrpt   10  209,947 ± 0,449  ops/ms
 Latin1BenchmarkTest.testLatinBytesV1a  thrpt   10    1,356 ± 0,005  ops/ms
 Latin1BenchmarkTest.testLatinBytesV1b  thrpt   10    1,361 ± 0,007  ops/ms
 Latin1BenchmarkTest.testLatinBytesV2   thrpt   10   24,501 ± 0,357  ops/ms
 Latin1BenchmarkTest.testLatinBytesV3   thrpt   10   24,387 ± 0,067  ops/ms

 -XX:CompileCommand=inline,java/lang/String.charAt
 Benchmark                               Mode  Cnt    Score   Error   Units
 Latin1BenchmarkTest.testBaseline       thrpt   10  210,304 ± 0,545  ops/ms
 Latin1BenchmarkTest.testLatinBytesV1a  thrpt   10    1,356 ± 0,013  ops/ms
 Latin1BenchmarkTest.testLatinBytesV1b  thrpt   10    1,360 ± 0,005  ops/ms
 Latin1BenchmarkTest.testLatinBytesV2   thrpt   10   24,803 ± 0,104  ops/ms
 Latin1BenchmarkTest.testLatinBytesV3   thrpt   10   24,527 ± 0,120  ops/ms

 Options for JDK 17/21: "-XX:+UseParallelGC", "-XX:+UseCompressedOops", "-XX:+DoEscapeAnalysis"

 @see io.vertx.core.parsetools.impl.RecordParserImpl#latin1StringToBytes(String)
 @author Andrej Fink
*/
@State(Scope.Thread)
public class Latin1BenchmarkTest extends BenchmarkBase {

  static final List<String> SAMPLES = new ArrayList<>(1100);
  static {
    SAMPLES.add("Wenn Fliegen hinter Fliegen fliegen, fliegen Fliegen Fliegen nach. 42!$");
    SAMPLES.add("");
    SAMPLES.add(" ");
    SAMPLES.add("\u0000");

    for (int i=1; i<1000; i++){
      byte[] bytes = new byte[i];
      ThreadLocalRandom.current().nextBytes(bytes);
      String s = new String(bytes, ISO_8859_1);// random Latin1 String
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
    for (int i = 0; i < len; i++){
      char c = str.charAt(i);
      bytes[i] = (byte)(c & 0xFF);
    }
    return bytes;
  }

  /**
   Deprecated fast low-byte copy version from JDK (deprecated, but the fastest and the same behavior as in original latin1StringToBytes)
   */
  public static byte[] latinBytesV2 (String str){
    int len = str.length();
    byte[] bytes = new byte[len];
    str.getBytes(0, len, bytes, 0);
    return bytes;
  }

  /** Correct behavior and fast conversion using JDK ISO_8859_1 encoder */
  public static byte[] latinBytesV3 (String str){
    return str.getBytes(ISO_8859_1);
  }


  @Test public void same (){
    AtomicInteger charsProcessed = new AtomicInteger();
    SAMPLES.forEach(s->{
      assertArrayEquals(latinBytesV1a(s), latinBytesV1b(s));
      assertArrayEquals(latinBytesV1a(s), latinBytesV2(s));
      assertArrayEquals(latinBytesV1a(s), latinBytesV3(s));
      String x = new String(latinBytesV3(s), ISO_8859_1);
      assertEquals(x, s);
      charsProcessed.addAndGet(s.length());
    });
    assertEquals(499_573, charsProcessed.get());
  }

  @Test public void sameNonLatin (){
    String s = "Andrej == Андрей 🚀";// non Latin1
    assertArrayEquals(latinBytesV1a(s), latinBytesV1b(s));
    assertArrayEquals(latinBytesV1a(s), latinBytesV2(s));

    assertFalse(Arrays.equals(latinBytesV1a(s), latinBytesV3(s)));
    assertEquals("Andrej == \u0010=4@59 =\u0080", new String(latinBytesV1a(s), ISO_8859_1));// "random" corruption
    assertEquals("Andrej == ?????? ?", new String(latinBytesV3(s), ISO_8859_1));// correct behavior
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
