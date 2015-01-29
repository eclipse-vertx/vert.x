/*
 * Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package docoverride.eventbus;

import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.docgen.Source;

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

  class MyPOJO {

  }

}
