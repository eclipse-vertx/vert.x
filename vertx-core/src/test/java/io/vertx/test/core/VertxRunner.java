package io.vertx.test.core;

import io.netty.util.internal.PlatformDependent;
import org.junit.*;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.*;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class VertxRunner extends BlockJUnit4ClassRunner {

  private final TestClass testClass;
  private Map<String, Object> classAttributes = new HashMap<>();

  // To remove
  private static final LinkedList<Long> timeoutStack = new LinkedList<>();

  public VertxRunner(Class<?> klass) throws InitializationError {
    super(klass);
    this.testClass = new TestClass(klass);
  }

  @Override
  protected void validatePublicVoidNoArgMethods(Class<? extends Annotation> annotation, boolean isStatic, List<Throwable> errors) {
    if (annotation == Test.class || annotation == Before.class || annotation == After.class ||
        annotation == BeforeClass.class || annotation == AfterClass.class) {
      List<FrameworkMethod> fMethods = getTestClass().getAnnotatedMethods(annotation);
      for (FrameworkMethod fMethod : fMethods) {
        fMethod.validatePublicVoid(isStatic, errors);
        try {
          validateTestMethod(fMethod);
        } catch (Exception e) {
          errors.add(e);
        }
      }
    } else {
      super.validatePublicVoidNoArgMethods(annotation, isStatic, errors);
    }
  }

  protected void validateTestMethod(FrameworkMethod fMethod) throws Exception {
    Class<?>[] paramTypes = fMethod.getMethod().getParameterTypes();
    for (Class<?> paramType : paramTypes) {
      if (paramType != Checkpoint.class) {
        throw new Exception("Method " + fMethod.getName() + " should have no parameters or " +
            "the " + Checkpoint.class.getName() + " parameter");
      }
    }
  }

  @Override
  protected Statement methodInvoker(FrameworkMethod method, Object test) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        invokeTestMethod(method, test);
      }
    };
  }

  protected void invokeTestMethod(FrameworkMethod fMethod, Object test) throws InvocationTargetException, IllegalAccessException, TimeoutException, InterruptedException {
    Method method = fMethod.getMethod();
    Class<?>[] paramTypes = method.getParameterTypes();
    Checkpoint[] params = new Checkpoint[paramTypes.length];
    for (int i = 0;i < paramTypes.length;i++) {
      params[i] = new Checkpoint();
    }
    try {
      method.invoke(test, (Object[]) params);
    } catch (InvocationTargetException e) {
      PlatformDependent.throwException(e.getCause());
    } catch (IllegalAccessException e) {
      PlatformDependent.throwException(e);
    }
    for (Checkpoint checkpoint : params) {
      if (!checkpoint.latch.await(10, TimeUnit.SECONDS)) {
        throw new TimeoutException();
      }
      checkpoint.await();
      checkpoint.awaitSuccess();
    }
  }

  @Override
  protected Statement methodBlock(FrameworkMethod method) {
    Statement statement = super.methodBlock(method);
    return statement;
  }

  @Override
  protected Statement withBefores(FrameworkMethod method, Object target, Statement statement) {
    return withBefores(getTestClass().getAnnotatedMethods(Before.class), target, statement);
  }

  @Override
  protected Statement withAfters(FrameworkMethod method, Object target, Statement statement) {
    List<FrameworkMethod> afters = getTestClass().getAnnotatedMethods(After.class);
    return withAfters(afters, target, statement);
  }

  @Override
  protected Statement withBeforeClasses(Statement statement) {
    List<FrameworkMethod> befores = testClass.getAnnotatedMethods(BeforeClass.class);
    return withBefores(befores, null, statement);
  }

  @Override
  protected Statement withAfterClasses(Statement statement) {
    List<FrameworkMethod> afters = getTestClass().getAnnotatedMethods(AfterClass.class);
    return withAfters(afters, null, statement);
  }

  @Override
  protected Statement withPotentialTimeout(FrameworkMethod method, Object test, Statement next) {
    // Need to be a noop since we handle that without a wrapping statement
    return next;
  }

  private Statement withBefores(List<FrameworkMethod> befores, Object target, Statement statement) {
    if (befores.isEmpty()) {
      return statement;
    } else {
      return new Statement() {
        @Override
        public void evaluate() throws Throwable {
          for (FrameworkMethod before : befores) {
            invokeTestMethod(before, target);
          }
          statement.evaluate();
        }
      };
    }
  }

  private Statement withAfters(List<FrameworkMethod> afters, Object target, Statement statement) {
    if (afters.isEmpty()) {
      return statement;
    } else {
      return new Statement() {
        @Override
        public void evaluate() throws Throwable {
          List<Throwable> errors = new ArrayList<Throwable>();
          try {
            statement.evaluate();
          } catch (Throwable e) {
            errors.add(e);
          } finally {
            for (FrameworkMethod after : afters) {
              try {
                invokeTestMethod(after, target);
              } catch (Throwable e) {
                errors.add(e);
              }
            }
          }
          MultipleFailureException.assertEmpty(errors);
        }
      };
    }
  }

  static void pushTimeout(long timeout) {
    timeoutStack.push(timeout);
  }

  static void popTimeout() {
    timeoutStack.pop();
  }
}
