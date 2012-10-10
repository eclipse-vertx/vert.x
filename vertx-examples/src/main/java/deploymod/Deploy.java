package deploymod;

import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.deploy.Verticle;

public class Deploy extends Verticle {

  public void start() {
    System.out.println("Deploying module");

    JsonObject conf = new JsonObject().putString("some-var", "hello");

    container.deployModule("org.foo.MyMod-v1.0", conf, 1, new Handler<String>() {
      public void handle(String deploymentID) {
        System.out.println("This gets called when deployment is complete, deployment id is " + deploymentID);
      }
    });
  }
}