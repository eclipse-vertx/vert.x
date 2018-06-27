/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
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
import io.vertx.core.*;
import io.vertx.core.dns.*;
import io.vertx.core.dns.DnsResponseCode;
import io.vertx.core.dns.impl.decoder.RecordDecoder;
import io.vertx.core.impl.ContextInternal;
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

  private final Vertx vertx;
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
    if (creatingContext != null && creatingContext.isMultiThreadedWorkerContext()) {
      throw new IllegalStateException("Cannot use DnsClient in a multi-threaded worker verticle");
    }

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
    lookupSingle(name, handler, DnsRecordType.A);
    return this;
  }

  @Override
  public DnsClient lookup6(String name, Handler<AsyncResult<String>> handler) {
    lookupSingle(name, handler, DnsRecordType.AAAA);
    return this;
  }

  @Override
  public DnsClient lookup(String name, Handler<AsyncResult<String>> handler) {
    lookupSingle(name, handler, DnsRecordType.A, DnsRecordType.AAAA);
    return this;
  }

  @Override
  public DnsClient resolveA(String name, Handler<AsyncResult<List<String>>> handler) {
    lookupList(name, handler, DnsRecordType.A);
    return this;
  }

  @Override
  public DnsClient resolveCNAME(String name, Handler<AsyncResult<List<String> >> handler) {
    lookupList(name, handler, DnsRecordType.CNAME);
    return this;
  }

  @Override
  public DnsClient resolveMX(String name, Handler<AsyncResult<List<MxRecord>>> handler) {
    lookupList(name, handler, DnsRecordType.MX);
    return this;
  }

  @Override
  public DnsClient resolveTXT(String name, Handler<AsyncResult<List<String>>> handler) {
    lookupList(name, new Handler<AsyncResult<List<String>>>() {
      @SuppressWarnings("unchecked")
      @Override
      public void handle(AsyncResult event) {
        if (event.failed()) {
          handler.handle(event);
        } else {
          List<String> txts = new ArrayList<>();
          List<List<String>> records = (List<List<String>>) event.result();
          for (List<String> txt: records) {
            txts.addAll(txt);
          }
          handler.handle(Future.succeededFuture(txts));
        }
      }
    }, DnsRecordType.TXT);
    return this;
  }

  @Override
  public DnsClient resolvePTR(String name, Handler<AsyncResult<String>> handler) {
    lookupSingle(name, handler, DnsRecordType.PTR);
    return this;
  }

  @Override
  public DnsClient resolveAAAA(String name, Handler<AsyncResult<List<String>>> handler) {
    lookupList(name, handler, DnsRecordType.AAAA);
    return this;
  }

  @Override
  public DnsClient resolveNS(String name, Handler<AsyncResult<List<String>>> handler) {
    lookupList(name, handler, DnsRecordType.NS);
    return this;
  }

  @Override
  public DnsClient resolveSRV(String name, Handler<AsyncResult<List<SrvRecord>>> handler) {
    lookupList(name, handler, DnsRecordType.SRV);
    return this;
  }

  @Override
  public DnsClient reverseLookup(String address, Handler<AsyncResult<String>> handler) {
    // TODO:  Check if the given address is a valid ip address before pass it to InetAddres.getByName(..)
    //        This is need as otherwise it may try to perform a DNS lookup.
    //        An other option would be to change address to be of type InetAddress.
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

      return resolvePTR(reverseName.toString(), handler);
    } catch (UnknownHostException e) {
      // Should never happen as we work with ip addresses as input
      // anyway just in case notify the handler
      actualCtx.runOnContext((v) -> handler.handle(Future.failedFuture(e)));
    }
    return this;
  }

  private <T> void lookupSingle(String name, Handler<AsyncResult<T>> handler, DnsRecordType... types) {
    this.<T>lookupList(name, ar -> handler.handle(ar.map(result -> result.isEmpty() ? null : result.get(0))), types);
  }

  @SuppressWarnings("unchecked")
  private <T> void lookupList(String name, Handler<AsyncResult<List<T>>> handler, DnsRecordType... types) {
    Objects.requireNonNull(name, "no null name accepted");
    EventLoop el = actualCtx.nettyEventLoop();
    if (el.inEventLoop()) {
      new Query(name, types, handler).run();
    } else {
      el.execute(() -> {
        new Query(name, types, handler).run();
      });
    }
  }

  // Testing purposes
  public void inProgressQueries(Handler<Integer> handler) {
    actualCtx.runOnContext(v -> {
      handler.handle(inProgressMap.size());
    });
  }

  private class Query<T> {

    final DatagramDnsQuery msg;
    final Future<List<T>> fut;
    final String name;
    final DnsRecordType[] types;
    long timerID;

    public Query(String name, DnsRecordType[] types, Handler<AsyncResult<List<T>>> handler) {
      this.msg = new DatagramDnsQuery(null, dnsServer, ThreadLocalRandom.current().nextInt()).setRecursionDesired(options.isRecursionDesired());
      for (DnsRecordType type: types) {
        msg.addRecord(DnsSection.QUESTION, new DefaultDnsQuestion(name, type, DnsRecord.CLASS_IN));
      }
      this.fut = Future.<List<T>>future().setHandler(handler);
      this.types = types;
      this.name = name;
    }

    private void fail(Throwable cause) {
      inProgressMap.remove(msg.id());
      if (timerID >= 0) {
        vertx.cancelTimer(timerID);
      }
      fut.tryFail(cause);
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
        actualCtx.executeFromIO(v -> {
          fut.tryComplete(records);
        });
      } else {
        actualCtx.executeFromIO(new DnsException(code), this::fail);
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
          actualCtx.executeFromIO(future.cause(), this::fail);
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
