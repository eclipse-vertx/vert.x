/*
 * Copyright (c) 2011-2022 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.tests.eventbus;

import io.vertx.core.*;
import io.vertx.core.eventbus.*;
import io.vertx.tests.shareddata.AsyncMapTest;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.core.spi.cluster.RegistrationUpdateEvent;
import io.vertx.test.core.TestUtils;
import io.vertx.test.fakecluster.FakeClusterManager;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.CoreMatchers.*;


/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ClusteredEventBusTestBase extends EventBusTestBase {

  protected static final String ADDRESS1 = "some-address1";

  protected ClusterManager getClusterManager() {
    return new FakeClusterManager();
  }

  @Override
  protected Future<Vertx> clusteredVertx(VertxOptions options, ClusterManager clusterManager) {
    Future<Vertx> fut = super.clusteredVertx(options, clusterManager);
    return fut.onSuccess(vertx -> {
      ImmutableObjectCodec immutableObjectCodec = new ImmutableObjectCodec();
      vertx.eventBus().registerCodec(immutableObjectCodec);
      vertx.eventBus().codecSelector(obj -> obj instanceof ImmutableObject ? immutableObjectCodec.name() : null);
      vertx.eventBus().clusterSerializableChecker(className -> className.startsWith(AsyncMapTest.class.getName()));
      vertx.eventBus().serializableChecker(className -> {
        return EventBus.DEFAULT_SERIALIZABLE_CHECKER.apply(className) || className.startsWith(AsyncMapTest.class.getName());
      });
    });
  }

  @Override
  protected boolean shouldImmutableObjectBeCopied() {
    return true;
  }

  @Override
  protected Vertx[] vertices(int num) {
    if (vertices == null) {
      startNodes(num);
    }
    Vertx[] instances = new Vertx[num];
    for (int i = 0;i < num;i++) {
      instances[i] = vertices[i];
    }
    return instances;
  }

  @Test
  public void testRegisterRemote1() {
    startNodes(2);
    String str = TestUtils.randomUnicodeString(100);
    vertices[0].eventBus().<String>consumer(ADDRESS1).handler((Message<String> msg) -> {
      assertEquals(str, msg.body());
      testComplete();
    }).completion().onComplete(ar -> {
      assertTrue(ar.succeeded());
      vertices[1].eventBus().send(ADDRESS1, str);
    });
    await();
  }

  @Test
  public void testRegisterRemote2() {
    startNodes(2);
    String str = TestUtils.randomUnicodeString(100);
    vertices[0].eventBus().consumer(ADDRESS1, (Message<String> msg) -> {
      assertEquals(str, msg.body());
      testComplete();
    }).completion().onComplete(ar -> {
      assertTrue(ar.succeeded());
      vertices[1].eventBus().send(ADDRESS1, str);
    });
    await();
  }

  @Test
  public void testMessageBodyInterceptor() throws Exception {
    String content = TestUtils.randomUnicodeString(13);
    startNodes(2);
    waitFor(2);
    CountDownLatch latch = new CountDownLatch(1);
    vertices[0].eventBus().registerCodec(new StringLengthCodec()).<Integer>consumer("whatever", msg -> {
      assertEquals(content.length(), (int) msg.body());
      complete();
    }).completion().onComplete(ar -> latch.countDown());
    awaitLatch(latch);
    StringLengthCodec codec = new StringLengthCodec();
    vertices[1].eventBus().registerCodec(codec).addOutboundInterceptor(sc -> {
      if ("whatever".equals(sc.message().address())) {
        assertEquals(content, sc.body());
        complete();
      }
      sc.next();
    }).send("whatever", content, new DeliveryOptions().setCodecName(codec.name()));
    await();
  }

  @Test
  public void testClusteredUnregistration() throws Exception {
    CountDownLatch updateLatch = new CountDownLatch(3);
    startNodes(2, () -> new WrappedClusterManager(getClusterManager()) {
      @Override
      public void registrationsUpdated(RegistrationUpdateEvent event) {
        super.registrationsUpdated(event);
        if (event.address().equals("foo") && event.registrations().isEmpty()) {
          updateLatch.countDown();
        }
      }
    });
    MessageConsumer<Object> consumer = vertices[0].eventBus().consumer("foo", msg -> msg.reply(msg.body()));
    consumer.completion().onComplete(onSuccess(reg -> {
      vertices[0].eventBus().request("foo", "echo").onComplete(onSuccess(reply1 -> {
        assertEquals("echo", reply1.body());
        vertices[1].eventBus().request("foo", "echo").onComplete(onSuccess(reply2 -> {
          assertEquals("echo", reply1.body());
          consumer.unregister().onComplete(onSuccess(unreg -> {
            updateLatch.countDown();
          }));
        }));
      }));
    }));
    awaitLatch(updateLatch);
    vertices[1].eventBus().request("foo", "echo").onComplete(onFailure(fail1 -> {
      assertThat(fail1, is(instanceOf(ReplyException.class)));
      assertEquals(ReplyFailure.NO_HANDLERS, ((ReplyException) fail1).failureType());
      vertices[0].eventBus().request("foo", "echo").onComplete(onFailure(fail2 -> {
        assertThat(fail2, is(instanceOf(ReplyException.class)));
        assertEquals(ReplyFailure.NO_HANDLERS, ((ReplyException) fail2).failureType());
        testComplete();
      }));
    }));
    await();
  }

  @Test
  public void testMessagingInStopMethod() throws Exception {
    startNodes(2);

    AtomicInteger count = new AtomicInteger();

    class MyVerticle extends AbstractVerticle {

      final String pingServerAddress;
      final String pingClientAddress;

      MyVerticle(String pingServerAddress, String pingClientAddress) {
        this.pingServerAddress = pingServerAddress;
        this.pingClientAddress = pingClientAddress;
      }

      @Override
      public void start(Promise<Void> startPromise) throws Exception {
        vertx.eventBus().consumer(pingServerAddress, msg -> msg.reply("pong")).completion().onComplete(startPromise);
      }

      @Override
      public void stop(Promise<Void> stopPromise) throws Exception {
        vertx.eventBus().<String>request(pingClientAddress, "ping").onComplete(onSuccess(msg -> {
          assertEquals("pong", msg.body());
          count.incrementAndGet();
          vertx.setPeriodic(10, l -> {
            if (count.get() == 2) {
              stopPromise.complete();
            }
          });
        }));
      }
    }

    waitFor(2);

    vertices[0].deployVerticle(new MyVerticle("foo", "bar")).onComplete(onSuccess(id1 -> {
      vertices[1].deployVerticle(new MyVerticle("bar", "foo")).onComplete(onSuccess(id2 -> {
        vertices[0].close().onComplete(onSuccess(v -> complete()));
        vertices[1].close().onComplete(onSuccess(v -> complete()));
      }));
    }));

    await();
  }
}
