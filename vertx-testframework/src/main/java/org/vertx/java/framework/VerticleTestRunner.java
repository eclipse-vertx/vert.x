package org.vertx.java.framework;

import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class VerticleTestRunner extends BlockJUnit4ClassRunner {
  private VertxTestFixture fixture = new VertxTestFixture();

  public VerticleTestRunner(Class<?> klass) throws InitializationError {
    super(klass);
  }

  @Override
  protected List<FrameworkMethod> computeTestMethods() {
    List<FrameworkMethod> result = new ArrayList<>();
    for (Method method : getTestClass().getJavaClass().getMethods()) {
      if (method.getName().startsWith("test") &&
          Modifier.isPublic(method.getModifiers()) &&
          !Modifier.isStatic(method.getModifiers()) &&
          method.getParameterTypes().length == 0) {
        result.add(new FrameworkMethod(method));
      }
    }
    return result;
  }

  @Override
  protected Object createTest() throws Exception {
    fixture.setUp();
    return fixture.startApp(false, getTestClass().getJavaClass().getName(), null, 1, true);
  }

  @Override
  protected Statement methodInvoker(final FrameworkMethod method, Object test) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        fixture.startTest(method.getName(), true);
        fixture.tearDown();
      }
    };
  }
}
