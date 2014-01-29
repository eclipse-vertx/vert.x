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
package org.vertx.java.core.dns.impl;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.dns.*;
import org.vertx.java.core.dns.DnsResponseCode;
import org.vertx.java.core.dns.impl.netty.*;
import org.vertx.java.core.dns.impl.netty.decoder.RecordDecoderFactory;
import org.vertx.java.core.dns.impl.netty.decoder.record.MailExchangerRecord;
import org.vertx.java.core.dns.impl.netty.decoder.record.ServiceRecord;
import org.vertx.java.core.impl.DefaultContext;
import org.vertx.java.core.impl.DefaultFutureResult;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.net.impl.PartialPooledByteBufAllocator;

import java.net.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class DefaultDnsClient implements DnsClient {

  private final Bootstrap bootstrap;
  private final List<InetSocketAddress> dnsServers;
  private static final char[] HEX_TABLE = "0123456789abcdef".toCharArray();
  private final DefaultContext actualCtx;
  private final VertxInternal vertx;

  public DefaultDnsClient(VertxInternal vertx, InetSocketAddress... dnsServers) {
    if (dnsServers == null || dnsServers.length == 0) {
      throw new IllegalArgumentException("Need at least one default DNS Server");
    }

    // use LinkedList as we will traverse it all the time
    this.dnsServers = new LinkedList<>(Arrays.asList(dnsServers));
    actualCtx = vertx.getOrCreateContext();
    this.vertx = vertx;
    bootstrap = new Bootstrap();
    bootstrap.group(actualCtx.getEventLoop());
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
  public DnsClient lookup4(String name, final Handler<AsyncResult<Inet4Address>> handler) {
    lookup(name, new HandlerAdapter<Inet4Address>(handler), DnsEntry.TYPE_A);
    return this;
  }

  @Override
  public DnsClient lookup6(String name, final Handler<AsyncResult<Inet6Address>> handler) {
    lookup(name, new HandlerAdapter<Inet6Address>(handler), DnsEntry.TYPE_AAAA);
    return this;
  }

  @Override
  public DnsClient lookup(String name, final Handler<AsyncResult<InetAddress>> handler) {
    lookup(name, new HandlerAdapter<InetAddress>(handler), DnsEntry.TYPE_A, DnsEntry.TYPE_AAAA);
    return this;
  }

  @Override
  public DnsClient resolveA(String name, final Handler<AsyncResult<List<Inet4Address>>> handler) {
    lookup(name, handler, DnsEntry.TYPE_A);
    return this;
  }

  @Override
  public DnsClient resolveCNAME(String name, Handler<AsyncResult<List<String> >> handler) {
    lookup(name, handler, DnsEntry.TYPE_CNAME);
    return this;
  }

  @Override
  public DnsClient resolveMX(String name, final Handler<AsyncResult<List<MxRecord>>> handler) {
    lookup(name, new ConvertingHandler<MailExchangerRecord, MxRecord>(handler, DefaultMxRecordComparator.INSTANCE) {
      @Override
      protected MxRecord convert(MailExchangerRecord entry) {
        return new DefaultMxRecord(entry);
      }
    }, DnsEntry.TYPE_MX);
    return this;
  }

  @Override
  public DnsClient resolveTXT(String name, final Handler<AsyncResult<List<String>>> handler) {
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
          handler.handle(new DefaultFutureResult(txts));
        }
      }
    }, DnsEntry.TYPE_TXT);
    return this;
  }

  @Override
  public DnsClient resolvePTR(String name, final Handler<AsyncResult<String>> handler) {
    lookup(name, new HandlerAdapter<String>(handler), DnsEntry.TYPE_PTR);
    return this;
  }

  @Override
  public DnsClient resolveAAAA(String name, Handler<AsyncResult<List<Inet6Address>>> handler) {
    lookup(name, handler, DnsEntry.TYPE_AAAA);
    return this;
  }

  @Override
  public DnsClient resolveNS(String name, Handler<AsyncResult<List<String>>> handler) {
    lookup(name, handler, DnsEntry.TYPE_NS);
    return this;
  }

  @Override
  public DnsClient resolveSRV(String name, final Handler<AsyncResult<List<SrvRecord>>> handler) {
    lookup(name, new ConvertingHandler<ServiceRecord, SrvRecord>(handler, DefaultSrvRecordComparator.INSTANCE) {
      @Override
      protected SrvRecord convert(ServiceRecord entry) {
        return new DefaultSrvRecord(entry);
      }
    }, DnsEntry.TYPE_SRV);
    return this;
  }

  @Override
  public DnsClient reverseLookup(String address, final Handler<AsyncResult<InetAddress>> handler) {
    // TODO:  Check if the given address is a valid ip address before pass it to InetAddres.getByName(..)
    //        This is need as otherwise it may try to perform a DNS lookup.
    //        An other option would be to change address to be of type InetAddress.
    try {
      final InetAddress inetAddress = InetAddress.getByName(address);
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

      return resolvePTR(reverseName.toString(), new Handler<AsyncResult<String>>() {
        @Override
        public void handle(AsyncResult event) {
          if (event.failed()) {
            handler.handle(event);
          } else {
            String result = (String) event.result();
            try {
              handler.handle(new DefaultFutureResult<>(InetAddress.getByAddress(result, inetAddress.getAddress())));
            } catch (UnknownHostException e) {
              // Should never happen
              handler.handle(new DefaultFutureResult<InetAddress>(e));
            }
          }
        }
      });
    } catch (final UnknownHostException e) {
      // Should never happen as we work with ip addresses as input
      // anyway just in case notify the handler
      actualCtx.execute(new Runnable() {
        public void run() {
          handler.handle(new DefaultFutureResult<InetAddress>(e));

        }
      });
    }
    return this;
  }

  @SuppressWarnings("unchecked")
  private void lookup(final String name, final Handler handler, final int... types) {
    final DefaultFutureResult result = new DefaultFutureResult<>();
    result.setHandler(handler);
    lookup(dnsServers.iterator(), name, result, types);
  }

  @SuppressWarnings("unchecked")
  private void lookup(final Iterator<InetSocketAddress> dns, final String name, final DefaultFutureResult result, final int... types) {
    bootstrap.connect(dns.next()).addListener(new RetryChannelFutureListener(dns, name, result, types) {
      @Override
      public void onSuccess(ChannelFuture future) throws Exception {
        DnsQuery query = new DnsQuery(ThreadLocalRandom.current().nextInt());
        for (int type: types) {
          query.addQuestion(new DnsQuestion(name, type));
        }
        future.channel().writeAndFlush(query).addListener(new RetryChannelFutureListener(dns, name, result, types) {
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
                    records.add(record);
                  }

                  setResult(result, ctx.channel().eventLoop(), records);
                } else {
                  setResult(result, ctx.channel().eventLoop(), new DnsException(code));
                }
                ctx.close();
              }

              @Override
              public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                setResult(result, ctx.channel().eventLoop(), cause);
                ctx.close();
              }
            });
          }
        });
      }
    });
  }

  @SuppressWarnings("unchecked")
  private void setResult(final DefaultFutureResult r, EventLoop loop, final Object result) {
    if (r.complete()) {
      return;
    }
    if (actualCtx.isOnCorrectWorker(loop)) {
      try {
        vertx.setContext(actualCtx);
        if (result instanceof Throwable) {
          r.setFailure((Throwable) result);
        } else {
          r.setResult(result);
        }
      } catch (Throwable t) {
        actualCtx.reportException(t);
      }
    } else {
      actualCtx.execute(new Runnable() {
        public void run() {
          if (result instanceof Throwable) {
            r.setFailure((Throwable) result);
          } else {
            r.setResult(result);
          }
        }
      });
    }
  }

  private static final class HandlerAdapter<T> implements Handler<AsyncResult<List<T>>> {
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
          handler.handle(new DefaultFutureResult<>((T)null));
        } else {
          handler.handle(new DefaultFutureResult<>(result.get(0)));
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
        handler.handle(new DefaultFutureResult(records));
      }
    }

    protected abstract T convert(F entry);
  }

  private abstract class RetryChannelFutureListener implements ChannelFutureListener {
    private final Iterator<InetSocketAddress> dns;
    private final String name;
    private final DefaultFutureResult result;
    private final int[] types;

    RetryChannelFutureListener(final Iterator<InetSocketAddress> dns, final String name, final DefaultFutureResult result, final int... types) {
      this.dns = dns;
      this.name = name;
      this.result = result;
      this.types = types;
    }

    @Override
    public final void operationComplete(ChannelFuture future) throws Exception {
      if (!future.isSuccess()) {
        if (!result.complete()) {
          if (dns.hasNext()) {
            // try next dns server
            lookup(dns, name, result, types);
          } else {
            // TODO: maybe use a special exception ?
            result.setFailure(future.cause());
          }
        }
      } else {
        onSuccess(future);
      }
    }

    protected abstract void onSuccess(ChannelFuture future) throws Exception;
  }


}
