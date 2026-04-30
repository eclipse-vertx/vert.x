package io.vertx.test.core;

import io.netty.util.internal.PlatformDependent;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.junit.*;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.*;

import java.io.Closeable;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

public class VertxRunner extends BlockJUnit4ClassRunner {

  private final TestClass testClass;

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
      if (paramType != Checkpoint.class && paramType != Vertx.class) {
        throw new Exception("Method " + fMethod.getName() + " should have no parameters or " +
            "the " + Checkpoint.class.getName() + " / " + Vertx.class.getName() + " parameter");
      }
    }
  }

  @Override
  protected Statement methodInvoker(FrameworkMethod method, Object test) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        Callable<Void> cleanup = invokeTestMethod(method, test);
        cleanup.call();
      }
    };
  }

  static class VertxInstance {
    final VertxProvider provider;
    final Vertx vertx;
    public VertxInstance(VertxProvider provider, Vertx vertx) {
      this.provider = provider;
      this.vertx = vertx;
    }
  }

  protected Callable<Void> invokeTestMethod(FrameworkMethod fMethod, Object test) throws InvocationTargetException, IllegalAccessException, TimeoutException, InterruptedException {
    TestContext testContext = new TestContext();
    Checkpoint invocationCheckpoint = new Checkpoint();
    testContext.checkpoints.add(invocationCheckpoint);
    Method method = fMethod.getMethod();
    Parameter[] params = method.getParameters();
    Object[] args = new Object[params.length];
    Annotation[][] paramsAnnotations = method.getParameterAnnotations();
    List<VertxInstance> vertxInstances = new ArrayList<>();
    for (int i = 0;i < params.length;i++) {
      Parameter param = method.getParameters()[i];
      Class<?> paramType = param.getType();
      if (paramType == Checkpoint.class) {
        Checkpoint checkpoint = new Checkpoint();
        args[i] = checkpoint;
        testContext.checkpoints.add(checkpoint);
      } else if (paramType == Vertx.class) {
        Annotation[] paramAnnotations = paramsAnnotations[i];
        VertxProvider provider = null;
        for (Annotation paramAnnotation : paramAnnotations) {
          if (paramAnnotation instanceof ProvidedBy) {
            ProvidedBy providedBy = (ProvidedBy) paramAnnotation;
            try {
              Class<? extends VertxProvider> providerClass = providedBy.value();
              provider = providerClass.getConstructor().newInstance();
              break;
            } catch (Exception e) {
              // Handle me
              throw new RuntimeException(e);
            }
          }
        }
        if (provider == null) {
          // Default provider
          provider = Vertx::vertx;
        }
        Vertx vertx;
        try {
          vertx = provider.call();
        } catch (Exception e) {
          // Handle me
          throw new RuntimeException(e);
        }
        vertxInstances.add(new VertxInstance(provider, vertx));
        args[i] = vertx;
        vertx.exceptionHandler(err -> {
          reportFailure(test, err);
        });
      }
    }
    failureReporterMap.put(test, testContext);
    try {
      method.invoke(test, (Object[]) args);
    } catch (InvocationTargetException e) {
      PlatformDependent.throwException(e.getCause());
    } catch (IllegalAccessException e) {
      PlatformDependent.throwException(e);
    }
    try {
      // Try to satisfy invocation checkpoint
      invocationCheckpoint.succeed();
      // Now awaits checkpoints
      for (Checkpoint checkpoint : testContext.checkpoints) {
        if (!checkpoint.latch.await(10, TimeUnit.SECONDS)) {
          throw new TimeoutException("Unsatisfied checkpoint");
        }
        checkpoint.awaitSuccess();
      }
    } finally {
      failureReporterMap.remove(test);
    }
    return () -> {
      for (VertxInstance vertxInstance : vertxInstances) {
        try {
          vertxInstance.provider.close(vertxInstance.vertx, Duration.ofSeconds(10));
        } catch (Exception ignore) {
        } finally {
          if (vertxInstance.provider instanceof Closeable) {
            try {
              ((Closeable)vertxInstance.provider).close();
            } catch (Exception ignore) {
            }
          }
        }
      }
      return null;
    };
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
          List<Callable<?>> beforeCleanup = cleanerMap.get(target);
          for (FrameworkMethod before : befores) {
            beforeCleanup.add(invokeTestMethod(before, target));
          }
          statement.evaluate();
        }
      };
    }
  }

  private Map<Object, List<Callable<?>>> cleanerMap = new HashMap<>();
  private Map<Object, TestContext> failureReporterMap = new HashMap<>();

  private Statement withAfters(List<FrameworkMethod> afters, Object target, Statement statement) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        ArrayList<Callable<?>> cleanTasks = new ArrayList<>();
        cleanerMap.put(target, cleanTasks);
        List<Throwable> errors = new ArrayList<>();
        try {
          try {
            statement.evaluate();
          } catch (Throwable e) {
            errors.add(e);
          } finally {
            for (FrameworkMethod after : afters) {
              try {
                invokeTestMethod(after, target).call();
              } catch (Throwable e) {
                errors.add(e);
              }
            }
          }
        } finally {
          cleanerMap.remove(target);
          for (Callable<?> cleanerTask : cleanTasks) {
            cleanerTask.call();
          }
        }
        MultipleFailureException.assertEmpty(errors);
      }
    };
  }

  private void reportFailure(Object test, Throwable failure) {
    TestContext testContext = failureReporterMap.get(test);
    if (testContext != null) {
      for (Checkpoint checkpoint : testContext.checkpoints) {
        checkpoint.fail(failure);
      }
    }
  }

  private static class TestContext {
    final List<Checkpoint> checkpoints = new ArrayList<>();
  }
}
