/*
 * Copyright 2014 Red Hat, Inc.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.test.core;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.test.fakecluster.FakeClusterManager;
import org.junit.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;


/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ClusteredEventBusTest extends EventBusTestBase {

  private static final String ADDRESS1 = "some-address1";

  protected ClusterManager getClusterManager() {
    return new FakeClusterManager();
  }

  @Override
  protected <T, R> void testSend(T val, R received, Consumer<T> consumer, DeliveryOptions options) {
    if (vertices == null) {
      startNodes(2);
    }

    MessageConsumer<T> reg = vertices[1].eventBus().<T>consumer(ADDRESS1).handler((Message<T> msg) -> {
      if (consumer == null) {
        assertEquals(received, msg.body());
        if (options != null && options.getHeaders() != null) {
          assertNotNull(msg.headers());
          assertEquals(options.getHeaders().size(), msg.headers().size());
          for (Map.Entry<String, String> entry: options.getHeaders().entries()) {
            assertEquals(msg.headers().get(entry.getKey()), entry.getValue());
          }
        }
      } else {
        consumer.accept(msg.body());
      }
      testComplete();
    });
    reg.completionHandler(ar -> {
      assertTrue(ar.succeeded());
      if (options == null) {
        vertices[0].eventBus().send(ADDRESS1, val);
      } else {
        vertices[0].eventBus().send(ADDRESS1, val, options);
      }
    });
    await();
  }

  @Override
  protected <T> void testSend(T val, Consumer <T> consumer) {
    testSend(val, val, consumer, null);
  }

  @Override
  protected <T> void testReply(T val, Consumer<T> consumer) {
    testReply(val, val, consumer, null);
  }

  @Override
  protected <T, R> void testReply(T val, R received, Consumer<R> consumer, DeliveryOptions options) {
    if (vertices == null) {
      startNodes(2);
    }
    String str = TestUtils.randomUnicodeString(1000);
    MessageConsumer<?> reg = vertices[1].eventBus().consumer(ADDRESS1).handler(msg -> {
      assertEquals(str, msg.body());
      if (options == null) {
        msg.reply(val);
      } else {
        msg.reply(val, options);
      }
    });
    reg.completionHandler(ar -> {
      assertTrue(ar.succeeded());
      vertices[0].eventBus().send(ADDRESS1, str, onSuccess((Message<R> reply) -> {
        if (consumer == null) {
          assertEquals(received, reply.body());
          if (options != null && options.getHeaders() != null) {
            assertNotNull(reply.headers());
            assertEquals(options.getHeaders().size(), reply.headers().size());
            for (Map.Entry<String, String> entry: options.getHeaders().entries()) {
              assertEquals(reply.headers().get(entry.getKey()), entry.getValue());
            }
          }
        } else {
          consumer.accept(reply.body());
        }
        testComplete();
      }));
    });

    await();
  }

  @Override
  protected <T> void testPublish(T val, Consumer<T> consumer) {
    int numNodes = 3;
    startNodes(numNodes);
    AtomicInteger count = new AtomicInteger();
    class MyHandler implements Handler<Message<T>> {
      @Override
      public void handle(Message<T> msg) {
        if (consumer == null) {
          assertEquals(val, msg.body());
        } else {
          consumer.accept(msg.body());
        }
        if (count.incrementAndGet() == numNodes - 1) {
          testComplete();
        }
      }
    }
    AtomicInteger registerCount = new AtomicInteger(0);
    class MyRegisterHandler implements Handler<AsyncResult<Void>> {
      @Override
      public void handle(AsyncResult<Void> ar) {
        assertTrue(ar.succeeded());
        if (registerCount.incrementAndGet() == 2) {
          vertices[0].eventBus().publish(ADDRESS1, (T)val);
        }
      }
    }
    MessageConsumer reg = vertices[2].eventBus().<T>consumer(ADDRESS1).handler(new MyHandler());
    reg.completionHandler(new MyRegisterHandler());
    reg = vertices[1].eventBus().<T>consumer(ADDRESS1).handler(new MyHandler());
    reg.completionHandler(new MyRegisterHandler());
    vertices[0].eventBus().publish(ADDRESS1, val);
    await();
  }
  
  @Override
  protected <T> void testForward(T val) {
    startNodes(2);
    
    vertices[0].eventBus().<T>consumer(ADDRESS1).handler(msg -> {
        assertEquals(val, msg.body());
        msg.forward(ADDRESS2);
    });

    vertices[1].eventBus().<T>consumer(ADDRESS2).handler(msg -> {
      assertEquals(val, msg.body());        
      assertTrue(msg.isForward());
      testComplete();
    });

    vertices[0].eventBus().send(ADDRESS1, val);
    await();
  
  }
  
  @Override
  protected <T> void testForwardWithHeaders(T val, DeliveryOptions options) {

    startNodes(2);
    int expectedHeaders = options.getHeaders().size();
    final String FIRST_KEY = "first";
    final String SEC_KEY = "second";

    vertices[0].eventBus().<T> consumer(ADDRESS1).handler(msg -> {
      assertEquals(val, msg.body());
      if (!msg.isForward()) {
        msg.forward(ADDRESS2);
      } else {
        assertTrue(msg.isForward());
        assertTrue(msg.headers().size() == expectedHeaders);
        assertEquals(msg.headers().get(FIRST_KEY), "first");
        assertEquals(msg.headers().get(SEC_KEY), "second");
        testComplete();
      }

    });

    vertices[1].eventBus().<T> consumer(ADDRESS2).handler(msg -> {
      assertEquals(val, msg.body());
      assertTrue(msg.isForward());
      msg.forward(ADDRESS1);
    });

    vertices[0].eventBus().send(ADDRESS1, val, options);
    await();

  }
  
  @Test
  public <T> void testForwardNoReadBodyOrHeaders(){
    startNodes(2);
    
    final String body = "Test Body";
    final String FIRST_KEY = "first";
    final String SEC_KEY = "second";

    vertices[0].eventBus().<T> consumer(ADDRESS1).handler(msg -> {

      if (!msg.isForward()) {
        msg.forward(ADDRESS2);
      } else {
        assertTrue(msg.isForward());
        assertEquals(msg.headers().get(FIRST_KEY), "first");
        assertEquals(msg.headers().get(SEC_KEY), "second");
        testComplete();
      }

    });

    vertices[1].eventBus().<T> consumer(ADDRESS2).handler(msg -> {
      assertTrue(msg.isForward());
      assertEquals(body, msg.body());
      msg.forward(ADDRESS1);
    });

    DeliveryOptions options = new DeliveryOptions();
    options.addHeader("first", "first");
    options.addHeader("second", "second");    
    vertices[0].eventBus().send(ADDRESS1, body, options);
    await();

  }
  
  @Test
  public <T> void testForwardReadBodyNoHeader(){
    startNodes(2);
    
    final String body = "Test Body";
    final String FIRST_KEY = "first";
    final String SEC_KEY = "second";

    vertices[0].eventBus().<T> consumer(ADDRESS1).handler(msg -> {

      if (!msg.isForward()) {
        assertEquals(body, msg.body());
        msg.forward(ADDRESS2);
      } else {
        assertTrue(msg.isForward());
        assertEquals(msg.headers().get(FIRST_KEY), "first");
        assertEquals(msg.headers().get(SEC_KEY), "second");
        testComplete();
      }

    });

    vertices[1].eventBus().<T> consumer(ADDRESS2).handler(msg -> {
      assertTrue(msg.isForward());
      assertEquals(body, msg.body());
      msg.forward(ADDRESS1);
    });

    DeliveryOptions options = new DeliveryOptions();
    options.addHeader("first", "first");
    options.addHeader("second", "second");    
    vertices[0].eventBus().send(ADDRESS1, body, options);
    await();

  }
  
  @Test
  public <T> void testForwardReadHeadersNoBody(){
    startNodes(2);
    
    final String body = "Test Body";
    final String FIRST_KEY = "first";
    final String SEC_KEY = "second";

    vertices[0].eventBus().<T> consumer(ADDRESS1).handler(msg -> {

      if (!msg.isForward()) {
        assertEquals(msg.headers().get(FIRST_KEY), "first");
        assertEquals(msg.headers().get(SEC_KEY), "second");
        msg.forward(ADDRESS2);

      } else {
        assertTrue(msg.isForward());
        assertEquals(msg.headers().get(FIRST_KEY), "first");
        assertEquals(msg.headers().get(SEC_KEY), "second");
        testComplete();
      }

    });

    vertices[1].eventBus().<T> consumer(ADDRESS2).handler(msg -> {
      assertTrue(msg.isForward());
      assertEquals(body, msg.body());
      assertEquals(msg.headers().get(FIRST_KEY), "first");
      assertEquals(msg.headers().get(SEC_KEY), "second");
      msg.forward(ADDRESS1);
    });

    DeliveryOptions options = new DeliveryOptions();
    options.addHeader("first", "first");
    options.addHeader("second", "second");    
    vertices[0].eventBus().send(ADDRESS1, body, options);
    await();

  }
 
  @Test
  public <T> void testForwardModifyHeaders(){
    startNodes(2);
    
    final String body = "Test Body";
    final String FIRST_KEY = "first";
    final String SEC_KEY = "second";

    vertices[0].eventBus().<T> consumer(ADDRESS1).handler(msg -> {

      if (!msg.isForward()) {
        msg.headers().remove("first");
        msg.headers().remove("second");
        msg.headers().add("third", "third");
        msg.headers().add("fourth", "fourth");
        msg.forward(ADDRESS2);

      } else {
        testComplete();
      }

    });

    vertices[1].eventBus().<T> consumer(ADDRESS2).handler(msg -> {
      assertTrue(msg.isForward());
      assertEquals(body, msg.body());
      assertNull(msg.headers().get(FIRST_KEY));
      assertNull(msg.headers().get(SEC_KEY));      
      assertEquals(msg.headers().get("third"), "third");
      assertEquals(msg.headers().get("fourth"), "fourth");

      msg.forward(ADDRESS1);
    });

    DeliveryOptions options = new DeliveryOptions();
    options.addHeader("first", "first");
    options.addHeader("second", "second");    
    vertices[0].eventBus().send(ADDRESS1, body, options);
    await();

  }

  @Test
  public void testLocalHandlerNotReceive() throws Exception {
    startNodes(2);
    vertices[1].eventBus().localConsumer(ADDRESS1).handler(msg -> {
      fail("Should not receive message");
    });
    vertices[0].eventBus().send(ADDRESS1, "foo");
    vertices[0].setTimer(1000, id -> testComplete());
    await();
  }

  
  @Test
  public void testDecoderSendAsymmetric() throws Exception {
    startNodes(2);
    MessageCodec codec = new MyPOJOEncoder1();
    vertices[0].eventBus().registerCodec(codec);
    vertices[1].eventBus().registerCodec(codec);
    String str = TestUtils.randomAlphaString(100);
    testSend(new MyPOJO(str), str, null, new DeliveryOptions().setCodecName(codec.name()));
  }

  @Test
  public void testDecoderReplyAsymmetric() throws Exception {
    startNodes(2);
    MessageCodec codec = new MyPOJOEncoder1();
    vertices[0].eventBus().registerCodec(codec);
    vertices[1].eventBus().registerCodec(codec);
    String str = TestUtils.randomAlphaString(100);
    testReply(new MyPOJO(str), str, null, new DeliveryOptions().setCodecName(codec.name()));
  }

  @Test
  public void testDecoderSendSymmetric() throws Exception {
    startNodes(2);
    MessageCodec codec = new MyPOJOEncoder2();
    vertices[0].eventBus().registerCodec(codec);
    vertices[1].eventBus().registerCodec(codec);
    String str = TestUtils.randomAlphaString(100);
    MyPOJO pojo = new MyPOJO(str);
    testSend(pojo, pojo, null, new DeliveryOptions().setCodecName(codec.name()));
  }

  @Test
  public void testDecoderReplySymmetric() throws Exception {
    startNodes(2);
    MessageCodec codec = new MyPOJOEncoder2();
    vertices[0].eventBus().registerCodec(codec);
    vertices[1].eventBus().registerCodec(codec);
    String str = TestUtils.randomAlphaString(100);
    MyPOJO pojo = new MyPOJO(str);
    testReply(pojo, pojo, null, new DeliveryOptions().setCodecName(codec.name()));
  }

  @Test
  public void testDefaultDecoderSendAsymmetric() throws Exception {
    startNodes(2);
    MessageCodec codec = new MyPOJOEncoder1();
    vertices[0].eventBus().registerDefaultCodec(MyPOJO.class, codec);
    vertices[1].eventBus().registerDefaultCodec(MyPOJO.class, codec);
    String str = TestUtils.randomAlphaString(100);
    testSend(new MyPOJO(str), str, null, null);
  }

  @Test
  public void testDefaultDecoderReplyAsymmetric() throws Exception {
    startNodes(2);
    MessageCodec codec = new MyPOJOEncoder1();
    vertices[0].eventBus().registerDefaultCodec(MyPOJO.class, codec);
    vertices[1].eventBus().registerDefaultCodec(MyPOJO.class, codec);
    String str = TestUtils.randomAlphaString(100);
    testReply(new MyPOJO(str), str, null, null);
  }

  @Test
  public void testDefaultDecoderSendSymetric() throws Exception {
    startNodes(2);
    MessageCodec codec = new MyPOJOEncoder2();
    vertices[0].eventBus().registerDefaultCodec(MyPOJO.class, codec);
    vertices[1].eventBus().registerDefaultCodec(MyPOJO.class, codec);
    String str = TestUtils.randomAlphaString(100);
    MyPOJO pojo = new MyPOJO(str);
    testSend(pojo, pojo, null, null);
  }

  @Test
  public void testDefaultDecoderReplySymetric() throws Exception {
    startNodes(2);
    MessageCodec codec = new MyPOJOEncoder2();
    vertices[0].eventBus().registerDefaultCodec(MyPOJO.class, codec);
    vertices[1].eventBus().registerDefaultCodec(MyPOJO.class, codec);
    String str = TestUtils.randomAlphaString(100);
    MyPOJO pojo = new MyPOJO(str);
    testReply(pojo, pojo, null, null);
  }

}
