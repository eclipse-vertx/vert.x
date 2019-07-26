package io.vertx.benchmarks;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

// just some dummy code to collect with async profiler statistics on method usage

public class JsonDecodeProfile {

  public static final int WARMUP_ITER = 1000;
  public static final int TEST_ITER = 10_000;

  Buffer buf;

  public JsonDecodeProfile(String filename) {
    buf = loadJsonAsBuffer(filename);
  }

  public static void main(String[] args) {
    JsonDecodeProfile decodeProfile = new JsonDecodeProfile("wide_bench.json");

    decodeProfile.warmup();
    decodeProfile.doWork();
  }

  public void warmup() {
    JsonObject[] jo = new JsonObject[WARMUP_ITER];

    for (int i = 0; i < WARMUP_ITER; i++) {
      jo[i] = buf.toJsonObject();
    }

    for (int i = 0; i < WARMUP_ITER; i++) {
      System.out.println(jo[i].fieldNames().iterator().next());
    }

  }

  public void doWork() {
    JsonObject[] jo = new JsonObject[TEST_ITER];

    for (int i = 0; i < TEST_ITER; i++) {
      jo[i] = buf.toJsonObject();
    }

    for (int i = 0; i < TEST_ITER; i++) {
      System.out.println(jo[i].fieldNames().iterator().next());
    }
  }

  private Buffer loadJsonAsBuffer(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    try (InputStream file = classLoader.getResourceAsStream(filename)) {
      String str = new BufferedReader(new InputStreamReader(file, StandardCharsets.UTF_8))
        .lines()
        .collect(Collectors.joining());
      Buffer encoded = Buffer.buffer(str);
      return Buffer.buffer().appendBuffer(encoded);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

}
