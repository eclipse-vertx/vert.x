/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */
package io.vertx.core.dns.impl;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.dns.DatagramDnsQuery;
import io.netty.handler.codec.dns.DatagramDnsQueryEncoder;
import io.netty.handler.codec.dns.DatagramDnsResponseDecoder;
import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsSection;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.dns.DnsClient;
import io.vertx.core.dns.DnsException;
import io.vertx.core.dns.DnsResponseCode;
import io.vertx.core.dns.MxRecord;
import io.vertx.core.dns.SrvRecord;
import io.vertx.core.dns.impl.decoder.RecordDecoder;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.impl.PartialPooledByteBufAllocator;

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

  private final Bootstrap bootstrap;
  private final InetSocketAddress dnsServer;
  private final ContextImpl actualCtx;

  public DnsClientImpl(VertxInternal vertx, int port, String host) {

    ContextImpl creatingContext = vertx.getContext();
    if (creatingContext != null && creatingContext.isMultiThreadedWorkerContext()) {
      throw new IllegalStateException("Cannot use DnsClient in a multi-threaded worker verticle");
    }

    this.dnsServer = new InetSocketAddress(host, port);

    actualCtx = vertx.getOrCreateContext();
    bootstrap = new Bootstrap();
    bootstrap.group(actualCtx.nettyEventLoop());
    bootstrap.channel(NioDatagramChannel.class);
    bootstrap.option(ChannelOption.ALLOCATOR, PartialPooledByteBufAllocator.INSTANCE);
    bootstrap.handler(new ChannelInitializer<DatagramChannel>() {
      @Override
      protected void initChannel(DatagramChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast(new DatagramDnsQueryEncoder());
        pipeline.addLast(new DatagramDnsResponseDecoder());
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
    Future<List<T>> result = Future.future();
    result.setHandler(handler);
    lookup(name, result, types);
  }

  @SuppressWarnings("unchecked")
  private <T> void lookup(String name, Future<List<T>> result, DnsRecordType... types) {
    Objects.requireNonNull(name, "no null name accepted");
    bootstrap.connect(dnsServer).addListener(new RetryChannelFutureListener(result) {
      @Override
      public void onSuccess(ChannelFuture future) throws Exception {
        DatagramDnsQuery query = new DatagramDnsQuery(null, dnsServer, ThreadLocalRandom.current().nextInt());
        for (DnsRecordType type: types) {
          query.addRecord(DnsSection.QUESTION, new DefaultDnsQuestion(name, type, DnsRecord.CLASS_IN));
        }
        future.channel().writeAndFlush(query).addListener(new RetryChannelFutureListener(result) {
          @Override
          public void onSuccess(ChannelFuture future) throws Exception {
            future.channel().pipeline().addLast(new SimpleChannelInboundHandler<DnsResponse>() {
              @Override
              protected void channelRead0(ChannelHandlerContext ctx, DnsResponse msg) throws Exception {
                DnsResponseCode code = DnsResponseCode.valueOf(msg.code().intValue());

                if (code == DnsResponseCode.NOERROR) {

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

                  setResult(result, records);
                } else {
                  setFailure(result, new DnsException(code));
                }
                ctx.close();
              }

              private boolean isRequestedType(DnsRecordType dnsRecordType, DnsRecordType[] types) {
                for (DnsRecordType t : types) {
                  if (t.equals(dnsRecordType)) {
                    return true;
                  }
                }
                return false;
              }

              @Override
              public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                setFailure(result, cause);
                ctx.close();
              }
            });
          }
        });
      }
    });
  }

  @SuppressWarnings("unchecked")
  private <T> void setResult(Future<List<T>> r, List<T> result) {
    if (r.isComplete()) {
      return;
    }
    actualCtx.executeFromIO(() -> {
      if (result instanceof Throwable) {
        r.fail((Throwable) result);
      } else {
        r.complete(result);
      }
    });
  }

  @SuppressWarnings("unchecked")
  private <T> void setFailure(Future<List<T>> r, Throwable err) {
    if (r.isComplete()) {
      return;
    }
    actualCtx.executeFromIO(() -> {
      r.fail(err);
    });
  }

  private abstract class RetryChannelFutureListener implements ChannelFutureListener {
    private final Future result;

    RetryChannelFutureListener(final Future result) {
      this.result = result;
    }

    @Override
    public final void operationComplete(ChannelFuture future) throws Exception {
      if (!future.isSuccess()) {
        if (!result.isComplete()) {
          result.fail(future.cause());
        }
      } else {
        onSuccess(future);
      }
    }

    protected abstract void onSuccess(ChannelFuture future) throws Exception;
  }

}
