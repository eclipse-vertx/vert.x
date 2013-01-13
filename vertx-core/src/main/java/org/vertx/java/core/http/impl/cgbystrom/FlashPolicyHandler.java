package org.vertx.java.core.http.impl.cgbystrom;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.handler.codec.frame.FrameDecoder;
import org.jboss.netty.util.CharsetUtil;

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
public class FlashPolicyHandler extends FrameDecoder {
    private static final String XML = "<cross-domain-policy><allow-access-from domain=\"*\" to-ports=\"*\" /></cross-domain-policy>";
    private ChannelBuffer policyResponse = ChannelBuffers.copiedBuffer(XML, CharsetUtil.UTF_8);

    /**
     * Creates a handler allowing access from any domain and any port
     */
    public FlashPolicyHandler() {
        super();
    }

    /**
     * Create a handler with a custom XML response. Useful for defining your own domains and ports.
     * @param policyResponse Response XML to be passed back to a connecting client
     */
    public FlashPolicyHandler(ChannelBuffer policyResponse) {
        super();
        this.policyResponse = policyResponse;
    }

    protected Object decode(ChannelHandlerContext ctx, Channel channel, ChannelBuffer buffer) throws Exception {
        if (buffer.readableBytes() < 2) {
            return null;
        }

        final int magic1 = buffer.getUnsignedByte(buffer.readerIndex());
        final int magic2 = buffer.getUnsignedByte(buffer.readerIndex() + 1);
        boolean isFlashPolicyRequest = (magic1 == '<' && magic2 == 'p');

        if (isFlashPolicyRequest) {
            // Discard everything
            buffer.skipBytes(buffer.readableBytes());

            // Make sure we don't have any downstream handlers interfering with our injected write of policy request.
            removeAllPipelineHandlers(channel.getPipeline());
            channel.write(policyResponse).addListener(ChannelFutureListener.CLOSE);
            return null;
        }

        // Remove ourselves, important since the byte length check at top can hinder frame decoding
        // down the pipeline
        ctx.getPipeline().remove(this);
        return buffer.readBytes(buffer.readableBytes());
    }
    
    private void removeAllPipelineHandlers(ChannelPipeline pipeline) {
        while (pipeline.getFirst() != null) {
            pipeline.removeFirst();
        }
    }
}