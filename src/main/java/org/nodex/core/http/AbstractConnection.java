package org.nodex.core.http;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.nodex.core.ConnectionBase;

/**
 * User: timfox
 * Date: 29/07/2011
 * Time: 16:47
 */
public abstract class AbstractConnection extends ConnectionBase {

  protected AbstractConnection(Channel channel, String contextID, Thread th) {
    super(channel, contextID, th);
  }

  public ChannelFuture write(Object obj) {
    return channel.write(obj);
  }

}
