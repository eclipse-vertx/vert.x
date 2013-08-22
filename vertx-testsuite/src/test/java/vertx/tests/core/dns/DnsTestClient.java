/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package vertx.tests.core.dns;


import org.apache.directory.server.dns.DnsServer;
import org.apache.directory.server.dns.messages.*;
import org.apache.directory.server.dns.protocol.DnsProtocolHandler;
import org.apache.directory.server.dns.store.DnsAttribute;
import org.apache.directory.server.dns.store.RecordStore;
import org.apache.directory.server.protocol.shared.transport.UdpTransport;
import org.apache.mina.transport.socket.DatagramAcceptor;
import org.apache.mina.transport.socket.DatagramSessionConfig;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.dns.*;
import org.vertx.java.core.dns.impl.netty.decoder.record.ServiceRecord;
import org.vertx.java.testframework.TestClientBase;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class DnsTestClient extends TestClientBase {
  private TestDnsServer dnsServer;
  @Override
  public void start() {
    super.start();
    tu.appReady();
  }

  @Override
  public void stop() {
    super.stop();
    if (dnsServer != null) {
      dnsServer.stop();
    }
  }

  public void testResolveA() throws Exception {
    DnsClient dns = prepareDns(new RecordStore() {
      @Override
      public Set<ResourceRecord> getRecords(QuestionRecord questionRecord) throws org.apache.directory.server.dns.DnsException {
        Set<ResourceRecord> set = new HashSet<>();

        ResourceRecordModifier rm = new ResourceRecordModifier();
        rm.setDnsClass(RecordClass.IN);
        rm.setDnsName("dns.vertx.io");
        rm.setDnsTtl(100);
        rm.setDnsType(RecordType.A);
        rm.put(DnsAttribute.IP_ADDRESS, "10.0.0.1");
        set.add(rm.getEntry());
        return set;
      }
    });

    dns.resolveA("vertx.io", new Handler<AsyncResult<List<Inet4Address>>>() {
      @Override
      public void handle(AsyncResult<List<Inet4Address>> event) {
        List<Inet4Address> result = event.result();

        tu.azzert(!result.isEmpty());
        tu.azzert(result.size() == 1);
        tu.azzert("10.0.0.1".equals(result.get(0).getHostAddress()));
        System.out.println(result);
        tu.testComplete();
      }
    });

  }

  public void testResolveAAAA() throws Exception {
    // TODO: Patch apacheds to support AAAA records
    tu.testComplete();
    /*
    DnsClient dns = prepareDns(new RecordStore() {
      @Override
      public Set<ResourceRecord> getRecords(QuestionRecord questionRecord) throws org.apache.directory.server.dns.DnsException {
        Set<ResourceRecord> set = new HashSet<>();

        ResourceRecordModifier rm = new ResourceRecordModifier();
        rm.setDnsClass(RecordClass.IN);
        rm.setDnsName("dns.vertx.io");
        rm.setDnsTtl(100);
        rm.setDnsType(RecordType.AAAA);
        rm.put(DnsAttribute.IP_ADDRESS, "::1");
        set.add(rm.getEntry());
        return set;
      }
    });

    dns.resolveAAAA("vertx.io", new Handler<AsyncResult<List<Inet6Address>>>() {
      @Override
      public void handle(AsyncResult<List<Inet6Address>> event) {
        List<Inet6Address> result = event.result();

        tu.azzert(!result.isEmpty());
        tu.azzert(result.size() == 1);
        tu.azzert("::1".equals(result.get(0).getHostAddress()));
        tu.testComplete();
      }
    });
    */
  }

  public void testResolveMX() throws Exception {
    DnsClient dns = prepareDns(new RecordStore() {
      @Override
      public Set<ResourceRecord> getRecords(QuestionRecord questionRecord) throws org.apache.directory.server.dns.DnsException {
        Set<ResourceRecord> set = new HashSet<>();

        ResourceRecordModifier rm = new ResourceRecordModifier();
        rm.setDnsClass(RecordClass.IN);
        rm.setDnsName("dns.vertx.io");
        rm.setDnsTtl(100);
        rm.setDnsType(RecordType.MX);
        rm.put(DnsAttribute.MX_PREFERENCE, String.valueOf(10));
        rm.put(DnsAttribute.DOMAIN_NAME, "mail.vertx.io");
        set.add(rm.getEntry());
        return set;
      }
    });

    dns.resolveMX("vertx.io", new Handler<AsyncResult<List<MxRecord>>>() {
      @Override
      public void handle(AsyncResult<List<MxRecord>> event) {
        List<MxRecord> result = event.result();
        tu.azzert(!result.isEmpty());
        tu.azzert(1 == result.size());
        MxRecord record = result.get(0);
        tu.azzert(record.priority() == 10);
        tu.azzert("mail.vertx.io".equals(record.name()));
        tu.testComplete();
      }
    });

  }

  public void testResolveTXT() throws Exception {
    DnsClient dns = prepareDns(new RecordStore() {
      @Override
      public Set<ResourceRecord> getRecords(QuestionRecord questionRecord) throws org.apache.directory.server.dns.DnsException {
        Set<ResourceRecord> set = new HashSet<>();

        ResourceRecordModifier rm = new ResourceRecordModifier();
        rm.setDnsClass(RecordClass.IN);
        rm.setDnsName("dns.vertx.io");
        rm.setDnsTtl(100);
        rm.setDnsType(RecordType.TXT);
        rm.put(DnsAttribute.CHARACTER_STRING, "vertx is awesome");
        set.add(rm.getEntry());
        return set;
      }
    });

    dns.resolveTXT("vertx.io", new Handler<AsyncResult<List<String>>>() {
      @Override
      public void handle(AsyncResult<List<String>> event) {
        List<String> result = event.result();
        tu.azzert(!result.isEmpty());
        tu.azzert(result.size() == 1);
        tu.azzert("vertx is awesome".equals(result.get(0)));
        tu.testComplete();
      }
    });

  }

  public void testResolveNS() throws Exception {
    DnsClient dns = prepareDns(new RecordStore() {
      @Override
      public Set<ResourceRecord> getRecords(QuestionRecord questionRecord) throws org.apache.directory.server.dns.DnsException {
        Set<ResourceRecord> set = new HashSet<>();

        ResourceRecordModifier rm = new ResourceRecordModifier();
        rm.setDnsClass(RecordClass.IN);
        rm.setDnsName("dns.vertx.io");
        rm.setDnsTtl(100);
        rm.setDnsType(RecordType.NS);
        rm.put(DnsAttribute.DOMAIN_NAME, "ns.vertx.io");
        set.add(rm.getEntry());
        return set;
      }
    });

    dns.resolveNS("vertx.io", new Handler<AsyncResult<List<String>>>() {
      @Override
      public void handle(AsyncResult<List<String>> event) {
        List<String> result = event.result();
        tu.azzert(!result.isEmpty());
        tu.azzert(result.size() == 1);
        tu.azzert("ns.vertx.io".equals(result.get(0)));
        tu.testComplete();
      }
    });

  }

  public void testResolveCNAME() throws Exception {
    DnsClient dns = prepareDns(new RecordStore() {
      @Override
      public Set<ResourceRecord> getRecords(QuestionRecord questionRecord) throws org.apache.directory.server.dns.DnsException {
        Set<ResourceRecord> set = new HashSet<>();

        ResourceRecordModifier rm = new ResourceRecordModifier();
        rm.setDnsClass(RecordClass.IN);
        rm.setDnsName("dns.vertx.io");
        rm.setDnsTtl(100);
        rm.setDnsType(RecordType.CNAME);
        rm.put(DnsAttribute.DOMAIN_NAME, "cname.vertx.io");
        set.add(rm.getEntry());
        return set;
      }
    });

    dns.resolveCNAME("vertx.io", new Handler<AsyncResult<List<String>>>() {
      @Override
      public void handle(AsyncResult<List<String>> event) {
        List<String> result = event.result();
        tu.azzert(!result.isEmpty());
        tu.azzert(result.size() == 1);

        String record = result.get(0);

        tu.azzert(!record.isEmpty());
        tu.azzert("cname.vertx.io".equals(record));
        tu.testComplete();
      }
    });
  }

  public void testResolveSRV() throws Exception {
    DnsClient dns = prepareDns(new RecordStore() {
      @Override
      public Set<ResourceRecord> getRecords(QuestionRecord questionRecord) throws org.apache.directory.server.dns.DnsException {
        Set<ResourceRecord> set = new HashSet<>();

        ResourceRecordModifier rm = new ResourceRecordModifier();
        rm.setDnsClass(RecordClass.IN);
        rm.setDnsName("dns.vertx.io");
        rm.setDnsTtl(100);
        rm.setDnsType(RecordType.SRV);
        rm.put(DnsAttribute.SERVICE_PRIORITY, "10");
        rm.put(DnsAttribute.SERVICE_WEIGHT, "1");
        rm.put(DnsAttribute.SERVICE_PORT, "80");
        rm.put(DnsAttribute.DOMAIN_NAME, "vertx.io");
        set.add(rm.getEntry());
        return set;
      }
    });

    dns.resolveSRV("vertx.io", new Handler<AsyncResult<List<SrvRecord>>>() {
      @Override
      public void handle(AsyncResult<List<SrvRecord>> event) {
        List<SrvRecord> result = event.result();
        tu.azzert(!result.isEmpty());
        tu.azzert(result.size() == 1);

        SrvRecord record = result.get(0);

        tu.azzert(10 == record.priority());
        tu.azzert(1 == record.weight());
        tu.azzert(80 == record.port());
        tu.azzert("vertx.io".equals(record.target()));

        tu.testComplete();
      }
    });
  }

  public void testLookup4() throws Exception {
    DnsClient dns = prepareDns(new RecordStore() {
      @Override
      public Set<ResourceRecord> getRecords(QuestionRecord questionRecord) throws org.apache.directory.server.dns.DnsException {
        Set<ResourceRecord> set = new HashSet<>();

        ResourceRecordModifier rm = new ResourceRecordModifier();
        rm.setDnsClass(RecordClass.IN);
        rm.setDnsName("dns.vertx.io");
        rm.setDnsTtl(100);
        rm.setDnsType(RecordType.A);
        rm.put(DnsAttribute.IP_ADDRESS, "10.0.0.1");

        set.add(rm.getEntry());
        return set;
      }
    });

    dns.lookup("vertx.io", new Handler<AsyncResult<InetAddress>>() {
      @Override
      public void handle(AsyncResult<InetAddress> event) {
        InetAddress result = event.result();
        tu.azzert(result != null);
        tu.azzert("10.0.0.1".equals(result.getHostAddress()));
        tu.testComplete();
      }
    });
  }

  public void testLookup6() throws Exception {
    tu.testComplete();
    /*
    DnsClient dns = prepareDns(new RecordStore() {
      @Override
      public Set<ResourceRecord> getRecords(QuestionRecord questionRecord) throws org.apache.directory.server.dns.DnsException {
        Set<ResourceRecord> set = new HashSet<>();

        ResourceRecordModifier rm = new ResourceRecordModifier();
        rm.setDnsClass(RecordClass.IN);
        rm.setDnsName("dns.vertx.io");
        rm.setDnsTtl(100);
        rm.setDnsType(RecordType.AAAA);
        rm.put(DnsAttribute.IP_ADDRESS, "::1");

        set.add(rm.getEntry());
        return set;
      }
    });

    dns.lookup6("vertx.io", new Handler<AsyncResult<Inet6Address>>() {
      @Override
      public void handle(AsyncResult<Inet6Address> event) {
        System.out.println(event);
        event.cause().printStackTrace();
        Inet6Address result = event.result();
        tu.azzert(result != null);
        tu.azzert("::1".equals(result.getHostAddress()));
        tu.testComplete();
      }
    });
    */
  }

  public void testLookup() throws Exception {
    DnsClient dns = prepareDns(new RecordStore() {
      @Override
      public Set<ResourceRecord> getRecords(QuestionRecord questionRecord) throws org.apache.directory.server.dns.DnsException {
        Set<ResourceRecord> set = new HashSet<>();

        ResourceRecordModifier rm = new ResourceRecordModifier();
        rm.setDnsClass(RecordClass.IN);
        rm.setDnsName("dns.vertx.io");
        rm.setDnsTtl(100);
        rm.setDnsType(RecordType.A);
        rm.put(DnsAttribute.IP_ADDRESS, "10.0.0.1");

        set.add(rm.getEntry());
        return set;
      }
    });

    dns.lookup("vertx.io", new Handler<AsyncResult<InetAddress>>() {
      @Override
      public void handle(AsyncResult<InetAddress> event) {
        InetAddress result = event.result();
        tu.azzert(result != null);
        tu.azzert("10.0.0.1".equals(result.getHostAddress()));
        tu.testComplete();
      }
    });
  }

  public void testLookupNonExisting() throws Exception {
    DnsClient dns = prepareDns(new RecordStore() {
      @Override
      public Set<ResourceRecord> getRecords(QuestionRecord questionRecord) throws org.apache.directory.server.dns.DnsException {
        return null;
      }
    });
    dns.lookup("gfegjegjf.sg1", new Handler<AsyncResult<InetAddress>>() {
      @Override
      public void handle(AsyncResult<InetAddress> event) {
        DnsException cause = (DnsException) event.cause();
        tu.azzert(cause.code() == DnsResponseCode.NXDOMAIN);
        tu.testComplete();
      }
    });
  }

  private DnsClient prepareDns(RecordStore store) throws Exception {
    dnsServer = new TestDnsServer(store);
    dnsServer.start();
    InetSocketAddress addr = (InetSocketAddress) dnsServer.getTransports()[0].getAcceptor().getLocalAddress();
    return vertx.createDnsClient(addr);
  }

  private final class TestDnsServer extends DnsServer {
    private final RecordStore store;

    TestDnsServer(RecordStore store) {
      this.store = store;
    }

    @Override
    public void start() throws IOException {
      UdpTransport transport = new UdpTransport("127.0.0.1", 53530);
      setTransports( transport );

      DatagramAcceptor acceptor = transport.getAcceptor();

      // Set the handler
      acceptor.setHandler( new DnsProtocolHandler(this, store));

      // Allow the port to be reused even if the socket is in TIME_WAIT state
      ((DatagramSessionConfig)acceptor.getSessionConfig()).setReuseAddress( true );

      // Start the listener
      acceptor.bind();
    }
  }
}
