/*
 * Copyright 2014 Red Hat, Inc.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.test.fakedns;

import org.apache.directory.server.dns.DnsServer;
import org.apache.directory.server.dns.io.encoder.DnsMessageEncoder;
import org.apache.directory.server.dns.io.encoder.ResourceRecordEncoder;
import org.apache.directory.server.dns.messages.DnsMessage;
import org.apache.directory.server.dns.messages.QuestionRecord;
import org.apache.directory.server.dns.messages.RecordClass;
import org.apache.directory.server.dns.messages.RecordType;
import org.apache.directory.server.dns.messages.ResourceRecord;
import org.apache.directory.server.dns.messages.ResourceRecordModifier;
import org.apache.directory.server.dns.protocol.DnsProtocolHandler;
import org.apache.directory.server.dns.protocol.DnsUdpDecoder;
import org.apache.directory.server.dns.protocol.DnsUdpEncoder;
import org.apache.directory.server.dns.store.DnsAttribute;
import org.apache.directory.server.dns.store.RecordStore;
import org.apache.directory.server.protocol.shared.transport.UdpTransport;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFactory;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.ProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolEncoder;
import org.apache.mina.filter.codec.ProtocolEncoderOutput;
import org.apache.mina.transport.socket.DatagramAcceptor;
import org.apache.mina.transport.socket.DatagramSessionConfig;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class FakeDNSServer extends DnsServer {

  public static RecordStore A_store(Map<String, String> entries) {
    return questionRecord -> entries.entrySet().stream().map(entry -> {
      ResourceRecordModifier rm = new ResourceRecordModifier();
      rm.setDnsClass(RecordClass.IN);
      rm.setDnsName(entry.getKey());
      rm.setDnsTtl(100);
      rm.setDnsType(RecordType.A);
      rm.put(DnsAttribute.IP_ADDRESS, entry.getValue());
      return rm.getEntry();
    }).collect(Collectors.toSet());
  }

  public static final int PORT = 53530;

  private int port = PORT;
  private final RecordStore store;
  private DatagramAcceptor acceptor;

  public FakeDNSServer(RecordStore store) {
    this.store = store;
  }

  public InetSocketAddress localAddress() {
    return (InetSocketAddress) getTransports()[0].getAcceptor().getLocalAddress();
  }

  public FakeDNSServer port(int p) {
    port = p;
    return this;
  }

  public static FakeDNSServer testResolveA(final String ipAddress) {
    return testResolveA(Collections.singletonMap("dns.vertx.io", ipAddress));
  }

  public static FakeDNSServer testResolveA(Map<String, String> entries) {
    return new FakeDNSServer(A_store(entries));
  }

  public static FakeDNSServer testResolveAAAA(final String ipAddress) {
    return new FakeDNSServer(new RecordStore() {
      @Override
      public Set<ResourceRecord> getRecords(QuestionRecord questionRecord) throws org.apache.directory.server.dns.DnsException {
        Set<ResourceRecord> set = new HashSet<>();

        ResourceRecordModifier rm = new ResourceRecordModifier();
        rm.setDnsClass(RecordClass.IN);
        rm.setDnsName("dns.vertx.io");
        rm.setDnsTtl(100);
        rm.setDnsType(RecordType.AAAA);
        rm.put(DnsAttribute.IP_ADDRESS, ipAddress);

        set.add(rm.getEntry());
        return set;
      }
    });
  }

  public static FakeDNSServer testResolveMX(final int prio, final String mxRecord) {
    return new FakeDNSServer(new RecordStore() {
      @Override
      public Set<ResourceRecord> getRecords(QuestionRecord questionRecord) throws org.apache.directory.server.dns.DnsException {
        Set<ResourceRecord> set = new HashSet<>();

        ResourceRecordModifier rm = new ResourceRecordModifier();
        rm.setDnsClass(RecordClass.IN);
        rm.setDnsName("dns.vertx.io");
        rm.setDnsTtl(100);
        rm.setDnsType(RecordType.MX);
        rm.put(DnsAttribute.MX_PREFERENCE, String.valueOf(prio));
        rm.put(DnsAttribute.DOMAIN_NAME, mxRecord);
        set.add(rm.getEntry());
        return set;
      }
    });
  }

  public static FakeDNSServer testResolveTXT(final String txt) {
    return new FakeDNSServer(new RecordStore() {
      @Override
      public Set<ResourceRecord> getRecords(QuestionRecord questionRecord) throws org.apache.directory.server.dns.DnsException {
        Set<ResourceRecord> set = new HashSet<>();

        ResourceRecordModifier rm = new ResourceRecordModifier();
        rm.setDnsClass(RecordClass.IN);
        rm.setDnsName("dns.vertx.io");
        rm.setDnsTtl(100);
        rm.setDnsType(RecordType.TXT);
        rm.put(DnsAttribute.CHARACTER_STRING, txt);
        set.add(rm.getEntry());
        return set;
      }
    });
  }

  public static FakeDNSServer testResolveNS(final String ns) {
    return new FakeDNSServer(new RecordStore() {
      @Override
      public Set<ResourceRecord> getRecords(QuestionRecord questionRecord) throws org.apache.directory.server.dns.DnsException {
        Set<ResourceRecord> set = new HashSet<>();

        ResourceRecordModifier rm = new ResourceRecordModifier();
        rm.setDnsClass(RecordClass.IN);
        rm.setDnsName("dns.vertx.io");
        rm.setDnsTtl(100);
        rm.setDnsType(RecordType.NS);
        rm.put(DnsAttribute.DOMAIN_NAME, ns);
        set.add(rm.getEntry());
        return set;
      }
    });
  }

  public static FakeDNSServer testResolveCNAME(final String cname) {
    return new FakeDNSServer(new RecordStore() {
      @Override
      public Set<ResourceRecord> getRecords(QuestionRecord questionRecord) throws org.apache.directory.server.dns.DnsException {
        Set<ResourceRecord> set = new HashSet<>();

        ResourceRecordModifier rm = new ResourceRecordModifier();
        rm.setDnsClass(RecordClass.IN);
        rm.setDnsName("dns.vertx.io");
        rm.setDnsTtl(100);
        rm.setDnsType(RecordType.CNAME);
        rm.put(DnsAttribute.DOMAIN_NAME, cname);
        set.add(rm.getEntry());
        return set;
      }
    });
  }

  public static FakeDNSServer testResolvePTR(final String ptr) {
    return new FakeDNSServer(new RecordStore() {
      @Override
      public Set<ResourceRecord> getRecords(QuestionRecord questionRecord) throws org.apache.directory.server.dns.DnsException {
        Set<ResourceRecord> set = new HashSet<>();

        ResourceRecordModifier rm = new ResourceRecordModifier();
        rm.setDnsClass(RecordClass.IN);
        rm.setDnsName("dns.vertx.io");
        rm.setDnsTtl(100);
        rm.setDnsType(RecordType.PTR);
        rm.put(DnsAttribute.DOMAIN_NAME, ptr);
        set.add(rm.getEntry());
        return set;
      }
    });
  }

  public static FakeDNSServer testResolveSRV(final int priority, final int weight, final int port, final String target) {
    return new FakeDNSServer(new RecordStore() {
      @Override
      public Set<ResourceRecord> getRecords(QuestionRecord questionRecord) throws org.apache.directory.server.dns.DnsException {
        Set<ResourceRecord> set = new HashSet<>();

        ResourceRecordModifier rm = new ResourceRecordModifier();
        rm.setDnsClass(RecordClass.IN);
        rm.setDnsName("dns.vertx.io");
        rm.setDnsTtl(100);
        rm.setDnsType(RecordType.SRV);
        rm.put(DnsAttribute.SERVICE_PRIORITY, String.valueOf(priority));
        rm.put(DnsAttribute.SERVICE_WEIGHT, String.valueOf(weight));
        rm.put(DnsAttribute.SERVICE_PORT, String.valueOf(port));
        rm.put(DnsAttribute.DOMAIN_NAME, target);
        set.add(rm.getEntry());
        return set;
      }
    });
  }

  public static FakeDNSServer testLookup4(final String ip) {
    return new FakeDNSServer(new RecordStore() {
      @Override
      public Set<ResourceRecord> getRecords(QuestionRecord questionRecord) throws org.apache.directory.server.dns.DnsException {
        Set<ResourceRecord> set = new HashSet<>();

        ResourceRecordModifier rm = new ResourceRecordModifier();
        rm.setDnsClass(RecordClass.IN);
        rm.setDnsName("dns.vertx.io");
        rm.setDnsTtl(100);
        rm.setDnsType(RecordType.A);
        rm.put(DnsAttribute.IP_ADDRESS, ip);

        set.add(rm.getEntry());
        return set;
      }
    });
  }

  public static FakeDNSServer testLookup6() {
    return new FakeDNSServer(new RecordStore() {
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
  }

  public static FakeDNSServer testLookup(final String ip) {
    return testLookup4(ip);
  }

  public static FakeDNSServer testLookupNonExisting() {
    return new FakeDNSServer(new RecordStore() {
      @Override
      public Set<ResourceRecord> getRecords(QuestionRecord questionRecord) throws org.apache.directory.server.dns.DnsException {
        return null;
      }
    });
  }

  public static FakeDNSServer testReverseLookup(final String ptr) {
    return new FakeDNSServer(new RecordStore() {
      @Override
      public Set<ResourceRecord> getRecords(QuestionRecord questionRecord) throws org.apache.directory.server.dns.DnsException {
        Set<ResourceRecord> set = new HashSet<>();

        ResourceRecordModifier rm = new ResourceRecordModifier();
        rm.setDnsClass(RecordClass.IN);
        rm.setDnsName("dns.vertx.io");
        rm.setDnsTtl(100);
        rm.setDnsType(RecordType.PTR);
        rm.put(DnsAttribute.DOMAIN_NAME, ptr);
        set.add(rm.getEntry());
        return set;
      }
    });
  }

  public static FakeDNSServer testResolveASameServer(final String ipAddress) {
    return new FakeDNSServer(A_store(Collections.singletonMap("vertx.io", ipAddress)));
  }

  public static FakeDNSServer testLookup4CNAME(final String cname, final String ip) {
    return new FakeDNSServer(new RecordStore() {
      @Override
      public Set<ResourceRecord> getRecords(QuestionRecord questionRecord)
          throws org.apache.directory.server.dns.DnsException {
        // use LinkedHashSet since the order of the result records has to be preserved to make sure the unit test fails
        Set<ResourceRecord> set = new LinkedHashSet<>();

        ResourceRecordModifier rm = new ResourceRecordModifier();
        rm.setDnsClass(RecordClass.IN);
        rm.setDnsName("vertx.io");
        rm.setDnsTtl(100);
        rm.setDnsType(RecordType.CNAME);
        rm.put(DnsAttribute.DOMAIN_NAME, cname);
        set.add(rm.getEntry());

        ResourceRecordModifier rm2 = new ResourceRecordModifier();
        rm2.setDnsClass(RecordClass.IN);
        rm2.setDnsName(cname);
        rm2.setDnsTtl(100);
        rm2.setDnsType(RecordType.A);
        rm2.put(DnsAttribute.IP_ADDRESS, ip);
        set.add(rm2.getEntry());

        return set;
      }
    });
  }

  @Override
  public void start() throws IOException {
    UdpTransport transport = new UdpTransport("127.0.0.1", port);
    setTransports( transport );

    acceptor = transport.getAcceptor();

    acceptor.setHandler(new DnsProtocolHandler(this, store) {
      @Override
      public void sessionCreated(IoSession session) throws Exception {
        // Use our own codec to support AAAA testing
        session.getFilterChain().addFirst("codec",
          new ProtocolCodecFilter(new TestDnsProtocolUdpCodecFactory()));
      }
    });

    // Allow the port to be reused even if the socket is in TIME_WAIT state
    ((DatagramSessionConfig) acceptor.getSessionConfig()).setReuseAddress(true);

    // Start the listener
    acceptor.bind();
  }


  @Override
  public void stop() {
    acceptor.dispose();
  }


  /**
   * ProtocolCodecFactory which allows to test AAAA resolution
   */
  private final class TestDnsProtocolUdpCodecFactory implements ProtocolCodecFactory {
    private DnsMessageEncoder encoder = new DnsMessageEncoder();
    private TestAAAARecordEncoder recordEncoder = new TestAAAARecordEncoder();

    @Override
    public ProtocolEncoder getEncoder(IoSession session) throws Exception {
      return new DnsUdpEncoder() {

        @Override
        public void encode(IoSession session, Object message, ProtocolEncoderOutput out) {
          IoBuffer buf = IoBuffer.allocate( 1024 );
          DnsMessage dnsMessage = (DnsMessage) message;
          encoder.encode(buf, dnsMessage);
          for (ResourceRecord record: dnsMessage.getAnswerRecords()) {
            // This is a hack to allow to also test for AAAA resolution as DnsMessageEncoder does not support it and it
            // is hard to extend, because the interesting methods are private...
            // In case of RecordType.AAAA we need to encode the RecordType by ourself
            if (record.getRecordType() == RecordType.AAAA) {
              try {
                recordEncoder.put(buf, record);
              } catch (IOException e) {
                // Should never happen
                throw new IllegalStateException(e);
              }
            }
          }
          buf.flip();

          out.write( buf );
        }
      };
    }

    @Override
    public ProtocolDecoder getDecoder(IoSession session) throws Exception {
      return new DnsUdpDecoder();
    }

    private final class TestAAAARecordEncoder extends ResourceRecordEncoder {
      @Override
      protected void putResourceRecordData(IoBuffer ioBuffer, ResourceRecord resourceRecord) {
        if (!resourceRecord.get(DnsAttribute.IP_ADDRESS).equals("::1")) {
          throw new IllegalStateException("Only supposed to be used with IPV6 address of ::1");
        }
        // encode the ::1
        ioBuffer.put(new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1});
      }
    }
  }
}
