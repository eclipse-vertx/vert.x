package vertx.tests.core.eventbus;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ClusteredClient extends LocalClient {

  protected boolean isLocal() {
    return false;
  }
}
