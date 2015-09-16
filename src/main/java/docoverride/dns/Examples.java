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

package docoverride.dns;

import io.vertx.core.Vertx;
import io.vertx.core.dns.DnsClient;
import io.vertx.core.dns.DnsException;
import io.vertx.core.dns.DnsResponseCode;
import io.vertx.docgen.Source;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@Source
public class Examples {

  public void example16(Vertx vertx) {
    DnsClient client = vertx.createDnsClient(53, "10.0.0.1");
    client.lookup("nonexisting.vert.xio", ar -> {
      if (ar.succeeded()) {
        String record = ar.result();
        System.out.println(record);
      } else {
        Throwable cause = ar.cause();
        if (cause instanceof DnsException) {
          DnsException exception = (DnsException) cause;
          DnsResponseCode code = exception.code();
          // ...
        } else {
          System.out.println("Failed to resolve entry" + ar.cause());
        }
      }
    });
  }
}
