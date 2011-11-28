package org.vertx.java.core.app.cli;

import org.vertx.java.core.Handler;
import org.vertx.java.core.app.AppManager;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.net.NetSocket;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class VertxCommand implements Serializable {

  private static final Logger log = Logger.getLogger(VertxCommand.class);

  public abstract void execute(AppManager appMgr) throws Exception;

  public boolean isBlock() {
    return true;
  }

  public void write(NetSocket socket, Handler<Void> doneHandler) {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      oos.writeObject(this);
      oos.flush();
      byte[] bytes = baos.toByteArray();
      Buffer buff = Buffer.create(4 + bytes.length);
      buff.appendInt(bytes.length);
      buff.appendBytes(bytes);
      if (doneHandler == null) {
        socket.write(buff);
      } else {
        socket.write(buff, doneHandler);
      }
    } catch (Exception e) {
      log.error("Faile to write command", e);
    }
  }

  public static VertxCommand read(Buffer buff) throws Exception {
    byte[] bytes = buff.getBytes();
    ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
    ObjectInputStream oois = new ObjectInputStream(bais);
    VertxCommand cmd = (VertxCommand)oois.readObject();
    return cmd;
  }
}
