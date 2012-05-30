package org.vertx.scala.deploy

import org.vertx.java.core.net.NetSocket
import org.vertx.java.core.Handler

/**
 * @author janmachacek
 */
trait NetSockets {

  type NetSocketHandler = NetSocket => Unit

  implicit def toHandler(netSocketHandler: NetSocketHandler) = new Handler[NetSocket] {
    def handle(event: NetSocket) {  netSocketHandler(event) }
  }

}
