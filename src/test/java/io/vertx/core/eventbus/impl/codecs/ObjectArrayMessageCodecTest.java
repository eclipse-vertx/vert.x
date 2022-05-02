package io.vertx.core.eventbus.impl.codecs;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.eventbus.impl.codecs.StringMessageCodec;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.fakecluster.FakeClusterManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static java.util.Arrays.asList;

@RunWith(Parameterized.class)
public class ObjectArrayMessageCodecTest extends VertxTestBase {


  private final Object[] message;

  public ObjectArrayMessageCodecTest(Object[] message) {
    this.message = message;
  }


  @Parameterized.Parameters
  public static Collection<Object[]> parameters() {
    return asList(new Object[][]{
      {new Object[]{1L, new JsonObject().put("mes", "text")}},
      {new Object[]{new Example("Hello"), Buffer.buffer("World!")}}
    });
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
  }

  @Override
  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Override
  protected ClusterManager getClusterManager() {
    return new FakeClusterManager();
  }

  @Test
  public void case1() {
    startNodes(2);

    vertices[0].eventBus().registerDefaultCodec(Example.class, new ExampleMessageCodec());
    vertices[1].eventBus().registerDefaultCodec(Example.class, new ExampleMessageCodec());
    vertices[0].exceptionHandler(this::fail);
    vertices[1].exceptionHandler(this::fail);

    vertices[0].eventBus().<Object[]>consumer("a").handler(m -> assertArrayEquals("Array not equals", message, m.body()))
      .exceptionHandler(this::fail)
      .completionHandler(ar -> {
        assertTrue(ar.succeeded());
        vertices[1].eventBus().send("a", message);
        testComplete();
      });
    await(30, TimeUnit.SECONDS);
  }

  @Test
  public void case2() {
    startNodes(2);

    ExampleMessageCodec messageCodec = new ExampleMessageCodec();
    vertices[0].eventBus().registerDefaultCodec(Example.class,messageCodec);
    vertices[1].eventBus().registerDefaultCodec(Example.class, messageCodec);
    vertices[0].exceptionHandler(this::fail);
    vertices[1].exceptionHandler(this::fail);

    vertices[0].eventBus().<Object[]>consumer("a").handler(m -> assertArrayEquals("Array not equals", message, m.body()))
      .exceptionHandler(this::fail)
      .completionHandler(ar -> {
        assertTrue(ar.succeeded());
        vertices[1].eventBus().publish("a", message);
        testComplete();
      });
    await(30, TimeUnit.SECONDS);
  }

  private static class Example {
    private final String value;

    private Example(String value) {
      this.value = value;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Example example = (Example) o;
      return Objects.equals(value, example.value);
    }

    @Override
    public int hashCode() {
      return Objects.hash(value);
    }

    @Override
    public String toString() {
      return value;
    }
  }

  private static class ExampleMessageCodec implements MessageCodec<Example, Example> {

    MessageCodec<String, String> delegate = new StringMessageCodec();

    @Override
    public void encodeToWire(Buffer buffer, Example example) {
      delegate.encodeToWire(buffer, example.value);
    }

    @Override
    public Example decodeFromWire(int pos, Buffer buffer) {
      return new Example(delegate.decodeFromWire(pos, buffer));
    }

    @Override
    public Example transform(Example example) {
      return example;
    }

    @Override
    public String name() {
      return "example";
    }

    @Override
    public byte systemCodecID() {
      return -1;
    }
  }
}
