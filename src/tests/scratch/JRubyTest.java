import junit.framework.TestCase;
import org.jruby.embed.LocalContextScope;
import org.jruby.embed.ScriptingContainer;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JRubyTest extends TestCase {

  public void testCatchRubyException() throws Exception {

    ScriptingContainer container = new ScriptingContainer(LocalContextScope.SINGLETHREAD);
    String script =
        "\nraise 'Foo'";

    try {
      container.runScriptlet(script);
    } catch (Throwable t) {
      System.out.println("Caught the exception in Java: " + t.getMessage());
    }
  }
}
