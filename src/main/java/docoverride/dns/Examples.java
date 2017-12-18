/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
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
