/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.test.fakedns;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.dns.*;
import io.netty.util.internal.PlatformDependent;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class MockDnsServer {
  private Channel channel;
  private volatile RecordStore store;

  public static final int PORT = 53530;
  public static final String IP_ADDRESS = "127.0.0.1";

  private String ipAddress = IP_ADDRESS;
  private int port = PORT;

  private EventLoopGroup eventLoopGroup;
  private final Deque<DnsMessage> currentMessage = new ArrayDeque<>();

  public MockDnsServer() {
  }

  public MockDnsServer store(RecordStore store) {
    this.store = store;
    return this;
  }

  public RecordStore store() {
    return store;
  }

  public InetSocketAddress localAddress() {
    return (InetSocketAddress) channel.localAddress();
  }

  public MockDnsServer ipAddress(String ipAddress) {
    this.ipAddress = ipAddress;
    return this;
  }

  public MockDnsServer port(int p) {
    port = p;
    return this;
  }

  public void start() throws Exception {
    if (this.eventLoopGroup != null) {
      throw new IllegalStateException();
    }
    EventLoopGroup eventLoopGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
    Bootstrap bootstrap = new Bootstrap()
      .group(eventLoopGroup)
      .channel(NioDatagramChannel.class)
      .handler(new ChannelInitializer<DatagramChannel>() {
        @Override
        protected void initChannel(DatagramChannel ch) throws Exception {
          ChannelPipeline p = ch.pipeline();
          p.addLast(new DatagramDnsQueryDecoder())
            .addLast(new DatagramDnsResponseEncoder())
            .addLast(new SimpleChannelInboundHandler<DatagramDnsQuery>() {
              @Override
              protected void channelRead0(ChannelHandlerContext ctx, DatagramDnsQuery msg) throws UnknownHostException {
                DnsRecord dnsRecord = msg.recordAt(DnsSection.QUESTION);
                synchronized (MockDnsServer.this) {
                  currentMessage.add(msg);
                }

                DnsResponse response = new DatagramDnsResponse(msg.recipient(), msg.sender(), msg.id(), DnsOpCode.QUERY);
                response.addRecord(DnsSection.QUESTION, dnsRecord);
                if (store != null) {
                  Collection<DnsRecord> records = store.getRecords((DnsQuestion) dnsRecord);
                  if (records != null) {
                    for (DnsRecord record : records) {
                      response.addRecord(DnsSection.ANSWER, record);
                    }
                  }
                }
                ctx.writeAndFlush(response);
              }
            });
        }
      });
    ChannelFuture fut = bootstrap.bind(ipAddress, port).sync();
    if (fut.isSuccess()) {
      this.channel = fut.channel();
      this.eventLoopGroup = eventLoopGroup;
    } else {
      eventLoopGroup.shutdownGracefully().await();
      PlatformDependent.throwException(fut.cause());
    }
  }

  public void stop() throws Exception {
    Channel channel = this.channel;
    this.channel = null;
    if (channel != null) {
      channel.close().sync();
    }
    EventLoopGroup eventLoopGroup = this.eventLoopGroup;
    this.eventLoopGroup = null;
    if (eventLoopGroup != null) {
      eventLoopGroup.shutdownGracefully(2, 2, TimeUnit.MILLISECONDS).sync();
    }
  }

  public static DnsRecord a(String domainName, int ttl, String ip) {
    byte[] address;
    try {
      address = InetAddress.getByName(ip).getAddress();
    } catch (UnknownHostException e) {
      throw new RuntimeException(e);
    }
    return new DefaultDnsRawRecord(domainName, DnsRecordType.A, ttl, Unpooled.copiedBuffer(address));
  }

  public static DnsRecord aaaa(String domainName, int ttl, String ip) {
    byte[] address;
    try {
      address = InetAddress.getByName(ip).getAddress();
    } catch (UnknownHostException e) {
      throw new RuntimeException(e);
    }
    return new DefaultDnsRawRecord(domainName, DnsRecordType.AAAA, ttl, Unpooled.copiedBuffer(address));
  }

  public static DnsRecord mx(String domainName, int ttl, String mxRecord, int preference) {
    return new DefaultDnsRawRecord(domainName, DnsRecordType.MX, ttl, encodeMx(mxRecord, preference));
  }

  public static DnsRecord txt(String domainName, int ttl, String txt) {
    return new DefaultDnsRawRecord(domainName, DnsRecordType.TXT, ttl, encodeTxt(txt));
  }

  public static DnsRecord ns(String domainName, int ttl, String ns) {
    return new DefaultDnsRawRecord(ns, DnsRecordType.NS, ttl, encodeName(domainName));
  }

  public static DnsRecord ptr(String domainName, int ttl, String reverseName) {
    return new DefaultDnsRawRecord(domainName, DnsRecordType.PTR, ttl, encodeName(reverseName));
  }

  public static DnsRecord srv(String domainName, int ttl, int priority, int weight, int port, String target) {
    return new DefaultDnsRawRecord(domainName, DnsRecordType.SRV, ttl, encodeSrv(priority, weight, port, target));
  }

  public static DnsRecord cname(String domainName, int ttl, String cname) {
    return new DefaultDnsRawRecord(cname, DnsRecordType.CNAME, ttl, encodeName(domainName));
  }

  public static DnsRecord dname(String domainName, int ttl, String dname) {
    return new DefaultDnsRawRecord(domainName, DnsRecordType.DNAME, ttl, encodeName(dname));
  }

  public static RecordStore A_store(Map<String, String> entries) {
    return questionRecord -> entries.entrySet().stream().map(entry ->
      a(entry.getKey(), 100, entry.getValue())).collect(Collectors.toSet());
  }

  public static RecordStore A_store(Function<String, String> entries) {
    return questionRecord -> {

      //Normalize the name by stripping the trailing dot
      String normalizedName = questionRecord.name().endsWith(".")
        ? questionRecord.name().substring(0, questionRecord.name().length() - 1)
        : questionRecord.name();


      String res = entries.apply(normalizedName);
      if (res != null) {
        return Collections.singleton(a(questionRecord.name(), 100, res));
      }
      return Collections.emptySet();
    };
  }

  public MockDnsServer testLookup4(String ip) {
    return store(questionRecord -> {
      Set<DnsRecord> set = new HashSet<>();
      if (questionRecord.type() == DnsRecordType.A) {
        set.add(a("vertx.io", 100, ip));
      }
      return set;
    });
  }

  public MockDnsServer testLookup6(String ip) {
    return store(questionRecord -> {
      Set<DnsRecord> set = new HashSet<>();
      if (questionRecord.type() == DnsRecordType.AAAA) {
        set.add(aaaa("vertx.io", 100, ip));
      }
      return set;
    });
  }

  public MockDnsServer testLookupNonExisting() {
    return store(questionRecord -> null);
  }

  public MockDnsServer testResolveA(final String ipAddress) {
    return testResolveA(Collections.singletonMap("vertx.io", ipAddress));
  }

  public MockDnsServer testResolveA(Map<String, String> entries) {
    return store(A_store(entries));
  }

  public MockDnsServer testResolveA(Function<String, String> entries) {
    return store(A_store(entries));
  }

  public MockDnsServer testResolveAAAA(final String ipAddress) {
    return store(questionRecord -> Collections.singleton(aaaa("vertx.io", 100, ipAddress)));
  }

  public MockDnsServer testResolveTXT(final String txt) {
    return store(questionRecord -> Collections.singleton(txt("vertx.io", 100, txt)));
  }

  public MockDnsServer testResolveMX(final int prio, final String mxRecord) {
    return store(questionRecord -> Collections.singleton(mx("vertx.io", 100, mxRecord, prio)));
  }

  public MockDnsServer testResolveNS(final String ns) {
    return store(questionRecord -> Collections.singleton(ns(ns, 100, "vertx.io")));
  }

  public MockDnsServer testResolvePTR(final String ptr) {
    return store(questionRecord -> Collections.singleton(ptr("vertx.io", 100, ptr)));
  }

  public MockDnsServer testResolveDNAME(final String dname) {
    return store(questionRecord -> Collections.singleton(dname("vertx.io", 100, dname)));
  }

  public MockDnsServer testResolveSRV(String name, int priority, int weight, int port, String target) {
    return store(questionRecord -> Collections.singleton(srv(name, 100, priority, weight, port, target)));
  }

  public MockDnsServer testResolveSRV2(final int priority, final int weight, final int basePort, final String target) {
    return store(questionRecord -> IntStream
      .range(0, 2)
      .mapToObj(i -> srv(target, 100, priority, weight, basePort + i, "svc" + i + ".vertx.io."))
      .collect(Collectors.toList()));
  }

  public MockDnsServer testReverseLookup(final String ptr) {
    return store(questionRecord -> Collections.singleton(ptr(ptr, 100, "vertx.io")));
  }

  public MockDnsServer testResolveCNAME(final String cname) {
    return store(questionRecord -> Collections.singleton(cname(cname, 100, "vertx.io")));
  }

  public MockDnsServer testLookup4CNAME(final String cname, final String ip) {
    return store(questionRecord -> {
      // use LinkedHashSet since the order of the result records has to be preserved to make sure the unit test fails
      Set<DnsRecord> set = new LinkedHashSet<>();
      set.add(cname("vertx.io", 100, cname));
      set.add(a("vertx.io", 100, ip));
      return set;
    });
  }

  public MockDnsServer testResolveASameServer(final String ipAddress) {
    return store(A_store(Collections.singletonMap("vertx.io", ipAddress)));
  }

  public synchronized DnsMessage pollMessage() {
    return currentMessage.poll();
  }

  /*public void addRecordsToStore(String domainName, String ...entries){
    Set<DnsRecord> records = new LinkedHashSet<>();
    Function<String, DnsRecord> createRecord = ipAddress -> new MockDnsServer().VertxResourceRecord(domainName, ipAddress);
    for (String e : entries){
      records.add(createRecord.apply(e));
    }
    store(x -> records);
  }*/

  private static ByteBuf encodeTxt(String txt) {
    byte[] data = txt.getBytes(StandardCharsets.UTF_8);
    if (data.length > 255) {
      throw new IllegalArgumentException("TXT record too long");
    }

    return Unpooled.wrappedBuffer(new byte[]{(byte) data.length}, data);
  }

  private static ByteBuf encodeName(String fqdn) {
    ByteBuf buf = Unpooled.buffer();

    String name = fqdn.endsWith(".")
      ? fqdn.substring(0, fqdn.length() - 1)
      : fqdn;

    for (String label : name.split("\\.")) {
      byte[] bytes = label.getBytes(StandardCharsets.US_ASCII);
      buf.writeByte(bytes.length);
      buf.writeBytes(bytes);
    }

    buf.writeByte(0); // root label
    return buf;
  }

  private static ByteBuf encodeMx(String exchange, int preference) {
    ByteBuf buf = Unpooled.buffer();

    // preference (uint16)
    buf.writeShort(preference);

    buf.writeBytes(encodeName(exchange));

    return buf;
  }

  private static ByteBuf encodeSrv(int priority, int weight, int port, String target) {

    ByteBuf buf = Unpooled.buffer();

    buf.writeShort(priority); // priority
    buf.writeShort(weight);   // weight
    buf.writeShort(port);     // port

    buf.writeBytes(encodeName(target));

    return buf;
  }

  public interface RecordStore {

    Collection<DnsRecord> getRecords(DnsQuestion question) throws UnknownHostException;
  }

  public static class VertxResourceRecord extends DefaultDnsRawRecord {

    private final String ipAddress;
    private final String domainName;

    public VertxResourceRecord(String name, String ipAddress) throws UnknownHostException {
      super(name, DnsRecordType.A, DnsRecord.CLASS_IN, 100, Unpooled.copiedBuffer(InetAddress.getByName(ipAddress).getAddress()));
      this.domainName = name;
      this.ipAddress = ipAddress;
    }

    @Override
    public String name() {
      return domainName;
    }

    @Override
    public DnsRecordType type() {
      return DnsRecordType.A;
    }

    @Override
    public int dnsClass() {
      return DnsRecord.CLASS_IN;
    }

    @Override
    public long timeToLive() {
      return 100;
    }

    @Override
    public boolean equals(Object obj) {
      return super.equals(obj) && Objects.equals(((VertxResourceRecord) obj).ipAddress, this.ipAddress);
    }
  }

  public void addRecordsToStore(String domainName, String... entries) {
    Set<DnsRecord> records = new LinkedHashSet<>();
    Function<String, DnsRecord> createRecord = ipAddress -> {
      try {
        return new VertxResourceRecord(domainName, ipAddress);
      } catch (UnknownHostException e) {
        throw new RuntimeException(e);
      }
    };
    for (String e : entries) {
      records.add(createRecord.apply(e));
    }
    store(x -> records);
  }
}
