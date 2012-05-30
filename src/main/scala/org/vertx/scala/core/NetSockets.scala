package org.vertx.scala.core

import org.vertx.java.core.net.NetSocket
import org.vertx.java.core.Handler

/**
 * This trait contains nicer APIs for dealing with NetSocket infrastructure. You can define
 * functions NetSocket => Unit, which will be converted to Handler[NetSocket]
 *
 * @author janmachacek
 */
trait NetSockets {

  type NetSocketHandler = NetSocket => Unit

  /**
   * This is a poor attempt at tidying up the API, but it demonstrates the principle
   */
  implicit def toHandler(netSocketHandler: NetSocketHandler) = new Handler[NetSocket] {
    def handle(event: NetSocket) {
      netSocketHandler(event)
    }
  }

}
