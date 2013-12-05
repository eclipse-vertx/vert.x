package org.vertx.java.core.http.impl.cgbystrom;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.util.CharsetUtil;

import java.util.List;

/**
 * A Flash policy file handler
 * Will detect connection attempts made by Adobe Flash clients and return a policy file response
 *
 * After the policy has been sent, it will instantly close the connection.
 * If the first bytes sent are not a policy file request the handler will simply remove itself
 * from the pipeline.
 *
 * Read more at http://www.adobe.com/devnet/articles/crossdomain_policy_file_spec.html
 *
 * Example usage:
 * <code>
 * ChannelPipeline pipeline = Channels.pipeline();
 * pipeline.addLast("flashPolicy", new FlashPolicyHandler());
 * pipeline.addLast("decoder", new MyProtocolDecoder());
 * pipeline.addLast("encoder", new MyProtocolEncoder());
 * pipeline.addLast("handler", new MyBusinessLogicHandler());
 * </code>
 *
 * For license see LICENSE file in this directory
 */
public class FlashPolicyHandler extends ByteToMessageDecoder {
  private static final String XML = "<cross-domain-policy><allow-access-from domain=\"*\" to-ports=\"*\" /></cross-domain-policy>";

  @Override
  protected void decode(ChannelHandlerContext ctx, ByteBuf buffer, List<Object> out) throws Exception {
    if (buffer.readableBytes() < 2) {
      return;
    }

    final int magic1 = buffer.getUnsignedByte(buffer.readerIndex());
    final int magic2 = buffer.getUnsignedByte(buffer.readerIndex() + 1);
    boolean isFlashPolicyRequest = (magic1 == '<' && magic2 == 'p');

    if (isFlashPolicyRequest) {
      // Discard everything
      buffer.skipBytes(buffer.readableBytes());

      ctx.writeAndFlush(Unpooled.copiedBuffer(XML, CharsetUtil.UTF_8)).addListener(ChannelFutureListener.CLOSE);
      return;
    }

    // Remove ourselves and forward bytes to next handler, important since the byte length check at top can hinder frame decoding
    // down the pipeline
    ctx.pipeline().remove(this);
  }
}