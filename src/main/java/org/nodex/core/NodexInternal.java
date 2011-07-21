package org.nodex.core;

import org.jboss.netty.channel.socket.nio.NioWorker;
import org.jboss.netty.channel.socket.nio.NioWorkerPool;

import java.util.concurrent.Executor;

/**
 * User: timfox
 * Date: 20/07/2011
 * Time: 18:57
 */
public interface NodexInternal extends Nodex {
  static NodexInternal instance = new NodexImpl();

  NioWorkerPool getWorkerPool();

  Executor getAcceptorPool();

  void executeOnContext(String contextID, Runnable runnable);

  String createContext(NioWorker worker);

  boolean destroyContext(String contextID);

  void setContextID(String contextID);
}
