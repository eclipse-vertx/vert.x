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

package io.vertx.core.http.impl;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientUpgradeHandler;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.util.collection.CharObjectMap;
import io.vertx.core.buffer.Buffer;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static io.netty.handler.codec.http2.Http2CodecUtil.HTTP_UPGRADE_SETTINGS_HEADER;
import static io.netty.util.CharsetUtil.UTF_8;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class VertxHttp2ClientUpgradeCodec implements HttpClientUpgradeHandler.UpgradeCodec {

  private static final List<CharSequence> UPGRADE_HEADERS = Collections.singletonList(HTTP_UPGRADE_SETTINGS_HEADER);

  private final io.vertx.core.http.Http2Settings settings;

  public VertxHttp2ClientUpgradeCodec(io.vertx.core.http.Http2Settings settings) {
    this.settings = settings;
  }

  @Override
  public CharSequence protocol() {
    return "h2c";
  }

  @Override
  public Collection<CharSequence> setUpgradeHeaders(ChannelHandlerContext ctx, HttpRequest upgradeRequest) {
    Http2Settings nettySettings = new Http2Settings();
    HttpUtils.fromVertxInitialSettings(false, settings, nettySettings);
    Buffer buf = Buffer.buffer();
    for (CharObjectMap.PrimitiveEntry<Long> entry : nettySettings.entries()) {
      buf.appendUnsignedShort(entry.key());
      buf.appendUnsignedInt(entry.value());
    }
    String encodedSettings = new String(java.util.Base64.getUrlEncoder().encode(buf.getBytes()), UTF_8);
    upgradeRequest.headers().set(HTTP_UPGRADE_SETTINGS_HEADER, encodedSettings);
    return UPGRADE_HEADERS;
  }

  @Override
  public void upgradeTo(ChannelHandlerContext ctx, FullHttpResponse upgradeResponse) throws Exception {
  }
}
