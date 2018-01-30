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

package examples;

import io.vertx.core.Vertx;
import io.vertx.core.dns.DnsClient;
import io.vertx.core.dns.DnsClientOptions;
import io.vertx.core.dns.MxRecord;
import io.vertx.core.dns.SrvRecord;

import java.util.List;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class DNSExamples {

  public void example1(Vertx vertx) {
    DnsClient client = vertx.createDnsClient(53, "10.0.0.1");
  }

  public void example1_(Vertx vertx) {
    DnsClient client = vertx.createDnsClient(new DnsClientOptions()
      .setPort(53)
      .setHost("10.0.0.1")
      .setQueryTimeout(10000)
    );
  }

  public void example1__(Vertx vertx) {
    DnsClient client1 = vertx.createDnsClient();

    // Just the same but with a different query timeout
    DnsClient client2 = vertx.createDnsClient(new DnsClientOptions().setQueryTimeout(10000));
  }

  public void example2(Vertx vertx) {
    DnsClient client = vertx.createDnsClient(53, "9.9.9.9");
    client.lookup("vertx.io", ar -> {
      if (ar.succeeded()) {
        System.out.println(ar.result());
      } else {
        System.out.println("Failed to resolve entry" + ar.cause());
      }
    });
  }

  public void example3(Vertx vertx) {
    DnsClient client = vertx.createDnsClient(53, "9.9.9.9");
    client.lookup4("vertx.io", ar -> {
      if (ar.succeeded()) {
        System.out.println(ar.result());
      } else {
        System.out.println("Failed to resolve entry" + ar.cause());
      }
    });
  }

  public void example4(Vertx vertx) {
    DnsClient client = vertx.createDnsClient(53, "9.9.9.9");
    client.lookup6("vertx.io", ar -> {
      if (ar.succeeded()) {
        System.out.println(ar.result());
      } else {
        System.out.println("Failed to resolve entry" + ar.cause());
      }
    });
  }

  public void example5(Vertx vertx) {
    DnsClient client = vertx.createDnsClient(53, "9.9.9.9");
    client.resolveA("vertx.io", ar -> {
      if (ar.succeeded()) {
        List<String> records = ar.result();
        for (String record : records) {
          System.out.println(record);
        }
      } else {
        System.out.println("Failed to resolve entry" + ar.cause());
      }
    });
  }

  public void example6(Vertx vertx) {
    DnsClient client = vertx.createDnsClient(53, "9.9.9.9");
    client.resolveAAAA("vertx.io", ar -> {
      if (ar.succeeded()) {
        List<String> records = ar.result();
        for (String record : records) {
          System.out.println(record);
        }
      } else {
        System.out.println("Failed to resolve entry" + ar.cause());
      }
    });
  }

  public void example7(Vertx vertx) {
    DnsClient client = vertx.createDnsClient(53, "9.9.9.9");
    client.resolveCNAME("vertx.io", ar -> {
      if (ar.succeeded()) {
        List<String> records = ar.result();
        for (String record : records) {
          System.out.println(record);
        }
      } else {
        System.out.println("Failed to resolve entry" + ar.cause());
      }
    });
  }

  public void example8(Vertx vertx) {
    DnsClient client = vertx.createDnsClient(53, "9.9.9.9");
    client.resolveMX("vertx.io", ar -> {
      if (ar.succeeded()) {
        List<MxRecord> records = ar.result();
        for (MxRecord record: records) {
          System.out.println(record);
        }
      } else {
        System.out.println("Failed to resolve entry" + ar.cause());
      }
    });
  }

  public void example9(MxRecord record) {
    record.priority();
    record.name();
  }

  public void example10(Vertx vertx) {
    DnsClient client = vertx.createDnsClient(53, "9.9.9.9");
    client.resolveTXT("vertx.io", ar -> {
      if (ar.succeeded()) {
        List<String> records = ar.result();
        for (String record: records) {
          System.out.println(record);
        }
      } else {
        System.out.println("Failed to resolve entry" + ar.cause());
      }
    });
  }

  public void example11(Vertx vertx) {
    DnsClient client = vertx.createDnsClient(53, "9.9.9.9");
    client.resolveNS("vertx.io", ar -> {
      if (ar.succeeded()) {
        List<String> records = ar.result();
        for (String record: records) {
          System.out.println(record);
        }
      } else {
        System.out.println("Failed to resolve entry" + ar.cause());
      }
    });
  }

  public void example12(Vertx vertx) {
    DnsClient client = vertx.createDnsClient(53, "9.9.9.9");
    client.resolveSRV("vertx.io", ar -> {
      if (ar.succeeded()) {
        List<SrvRecord> records = ar.result();
        for (SrvRecord record: records) {
          System.out.println(record);
        }
      } else {
        System.out.println("Failed to resolve entry" + ar.cause());
      }
    });
  }

  private static SrvRecord getSrvRecord() {
    return null;
  }

  public void example13(SrvRecord record) {
    record.priority();
    record.name();
    record.weight();
    record.port();
    record.protocol();
    record.service();
    record.target();
  }

  public void example14(Vertx vertx) {
    DnsClient client = vertx.createDnsClient(53, "9.9.9.9");
    client.resolvePTR("1.0.0.10.in-addr.arpa", ar -> {
      if (ar.succeeded()) {
        String record = ar.result();
        System.out.println(record);
      } else {
        System.out.println("Failed to resolve entry" + ar.cause());
      }
    });
  }

  public void example15(Vertx vertx) {
    DnsClient client = vertx.createDnsClient(53, "9.9.9.9");
    client.reverseLookup("10.0.0.1", ar -> {
      if (ar.succeeded()) {
        String record = ar.result();
        System.out.println(record);
      } else {
        System.out.println("Failed to resolve entry" + ar.cause());
      }
    });
  }
}
