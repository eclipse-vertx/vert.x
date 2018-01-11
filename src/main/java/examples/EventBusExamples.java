/*
 * Copyright (c) 2014 Red Hat, Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package examples;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.EventBusOptions;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.ClientAuth;
import io.vertx.core.net.JksOptions;

/**
 * Created by tim on 09/01/15.
 */
public class EventBusExamples {

  public void example0_5(Vertx vertx) {
    EventBus eb = vertx.eventBus();
  }

  public void example1(Vertx vertx) {
    EventBus eb = vertx.eventBus();

    eb.consumer("news.uk.sport", message -> {
      System.out.println("I have received a message: " + message.body());
    });
  }

  public void example2(Vertx vertx) {
    EventBus eb = vertx.eventBus();

    MessageConsumer<String> consumer = eb.consumer("news.uk.sport");
    consumer.handler(message -> {
      System.out.println("I have received a message: " + message.body());
    });
  }

  public void example3(MessageConsumer<String> consumer) {
    consumer.completionHandler(res -> {
      if (res.succeeded()) {
        System.out.println("The handler registration has reached all nodes");
      } else {
        System.out.println("Registration failed!");
      }
    });
  }

  public void example4(MessageConsumer<String> consumer) {
    consumer.unregister(res -> {
      if (res.succeeded()) {
        System.out.println("The handler un-registration has reached all nodes");
      } else {
        System.out.println("Un-registration failed!");
      }
    });
  }

  public void example5(EventBus eventBus) {
    eventBus.publish("news.uk.sport", "Yay! Someone kicked a ball");
  }

  public void example6(EventBus eventBus) {
    eventBus.send("news.uk.sport", "Yay! Someone kicked a ball");
  }

  public void example8(EventBus eventBus) {
    MessageConsumer<String> consumer = eventBus.consumer("news.uk.sport");
    consumer.handler(message -> {
      System.out.println("I have received a message: " + message.body());
      message.reply("how interesting!");
    });
  }

  public void example9(EventBus eventBus) {
    eventBus.send("news.uk.sport", "Yay! Someone kicked a ball across a patch of grass", ar -> {
      if (ar.succeeded()) {
        System.out.println("Received reply: " + ar.result().body());
      }
    });
  }

  public void example12() {
    VertxOptions options = new VertxOptions();
    Vertx.clusteredVertx(options, res -> {
      if (res.succeeded()) {
        Vertx vertx = res.result();
        EventBus eventBus = vertx.eventBus();
        System.out.println("We now have a clustered event bus: " + eventBus);
      } else {
        System.out.println("Failed: " + res.cause());
      }
    });
  }

  public void example13() {
    VertxOptions options = new VertxOptions()
        .setEventBusOptions(new EventBusOptions()
            .setSsl(true)
            .setKeyStoreOptions(new JksOptions().setPath("keystore.jks").setPassword("wibble"))
            .setTrustStoreOptions(new JksOptions().setPath("keystore.jks").setPassword("wibble"))
            .setClientAuth(ClientAuth.REQUIRED)
        );

    Vertx.clusteredVertx(options, res -> {
      if (res.succeeded()) {
        Vertx vertx = res.result();
        EventBus eventBus = vertx.eventBus();
        System.out.println("We now have a clustered event bus: " + eventBus);
      } else {
        System.out.println("Failed: " + res.cause());
      }
    });
  }

  public void example14() {
    VertxOptions options = new VertxOptions()
        .setEventBusOptions(new EventBusOptions()
            .setClusterPublicHost("whatever")
            .setClusterPublicPort(1234)
        );

    Vertx.clusteredVertx(options, res -> {
      if (res.succeeded()) {
        Vertx vertx = res.result();
        EventBus eventBus = vertx.eventBus();
        System.out.println("We now have a clustered event bus: " + eventBus);
      } else {
        System.out.println("Failed: " + res.cause());
      }
    });
  }


}
