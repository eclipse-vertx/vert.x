/**
 * 
 */
package io.vertx.core.net.impl;

import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;

/**
 * some additional operations we need to set up the ChannelProvider for a proxy
 * e.g. for setting up ssl or the HttpCodec
 *
 * @author <a href="http://oss.lehmann.cx/">Alexander Lehmann</a>
 *
 */
public interface ChannelProviderAdditionalOperations {
  void channelStartup(Channel ch);
  void pipelineSetup(ChannelPipeline pipeline);
  void pipelineDeprov(ChannelPipeline pipeline);
}
