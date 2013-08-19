/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.vertx.java.core.dns.impl;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.dns.DnsClient;
import org.vertx.java.core.dns.DnsResponseCode;
import org.vertx.java.core.dns.DnsException;
import org.vertx.java.core.dns.MxRecord;
import org.vertx.java.core.dns.impl.netty.*;
import org.vertx.java.core.dns.impl.netty.decoder.RecordDecoderFactory;
import org.vertx.java.core.dns.impl.netty.decoder.record.MailExchangerRecord;
import org.vertx.java.core.impl.DefaultContext;
import org.vertx.java.core.impl.DefaultFutureResult;
import org.vertx.java.core.impl.VertxInternal;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class DefaultDnsClient implements DnsClient {

  private final Bootstrap bootstrap;
  private final List<InetSocketAddress> dnsServers;

  public DefaultDnsClient(VertxInternal vertx, InetSocketAddress... dnsServers) {
    if (dnsServers == null || dnsServers.length == 0) {
      throw new IllegalArgumentException("Need at least one default DNS Server");
    }

    // use LinkedList as we will traverse it all the time
    this.dnsServers = new LinkedList<>(Arrays.asList(dnsServers));
    DefaultContext actualCtx = vertx.getOrCreateContext();

    bootstrap = new Bootstrap();
    bootstrap.group(actualCtx.getEventLoop());
    bootstrap.channel(NioDatagramChannel.class);
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
  public DnsClient resolveCNAME(String name, Handler<AsyncResult<String>> handler) {
    lookup(name, handler, DnsEntry.TYPE_CNAME);
    return this;
  }

  @Override
  public DnsClient resolveMX(String name, final Handler<AsyncResult<List<MxRecord>>> handler) {
    lookup(name, new Handler<AsyncResult>() {
      @SuppressWarnings("unchecked")
      @Override
      public void handle(AsyncResult event) {
        if (event.failed()) {
          handler.handle(event);
        } else {
          List records = (List) event.result();
          for (int i = 0; i < records.size(); i++) {
            MailExchangerRecord mx = (MailExchangerRecord) records.get(i);
            records.set(i, new DefaultMxRecord(mx));
          }
          Collections.sort((List<DefaultMxRecord>) records);
          handler.handle(new DefaultFutureResult(records));
        }
      }
    }, DnsEntry.TYPE_MX);
    return this;
  }

  @Override
  public DnsClient resolveTXT(String name, final Handler<AsyncResult<List<String>>> handler) {
    lookup(name, new Handler<AsyncResult>() {
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
  public DnsClient resolveSRV(String name, Handler<AsyncResult<List<String>>> handler) {
    lookup(name, handler, DnsEntry.TYPE_SRV);
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
  private static void setResult(DefaultFutureResult r, Object result) {
    if (r.complete()) {
      return;
    }
    if (result instanceof Throwable) {
      r.setFailure((Throwable) result);
    } else {
      r.setResult(result);
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
