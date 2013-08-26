package vertx.tests.core.dns;

import org.apache.directory.server.dns.DnsServer;
import org.apache.directory.server.dns.io.encoder.DnsMessageEncoder;
import org.apache.directory.server.dns.io.encoder.ResourceRecordEncoder;
import org.apache.directory.server.dns.messages.*;
import org.apache.directory.server.dns.protocol.DnsProtocolHandler;
import org.apache.directory.server.dns.protocol.DnsUdpDecoder;
import org.apache.directory.server.dns.protocol.DnsUdpEncoder;
import org.apache.directory.server.dns.store.DnsAttribute;
import org.apache.directory.server.dns.store.RecordStore;
import org.apache.directory.server.protocol.shared.transport.UdpTransport;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.*;
import org.apache.mina.transport.socket.DatagramAcceptor;
import org.apache.mina.transport.socket.DatagramSessionConfig;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class TestDnsServer extends DnsServer {
  private final RecordStore store;

  private TestDnsServer(RecordStore store) {
    this.store = store;
  }

  public static TestDnsServer testResolveA(final String ipAddress) {
    return new TestDnsServer(new RecordStore() {
      @Override
      public Set<ResourceRecord> getRecords(QuestionRecord questionRecord) throws org.apache.directory.server.dns.DnsException {
        Set<ResourceRecord> set = new HashSet<>();

        ResourceRecordModifier rm = new ResourceRecordModifier();
        rm.setDnsClass(RecordClass.IN);
        rm.setDnsName("dns.vertx.io");
        rm.setDnsTtl(100);
        rm.setDnsType(RecordType.A);
        rm.put(DnsAttribute.IP_ADDRESS, ipAddress);
        set.add(rm.getEntry());
        return set;
      }
    });
  }

  public static TestDnsServer testResolveAAAA(final String ipAddress) {
    return new TestDnsServer(new RecordStore() {
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

  public static TestDnsServer testResolveMX(final int prio, final String mxRecord) {
    return new TestDnsServer(new RecordStore() {
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

  public static TestDnsServer testResolveTXT(final String txt) {
    return new TestDnsServer(new RecordStore() {
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

  public static TestDnsServer testResolveNS(final String ns) {
    return new TestDnsServer(new RecordStore() {
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

  public static TestDnsServer testResolveCNAME(final String cname) {
    return new TestDnsServer(new RecordStore() {
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

  public static TestDnsServer testResolvePTR(final String ptr) {
    return new TestDnsServer(new RecordStore() {
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

  public static TestDnsServer testResolveSRV(final int priority, final int weight, final int port, final String target) {
     return new TestDnsServer(new RecordStore() {
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

  public static TestDnsServer testLookup4(final String ip) {
    return new TestDnsServer(new RecordStore() {
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

  public static TestDnsServer testLookup6() {
    return new TestDnsServer(new RecordStore() {
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

  public static TestDnsServer testLookup(final String ip) {
    return testLookup4(ip);
  }

  public static TestDnsServer testLookupNonExisting() {
    return new TestDnsServer(new RecordStore() {
      @Override
      public Set<ResourceRecord> getRecords(QuestionRecord questionRecord) throws org.apache.directory.server.dns.DnsException {
        return null;
      }
    });
  }

  public static TestDnsServer testReverseLookup(final String ptr) {
    return new TestDnsServer(new RecordStore() {
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

  @Override
  public void start() throws IOException {
    UdpTransport transport = new UdpTransport("127.0.0.1", 53530);
    setTransports( transport );

    DatagramAcceptor acceptor = transport.getAcceptor();

    acceptor.setHandler(new DnsProtocolHandler(this, store) {
      @Override
      public void sessionCreated( IoSession session ) throws Exception {
        // USe our own codec to support AAAA testing
        session.getFilterChain().addFirst( "codec",
                new ProtocolCodecFilter(new TestDnsProtocolUdpCodecFactory()));
      }
    });

    // Allow the port to be reused even if the socket is in TIME_WAIT state
    ((DatagramSessionConfig)acceptor.getSessionConfig()).setReuseAddress(true);

    // Start the listener
    acceptor.bind();
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
