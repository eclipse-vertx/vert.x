package vertx.tests.core.eventbus;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ClusteredEchoClient extends LocalEchoClient {

  protected boolean isLocal() {
    return false;
  }
}
