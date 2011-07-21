package org.nodex.core.net;

import org.jboss.netty.channel.socket.nio.NioSocketChannel;

/**
 * User: timfox
 * Date: 21/07/2011
 * Time: 21:01
 */
public class NetBase {

  /*
  Currently Netty does not provide all events for a connection on the same thread - e.g. connection open
  connection bound etc are provided on the acceptor thread.
  In node.x we must ensure all events are executed on the correct event loop for the context
  So for now we need to do this manually by checking the thread and executing it on the event loop
  thread if it's not the right one.
  This code will go away if Netty acts like a proper event loop.
   */
  protected void runOnCorrectThread(NioSocketChannel nch, Runnable runnable) {
    if (Thread.currentThread() != nch.getWorker().getThread()) {
      nch.getWorker().scheduleOtherTask(runnable);
    } else {
      runnable.run();
    }
  }
}
