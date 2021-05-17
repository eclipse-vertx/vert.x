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

package docoverride.eventbus;

import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.shareddata.Shareable;
import io.vertx.docgen.Source;

import java.util.function.Function;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@Source
public class Examples {

  public void example10(EventBus eventBus, MessageCodec myCodec) {

    eventBus.registerCodec(myCodec);

    DeliveryOptions options = new DeliveryOptions().setCodecName(myCodec.name());

    eventBus.send("orders", new MyPOJO(), options);
  }

  public void example11(EventBus eventBus, MessageCodec myCodec) {

    eventBus.registerDefaultCodec(MyPOJO.class, myCodec);

    eventBus.send("orders", new MyPOJO());
  }

  public void example12(EventBus eventBus) {
    // Class does not implement Shareable
    eventBus.registerLocalCodec(MyPOJO.class, MyPOJO::copy);

    // Class implements Shareable
    eventBus.registerLocalCodec(MyPOJO.class);
  }

  public void example13(EventBus eventBus) {
    // Class is immutable, so there's no need to copy
    eventBus.registerLocalCodec(MyPOJO.class, Function.identity());
  }

  public void headers(EventBus eventBus) {
    DeliveryOptions options = new DeliveryOptions();
    options.addHeader("some-header", "some-value");
    eventBus.send("news.uk.sport", "Yay! Someone kicked a ball", options);
  }

  class MyPOJO implements Shareable {
    public MyPOJO copy() {
      return null;
    }
  }

}
