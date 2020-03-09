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

package io.vertx.core.dns.impl;

import io.netty.channel.*;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.handler.codec.dns.*;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.*;
import io.vertx.core.dns.*;
import io.vertx.core.dns.DnsResponseCode;
import io.vertx.core.dns.impl.decoder.RecordDecoder;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.PromiseInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.impl.PartialPooledByteBufAllocator;
import io.vertx.core.net.impl.transport.Transport;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class DnsClientImpl implements DnsClient {

  private static final char[] HEX_TABLE = "0123456789abcdef".toCharArray();

  private final VertxInternal vertx;
  private final IntObjectMap<Query> inProgressMap = new IntObjectHashMap<>();
  private final InetSocketAddress dnsServer;
  private final ContextInternal actualCtx;
  private final DatagramChannel channel;
  private final DnsClientOptions options;

  public DnsClientImpl(VertxInternal vertx, DnsClientOptions options) {
    Objects.requireNonNull(options, "no null options accepted");
    Objects.requireNonNull(options.getHost(), "no null host accepted");

    this.options = new DnsClientOptions(options);

    ContextInternal creatingContext = vertx.getContext();

    this.dnsServer = new InetSocketAddress(options.getHost(), options.getPort());
    if (this.dnsServer.isUnresolved()) {
      throw new IllegalArgumentException("Cannot resolve the host to a valid ip address");
    }
    this.vertx = vertx;

    Transport transport = vertx.transport();
    actualCtx = vertx.getOrCreateContext();
    channel = transport.datagramChannel(this.dnsServer.getAddress() instanceof Inet4Address ? InternetProtocolFamily.IPv4 : InternetProtocolFamily.IPv6);
    channel.config().setOption(ChannelOption.DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION, true);
    MaxMessagesRecvByteBufAllocator bufAllocator = channel.config().getRecvByteBufAllocator();
    bufAllocator.maxMessagesPerRead(1);
    channel.config().setAllocator(PartialPooledByteBufAllocator.INSTANCE);
    actualCtx.nettyEventLoop().register(channel);
    if (options.getLogActivity()) {
      channel.pipeline().addLast("logging", new LoggingHandler());
    }
    channel.pipeline().addLast(new DatagramDnsQueryEncoder());
    channel.pipeline().addLast(new DatagramDnsResponseDecoder());
    channel.pipeline().addLast(new SimpleChannelInboundHandler<DnsResponse>() {
      @Override
      protected void channelRead0(ChannelHandlerContext ctx, DnsResponse msg) throws Exception {
        int id = msg.id();
        Query query = inProgressMap.get(id);
        if (query != null) {
          query.handle(msg);
        }
      }
    });
  }

  @Override
  public DnsClient lookup4(String name, Handler<AsyncResult<String>> handler) {
    lookup4(name).onComplete(handler);
    return this;
  }

  @Override
  public Future<@Nullable String> lookup4(String name) {
    return lookupSingle(name, DnsRecordType.A);
  }

  @Override
  public DnsClient lookup6(String name, Handler<AsyncResult<String>> handler) {
    lookup6(name).onComplete(handler);
    return this;
  }

  @Override
  public Future<@Nullable String> lookup6(String name) {
    return lookupSingle(name, DnsRecordType.AAAA);
  }

  @Override
  public DnsClient lookup(String name, Handler<AsyncResult<String>> handler) {
    lookup(name).onComplete(handler);
    return this;
  }

  @Override
  public Future<@Nullable String> lookup(String name) {
    return lookupSingle(name, DnsRecordType.A, DnsRecordType.AAAA);
  }

  @Override
  public DnsClient resolveA(String name, Handler<AsyncResult<List<String>>> handler) {
    resolveA(name).onComplete(handler);
    return this;
  }

  @Override
  public Future<List<String>> resolveA(String name) {
    return lookupList(name, DnsRecordType.A);
  }

  @Override
  public DnsClient resolveCNAME(String name, Handler<AsyncResult<List<String> >> handler) {
    resolveCNAME(name).onComplete(handler);
    return this;
  }

  @Override
  public Future<List<String>> resolveCNAME(String name) {
    return lookupList(name, DnsRecordType.CNAME);
  }

  @Override
  public DnsClient resolveMX(String name, Handler<AsyncResult<List<MxRecord>>> handler) {
    resolveMX(name).onComplete(handler);
    return this;
  }

  @Override
  public Future<List<MxRecord>> resolveMX(String name) {
    return lookupList(name, DnsRecordType.MX);
  }

  @Override
  public Future<List<String>> resolveTXT(String name) {
    return this.<List<String>>lookupList(name, DnsRecordType.TXT).map(records -> {
      List<String> txts = new ArrayList<>();
      for (List<String> txt: records) {
        txts.addAll(txt);
      }
      return txts;
    });
  }

  @Override
  public DnsClient resolveTXT(String name, Handler<AsyncResult<List<String>>> handler) {
    resolveTXT(name).onComplete(handler);
    return this;
  }

  @Override
  public Future<@Nullable String> resolvePTR(String name) {
    return lookupSingle(name, DnsRecordType.PTR);
  }

  @Override
  public DnsClient resolvePTR(String name, Handler<AsyncResult<String>> handler) {
    resolvePTR(name).onComplete(handler);
    return this;
  }

  @Override
  public DnsClient resolveAAAA(String name, Handler<AsyncResult<List<String>>> handler) {
    resolveAAAA(name).onComplete(handler);
    return this;
  }

  @Override
  public Future<List<String>> resolveAAAA(String name) {
    return lookupList(name, DnsRecordType.AAAA);
  }

  @Override
  public Future<List<String>> resolveNS(String name) {
    return lookupList(name, DnsRecordType.NS);
  }

  @Override
  public DnsClient resolveNS(String name, Handler<AsyncResult<List<String>>> handler) {
    resolveNS(name).onComplete(handler);
    return this;
  }

  @Override
  public Future<List<SrvRecord>> resolveSRV(String name) {
    return lookupList(name, DnsRecordType.SRV);
  }

  @Override
  public DnsClient resolveSRV(String name, Handler<AsyncResult<List<SrvRecord>>> handler) {
    resolveSRV(name).onComplete(handler);
    return this;
  }

  @Override
  public Future<@Nullable String> reverseLookup(String address) {
    try {
      InetAddress inetAddress = InetAddress.getByName(address);
      byte[] addr = inetAddress.getAddress();

      StringBuilder reverseName = new StringBuilder(64);
      if (inetAddress instanceof Inet4Address) {
        // reverse ipv4 address
        reverseName.append(addr[3] & 0xff).append(".")
          .append(addr[2]& 0xff).append(".")
          .append(addr[1]& 0xff).append(".")
          .append(addr[0]& 0xff);
      } else {
        // It is an ipv 6 address time to reverse it
        for (int i = 0; i < 16; i++) {
          reverseName.append(HEX_TABLE[(addr[15 - i] & 0xf)]);
          reverseName.append(".");
          reverseName.append(HEX_TABLE[(addr[15 - i] >> 4) & 0xf]);
          if (i != 15) {
            reverseName.append(".");
          }
        }
      }
      reverseName.append(".in-addr.arpa");

      return resolvePTR(reverseName.toString());
    } catch (UnknownHostException e) {
      // Should never happen as we work with ip addresses as input
      // anyway just in case notify the handler
      return Future.failedFuture(e);
    }
  }

  @Override
  public DnsClient reverseLookup(String address, Handler<AsyncResult<String>> handler) {
    reverseLookup(address).onComplete(handler);
    return this;
  }

  private <T> Future<T> lookupSingle(String name, DnsRecordType... types) {
    return this.<T>lookupList(name, types).map(result -> result.isEmpty() ? null : result.get(0));
  }

  @SuppressWarnings("unchecked")
  private <T> Future<List<T>> lookupList(String name, DnsRecordType... types) {
    ContextInternal ctx = vertx.getOrCreateContext();
    PromiseInternal<List<T>> promise = ctx.promise();
    Objects.requireNonNull(name, "no null name accepted");
    EventLoop el = actualCtx.nettyEventLoop();
    Query query = new Query(name, types);
    query.promise.addListener(promise);
    if (el.inEventLoop()) {
      query.run();
    } else {
      el.execute(query::run);
    }
    return promise.future();
  }

  // Testing purposes
  public void inProgressQueries(Handler<Integer> handler) {
    actualCtx.runOnContext(v -> {
      handler.handle(inProgressMap.size());
    });
  }

  private class Query<T> {

    final DatagramDnsQuery msg;
    final io.netty.util.concurrent.Promise<List<T>> promise;
    final String name;
    final DnsRecordType[] types;
    long timerID;

    public Query(String name, DnsRecordType[] types) {
      this.msg = new DatagramDnsQuery(null, dnsServer, ThreadLocalRandom.current().nextInt()).setRecursionDesired(options.isRecursionDesired());
      for (DnsRecordType type: types) {
        msg.addRecord(DnsSection.QUESTION, new DefaultDnsQuestion(name, type, DnsRecord.CLASS_IN));
      }
      this.promise = actualCtx.nettyEventLoop().newPromise();
      this.types = types;
      this.name = name;
    }

    private void fail(Throwable cause) {
      inProgressMap.remove(msg.id());
      if (timerID >= 0) {
        vertx.cancelTimer(timerID);
      }
      promise.setFailure(cause);
    }

    void handle(DnsResponse msg) {
      DnsResponseCode code = DnsResponseCode.valueOf(msg.code().intValue());
      if (code == DnsResponseCode.NOERROR) {
        inProgressMap.remove(msg.id());
        if (timerID >= 0) {
          vertx.cancelTimer(timerID);
        }
        int count = msg.count(DnsSection.ANSWER);
        List<T> records = new ArrayList<>(count);
        for (int idx = 0;idx < count;idx++) {
          DnsRecord a = msg.recordAt(DnsSection.ANSWER, idx);
          T record = RecordDecoder.decode(a);
          if (isRequestedType(a.type(), types)) {
            records.add(record);
          }
        }
        if (records.size() > 0 && (records.get(0) instanceof MxRecordImpl || records.get(0) instanceof SrvRecordImpl)) {
          Collections.sort((List) records);
        }
        promise.setSuccess(records);
      } else {
        fail(new DnsException(code));
      }
    }

    void run() {
      inProgressMap.put(msg.id(), this);
      timerID = vertx.setTimer(options.getQueryTimeout(), id -> {
        timerID = -1;
        actualCtx.runOnContext(v -> {
          fail(new VertxException("DNS query timeout for " + name));
        });
      });
      channel.writeAndFlush(msg).addListener((ChannelFutureListener) future -> {
        if (!future.isSuccess()) {
          actualCtx.dispatch(future.cause(), this::fail);
        }
      });
    }

    private boolean isRequestedType(DnsRecordType dnsRecordType, DnsRecordType[] types) {
      for (DnsRecordType t : types) {
        if (t.equals(dnsRecordType)) {
          return true;
        }
      }
      return false;
    }
  }
}
