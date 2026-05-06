package io.vertx.test.fakedns;

import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.util.internal.PlatformDependent;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class DnsServer implements TestRule {

  private static final ThreadLocal<Boolean> STARTED = new ThreadLocal<>();

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
    return STARTED.get() == Boolean.TRUE;
  }

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

  private static class Lifecycle extends Statement {

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
      STARTED.set(true);
      try {
        base.evaluate();
      } finally {
        STARTED.remove();
        failures = server.stop();
      }
      if (failures.size() > 0) {
        PlatformDependent.throwException(failures.get(0));
      }
    }
  }

}
