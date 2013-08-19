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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class DefaultDnsClient implements DnsClient {

  private final Bootstrap bootstrap;
  private final InetSocketAddress[] dnsServers;

  public DefaultDnsClient(VertxInternal vertx, InetSocketAddress... dnsServers) {
    if (dnsServers == null || dnsServers.length == 0) {
      throw new IllegalArgumentException("Need at least one default DNS Server");
    }
    this.dnsServers = dnsServers;
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

  @SuppressWarnings("unchecked")
  @Override
  public DnsClient lookup4(String record, final Handler<AsyncResult<Inet4Address>> handler) {
    final DefaultFutureResult result = new DefaultFutureResult<>();
    result.setHandler(new Handler<AsyncResult>() {
      @Override
      public void handle(AsyncResult event) {
        if (event.failed()) {
          handler.handle(event);
        } else {
          handler.handle(new DefaultFutureResult<>((Inet4Address) ((List)event.result()).get(0)));
        }
      }
    });
    lookup(record, result, DnsEntry.TYPE_AAAA);
    return this;
  }

  @SuppressWarnings("unchecked")
  @Override
  public DnsClient lookup6(String record, final Handler<AsyncResult<Inet6Address>> handler) {
    final DefaultFutureResult result = new DefaultFutureResult<>();
    result.setHandler(new Handler<AsyncResult>() {
      @Override
      public void handle(AsyncResult event) {
        if (event.failed()) {
          handler.handle(event);
        } else {
          handler.handle(new DefaultFutureResult<>((Inet6Address) ((List)event.result()).get(0)));
        }
      }
    });
    lookup(record, result, DnsEntry.TYPE_AAAA);
    return this;
  }

  @SuppressWarnings("unchecked")
  @Override
  public DnsClient lookup(String record, final Handler<AsyncResult<InetAddress>> handler) {
    final DefaultFutureResult result = new DefaultFutureResult<>();
    result.setHandler(new Handler<AsyncResult>() {
      @Override
      public void handle(AsyncResult event) {
        if (event.failed()) {
          handler.handle(event);
        } else {
          handler.handle(new DefaultFutureResult<>((InetAddress) ((List)event.result()).get(0)));
        }
      }
    });
    lookup(record, result, DnsEntry.TYPE_A, DnsEntry.TYPE_AAAA);
    return this;
  }

  @SuppressWarnings("unchecked")
  @Override
  public DnsClient lookupARecords( String record, final Handler<AsyncResult<List<InetAddress>>> handler) {
    final DefaultFutureResult result = new DefaultFutureResult<>();
    result.setHandler(handler);
    lookup(record, result, DnsEntry.TYPE_A);
    return this;
  }

  @SuppressWarnings("unchecked")
  @Override
  public DnsClient lookupCName(String record, Handler<AsyncResult<String>> handler) {
    final DefaultFutureResult result = new DefaultFutureResult<>();
    result.setHandler(handler);
    lookup(record, result, DnsEntry.TYPE_CNAME);
    return this;
  }

  @SuppressWarnings("unchecked")
  @Override
  public DnsClient lookupMXRecords( String record, final Handler<AsyncResult<List<MxRecord>>> handler) {
    final DefaultFutureResult result = new DefaultFutureResult<>();
    result.setHandler(new Handler<AsyncResult>() {
      @Override
      public void handle(AsyncResult event) {
        if (event.failed()) {
          handler.handle(event);
        } else {
          List<Object> records = (List<Object>) event.result();
          for (int i = 0; i < records.size(); i++) {
            MailExchangerRecord mx = (MailExchangerRecord) records.get(i);
            records.set(i, new DefaultMxRecord(mx));
          }
          handler.handle(new DefaultFutureResult(records));
        }
      }
    });
    lookup(record, result, DnsEntry.TYPE_A);
    return this;
  }

  @SuppressWarnings("unchecked")
  @Override
  public DnsClient lookupTXTRecords(String record, Handler<AsyncResult<List<String>>> handler) {
    final DefaultFutureResult result = new DefaultFutureResult<>();
    result.setHandler(handler);
    lookup(record, result, DnsEntry.TYPE_TXT);
    return this;
  }

  @SuppressWarnings("unchecked")
  @Override
  public DnsClient lookupPTRRecord(String record, Handler<AsyncResult<List>> handler) {
    final DefaultFutureResult result = new DefaultFutureResult<>();
    result.setHandler(handler);
    lookup(record, result, DnsEntry.TYPE_PTR);
    return this;
  }

  @SuppressWarnings("unchecked")
  @Override
  public DnsClient lookupAAAARecord(String record, Handler<AsyncResult<List<InetAddress>>> handler) {
    final DefaultFutureResult result = new DefaultFutureResult<>();
    result.setHandler(handler);
    lookup(record, result, DnsEntry.TYPE_AAAA);
    return this;
  }

  @SuppressWarnings("unchecked")
  private void lookup(final String record, final DefaultFutureResult result, final int... types) {
    bootstrap.connect(chooseDnsServer()).addListener(new ChannelFutureListener() {
      @Override
      public void operationComplete(ChannelFuture future) throws Exception {
        if (!future.isSuccess()) {
          if (!result.complete()) {
            result.setFailure(future.cause());
          }
        } else {
          DnsQuery query = new DnsQuery(ThreadLocalRandom.current().nextInt());
          for (int type: types) {
            query.addQuestion(new DnsQuestion(record, type));
          }
          future.channel().write(query).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
              if (!future.isSuccess()) {
                if (!result.complete()) {
                  result.setFailure(future.cause());
                }
              } else {
                // write was successful add the handler now which will handle the responses
                future.channel().pipeline().addLast(new SimpleChannelInboundHandler<DnsResponse>() {
                  @Override
                  protected void channelRead0(ChannelHandlerContext ctx, DnsResponse msg) throws Exception {
                    List<DnsResource> resources = msg.getAnswers();

                    List<Object> records = new ArrayList<>(resources.size());
                    for (DnsResource resource : msg.getAnswers()) {
                      Object record = RecordDecoderFactory.getFactory().decode(resource.type(), msg, resource);
                      records.add(record);
                    }
                    if (!result.complete()) {
                      result.setResult(records);
                    }
                    ctx.close();
                  }

                  @Override
                  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                    if (!result.complete()) {
                      result.setFailure(cause);
                    }
                  }
                });
              }
            }
          });
        }
      }
    });
  }

  private InetSocketAddress chooseDnsServer() {
    // TODO: Round-robin ?
    return dnsServers[0];
  }
}
