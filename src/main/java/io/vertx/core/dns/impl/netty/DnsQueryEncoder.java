/*
 * Copyright (c) 2013 The Netty Project
 * ------------------------------------
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
package io.vertx.core.dns.impl.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.CharsetUtil;

import java.nio.charset.Charset;
import java.util.List;

/**
 * DnsQueryEncoder accepts {@link DnsQuery} and encodes to {@link io.netty.buffer.ByteBuf}. This
 * class also contains methods for encoding parts of DnsQuery's such as the
 * header and questions.
 */
public class DnsQueryEncoder extends MessageToByteEncoder<DnsQuery> {

  /**
   * Encodes the information in a {@link DnsQueryHeader} and writes it to the
   * specified {@link io.netty.buffer.ByteBuf}. The header is always 12 bytes long.
   *
   * @param header the query header being encoded
   * @param buf    the buffer the encoded data should be written to
   */
  private static void encodeHeader(DnsQueryHeader header, ByteBuf buf) {
    buf.writeShort(header.getId());
    int flags = 0;
    flags |= header.getType() << 15;
    flags |= header.getOpcode() << 14;
    flags |= header.isRecursionDesired() ? 1 << 8 : 0;
    buf.writeShort(flags);
    buf.writeShort(header.questionCount());
    buf.writeShort(header.answerCount()); // Must be 0
    buf.writeShort(header.authorityResourceCount()); // Must be 0
    buf.writeShort(header.additionalResourceCount()); // Must be 0
  }

  /**
   * Encodes the information in a {@link DnsQuestion} and writes it to the
   * specified {@link io.netty.buffer.ByteBuf}.
   *
   * @param question the question being encoded
   * @param charset  charset names are encoded in
   * @param buf      the buffer the encoded data should be written to
   */
  private static void encodeQuestion(DnsQuestion question, Charset charset, ByteBuf buf) {
    String[] parts = question.name().split("\\.");
    for (int i = 0; i < parts.length; i++) {
      buf.writeByte(parts[i].length());
      buf.writeBytes(charset.encode(parts[i]));
    }
    buf.writeByte(0); // marks end of name field
    buf.writeShort(question.type());
    buf.writeShort(question.dnsClass());
  }

  /**
   * Encodes a query and writes it to a {@link io.netty.buffer.ByteBuf}.
   *
   * @param query the {@link DnsQuery} being encoded
   * @param buf   the {@link io.netty.buffer.ByteBuf} the query will be written to
   */
  protected static void encodeQuery(DnsQuery query, ByteBuf buf) {
    encodeHeader(query.getHeader(), buf);
    List<DnsQuestion> questions = query.getQuestions();
    for (DnsQuestion question : questions) {
      encodeQuestion(question, CharsetUtil.UTF_8, buf);
    }
  }

  /**
   * Encodes a query and writes it to a {@link io.netty.buffer.ByteBuf}. Queries are sent to a
   * DNS server and a response will be returned from the server. The encoded
   * ByteBuf is written to the specified {@link io.netty.buffer.ByteBuf}.
   *
   * @param ctx   the {@link io.netty.channel.ChannelHandlerContext} this {@link io.vertx.core.dns.impl.netty.DnsQueryEncoder}
   *              belongs to
   * @param query the query being encoded
   * @param out   the {@link io.netty.buffer.ByteBuf} to which encoded messages should be
   *              written
   * @throws Exception
   */
  @Override
  protected void encode(ChannelHandlerContext ctx, DnsQuery query, ByteBuf out) throws Exception {
    encodeQuery(query, out);
  }

}
