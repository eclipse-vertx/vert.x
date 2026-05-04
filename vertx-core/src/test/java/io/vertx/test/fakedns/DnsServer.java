package io.vertx.test.fakedns;

import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.util.internal.PlatformDependent;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class DnsServer implements TestRule {

  private final int port;

  public DnsServer(int port) {
    this.port = port;
  }

  public DnsServer() {
    this(MockDnsServer.PORT);
  }

  public int port() {
    return port;
  }

  public boolean isStarted() {
    return started;
  }

  public boolean started;

  @Override
  public Statement apply(Statement base, Description description) {
    Statement result = base;
    Hosts repeat = description.getAnnotation(Hosts.class);
    if( repeat != null ) {
      MockDnsServer server = new MockDnsServer();
      server.port(port);
      server.store(new MockDnsServer.RecordStore() {
        @Override
        public Collection<DnsRecord> getRecords(DnsQuestion question) throws UnknownHostException {
          String name = question.name();
          if (name.endsWith(".")) {
            name = name.substring(0, name.length() - 1);
          }
          List<DnsRecord> responses = new ArrayList<>();
          if (question.type() == DnsRecordType.A) {
            for (Host host : repeat.value()) {
              if (host.name().equals(name)) {
                responses.add(MockDnsServer.a(host.name(), host.ttl(), host.address()));
              }
            }
          }
          return responses;
        }
      });
      return new Lifecycle(server, base);
    }
    return result;
  }

  private class Lifecycle extends Statement {

    private final MockDnsServer server;
    private final Statement base;

    Lifecycle(MockDnsServer server, Statement base) {
      this.server = server;
      this.base = base;
    }

    @Override
    public void evaluate() throws Throwable {
      List<Throwable> failures;
      server.start();
      boolean started = true;
      try {
        base.evaluate();
      } finally {
        started = false;
        failures = server.stop();
      }
      if (failures.size() > 0) {
        PlatformDependent.throwException(failures.get(0));
      }
    }
  }

}
