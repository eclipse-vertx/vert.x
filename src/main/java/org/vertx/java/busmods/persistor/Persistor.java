package org.vertx.java.busmods.persistor;

import org.vertx.java.busmods.BusModBase;
import org.vertx.java.core.Handler;
import org.vertx.java.core.app.VertxApp;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.JsonHelper;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.logging.Logger;

import java.util.Map;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class Persistor extends BusModBase {

  private static final Logger log = Logger.getLogger(Persistor.class);

  public Persistor(final String address) {
    super(address);
  }

  public void handle(Message message, Map<String, Object> json) {
    // TODO
  }

}
