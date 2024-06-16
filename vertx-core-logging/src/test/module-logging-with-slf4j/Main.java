package logging;

import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.internal.logging.Logger;

public class Main {
  public static void main(String[] args) {
    Logger logger = LoggerFactory.getLogger("com.example");
    String implementation = logger.implementation();
    if (!implementation.equals("slf4j")) {
      throw new AssertionError("Was expecting slf4j instead of " + implementation);
    }
  }
}
