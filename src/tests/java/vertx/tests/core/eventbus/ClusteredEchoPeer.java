package vertx.tests.core.eventbus;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ClusteredEchoPeer extends LocalEchoPeer {

  protected boolean isLocal() {
    return false;
  }
}
