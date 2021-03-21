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
package io.vertx.core.eventbus;

import io.vertx.core.VertxOptions;
import io.vertx.core.metrics.MetricsOptions;
import io.vertx.core.spi.metrics.EventBusMetrics;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.fakemetrics.FakeEventBusMetrics;
import io.vertx.test.fakemetrics.FakeVertxMetrics;
import org.junit.Ignore;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public class StreamTest extends VertxTestBase {

  private static final String ADDRESS1 = "some-address1";

  private FakeEventBusMetrics eventBusMetrics;

  @Override
  protected VertxOptions getOptions() {
    eventBusMetrics = new FakeEventBusMetrics();
    VertxOptions options = super.getOptions();
    options.setMetricsOptions(new MetricsOptions()
      .setFactory(options1 -> new FakeVertxMetrics() {
        @Override
        public EventBusMetrics createEventBusMetrics() {
          return eventBusMetrics;
        }
      }).setEnabled(true));
    return options;
  }

  @Test
  public void testSimple1() {
    EventBus bus = vertx.eventBus();
    EventBusStream.bindConsumer(vertx, ADDRESS1, stream -> {
      stream.handler(msg -> {
        assertEquals("hello", msg);
        testComplete();
      });
    }, onSuccess(v -> {
      EventBusStream.<String>openProducer(bus, ADDRESS1, onSuccess(producer -> {
        producer.write("hello");
      }));
    }));
    await();
  }

  @Test
  public void testSimple2() {
    EventBus bus = vertx.eventBus();
    EventBusStream.bindProducer(bus, ADDRESS1, stream -> {
      stream.write("hello");
      }, onSuccess(ret -> {
      EventBusStream.openConsumer(vertx, ADDRESS1, onSuccess(stream -> {
        stream.handler(msg -> {
          assertEquals("hello", msg);
          testComplete();
        });
      }));
    }));
    await();
  }

  @Test
  public void testSimpleDuplex() {
    EventBusStream.<String>bindDuplex(vertx, ADDRESS1, stream -> {
      stream.handler(msg -> {
        assertEquals("ping", msg);
        stream.write("pong");
      });
    }, onSuccess(ret -> {
      EventBusStream.openDuplex(vertx, ADDRESS1, onSuccess(stream -> {
        stream.handler(msg -> {
          assertEquals("pong", msg);
          testComplete();
        });
        stream.write("ping");
      }));
    }));
    await();
  }

  @Test
  public void testPauseResume() {
    waitFor(2);
    EventBus bus = vertx.eventBus();
    CompletableFuture<Integer> resume = new CompletableFuture<>();
    EventBusStream.bindConsumer(vertx, ADDRESS1, stream -> {
      stream.pause();
      resume.whenComplete((val, err) -> {
        AtomicInteger count = new AtomicInteger(val);
        stream.handler(msg -> {
          assertEquals("hello", msg);
          if (count.decrementAndGet() == 0) {
            complete();
          }
        });
        stream.resume();
      });
    }, onSuccess(v -> {
      EventBusStream.<String>openProducer(bus, ADDRESS1, onSuccess(producer -> {
        producer.drainHandler(v2 -> {
          complete();
        });
        int count = 0;
        while (!producer.writeQueueFull()) {
          producer.write("hello");
          count++;
        }
        resume.complete(count);
      }));
    }));
    await();
  }

  @Test
  public void testOpenConsumerRemoteFailure() {
    EventBus bus = vertx.eventBus();
    bus.consumer(ADDRESS1, msg -> {
      msg.fail(0, "no-reason");
    }).completionHandler(onSuccess(v -> {
      EventBusStream.openConsumer(vertx, ADDRESS1, onFailure(err -> {
        // Check unbinding of stream address
        assertEquals(1, eventBusMetrics.getRegistrations().size());
        assertEquals(ADDRESS1, eventBusMetrics.getRegistrations().get(0).address);
        testComplete();
      }));
    }));
    await();
  }

  @Test
  public void testOpenConsumerMissingRemoteAddress() {
    EventBus bus = vertx.eventBus();
    bus.consumer(ADDRESS1, msg -> {
      msg.reply(null);
    }).completionHandler(onSuccess(v -> {
      EventBusStream.openConsumer(vertx, ADDRESS1, onFailure(err -> {
        // Check unbinding of stream address
        // runOnContext required because of reply address still present
        vertx.runOnContext(v2 -> {
          assertEquals(1, eventBusMetrics.getRegistrations().size());
          assertEquals(ADDRESS1, eventBusMetrics.getRegistrations().get(0).address);
          testComplete();
        });
      }));
    }));
    await();
  }

  @Ignore
  @Test
  public void testOpenConsumerHandshakeTimeout() {
    EventBus bus = vertx.eventBus();
    bus.consumer(ADDRESS1, msg -> {
      // No reply
    }).completionHandler(onSuccess(v -> {
      EventBusStream.openConsumer(vertx, ADDRESS1, onFailure(err -> {
        System.out.println("FAILURE");
      }));
    }));
    await();
  }
}
