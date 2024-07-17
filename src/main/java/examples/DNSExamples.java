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
    DnsClient client2 =
      vertx.createDnsClient(new DnsClientOptions().setQueryTimeout(10000));
  }

  public void example2(Vertx vertx) {
    DnsClient client = vertx.createDnsClient(53, "9.9.9.9");
    client
      .lookup("vertx.io")
      .onComplete(ar -> {
        if (ar.succeeded()) {
          System.out.printf("Dns lookup result: %s\n", ar.result());
        } else {
          System.out.println("Failed to dns lookup entry" + ar.cause());
        }
      });
  }

  public void example3(Vertx vertx) {
    DnsClient client = vertx.createDnsClient(53, "9.9.9.9");
    client
      .lookup4("vertx.io")
      .onComplete(ar -> {
        if (ar.succeeded()) {
          System.out.printf("Dns lookup4 result: %s\n", ar.result());
        } else {
          System.out.println("Failed to dns lookup4 entry" + ar.cause());
        }
      });
  }

  public void example4(Vertx vertx) {
    DnsClient client = vertx.createDnsClient(53, "9.9.9.9");
    client
      .lookup6("vertx.io")
      .onComplete(ar -> {
        if (ar.succeeded()) {
          System.out.printf("Dns lookup6 result: %s\n", ar.result());
        } else {
          System.out.println("Failed to dns lookup6 entry" + ar.cause());
        }
      });
  }

  public void example5(Vertx vertx) {
    DnsClient client = vertx.createDnsClient(53, "9.9.9.9");
    client
      .resolveA("vertx.io")
      .onComplete(ar -> {
        if (ar.succeeded()) {
          List<String> records = ar.result();
          for (int i = 0; i < records.size(); i++) {
            System.out.printf("Dns resolveA (%s): %s\n", i, records.get(i));
          }
        } else {
          System.out.println("Failed to dns resolveA entry" + ar.cause());
        }
      });
  }

  public void example6(Vertx vertx) {
    DnsClient client = vertx.createDnsClient(53, "9.9.9.9");
    client
      .resolveAAAA("vertx.io")
      .onComplete(ar -> {
        if (ar.succeeded()) {
          List<String> records = ar.result();
          for (int i = 0; i < records.size(); i++) {
            System.out.printf("Dns resolveAAAA (%s): %s\n", i, records.get(i));
          }
        } else {
          System.out.println("Failed to dns resolveAAAA entry" + ar.cause());
        }
      });
  }

  public void example7(Vertx vertx) {
    DnsClient client = vertx.createDnsClient(53, "9.9.9.9");
    client
      .resolveCNAME("vertx.io")
      .onComplete(ar -> {
        if (ar.succeeded()) {
          List<String> records = ar.result();
          for (int i = 0; i < records.size(); i++) {
            System.out.printf("Dns resolveCNAME (%s): %s\n", i, records.get(i));
          }
        } else {
          System.out.println("Failed to dns resolveCNAME entry" + ar.cause());
        }
      });
  }

  public void example8(Vertx vertx) {
    DnsClient client = vertx.createDnsClient(53, "9.9.9.9");
    client
      .resolveMX("vertx.io")
      .onComplete(ar -> {
        if (ar.succeeded()) {
          List<MxRecord> records = ar.result();
          for (int i = 0; i < records.size(); i++) {
            System.out.printf("Dns resolveMX (%s): %s\n", i, records.get(i));
          }
        } else {
          System.out.println("Failed to dns resolveMX entry" + ar.cause());
        }
      });
  }

  public void example9(MxRecord record) {
    record.priority();
    record.name();
  }

  public void example10(Vertx vertx) {
    DnsClient client = vertx.createDnsClient(53, "9.9.9.9");
    client
      .resolveTXT("vertx.io")
      .onComplete(ar -> {
        if (ar.succeeded()) {
          List<String> records = ar.result();
          for (int i = 0; i < records.size(); i++) {
            System.out.printf("Dns resolveTXT (%s): %s\n", i, records.get(i));
          }
        } else {
          System.out.println("Failed to dns resolveTXT entry" + ar.cause());
        }
      });
  }

  public void example11(Vertx vertx) {
    DnsClient client = vertx.createDnsClient(53, "9.9.9.9");
    client
      .resolveNS("vertx.io")
      .onComplete(ar -> {
        if (ar.succeeded()) {
          List<String> records = ar.result();
          for (int i = 0; i < records.size(); i++) {
            System.out.printf("Dns resolveNS (%s): %s\n", i, records.get(i));
          }
        } else {
          System.out.println("Failed to dns resolveNS entry" + ar.cause());
        }
      });
  }

  public void example12(Vertx vertx) {
    DnsClient client = vertx.createDnsClient(53, "9.9.9.9");
    client
      .resolveSRV("vertx.io")
      .onComplete(ar -> {
        if (ar.succeeded()) {
          List<SrvRecord> records = ar.result();
          for (int i = 0; i < records.size(); i++) {
            System.out.printf("Dns resolveSRV (%s): %s\n", i, records.get(i));
          }
        } else {
          System.out.println("Failed to dns resolveSRV entry" + ar.cause());
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
    client
      .resolvePTR("1.0.0.10.in-addr.arpa")
      .onComplete(ar -> {
        if (ar.succeeded()) {
          String record = ar.result();
          System.out.printf("Dns resolvePTR result: %s\n", record);
        } else {
          System.out.println("Failed to dns resolvePTR entry" + ar.cause());
        }
      });
  }

  public void example15(Vertx vertx) {
    DnsClient client = vertx.createDnsClient(53, "9.9.9.9");
    client
      .reverseLookup("10.0.0.1")
      .onComplete(ar -> {
        if (ar.succeeded()) {
          String record = ar.result();
          System.out.printf("Dns reverseLookup result: %s\n", record);
        } else {
          System.out.println("Failed to dns reverseLookup entry" + ar.cause());
        }
      });
  }

  public void example16(Vertx vertx) {
    DnsClient client = vertx.createDohClient();
    client
      .lookup("vertx.io")
      .onComplete(ar -> {
        if (ar.succeeded()) {
          System.out.printf("DoH lookup result: %s\n", ar.result());
        } else {
          System.out.println("Failed to DoH lookup entry" + ar.cause());
        }
      });
  }

  public void example17(Vertx vertx) {
    DnsClient client = vertx.createDohClient();
    client
      .lookup4("vertx.io")
      .onComplete(ar -> {
        if (ar.succeeded()) {
          System.out.printf("DoH lookup4 result: %s\n", ar.result());
        } else {
          System.out.println("Failed to DoH lookup4 entry" + ar.cause());
        }
      });
  }

  public void example18(Vertx vertx) {
    DnsClient client = vertx.createDohClient();
    client
      .lookup6("google.com")
      .onComplete(ar -> {
        if (ar.succeeded()) {
          System.out.printf("DoH lookup6 result: %s\n", ar.result());
        } else {
          System.out.println("Failed to DoH lookup6 entry" + ar.cause());
        }
      });
  }
  public void example19(Vertx vertx) {
    DnsClient client = vertx.createDohClient();
    client
      .resolveA("vertx.io")
      .onComplete(ar -> {
        if (ar.succeeded()) {
          List<String> records = ar.result();
          for (int i = 0; i < records.size(); i++) {
            System.out.printf("DoH resolveA (%s): %s\n", i, records.get(i));}
        } else {
          System.out.println("Failed to DoH resolveA entry" + ar.cause());
        }
      });
  }

  public void example20(Vertx vertx) {
    DnsClient client = vertx.createDohClient();
    client
      .resolveAAAA("vertx.io")
      .onComplete(ar -> {
        if (ar.succeeded()) {
          List<String> records = ar.result();
          for (int i = 0; i < records.size(); i++) {
            System.out.printf("DoH resolveAAAA (%s): %s\n", i, records.get(i));
          }
        } else {
          System.out.println("Failed to DoH resolveAAAA entry" + ar.cause());
        }
      });
  }

  public void example21(Vertx vertx) {
    DnsClient client = vertx.createDohClient();
    client
      .resolveCNAME("vertx.io")
      .onComplete(ar -> {
        if (ar.succeeded()) {
          List<String> records = ar.result();
          for (int i = 0; i < records.size(); i++) {
            System.out.printf("DoH resolveCNAME (%s): %s\n", i, records.get(i));
          }
        } else {
          System.out.println("Failed to DoH resolveCNAME entry" + ar.cause());
        }
      });
  }

  public void example22(Vertx vertx) {
    DnsClient client = vertx.createDohClient();
    client
      .resolveMX("vertx.io")
      .onComplete(ar -> {
        if (ar.succeeded()) {
          List<MxRecord> records = ar.result();
          for (int i = 0; i < records.size(); i++) {
            System.out.printf("DoH resolveMX (%s): %s\n", i, records.get(i));
          }
        } else {
          System.out.println("Failed to DoH resolveMX entry" + ar.cause());
        }
      });
  }

  public void example23(Vertx vertx) {
    DnsClient client = vertx.createDohClient();
    client
      .resolveTXT("vertx.io")
      .onComplete(ar -> {
        if (ar.succeeded()) {
          List<String> records = ar.result();
          for (int i = 0; i < records.size(); i++) {
            System.out.printf("DoH resolveTXT (%s): %s\n", i, records.get(i));
          }
        } else {
          System.out.println("Failed to DoH resolveTXT entry" + ar.cause());
        }
      });
  }

  public void example24(Vertx vertx) {
    DnsClient client = vertx.createDohClient();
    client
      .resolveNS("vertx.io")
      .onComplete(ar -> {
        if (ar.succeeded()) {
          List<String> records = ar.result();
          for (int i = 0; i < records.size(); i++) {
            System.out.printf("DoH resolveNS (%s): %s\n", i, records.get(i));
          }
        } else {
          System.out.println("Failed to DoH resolveNS entry" + ar.cause());
        }
      });
  }

  public void example25(Vertx vertx) {
    DnsClient client = vertx.createDohClient();
    client
      .resolveSRV("vertx.io")
      .onComplete(ar -> {
        if (ar.succeeded()) {
          List<SrvRecord> records = ar.result();
          for (int i = 0; i < records.size(); i++) {
            System.out.printf("DoH resolveSRV (%s): %s\n", i, records.get(i));
          }
        } else {
          System.out.println("Failed to DoH resolveSRV entry" + ar.cause());
        }
      });
  }

  public void example26(Vertx vertx) {
    DnsClient client = vertx.createDohClient();
    client
      .resolvePTR("1.0.0.10.in-addr.arpa")
      .onComplete(ar -> {
        if (ar.succeeded()) {
          String record = ar.result();
          System.out.printf("DoH resolvePTR result: %s\n", record);
        } else {
          System.out.println("Failed to DoH resolvePTR entry" + ar.cause());
        }
      });
  }

  public void example27(Vertx vertx) {
    DnsClient client = vertx.createDohClient();
    client
      .reverseLookup("10.0.0.1")
      .onComplete(ar -> {
        if (ar.succeeded()) {
          String record = ar.result();
          System.out.printf("DoH reverseLookup result: %s\n", record);
        } else {
          System.out.println("Failed to DoH reverseLookup entry" + ar.cause());
        }
      });
  }

  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();

    DNSExamples dnsExamples = new DNSExamples();

    dnsExamples.example1(vertx);
    dnsExamples.example1_(vertx);
    dnsExamples.example1__(vertx);
    dnsExamples.example2(vertx);
    dnsExamples.example3(vertx);
    dnsExamples.example4(vertx);
    dnsExamples.example5(vertx);
    dnsExamples.example6(vertx);
    dnsExamples.example7(vertx);
    dnsExamples.example8(vertx);
    dnsExamples.example10(vertx);
    dnsExamples.example11(vertx);
    dnsExamples.example12(vertx);
    dnsExamples.example14(vertx);
    dnsExamples.example15(vertx);


    dnsExamples.example16(vertx);
    dnsExamples.example17(vertx);
    dnsExamples.example18(vertx);
    dnsExamples.example19(vertx);
    dnsExamples.example20(vertx);
    dnsExamples.example21(vertx);
    dnsExamples.example22(vertx);
    dnsExamples.example23(vertx);
    dnsExamples.example24(vertx);
    dnsExamples.example25(vertx);
    dnsExamples.example26(vertx);
    dnsExamples.example27(vertx);
//
  }
}
