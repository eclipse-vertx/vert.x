package org.vertx.scala.examples.echo


/**
 * @author janmachacek
 */

import org.vertx.java.core.Handler
import org.vertx.java.core.buffer.Buffer
import org.vertx.java.core.net.NetSocket
import org.vertx.java.deploy.Verticle

class EchoClient extends Verticle {

  def start() {
    vertx.createNetClient.connect(1234, "localhost", new Handler[NetSocket] {
      def handle(socket: NetSocket) {
        socket.dataHandler(new Handler[Buffer] {
          def handle(buffer: Buffer) {
            System.out.println("Net client receiving: " + buffer)
          }
        })

        for (i <- 0 until 10) {
          val str: String = "hello" + i + "\n"
          System.out.print("Net client sending: " + str)
          socket.write(new Buffer(str))
        }
      }
    })
  }
}

