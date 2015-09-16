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
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.dns.DnsClient;
import io.vertx.core.dns.DnsException;
import io.vertx.core.dns.DnsResponseCode;
import io.vertx.core.dns.MxRecord;
import io.vertx.core.dns.SrvRecord;
import io.vertx.core.dns.impl.netty.DnsEntry;
import io.vertx.core.dns.impl.netty.DnsQuery;
import io.vertx.core.dns.impl.netty.DnsQueryEncoder;
import io.vertx.core.dns.impl.netty.DnsQuestion;
import io.vertx.core.dns.impl.netty.DnsResource;
import io.vertx.core.dns.impl.netty.DnsResponse;
import io.vertx.core.dns.impl.netty.DnsResponseDecoder;
import io.vertx.core.dns.impl.netty.decoder.RecordDecoderFactory;
import io.vertx.core.dns.impl.netty.decoder.record.MailExchangerRecord;
import io.vertx.core.dns.impl.netty.decoder.record.ServiceRecord;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.impl.PartialPooledByteBufAllocator;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
    bootstrap.group(actualCtx.eventLoop());
    bootstrap.channel(NioDatagramChannel.class);
    bootstrap.option(ChannelOption.ALLOCATOR, PartialPooledByteBufAllocator.INSTANCE);
    bootstrap.handler(new ChannelInitializer<DatagramChannel>() {
      @Override
      protected void initChannel(DatagramChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast(new DnsQueryEncoder());
        pipeline.addLast(new DnsResponseDecoder());
      }
    });
  }

  @Override
  public DnsClient lookup4(String name, Handler<AsyncResult<String>> handler) {
    lookup(name, new HandlerAdapter<String>(handler), DnsEntry.TYPE_A);
    return this;
  }

  @Override
  public DnsClient lookup6(String name, Handler<AsyncResult<String>> handler) {
    lookup(name, new HandlerAdapter<String>(handler), DnsEntry.TYPE_AAAA);
    return this;
  }

  @Override
  public DnsClient lookup(String name, Handler<AsyncResult<String>> handler) {
    lookup(name, new HandlerAdapter<String>(handler), DnsEntry.TYPE_A, DnsEntry.TYPE_AAAA);
    return this;
  }

  @Override
  public DnsClient resolveA(String name, Handler<AsyncResult<List<String>>> handler) {
    lookup(name, handler, DnsEntry.TYPE_A);
    return this;
  }

  @Override
  public DnsClient resolveCNAME(String name, Handler<AsyncResult<List<String> >> handler) {
    lookup(name, handler, DnsEntry.TYPE_CNAME);
    return this;
  }

  @Override
  public DnsClient resolveMX(String name, Handler<AsyncResult<List<MxRecord>>> handler) {
    lookup(name, new ConvertingHandler<MailExchangerRecord, MxRecord>(handler, MxRecordComparator.INSTANCE) {
      @Override
      protected MxRecord convert(MailExchangerRecord entry) {
        return new MxRecordImpl(entry);
      }
    }, DnsEntry.TYPE_MX);
    return this;
  }

  @Override
  public DnsClient resolveTXT(String name, Handler<AsyncResult<List<String>>> handler) {
    lookup(name, new Handler<AsyncResult>() {
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
    }, DnsEntry.TYPE_TXT);
    return this;
  }

  @Override
  public DnsClient resolvePTR(String name, Handler<AsyncResult<String>> handler) {
    lookup(name, new HandlerAdapter<String>(handler), DnsEntry.TYPE_PTR);
    return this;
  }

  @Override
  public DnsClient resolveAAAA(String name, Handler<AsyncResult<List<String>>> handler) {
    lookup(name, handler, DnsEntry.TYPE_AAAA);
    return this;
  }

  @Override
  public DnsClient resolveNS(String name, Handler<AsyncResult<List<String>>> handler) {
    lookup(name, handler, DnsEntry.TYPE_NS);
    return this;
  }

  @Override
  public DnsClient resolveSRV(String name, Handler<AsyncResult<List<SrvRecord>>> handler) {
    lookup(name, new ConvertingHandler<ServiceRecord, SrvRecord>(handler, SrvRecordComparator.INSTANCE) {
      @Override
      protected SrvRecord convert(ServiceRecord entry) {
        return new SrcRecordImpl(entry);
      }
    }, DnsEntry.TYPE_SRV);
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

      return resolvePTR(reverseName.toString(), ar -> {
          if (ar.failed()) {
            handler.handle(Future.failedFuture(ar.cause()));
          } else {
            String result = ar.result();
            handler.handle(Future.succeededFuture(result));
          }
        });
    } catch (UnknownHostException e) {
      // Should never happen as we work with ip addresses as input
      // anyway just in case notify the handler
      actualCtx.runOnContext((v) -> handler.handle(Future.failedFuture(e)));
    }
    return this;
  }

  @SuppressWarnings("unchecked")
  private void lookup(String name, Handler handler, int... types) {
    Future result = Future.future();
    result.setHandler(handler);
    lookup(name, result, types);
  }

  @SuppressWarnings("unchecked")
  private void lookup(String name, Future result, int... types) {
    Objects.requireNonNull(name, "no null name accepted");
    bootstrap.connect(dnsServer).addListener(new RetryChannelFutureListener(result) {
      @Override
      public void onSuccess(ChannelFuture future) throws Exception {
        DnsQuery query = new DnsQuery(ThreadLocalRandom.current().nextInt());
        for (int type: types) {
          query.addQuestion(new DnsQuestion(name, type));
        }
        future.channel().writeAndFlush(query).addListener(new RetryChannelFutureListener(result) {
          @Override
          public void onSuccess(ChannelFuture future) throws Exception {
            future.channel().pipeline().addLast(new SimpleChannelInboundHandler<DnsResponse>() {
              @Override
              protected void channelRead0(ChannelHandlerContext ctx, DnsResponse msg) throws Exception {
                DnsResponseCode code = DnsResponseCode.valueOf(msg.getHeader().getResponseCode());

                if (code == DnsResponseCode.NOERROR) {
                  List<DnsResource> resources = msg.getAnswers();
                  List<Object> records = new ArrayList<>(resources.size());
                  for (DnsResource resource : msg.getAnswers()) {
                    Object record = RecordDecoderFactory.getFactory().decode(resource.type(), msg, resource);
                    if (record instanceof InetAddress) {
                      record = ((InetAddress)record).getHostAddress();
                    }
                    records.add(record);
                  }

                  setResult(result, records);
                } else {
                  setResult(result, new DnsException(code));
                }
                ctx.close();
              }

              @Override
              public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                setResult(result, cause);
                ctx.close();
              }
            });
          }
        });
      }
    });
  }

  @SuppressWarnings("unchecked")
  private void setResult(Future r, Object result) {
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

  private static class HandlerAdapter<T> implements Handler<AsyncResult<List<T>>> {
    private final Handler handler;

    HandlerAdapter(Handler handler) {
      this.handler = handler;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void handle(AsyncResult<List<T>> event) {
      if (event.failed()) {
        handler.handle(event);
      } else {
        List<T> result = event.result();
        if (result.isEmpty()) {
          handler.handle(Future.succeededFuture());
        } else {
          handler.handle(Future.succeededFuture(result.get(0)));
        }
      }
    }
  }

  protected abstract class ConvertingHandler<F, T> implements Handler<AsyncResult<List<F>>> {
    private final Handler handler;
    private final Comparator comparator;

    ConvertingHandler(Handler<AsyncResult<List<T>>> handler, Comparator comparator) {
      this.handler = handler;
      this.comparator = comparator;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void handle(AsyncResult<List<F>> event) {
      if (event.failed()) {
        handler.handle(event);
      } else {
        List records = (List) event.result();
        for (int i = 0; i < records.size(); i++) {
          F record = (F) records.get(i);
          records.set(i, convert(record));
        }

        Collections.sort(records, comparator);
        handler.handle(Future.succeededFuture(records));
      }
    }

    protected abstract T convert(F entry);
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
