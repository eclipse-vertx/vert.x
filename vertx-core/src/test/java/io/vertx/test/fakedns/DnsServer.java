package io.vertx.test.fakedns;

import io.netty.handler.codec.dns.DnsQuestion;
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
    WithDnsServer config = description.getAnnotation(WithDnsServer.class);
    if( config != null ) {
      MockDnsServer server = new MockDnsServer();
      server.port(config.port());
      server.store(new MockDnsServer.RecordStore() {
        @Override
        public Collection<io.netty.handler.codec.dns.DnsRecord> getRecords(DnsQuestion question) {
          String name = question.name();
          if (name.endsWith(".")) {
            name = name.substring(0, name.length() - 1);
          }
          List<io.netty.handler.codec.dns.DnsRecord> responses = new ArrayList<>();
          for (DnsRecord record : config.records()) {
            if (question.type().name().equals(record.type()) && record.name().equals(name)) {
              responses.add(MockDnsServer.a(record.name(), record.ttl(), record.address()));
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
