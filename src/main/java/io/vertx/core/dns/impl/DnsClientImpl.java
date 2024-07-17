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
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.dns.*;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.collection.LongObjectHashMap;
import io.netty.util.collection.LongObjectMap;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.VertxException;
import io.vertx.core.buffer.impl.PartialPooledByteBufAllocator;
import io.vertx.core.dns.DnsClientOptions;
import io.vertx.core.dns.DnsException;
import io.vertx.core.dns.DnsResponseCode;
import io.vertx.core.dns.impl.decoder.JsonHelper;
import io.vertx.core.dns.impl.decoder.RecordDecoder;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.future.PromiseInternal;
import io.vertx.core.net.impl.ConnectionBase;
import io.vertx.core.spi.transport.Transport;

import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class DnsClientImpl extends BaseDnsClientImpl {

  private final LongObjectMap<DnsClientImpl.Query> inProgressMap = new LongObjectHashMap<>();
  private final InetSocketAddress dnsServer;
  private final DatagramChannel channel;

  public DnsClientImpl(VertxInternal vertx, DnsClientOptions options) {
    Objects.requireNonNull(options, "no null options accepted");
    Objects.requireNonNull(options.getHost(), "no null host accepted");

    this.options = new DnsClientOptions(options);

    this.dnsServer = new InetSocketAddress(options.getHost(), options.getPort());
    if (this.dnsServer.isUnresolved()) {
      throw new IllegalArgumentException("Cannot resolve the host to a valid ip address");
    }
    this.vertx = vertx;

    Transport transport = vertx.transport();
    context = vertx.getOrCreateContext();
    channel = transport.datagramChannel(this.dnsServer.getAddress() instanceof Inet4Address ?
      InternetProtocolFamily.IPv4 : InternetProtocolFamily.IPv6);
    channel.config().setOption(ChannelOption.DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION, true);
    MaxMessagesRecvByteBufAllocator bufAllocator = channel.config().getRecvByteBufAllocator();
    bufAllocator.maxMessagesPerRead(1);
    channel.config().setAllocator(PartialPooledByteBufAllocator.INSTANCE);
    context.nettyEventLoop().register(channel);
    if (options.getLogActivity()) {
      channel.pipeline().addLast("logging", new LoggingHandler(options.getActivityLogFormat()));
    }
    channel.pipeline().addLast(new DatagramDnsQueryEncoder());
    channel.pipeline().addLast(new DatagramDnsResponseDecoder());
    channel.pipeline().addLast(new SimpleChannelInboundHandler<DnsResponse>() {
      @Override
      protected void channelRead0(ChannelHandlerContext ctx, DnsResponse msg) {
        DefaultDnsQuestion question = msg.recordAt(DnsSection.QUESTION);
        Query query = inProgressMap.get(dnsMessageId(msg.id(), question.name()));
        if (query != null) {
          query.handle(msg);
        }
      }
    });
  }


  @SuppressWarnings("unchecked")
  protected <T> Future<List<T>> lookupList(String name, DnsRecordType... types) {
    ContextInternal ctx = vertx.getOrCreateContext();
    if (closed != null) {
      return ctx.failedFuture(ConnectionBase.CLOSED_EXCEPTION);
    }
    PromiseInternal<List<T>> promise = ctx.promise();
    Objects.requireNonNull(name, "no null name accepted");
    EventLoop el = context.nettyEventLoop();
    Query query = new Query(name, types);
    query.promise.addListener(promise);
    if (el.inEventLoop()) {
      query.run();
    } else {
      el.execute(query::run);
    }
    return promise.future();
  }

  private long dnsMessageId(int id, String query) {
    return ((long) query.hashCode() << 16) + (id & 65535);
  }

  // Testing purposes
  public void inProgressQueries(Handler<Integer> handler) {
    context.runOnContext(v -> {
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
      this.msg =
        new DatagramDnsQuery(null, dnsServer, ThreadLocalRandom.current().nextInt()).setRecursionDesired(options.isRecursionDesired());
      name = JsonHelper.appendDotIfRequired(name);
      for (DnsRecordType type : types) {
        msg.addRecord(DnsSection.QUESTION, new DefaultDnsQuestion(name, type, DnsRecord.CLASS_IN));
      }
      this.promise = context.nettyEventLoop().newPromise();
      this.types = types;
      this.name = name;
    }

    private void fail(Throwable cause) {
      inProgressMap.remove(dnsMessageId(msg.id(), name));
      if (timerID >= 0) {
        vertx.cancelTimer(timerID);
      }
      promise.setFailure(cause);
    }

    void handle(DnsResponse msg) {
      DnsResponseCode code = DnsResponseCode.valueOf(msg.code().intValue());
      if (code == DnsResponseCode.NOERROR) {
        inProgressMap.remove(dnsMessageId(msg.id(), name));
        if (timerID >= 0) {
          vertx.cancelTimer(timerID);
        }
        int count = msg.count(DnsSection.ANSWER);
        List<T> records = new ArrayList<>(count);
        for (int idx = 0; idx < count; idx++) {
          DnsRecord a = msg.recordAt(DnsSection.ANSWER, idx);
          T record;
          try {
            record = RecordDecoder.decode(a);
          } catch (DecoderException e) {
            fail(e);
            return;
          }

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
      inProgressMap.put(dnsMessageId(msg.id(), name), this);
      timerID = vertx.setTimer(options.getQueryTimeout(), id -> {
        timerID = -1;
        context.runOnContext(v -> {
          fail(new VertxException("DNS query timeout for " + name));
        });
      });
      channel.writeAndFlush(msg).addListener((ChannelFutureListener) future -> {
        if (!future.isSuccess()) {
          context.emit(future.cause(), this::fail);
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
  public Future<Void> close() {
    PromiseInternal<Void> promise;
    synchronized (this) {
      if (closed != null) {
        return closed;
      }
      promise = vertx.promise();
      closed = promise.future();
    }
    context.runOnContext(v -> {
      new ArrayList<>(inProgressMap.values()).forEach(query -> {
        query.fail(ConnectionBase.CLOSED_EXCEPTION);
      });
      channel.close().addListener(promise);
    });
    return promise.future();
  }
}
