package org.vertx.java.core.datagram.impl;

import org.vertx.java.core.datagram.DatagramClient;
import org.vertx.java.core.datagram.DatagramPacket;
import org.vertx.java.core.impl.VertxInternal;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class DefaultDatagramClient extends AbstractDatagramSupport<DatagramClient> implements DatagramClient {

  public DefaultDatagramClient(VertxInternal vertx) {
    super(vertx, null);
  }
}
