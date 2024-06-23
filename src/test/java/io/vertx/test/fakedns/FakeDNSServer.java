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

package io.vertx.test.fakedns;

import io.netty.handler.codec.dns.DnsRecordType;
import io.vertx.core.Vertx;
import io.vertx.core.dns.DnsResponseCode;
import io.vertx.core.dns.dnsrecord.DohRecord;
import io.vertx.core.dns.dnsrecord.DohResourceRecord;
import io.vertx.core.http.*;
import io.vertx.test.tls.Trust;
import org.apache.directory.server.dns.DnsException;
import org.apache.directory.server.dns.DnsServer;
import org.apache.directory.server.dns.io.encoder.ResourceRecordEncoder;
import org.apache.directory.server.dns.messages.*;
import org.apache.directory.server.dns.protocol.DnsProtocolHandler;
import org.apache.directory.server.dns.protocol.DnsTcpDecoder;
import org.apache.directory.server.dns.protocol.DnsUdpDecoder;
import org.apache.directory.server.dns.protocol.DnsUdpEncoder;
import org.apache.directory.server.dns.store.DnsAttribute;
import org.apache.directory.server.dns.store.RecordStore;
import org.apache.directory.server.protocol.shared.transport.TcpTransport;
import org.apache.directory.server.protocol.shared.transport.Transport;
import org.apache.directory.server.protocol.shared.transport.UdpTransport;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.service.IoAcceptor;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.*;
import org.apache.mina.transport.socket.DatagramSessionConfig;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.function.Function;
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

  public static RecordStore A_store(Function<String, String> entries) {
    return questionRecord -> {
      String res = entries.apply(questionRecord.getDomainName());
      if (res != null) {
        ResourceRecordModifier rm = new ResourceRecordModifier();
        rm.setDnsClass(RecordClass.IN);
        rm.setDnsName(questionRecord.getDomainName());
        rm.setDnsTtl(100);
        rm.setDnsType(RecordType.A);
        rm.put(DnsAttribute.IP_ADDRESS, res);
        rm.getEntry();
        return Collections.singleton(rm.getEntry());
      }
      return Collections.emptySet();
    };
  }

  public static final int PORT = 53530;
  public static final String IP_ADDRESS = "127.0.0.1";

  private String ipAddress = IP_ADDRESS;
  private int port = PORT;
  private volatile RecordStore store;
  private List<IoAcceptor> acceptors;
  private final Deque<DnsMessage> currentMessage = new ArrayDeque<>();
  private final boolean ssl;
  private Vertx vertx;
  private HttpServer httpServer;

  public FakeDNSServer() {
    this.ssl = false;
  }

  public FakeDNSServer(boolean ssl, Vertx vertx) {
    this.ssl = ssl;
    this.vertx = vertx;
  }

  public RecordStore store() {
    return store;
  }

  public FakeDNSServer store(RecordStore store) {
    this.store = store;
    return this;
  }

  public synchronized DnsMessage pollMessage() {
    return currentMessage.poll();
  }

  public InetSocketAddress localAddress() {
    return (InetSocketAddress) getTransports()[0].getAcceptor().getLocalAddress();
  }

  public FakeDNSServer ipAddress(String ipAddress) {
    this.ipAddress = ipAddress;
    return this;
  }

  public String ipAddress() {
    return this.ipAddress;
  }

  public FakeDNSServer port(int p) {
    port = p;
    return this;
  }

  public int port() {
    return this.port;
  }

  public FakeDNSServer testResolveA(final String ipAddress) {
    return testResolveA(Collections.singletonMap("dns.vertx.io", ipAddress));
  }

  public FakeDNSServer testResolveA(Map<String, String> entries) {
    return store(A_store(entries));
  }

  public FakeDNSServer testResolveA(Function<String, String> entries) {
    return store(A_store(entries));
  }

  public FakeDNSServer testResolveAAAA(final String ipAddress) {
    return store(new RecordStore() {
      @Override
      public Set<ResourceRecord> getRecords(QuestionRecord questionRecord) {
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

  public FakeDNSServer testResolveMX(final int prio, final String mxRecord) {
    return store(new RecordStore() {
      @Override
      public Set<ResourceRecord> getRecords(QuestionRecord questionRecord) {
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

  public FakeDNSServer testResolveTXT(final String txt) {
    return store(new RecordStore() {
      @Override
      public Set<ResourceRecord> getRecords(QuestionRecord questionRecord) {
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

  public FakeDNSServer testResolveNS(final String ns) {
    return store(new RecordStore() {
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

  public FakeDNSServer testResolveCNAME(final String cname) {
    return store(new RecordStore() {
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

  public FakeDNSServer testResolvePTR(final String ptr) {
    return store(new RecordStore() {
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

  public FakeDNSServer testResolveSRV(final int priority, final int weight, final int port, final String target) {
    return store(new RecordStore() {
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

  public FakeDNSServer testResolveDNAME(final String dname) {
    return store(new RecordStore() {
      @Override
      public Set<ResourceRecord> getRecords(QuestionRecord questionRecord) throws org.apache.directory.server.dns.DnsException {
        Set<ResourceRecord> set = new HashSet<>();

        ResourceRecordModifier rm = new ResourceRecordModifier();
        rm.setDnsClass(RecordClass.IN);
        rm.setDnsName("dns.vertx.io");
        rm.setDnsTtl(100);
        rm.setDnsType(RecordType.DNAME);
        rm.put(DnsAttribute.DOMAIN_NAME, dname);
        set.add(rm.getEntry());
        return set;
      }
    });
  }

  public FakeDNSServer testLookup4(final String ip) {
    return store(new RecordStore() {
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

  public FakeDNSServer testLookup6() {
    return store(new RecordStore() {
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

  public FakeDNSServer testLookup(final String ip) {
    return testLookup4(ip);
  }

  public FakeDNSServer testLookupNonExisting() {
    return store(new RecordStore() {
      @Override
      public Set<ResourceRecord> getRecords(QuestionRecord questionRecord) throws org.apache.directory.server.dns.DnsException {
        return null;
      }
    });
  }

  public FakeDNSServer testReverseLookup(final String ptr) {
    return store(new RecordStore() {
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

  public FakeDNSServer testResolveASameServer(final String ipAddress) {
    return store(A_store(Collections.singletonMap("vertx.io", ipAddress)));
  }

  public FakeDNSServer testLookup4CNAME(final String cname, final String ip) {
    return store(new RecordStore() {
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
    if (this.ssl) {
      HttpServerOptions options = new HttpServerOptions()
        .setSsl(true)
        .setPort(port)
        .setHost(ipAddress)
        .setKeyCertOptions(Trust.DOH_JKS_HOST.get());

      httpServer = vertx.createHttpServer(options);
      httpServer.requestHandler(this::simulateDohServer).listen();

      return;
    }

    DnsProtocolHandler handler = new DnsProtocolHandler(this, new RecordStore() {
      @Override
      public Set<ResourceRecord> getRecords(QuestionRecord question) throws DnsException {
        RecordStore actual = store;
        if (actual == null) {
          return Collections.emptySet();
        } else {
          return actual.getRecords(question);
        }
      }
    }) {
      @Override
      public void sessionCreated(IoSession session) throws Exception {
        // Use our own codec to support AAAA testing
        if (session.getTransportMetadata().isConnectionless()) {
          session.getFilterChain().addFirst("codec", new ProtocolCodecFilter(new TestDnsProtocolUdpCodecFactory()));
        } else {
          session.getFilterChain().addFirst("codec", new ProtocolCodecFilter(new TestDnsProtocolTcpCodecFactory()));
        }
      }

      @Override
      public void messageReceived(IoSession session, Object message) {
        if (message instanceof DnsMessage) {
          synchronized (FakeDNSServer.this) {
            currentMessage.add((DnsMessage) message);
          }
        }
        super.messageReceived(session, message);
      }
    };

    UdpTransport udpTransport = new UdpTransport(ipAddress, port);
    ((DatagramSessionConfig) udpTransport.getAcceptor().getSessionConfig()).setReuseAddress(true);
    TcpTransport tcpTransport = new TcpTransport(ipAddress, port);
    tcpTransport.getAcceptor().getSessionConfig().setReuseAddress(true);

    setTransports(udpTransport, tcpTransport);

    for (Transport transport : getTransports()) {
      IoAcceptor acceptor = transport.getAcceptor();

      acceptor.setHandler(handler);

      // Start the listener
      acceptor.bind();
    }
  }

  private void simulateDohServer(HttpServerRequest request) {
    if (HttpMethod.GET != request.method() ||
      !request.getHeader("accept").equalsIgnoreCase("application/dns-json")) {
      HttpServerResponse response = request.response();
      response.setStatusCode(400);
      response.send("DoH Request is not correct!");
      return;
    }

    QuestionRecord questionRecord = createQuestionRecord(request);

    cacheDnsMessage(questionRecord);

    Set<ResourceRecord> resourceRecords = getStoredRecords(questionRecord);
    List<DohResourceRecord> answers =
      resourceRecords.stream().map(DohMessageEncoder::encode).collect(Collectors.toList());

    if (answers.isEmpty()) {
      DohRecord dohRecord = new DohRecord();
      dohRecord.setStatus(DnsResponseCode.NXDOMAIN.code());
      sendResponse(dohRecord, request.response());
      return;
    }

    DohRecord dohRecord = new DohRecord();
    dohRecord.setStatus(0);
    dohRecord.setAnswer(answers);
    sendResponse(dohRecord, request.response());
  }

  private QuestionRecord createQuestionRecord(HttpServerRequest request) {
    String domainName = request.getParam("name");
    RecordType type = RecordType.convert((short) DnsRecordType.valueOf(request.getParam("type")).intValue());
    return new QuestionRecord(domainName, type, RecordClass.IN);
  }

  private void cacheDnsMessage(QuestionRecord questionRecord) {
    DnsMessageModifier dnsMessageModifier = new DnsMessageModifier();
    dnsMessageModifier.setMessageType(MessageType.QUERY);
    dnsMessageModifier.setRecursionDesired(true);
    dnsMessageModifier.setQuestionRecords(Collections.singletonList(questionRecord));
    currentMessage.add(dnsMessageModifier.getDnsMessage());
  }

  private Set<ResourceRecord> getStoredRecords(QuestionRecord questionRecord) {
    Set<ResourceRecord> records = null;
    try {
      records = store.getRecords(questionRecord);
    } catch (DnsException ignored) {
    }
    if (records != null) {
      return records;
    }
    return new HashSet<>();
  }

  private void sendResponse(DohRecord record, HttpServerResponse response) {
    response.putHeader("content-type", "application/dns-json");
    response.setStatusCode(200);
    response.send(record.toJson().toBuffer());
  }

  @Override
  public void stop() {
    if (this.ssl) {
      this.httpServer.close();
    } else {
      for (Transport transport : getTransports()) {
        transport.getAcceptor().dispose();
      }
    }
  }

  public static class VertxResourceRecord implements ResourceRecord {

    private final String ipAddress;
    private final String domainName;
    private boolean isTruncated;

    public VertxResourceRecord(String domainName, String ipAddress) {
      this.domainName = domainName;
      this.ipAddress = ipAddress;
    }

    public boolean isTruncated() {
      return isTruncated;
    }

    public VertxResourceRecord setTruncated(boolean truncated) {
      isTruncated = truncated;
      return this;
    }

    @Override
    public String getDomainName() {
      return domainName;
    }

    @Override
    public RecordType getRecordType() {
      return RecordType.A;
    }

    @Override
    public RecordClass getRecordClass() {
      return RecordClass.IN;
    }

    @Override
    public int getTimeToLive() {
      return 100;
    }

    @Override
    public String get(String id) {
      return DnsAttribute.IP_ADDRESS.equals(id) ? ipAddress : null;
    }
  }

  private static final ResourceRecordEncoder TestAAAARecordEncoder = new ResourceRecordEncoder() {
    @Override
    protected void putResourceRecordData(IoBuffer ioBuffer, ResourceRecord resourceRecord) {
      if (!resourceRecord.get(DnsAttribute.IP_ADDRESS).equals("::1")) {
        throw new IllegalStateException("Only supposed to be used with IPV6 address of ::1");
      }
      // encode the ::1
      ioBuffer.put(new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1});
    }
  };

  private final DnsMessageEncoder encoder = new DnsMessageEncoder();

  private void encode(DnsMessage dnsMessage, IoBuffer buf) {

    // Hack
    if (dnsMessage.getAnswerRecords().size() == 1 && dnsMessage.getAnswerRecords().get(0) instanceof VertxResourceRecord) {
      VertxResourceRecord vrr = (VertxResourceRecord) dnsMessage.getAnswerRecords().get(0);

      DnsMessageModifier modifier = new DnsMessageModifier();
      modifier.setTransactionId(dnsMessage.getTransactionId());
      modifier.setMessageType(dnsMessage.getMessageType());
      modifier.setOpCode(dnsMessage.getOpCode());
      modifier.setAuthoritativeAnswer(dnsMessage.isAuthoritativeAnswer());
      modifier.setTruncated(dnsMessage.isTruncated());
      modifier.setRecursionDesired(dnsMessage.isRecursionDesired());
      modifier.setRecursionAvailable(dnsMessage.isRecursionAvailable());
      modifier.setReserved(dnsMessage.isReserved());
      modifier.setAcceptNonAuthenticatedData(dnsMessage.isAcceptNonAuthenticatedData());
      modifier.setResponseCode(dnsMessage.getResponseCode());
      modifier.setQuestionRecords(dnsMessage.getQuestionRecords());
      modifier.setAnswerRecords(dnsMessage.getAnswerRecords());
      modifier.setAuthorityRecords(dnsMessage.getAuthorityRecords());
      modifier.setAdditionalRecords(dnsMessage.getAdditionalRecords());

      modifier.setTruncated(vrr.isTruncated);

      dnsMessage = modifier.getDnsMessage();
    }

    encoder.encode(buf, dnsMessage);

    for (ResourceRecord record : dnsMessage.getAnswerRecords()) {
      // This is a hack to allow to also test for AAAA resolution as DnsMessageEncoder does not support it and it
      // is hard to extend, because the interesting methods are private...
      // In case of RecordType.AAAA we need to encode the RecordType by ourself
      if (record.getRecordType() == RecordType.AAAA) {
        try {
          TestAAAARecordEncoder.put(buf, record);
        } catch (IOException e) {
          // Should never happen
          throw new IllegalStateException(e);
        }
      }
    }
  }

  /**
   * ProtocolCodecFactory which allows to test AAAA resolution
   */
  private final class TestDnsProtocolUdpCodecFactory implements ProtocolCodecFactory {
    @Override
    public ProtocolEncoder getEncoder(IoSession session) throws Exception {
      return new DnsUdpEncoder() {

        @Override
        public void encode(IoSession session, Object message, ProtocolEncoderOutput out) {
          IoBuffer buf = IoBuffer.allocate(1024);
          FakeDNSServer.this.encode((DnsMessage) message, buf);
          buf.flip();
          out.write(buf);
        }
      };
    }

    @Override
    public ProtocolDecoder getDecoder(IoSession session) throws Exception {
      return new DnsUdpDecoder();
    }
  }

  /**
   * ProtocolCodecFactory which allows to test AAAA resolution
   */
  private final class TestDnsProtocolTcpCodecFactory implements ProtocolCodecFactory {
    @Override
    public ProtocolEncoder getEncoder(IoSession session) throws Exception {
      return new DnsUdpEncoder() {

        @Override
        public void encode(IoSession session, Object message, ProtocolEncoderOutput out) {
          IoBuffer buf = IoBuffer.allocate(1024);
          buf.putShort((short) 0);
          FakeDNSServer.this.encode((DnsMessage) message, buf);
          encoder.encode(buf, (DnsMessage) message);
          int end = buf.position();
          short recordLength = (short) (end - 2);
          buf.rewind();
          buf.putShort(recordLength);
          buf.position(end);
          buf.flip();
          out.write(buf);
        }
      };
    }

    @Override
    public ProtocolDecoder getDecoder(IoSession session) throws Exception {
      return new DnsTcpDecoder();
    }
  }
}
