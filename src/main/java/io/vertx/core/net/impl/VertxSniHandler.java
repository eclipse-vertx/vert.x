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
package io.vertx.core.net.impl;

import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.ssl.ReferenceCountedOpenSslEngine;
import io.netty.handler.ssl.SniHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.internal.tcnative.SSL;
import io.netty.util.AsyncMapping;
import io.vertx.core.net.HostAndPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLEngine;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Extend the {@code SniHandler} to support delegated  task executor
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class VertxSniHandler extends SniHandler {

  private final Executor delegatedTaskExec;
  private final HostAndPort remoteAddress;
  private final boolean useHybrid;

  private static final Logger log = LoggerFactory.getLogger(SslChannelProvider.class);

  public VertxSniHandler(AsyncMapping<? super String, ? extends SslContext> mapping, long handshakeTimeoutMillis, Executor delegatedTaskExec,
      boolean useHybrid, HostAndPort remoteAddress) {
    super(mapping, handshakeTimeoutMillis);

    this.delegatedTaskExec = delegatedTaskExec;
    this.useHybrid = useHybrid;
    this.remoteAddress = remoteAddress;
  }

  @Override
  protected SslHandler newSslHandler(SslContext context, ByteBufAllocator allocator) {
    SslHandler sslHandler;
    if (remoteAddress != null) {
      sslHandler = context.newHandler(allocator, remoteAddress.host(), remoteAddress.port(), delegatedTaskExec);
    } else {
      sslHandler = context.newHandler(allocator, delegatedTaskExec);
    }
    if(useHybrid){
      SSLEngine engine = sslHandler.engine();
      try {
        long sslPtr = ((ReferenceCountedOpenSslEngine) engine).sslPointer();
        boolean success = SSL.setCurvesList(sslPtr, "X25519MLKEM768");
        if (!success) {
          throw new Exception("Failed to set hybrid PQC groups on SSL instance");
        }
      } catch (Exception e) {
        /*
          todo : would like to throw instead of returning null to be consistent with
           io.vertx.core.net.impl.SslChannelProvider.createServerSslHandler(...) but can't as we extend a netty class here.
         */
        return null;
      }
    }
    sslHandler.setHandshakeTimeout(handshakeTimeoutMillis, TimeUnit.MILLISECONDS);
    return sslHandler;
  }
}
