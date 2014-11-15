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
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.util.CharsetUtil;

import java.util.List;

/**
 * DnsResponseDecoder accepts {@link io.netty.channel.socket.DatagramPacket} and encodes to
 * {@link DnsResponse}. This class also contains methods for decoding parts of
 * DnsResponses such as questions and resource records.
 */
public class DnsResponseDecoder extends MessageToMessageDecoder<DatagramPacket> {

  /**
   * Retrieves a domain name given a buffer containing a DNS packet. If the
   * name contains a pointer, the position of the buffer will be set to
   * directly after the pointer's index after the name has been read.
   *
   * @param buf the byte buffer containing the DNS packet
   * @return the domain name for an entry
   */
  public static String readName(ByteBuf buf) {
    int position = -1;
    StringBuilder name = new StringBuilder();
    for (int len = buf.readUnsignedByte(); buf.isReadable() && len != 0; len = buf.readUnsignedByte()) {
      boolean pointer = (len & 0xc0) == 0xc0;
      if (pointer) {
        if (position == -1) {
          position = buf.readerIndex() + 1;
        }
        buf.readerIndex((len & 0x3f) << 8 | buf.readUnsignedByte());
      } else {
        name.append(buf.toString(buf.readerIndex(), len, CharsetUtil.UTF_8)).append(".");
        buf.skipBytes(len);
      }
    }
    if (position != -1) {
      buf.readerIndex(position);
    }
    if (name.length() == 0) {
      return null;
    }
    return name.substring(0, name.length() - 1);
  }

  /**
   * Retrieves a domain name given a buffer containing a DNS packet without
   * advancing the readerIndex for the buffer.
   *
   * @param buf    the byte buffer containing the DNS packet
   * @param offset the position at which the name begins
   * @return the domain name for an entry
   */
  public static String getName(ByteBuf buf, int offset) {
    StringBuilder name = new StringBuilder();
    for (int len = buf.getUnsignedByte(offset++); buf.writerIndex() > offset && len != 0; len = buf
      .getUnsignedByte(offset++)) {
      boolean pointer = (len & 0xc0) == 0xc0;
      if (pointer) {
        offset = (len & 0x3f) << 8 | buf.getUnsignedByte(offset++);
      } else {
        name.append(buf.toString(offset, len, CharsetUtil.UTF_8)).append(".");
        offset += len;
      }
    }
    if (name.length() == 0) {
      return null;
    }
    return name.substring(0, name.length() - 1);
  }

  /**
   * Decodes a question, given a DNS packet in a byte buffer.
   *
   * @param buf the byte buffer containing the DNS packet
   * @return a decoded {@link DnsQuestion}
   */
  private static DnsQuestion decodeQuestion(ByteBuf buf) {
    String name = readName(buf);
    int type = buf.readUnsignedShort();
    int qClass = buf.readUnsignedShort();
    return new DnsQuestion(name, type, qClass);
  }

  /**
   * Decodes a resource record, given a DNS packet in a byte buffer.
   *
   * @param buf the byte buffer containing the DNS packet
   * @return a {@link DnsResource} record containing response data
   */
  private static DnsResource decodeResource(ByteBuf buf, ByteBufAllocator allocator) {
    String name = readName(buf);
    int type = buf.readUnsignedShort();
    int aClass = buf.readUnsignedShort();
    long ttl = buf.readUnsignedInt();
    int len = buf.readUnsignedShort();
    ByteBuf resourceData = allocator.buffer(len);
    int contentIndex = buf.readerIndex();
    resourceData.writeBytes(buf, len);
    return new DnsResource(name, type, aClass, ttl, contentIndex, resourceData);
  }

  /**
   * Decodes a DNS response header, given a DNS packet in a byte buffer.
   *
   * @param parent the parent {@link DnsResponse} to this header
   * @param buf    the byte buffer containing the DNS packet
   * @return a {@link DnsResponseHeader} containing the response's header
   * information
   */
  private static DnsResponseHeader decodeHeader(DnsResponse parent, ByteBuf buf) {
    int id = buf.readUnsignedShort();
    DnsResponseHeader header = new DnsResponseHeader(parent, id);
    int flags = buf.readUnsignedShort();
    header.setType(flags >> 15);
    header.setOpcode(flags >> 11 & 0xf);
    header.setRecursionDesired((flags >> 8 & 1) == 1);
    header.setAuthoritativeAnswer((flags >> 10 & 1) == 1);
    header.setTruncated((flags >> 9 & 1) == 1);
    header.setRecursionAvailable((flags >> 7 & 1) == 1);
    header.setZ(flags >> 4 & 0x7);
    header.setResponseCode(flags & 0xf);
    header.setReadQuestions(buf.readUnsignedShort());
    header.setReadAnswers(buf.readUnsignedShort());
    header.setReadAuthorityResources(buf.readUnsignedShort());
    header.setReadAdditionalResources(buf.readUnsignedShort());
    return header;
  }

  /**
   * Decodes a full DNS response packet.
   *
   * @param buf the raw DNS response packet
   * @return the decoded {@link DnsResponse}
   */
  protected static DnsResponse decodeResponse(ByteBuf buf, ByteBufAllocator allocator) {
    DnsResponse response = new DnsResponse(buf);
    DnsResponseHeader header = decodeHeader(response, buf);
    response.setHeader(header);
    for (int i = 0; i < header.getReadQuestions(); i++) {
      response.addQuestion(decodeQuestion(buf));
    }
    if (header.getResponseCode() != 0) {
      // TODO: Provide patch upstream to Netty
      return response;
    }
    for (int i = 0; i < header.getReadAnswers(); i++) {
      response.addAnswer(decodeResource(buf, allocator));
    }
    for (int i = 0; i < header.getReadAuthorityResources(); i++) {
      response.addAuthorityResource(decodeResource(buf, allocator));
    }
    for (int i = 0; i < header.getReadAdditionalResources(); i++) {
      response.addAdditionalResource(decodeResource(buf, allocator));
    }
    return response;
  }

  /**
   * Decodes a response from a {@link io.netty.channel.socket.DatagramPacket} containing a
   * {@link io.netty.buffer.ByteBuf} with a DNS packet. Responses are sent from a DNS server
   * to a client in response to a query. This method writes the decoded
   * response to the specified {@link java.util.List} to be handled by a specialized
   * message handler.
   *
   * @param ctx    the {@link io.netty.channel.ChannelHandlerContext} this
   *               {@link io.vertx.core.dns.impl.netty.DnsResponseDecoder} belongs to
   * @param packet the message being decoded, a {@link io.netty.channel.socket.DatagramPacket} containing
   *               a DNS packet
   * @param out    the {@link java.util.List} to which decoded messages should be added
   * @throws Exception
   */
  @Override
  protected void decode(ChannelHandlerContext ctx, DatagramPacket packet, List<Object> out) throws Exception {
    out.add(decodeResponse(packet.content(), ctx.alloc()).retain());
  }

}
