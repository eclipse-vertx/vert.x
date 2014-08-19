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
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.VertxException;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.Test;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class EventBusProxyTest extends VertxTestBase {

  protected EventBus eb;
  protected MyService proxy;
  protected MyServiceImpl impl;

  public void setUp() throws Exception {
    super.setUp();
    eb = vertx.eventBus();
    impl = new MyServiceImpl();
    eb.registerService(impl, "service_address");
    proxy = eb.createProxy(MyService.class, "service_address");
  }

  @Test
  public void testSimpleParams() throws Exception {
    proxy.methodWithParams((byte)123, (short)123456, 124351423, 125312653l, 1.23f, 34.45d, true, "foobar",
                           new JsonObject().putString("foo", "bar"), new JsonArray().add("socks"));
    waitUntil(() -> impl.called);
  }

  @Test
  public void testBoxedParams() throws Exception {
    proxy.methodWithBoxedParams(Byte.valueOf((byte)123), Short.valueOf((short)123456), Integer.valueOf(124351423), Long.valueOf(125312653l),
      Float.valueOf(1.23f), Double.valueOf(34.45d), Boolean.valueOf(true));
    waitUntil(() -> impl.called);
  }

  @Test
  public void testSimpleParamsAndHandler() throws Exception {
    proxy.methodWithParamsAndHandler((byte) 123, (short) 123456, 124351423, 125312653l, 1.23f, 34.45d, true, "foobar",
      new JsonObject().putString("foo", "bar"), new JsonArray().add("socks"), ar -> {
        assertTrue(ar.succeeded());
        assertEquals("foobar", ar.result());
        testComplete();
      });
    await();
  }

  @Test
  public void testBoxedParamsAndHandler() throws Exception {
    proxy.methodWithBoxedParamsAndHandler(Byte.valueOf((byte) 123), Short.valueOf((short) 123456), Integer.valueOf(124351423), Long.valueOf(125312653l),
      Float.valueOf(1.23f), Double.valueOf(34.45d), Boolean.valueOf(true), ar -> {
        assertTrue(ar.succeeded());
        assertEquals("foobar", ar.result());
        testComplete();
      });
    await();
  }

  @Test
  public void testJustHandler() throws Exception {
    proxy.methodWithJustHandler(ar -> {
      assertTrue(ar.succeeded());
      assertEquals("foobar", ar.result());
      testComplete();
    });
    await();
  }

  @Test
  public void testByteHandler() throws Exception {
    proxy.methodWithByteHandler("foo", ar -> {
      assertTrue(ar.succeeded());
      assertEquals((byte) 123, ar.result().byteValue());
      testComplete();
    });
    await();
  }

  @Test
  public void testShortHandler() throws Exception {
    proxy.methodWithShortHandler("foo", ar -> {
      assertTrue(ar.succeeded());
      assertEquals((short) 123456, ar.result().shortValue());
      testComplete();
    });
    await();
  }

  @Test
  public void testIntHandler() throws Exception {
    proxy.methodWithIntHandler("foo", ar -> {
      assertTrue(ar.succeeded());
      assertEquals(124351423, ar.result().intValue());
      testComplete();
    });
    await();
  }

  @Test
  public void testLongHandler() throws Exception {
    proxy.methodWithLongHandler("foo", ar -> {
      assertTrue(ar.succeeded());
      assertEquals(125312653l, ar.result().longValue());
      testComplete();
    });
    await();
  }

  @Test
  public void testFloatHandler() throws Exception {
    proxy.methodWithFloatHandler("foo", ar -> {
      assertTrue(ar.succeeded());
      assertEquals(1.23f, ar.result().floatValue(), 0);
      testComplete();
    });
    await();
  }

  @Test
  public void testDoubleHandler() throws Exception {
    proxy.methodWithDoubleHandler("foo", ar -> {
      assertTrue(ar.succeeded());
      assertEquals(34.45d, ar.result().doubleValue(), 0);
      testComplete();
    });
    await();
  }

  @Test
  public void testBooleanHandler() throws Exception {
    proxy.methodWithBooleanHandler("foo", ar -> {
      assertTrue(ar.succeeded());
      assertTrue(ar.result());
      testComplete();
    });
    await();
  }

  @Test
  public void testJsonObjectHandler() throws Exception {
    proxy.methodWithJsonObjectHandler("foo", ar -> {
      assertTrue(ar.succeeded());
      assertEquals("bar", ar.result().getString("foo"));
      testComplete();
    });
    await();
  }

  @Test
  public void testJsonArrayHandler() throws Exception {
    proxy.methodWithJsonArrayHandler("foo", ar -> {
      assertTrue(ar.succeeded());
      assertEquals("socks", ar.result().get(0));
      testComplete();
    });
    await();
  }


  @Test
  public void testMethodWithUnsupportedParam() throws Exception {
    try {
      proxy.methodWithUnsupportedParam(new Foo());
      fail("Should throw exception");
    } catch (VertxException e) {
      // OK
    }
  }

  @Test
  public void testMethodWithHandlerWrongPosition() throws Exception {
    try {
      proxy.methodWithHandlerWrongPosition(ar -> {}, "foo");
      fail("Should throw exception");
    } catch (VertxException e) {
      // OK
    }
  }

  @Test
  public void testMethodWithTwoHandlers() throws Exception {
    try {
      proxy.methodWithTwoHandlers("foo", ar -> {}, ar -> {});
      fail("Should throw exception");
    } catch (VertxException e) {
      // OK
    }
  }

  @Test
  public void testMethodWithNonVoidReturn() throws Exception {
    try {
      proxy.methodWithNonVoidReturn("foo");
      fail("Should throw exception");
    } catch (VertxException e) {
      // OK
    }
  }

  @Test
  public void testMethodWithNullArgs() throws Exception {
    proxy.methodWithNullArgs(null, null);
    waitUntil(() -> impl.called);
  }

  @Test
  public void testHandlerFailure() throws Exception {
    proxy.handlerFailure("foo", ar -> {
      assertTrue(ar.failed());
      assertTrue(ar.cause() instanceof VertxException);
      assertEquals("foo", ar.cause().getMessage());
      testComplete();
    });
    await();
  }

  class Foo {

  }

  /*

  overloaded methods?


   */

  interface MyService {

    void methodWithParams(byte b, short s, int i, long l, float f, double d, boolean bool, String str, JsonObject object,
                          JsonArray array);

    void methodWithBoxedParams(Byte b, Short s, Integer i, Long l, Float f, Double d, Boolean bool);

    void methodWithParamsAndHandler(byte b, short s, int i, long l, float f, double d, boolean bool, String str,
                                    JsonObject object, JsonArray array, Handler<AsyncResult<String>> resultHandler);

    void methodWithBoxedParamsAndHandler(Byte b, Short s, Integer i, Long l, Float f, Double d, Boolean bool,
                                         Handler<AsyncResult<String>> resultHandler);

    void methodWithJustHandler(Handler<AsyncResult<String>> resultHandler);

    void methodWithByteHandler(String str, Handler<AsyncResult<Byte>> resultHandler);

    void methodWithShortHandler(String str, Handler<AsyncResult<Short>> resultHandler);

    void methodWithIntHandler(String str, Handler<AsyncResult<Integer>> resultHandler);

    void methodWithLongHandler(String str, Handler<AsyncResult<Long>> resultHandler);

    void methodWithFloatHandler(String str, Handler<AsyncResult<Float>> resultHandler);

    void methodWithDoubleHandler(String str, Handler<AsyncResult<Double>> resultHandler);

    void methodWithBooleanHandler(String str, Handler<AsyncResult<Boolean>> resultHandler);

    void methodWithJsonObjectHandler(String str, Handler<AsyncResult<JsonObject>> resultHandler);

    void methodWithJsonArrayHandler(String str, Handler<AsyncResult<JsonArray>> resultHandler);


    void methodWithUnsupportedParam(Foo foo);

    void methodWithHandlerWrongPosition(Handler<AsyncResult<Integer>> resultHandler, String str);

    void methodWithTwoHandlers(String str, Handler<AsyncResult<Integer>> resultHandler1,
                               Handler<AsyncResult<Integer>> resultHandler2);

    String methodWithNonVoidReturn(String str);

    void methodWithNullArgs(String str, Long l);

    void handlerFailure(String str, Handler<AsyncResult<String>> resultHandler);

  }

  public class MyServiceImpl implements MyService {

    boolean called;

    @Override
    public void methodWithParams(byte b, short s, int i, long l, float f, double d, boolean bool, String str,
                                 JsonObject object, JsonArray array) {
      assertEquals((byte)123, b);
      assertEquals((short)123456, s);
      assertEquals(124351423, i);
      assertEquals(125312653l, l);
      assertEquals(1.23f, f, 0);
      assertEquals(34.45d, d, 0);
      assertTrue(bool);
      assertEquals("foobar", str);
      assertEquals("bar", object.getString("foo"));
      assertEquals("socks", array.get(0));
      called = true;
    }

    @Override
    public void methodWithBoxedParams(Byte b, Short s, Integer i, Long l, Float f, Double d, Boolean bool) {
      assertEquals(Byte.valueOf((byte)123), b);
      assertEquals(Short.valueOf((short)123456), s);
      assertEquals(Integer.valueOf(124351423), i);
      assertEquals(Long.valueOf(125312653l), l);
      assertEquals(Float.valueOf(1.23f), f, 0);
      assertEquals(Double.valueOf(34.45d), d, 0);
      assertTrue(bool);
      called = true;
    }

    @Override
    public void methodWithParamsAndHandler(byte b, short s, int i, long l, float f, double d, boolean bool, String str,
                                           JsonObject object, JsonArray array, Handler<AsyncResult<String>> resultHandler) {
      assertEquals((byte)123, b);
      assertEquals((short)123456, s);
      assertEquals(124351423, i);
      assertEquals(125312653l, l);
      assertEquals(1.23f, f, 0);
      assertEquals(34.45d, d, 0);
      assertTrue(bool);
      assertEquals("foobar", str);
      assertEquals("bar", object.getString("foo"));
      assertEquals("socks", array.get(0));
      resultHandler.handle(Future.completedFuture("foobar"));
    }

    @Override
    public void methodWithBoxedParamsAndHandler(Byte b, Short s, Integer i, Long l, Float f, Double d, Boolean bool,
                                                Handler<AsyncResult<String>> resultHandler) {
      assertEquals(Byte.valueOf((byte)123), b);
      assertEquals(Short.valueOf((short)123456), s);
      assertEquals(Integer.valueOf(124351423), i);
      assertEquals(Long.valueOf(125312653l), l);
      assertEquals(Float.valueOf(1.23f), f, 0);
      assertEquals(Double.valueOf(34.45d), d, 0);
      assertTrue(bool);
      resultHandler.handle(Future.completedFuture("foobar"));
    }

    @Override
    public void methodWithJustHandler(Handler<AsyncResult<String>> resultHandler) {
      resultHandler.handle(Future.completedFuture("foobar"));
    }

    @Override
    public void methodWithByteHandler(String str, Handler<AsyncResult<Byte>> resultHandler) {
      resultHandler.handle(Future.completedFuture((byte)123));
    }

    @Override
    public void methodWithShortHandler(String str, Handler<AsyncResult<Short>> resultHandler) {
      resultHandler.handle(Future.completedFuture((short)123456));
    }

    @Override
    public void methodWithIntHandler(String str, Handler<AsyncResult<Integer>> resultHandler) {
      resultHandler.handle(Future.completedFuture(124351423));
    }

    @Override
    public void methodWithLongHandler(String str, Handler<AsyncResult<Long>> resultHandler) {
      resultHandler.handle(Future.completedFuture(125312653l));
    }

    @Override
    public void methodWithFloatHandler(String str, Handler<AsyncResult<Float>> resultHandler) {
      resultHandler.handle(Future.completedFuture(1.23f));
    }

    @Override
    public void methodWithDoubleHandler(String str, Handler<AsyncResult<Double>> resultHandler) {
      resultHandler.handle(Future.completedFuture(34.45d));
    }

    @Override
    public void methodWithBooleanHandler(String str, Handler<AsyncResult<Boolean>> resultHandler) {
      resultHandler.handle(Future.completedFuture(true));
    }

    @Override
    public void methodWithJsonObjectHandler(String str, Handler<AsyncResult<JsonObject>> resultHandler) {
      resultHandler.handle(Future.completedFuture(new JsonObject().putString("foo", "bar")));
    }

    @Override
    public void methodWithJsonArrayHandler(String str, Handler<AsyncResult<JsonArray>> resultHandler) {
      resultHandler.handle(Future.completedFuture(new JsonArray().add("socks")));
    }

    @Override
    public void methodWithUnsupportedParam(Foo foo) {
    }

    @Override
    public void methodWithHandlerWrongPosition(Handler<AsyncResult<Integer>> resultHandler, String str) {
    }

    @Override
    public void methodWithTwoHandlers(String str, Handler<AsyncResult<Integer>> resultHandler1,
                                      Handler<AsyncResult<Integer>> resultHandler2) {
    }

    @Override
    public String methodWithNonVoidReturn(String str) {
      return null;
    }

    @Override
    public void methodWithNullArgs(String str, Long l) {
      assertNull(str);
      assertNull(l);
      called = true;
    }

    @Override
    public void handlerFailure(String str, Handler<AsyncResult<String>> resultHandler) {
      resultHandler.handle(Future.completedFuture(new VertxException("foo")));
    }
  }

}
