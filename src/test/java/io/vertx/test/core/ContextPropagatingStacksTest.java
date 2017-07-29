package io.vertx.test.core;

import io.vertx.core.Context;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.LongAdder;

/**
 * @author fmzakari
 */
public class ContextPropagatingStacksTest extends VertxTestBase {

  /**
   * Test long stack use cases --
   * The expected stack traces are included to help guide the reader on the expected output.
   */

  /*
  Example of what this stack should look like:
  <pre>
  java.lang.IllegalStateException: This is a sample exception
  at io.vertx.test.core.ContextTest.lambda$testLongStacks$30(ContextTest.java:386)
  at io.vertx.core.impl.StackTracePropagationHandler.lambda$wrap$0(StackTracePropagationHandler.java:25)
  at io.vertx.core.impl.ContextImpl.lambda$wrapTask$2(ContextImpl.java:353)
  at io.netty.util.concurrent.AbstractEventExecutor.safeExecute(AbstractEventExecutor.java:163)
  at io.netty.util.concurrent.SingleThreadEventExecutor.runAllTasks(SingleThreadEventExecutor.java:403)
  at io.netty.channel.nio.NioEventLoop.run(NioEventLoop.java:445)
  at io.netty.util.concurrent.SingleThreadEventExecutor$5.run(SingleThreadEventExecutor.java:858)
  at java.lang.Thread.run(Thread.java:748)
  at Async.Async(Unknown Source)
  at java.lang.Thread.getStackTrace(Thread.java:1559)
  at io.vertx.core.impl.StackTracePropagationHandler.wrap(StackTracePropagationHandler.java:21)
  at io.vertx.core.impl.ContextImpl.runOnContext(ContextImpl.java:217)
  at io.vertx.test.core.ContextTest.testLongStacks(ContextTest.java:385)
  at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
  at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)
  at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
  at java.lang.reflect.Method.invoke(Method.java:498)
  at org.junit.runners.model.FrameworkMethod$1.runReflectiveCall(FrameworkMethod.java:50)
  at org.junit.internal.runners.model.ReflectiveCallable.run(ReflectiveCallable.java:12)
  at org.junit.runners.model.FrameworkMethod.invokeExplosively(FrameworkMethod.java:47)
  at org.junit.internal.runners.statements.InvokeMethod.evaluate(InvokeMethod.java:17)
  at org.junit.internal.runners.statements.RunBefores.evaluate(RunBefores.java:26)
  at org.junit.internal.runners.statements.RunAfters.evaluate(RunAfters.java:27)
  at org.junit.rules.TestWatcher$1.evaluate(TestWatcher.java:55)
  at org.junit.rules.RunRules.evaluate(RunRules.java:20)
  at org.junit.runners.ParentRunner.runLeaf(ParentRunner.java:325)
  at org.junit.runners.BlockJUnit4ClassRunner.runChild(BlockJUnit4ClassRunner.java:78)
  at org.junit.runners.BlockJUnit4ClassRunner.runChild(BlockJUnit4ClassRunner.java:57)
  at org.junit.runners.ParentRunner$3.run(ParentRunner.java:290)
  at org.junit.runners.ParentRunner$1.schedule(ParentRunner.java:71)
  at org.junit.runners.ParentRunner.runChildren(ParentRunner.java:288)
  at org.junit.runners.ParentRunner.access$000(ParentRunner.java:58)
  at org.junit.runners.ParentRunner$2.evaluate(ParentRunner.java:268)
  at org.junit.runners.ParentRunner.run(ParentRunner.java:363)
  at org.junit.runner.JUnitCore.run(JUnitCore.java:137)
  at com.intellij.junit4.JUnit4IdeaTestRunner.startRunnerWithArgs(JUnit4IdeaTestRunner.java:68)
  at com.intellij.rt.execution.junit.IdeaTestRunner$Repeater.startRunnerWithArgs(IdeaTestRunner.java:51)
  at com.intellij.rt.execution.junit.JUnitStarter.prepareStreamsAndStart(JUnitStarter.java:242)
  at com.intellij.rt.execution.junit.JUnitStarter.main(JUnitStarter.java:70)
  </pre>
  */
  @Test
  public void testLongStacksSimpleEventLoop() {
    try {
      System.setProperty("vertx.longStacks", "true");
      LongAdder foundAsync = new LongAdder();

      Context context = vertx.getOrCreateContext();
      context.exceptionHandler(throwable -> {
        throwable.printStackTrace();
        for (StackTraceElement e : throwable.getStackTrace()) {
          if (e.getClassName().equals("Async")) {
            foundAsync.increment();
          }
        }
        testComplete();
      });

      context.runOnContext(v -> {
        throw new IllegalStateException("This is a sample exception");
      });
      await();
      assertEquals(foundAsync.sum(), 1L);
    } finally {
      System.clearProperty("vertx.longStacks");
    }
  }

  @Test
  public void testLongStacksSimpleBlocking() {
    try {
      System.setProperty("vertx.longStacks", "true");
      LongAdder foundAsync = new LongAdder();

      Context context = vertx.getOrCreateContext();
      context.executeBlocking(future -> {
        future.fail(new IllegalStateException("This is a sample exception"));
      }, result -> {
        Throwable throwable = result.cause();
        throwable.printStackTrace();
        for (StackTraceElement e : throwable.getStackTrace()) {
          if (e.getClassName().equals("Async")) {
            foundAsync.increment();
          }
        }
        testComplete();
      });
      await();
      assertEquals(foundAsync.sum(), 1L);
    } finally {
      System.clearProperty("vertx.longStacks");
    }
  }

  /*  java.lang.IllegalStateException: This is a sample exception
    at io.vertx.test.core.ContextTest.lambda$null$33(ContextTest.java:472)
    at io.vertx.core.impl.StackTracePropagationHandler.lambda$wrapWithFuture$0(StackTracePropagationHandler.java:39)
    at io.vertx.core.impl.ContextImpl.lambda$executeBlocking$1(ContextImpl.java:291)
    at io.vertx.core.impl.TaskQueue.run(TaskQueue.java:80)
    at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1142)
    at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:617)
    at java.lang.Thread.run(Thread.java:748)
    at Async.Async(Unknown Source)
    at java.lang.Thread.getStackTrace(Thread.java:1559)
    at io.vertx.core.impl.StackTracePropagationHandler.wrapWithFuture(StackTracePropagationHandler.java:34)
    at io.vertx.core.impl.ContextImpl.executeBlocking(ContextImpl.java:275)
    at io.vertx.core.impl.ContextImpl.executeBlocking(ContextImpl.java:255)
    at io.vertx.core.impl.ContextImpl.executeBlocking(ContextImpl.java:260)
    at io.vertx.test.core.ContextTest.lambda$testLongStacksEventLoopWithBlocking$35(ContextTest.java:471)
    at io.vertx.core.impl.StackTracePropagationHandler.lambda$wrap$2(StackTracePropagationHandler.java:101)
    at io.vertx.core.impl.ContextImpl.lambda$wrapTask$2(ContextImpl.java:358)
    at io.netty.util.concurrent.AbstractEventExecutor.safeExecute(AbstractEventExecutor.java:163)
    at io.netty.util.concurrent.SingleThreadEventExecutor.runAllTasks(SingleThreadEventExecutor.java:403)
    at io.netty.channel.nio.NioEventLoop.run(NioEventLoop.java:445)
    at io.netty.util.concurrent.SingleThreadEventExecutor$5.run(SingleThreadEventExecutor.java:858)
    at java.lang.Thread.run(Thread.java:748)
    at Async.Async(Unknown Source)
    at java.lang.Thread.getStackTrace(Thread.java:1559)
    at io.vertx.core.impl.StackTracePropagationHandler.wrap(StackTracePropagationHandler.java:94)
    at io.vertx.core.impl.ContextImpl.runOnContext(ContextImpl.java:217)
    at io.vertx.test.core.ContextTest.testLongStacksEventLoopWithBlocking(ContextTest.java:469)
    at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
    at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)
    at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
    at java.lang.reflect.Method.invoke(Method.java:498)
    at org.junit.runners.model.FrameworkMethod$1.runReflectiveCall(FrameworkMethod.java:50)
    at org.junit.internal.runners.model.ReflectiveCallable.run(ReflectiveCallable.java:12)
    at org.junit.runners.model.FrameworkMethod.invokeExplosively(FrameworkMethod.java:47)
    at org.junit.internal.runners.statements.InvokeMethod.evaluate(InvokeMethod.java:17)
    at org.junit.internal.runners.statements.RunBefores.evaluate(RunBefores.java:26)
    at org.junit.internal.runners.statements.RunAfters.evaluate(RunAfters.java:27)
    at org.junit.rules.TestWatcher$1.evaluate(TestWatcher.java:55)
    at org.junit.rules.RunRules.evaluate(RunRules.java:20)
    at org.junit.runners.ParentRunner.runLeaf(ParentRunner.java:325)
    at org.junit.runners.BlockJUnit4ClassRunner.runChild(BlockJUnit4ClassRunner.java:78)
    at org.junit.runners.BlockJUnit4ClassRunner.runChild(BlockJUnit4ClassRunner.java:57)
    at org.junit.runners.ParentRunner$3.run(ParentRunner.java:290)
    at org.junit.runners.ParentRunner$1.schedule(ParentRunner.java:71)
    at org.junit.runners.ParentRunner.runChildren(ParentRunner.java:288)
    at org.junit.runners.ParentRunner.access$000(ParentRunner.java:58)
    at org.junit.runners.ParentRunner$2.evaluate(ParentRunner.java:268)
    at org.junit.runners.ParentRunner.run(ParentRunner.java:363)
    at org.junit.runner.JUnitCore.run(JUnitCore.java:137)
    at com.intellij.junit4.JUnit4IdeaTestRunner.startRunnerWithArgs(JUnit4IdeaTestRunner.java:68)
    at com.intellij.rt.execution.junit.IdeaTestRunner$Repeater.startRunnerWithArgs(IdeaTestRunner.java:51)
    at com.intellij.rt.execution.junit.JUnitStarter.prepareStreamsAndStart(JUnitStarter.java:242)
    at com.intellij.rt.execution.junit.JUnitStarter.main(JUnitStarter.java:70)*/
  @Test
  public void testLongStacksEventLoopWithBlocking() {
    try {
      System.setProperty("vertx.longStacks", "true");
      LongAdder foundAsync = new LongAdder();

      Context context = vertx.getOrCreateContext();
      context.runOnContext(event -> {

        context.executeBlocking(future -> {
          future.fail(new IllegalStateException("This is a sample exception"));
        }, result -> {
          Throwable throwable = result.cause();
          throwable.printStackTrace();
          for (StackTraceElement e : throwable.getStackTrace()) {
            if (e.getClassName().equals("Async")) {
              foundAsync.increment();
            }
          }
          testComplete();
        });

      });

      await();
      assertEquals(foundAsync.sum(), 2L);
    } finally {
      System.clearProperty("vertx.longStacks");
    }
  }

  /**
   * java.lang.IllegalStateException: This is a sample exception
   at io.vertx.test.core.ContextPropagatingStacksTest.lambda$null$7(ContextPropagatingStacksTest.java:212)
   at io.vertx.core.impl.StackTracePropagationHandler.lambda$wrapWithFuture$0(StackTracePropagationHandler.java:43)
   at io.vertx.core.impl.ContextImpl.lambda$executeBlocking$2(ContextImpl.java:291)
   at io.vertx.core.impl.TaskQueue.run(TaskQueue.java:80)
   at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1142)
   at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:617)
   at java.lang.Thread.run(Thread.java:748)
   at Async.Async(Unknown Source)
   at java.lang.Thread.getStackTrace(Thread.java:1559)
   at io.vertx.core.impl.StackTracePropagationHandler.wrapWithFuture(StackTracePropagationHandler.java:34)
   at io.vertx.core.impl.ContextImpl.executeBlocking(ContextImpl.java:275)
   at io.vertx.core.impl.ContextImpl.executeBlocking(ContextImpl.java:255)
   at io.vertx.core.impl.ContextImpl.executeBlocking(ContextImpl.java:260)
   at io.vertx.test.core.ContextPropagatingStacksTest.lambda$null$9(ContextPropagatingStacksTest.java:211)
   at io.vertx.core.impl.StackTracePropagationHandler.lambda$wrapWithFuture$0(StackTracePropagationHandler.java:43)
   at io.vertx.core.impl.ContextImpl.lambda$executeBlocking$2(ContextImpl.java:291)
   at io.vertx.core.impl.TaskQueue.run(TaskQueue.java:80)
   at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1142)
   at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:617)
   at java.lang.Thread.run(Thread.java:748)
   at Async.Async(Unknown Source)
   at java.lang.Thread.getStackTrace(Thread.java:1559)
   at io.vertx.core.impl.StackTracePropagationHandler.wrapWithFuture(StackTracePropagationHandler.java:34)
   at io.vertx.core.impl.ContextImpl.executeBlocking(ContextImpl.java:275)
   at io.vertx.core.impl.ContextImpl.executeBlocking(ContextImpl.java:255)
   at io.vertx.core.impl.ContextImpl.executeBlocking(ContextImpl.java:260)
   at io.vertx.test.core.ContextPropagatingStacksTest.lambda$testLongStacksEventLoopWithBlockingNested$11(ContextPropagatingStacksTest.java:209)
   at io.vertx.core.impl.StackTracePropagationHandler.lambda$wrap$2(StackTracePropagationHandler.java:110)
   at io.vertx.core.impl.ContextImpl.lambda$wrapTask$3(ContextImpl.java:358)
   at io.netty.util.concurrent.AbstractEventExecutor.safeExecute(AbstractEventExecutor.java:163)
   at io.netty.util.concurrent.SingleThreadEventExecutor.runAllTasks(SingleThreadEventExecutor.java:403)
   at io.netty.channel.nio.NioEventLoop.run(NioEventLoop.java:445)
   at io.netty.util.concurrent.SingleThreadEventExecutor$5.run(SingleThreadEventExecutor.java:858)
   at java.lang.Thread.run(Thread.java:748)
   at Async.Async(Unknown Source)
   at java.lang.Thread.getStackTrace(Thread.java:1559)
   at io.vertx.core.impl.StackTracePropagationHandler.wrap(StackTracePropagationHandler.java:101)
   at io.vertx.core.impl.ContextImpl.runOnContext(ContextImpl.java:217)
   at io.vertx.test.core.ContextPropagatingStacksTest.testLongStacksEventLoopWithBlockingNested(ContextPropagatingStacksTest.java:207)
   at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
   at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)
   at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
   at java.lang.reflect.Method.invoke(Method.java:498)
   at org.junit.runners.model.FrameworkMethod$1.runReflectiveCall(FrameworkMethod.java:50)
   at org.junit.internal.runners.model.ReflectiveCallable.run(ReflectiveCallable.java:12)
   at org.junit.runners.model.FrameworkMethod.invokeExplosively(FrameworkMethod.java:47)
   at org.junit.internal.runners.statements.InvokeMethod.evaluate(InvokeMethod.java:17)
   at org.junit.internal.runners.statements.RunBefores.evaluate(RunBefores.java:26)
   at org.junit.internal.runners.statements.RunAfters.evaluate(RunAfters.java:27)
   at org.junit.rules.TestWatcher$1.evaluate(TestWatcher.java:55)
   at org.junit.rules.RunRules.evaluate(RunRules.java:20)
   at org.junit.runners.ParentRunner.runLeaf(ParentRunner.java:325)
   at org.junit.runners.BlockJUnit4ClassRunner.runChild(BlockJUnit4ClassRunner.java:78)
   at org.junit.runners.BlockJUnit4ClassRunner.runChild(BlockJUnit4ClassRunner.java:57)
   at org.junit.runners.ParentRunner$3.run(ParentRunner.java:290)
   at org.junit.runners.ParentRunner$1.schedule(ParentRunner.java:71)
   at org.junit.runners.ParentRunner.runChildren(ParentRunner.java:288)
   at org.junit.runners.ParentRunner.access$000(ParentRunner.java:58)
   at org.junit.runners.ParentRunner$2.evaluate(ParentRunner.java:268)
   at org.junit.runners.ParentRunner.run(ParentRunner.java:363)
   at org.junit.runner.JUnitCore.run(JUnitCore.java:137)
   at com.intellij.junit4.JUnit4IdeaTestRunner.startRunnerWithArgs(JUnit4IdeaTestRunner.java:68)
   at com.intellij.rt.execution.junit.IdeaTestRunner$Repeater.startRunnerWithArgs(IdeaTestRunner.java:51)
   at com.intellij.rt.execution.junit.JUnitStarter.prepareStreamsAndStart(JUnitStarter.java:242)
   at com.intellij.rt.execution.junit.JUnitStarter.main(JUnitStarter.java:70)
   java.lang.AssertionError: expected:<0> but was:<2>
   at org.junit.Assert.fail(Assert.java:88)
   at org.junit.Assert.failNotEquals(Assert.java:834)
   at org.junit.Assert.assertEquals(Assert.java:645)
   at org.junit.Assert.assertEquals(Assert.java:631)
   at io.vertx.test.core.AsyncTestBase.assertEquals(AsyncTestBase.java:235)
   at io.vertx.test.core.ContextPropagatingStacksTest.testLongStacksEventLoopWithBlockingNested(ContextPropagatingStacksTest.java:225)
   at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
   at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)
   at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
   at java.lang.reflect.Method.invoke(Method.java:498)
   at org.junit.runners.model.FrameworkMethod$1.runReflectiveCall(FrameworkMethod.java:50)
   at org.junit.internal.runners.model.ReflectiveCallable.run(ReflectiveCallable.java:12)
   at org.junit.runners.model.FrameworkMethod.invokeExplosively(FrameworkMethod.java:47)
   at org.junit.internal.runners.statements.InvokeMethod.evaluate(InvokeMethod.java:17)
   at org.junit.internal.runners.statements.RunBefores.evaluate(RunBefores.java:26)
   at org.junit.internal.runners.statements.RunAfters.evaluate(RunAfters.java:27)
   at org.junit.rules.TestWatcher$1.evaluate(TestWatcher.java:55)
   at org.junit.rules.RunRules.evaluate(RunRules.java:20)
   at org.junit.runners.ParentRunner.runLeaf(ParentRunner.java:325)
   at org.junit.runners.BlockJUnit4ClassRunner.runChild(BlockJUnit4ClassRunner.java:78)
   at org.junit.runners.BlockJUnit4ClassRunner.runChild(BlockJUnit4ClassRunner.java:57)
   at org.junit.runners.ParentRunner$3.run(ParentRunner.java:290)
   at org.junit.runners.ParentRunner$1.schedule(ParentRunner.java:71)
   at org.junit.runners.ParentRunner.runChildren(ParentRunner.java:288)
   at org.junit.runners.ParentRunner.access$000(ParentRunner.java:58)
   at org.junit.runners.ParentRunner$2.evaluate(ParentRunner.java:268)
   at org.junit.runners.ParentRunner.run(ParentRunner.java:363)
   at org.junit.runner.JUnitCore.run(JUnitCore.java:137)
   at com.intellij.junit4.JUnit4IdeaTestRunner.startRunnerWithArgs(JUnit4IdeaTestRunner.java:68)
   at com.intellij.rt.execution.junit.IdeaTestRunner$Repeater.startRunnerWithArgs(IdeaTestRunner.java:51)
   at com.intellij.rt.execution.junit.JUnitStarter.prepareStreamsAndStart(JUnitStarter.java:242)
   at com.intellij.rt.execution.junit.JUnitStarter.main(JUnitStarter.java:70)
   */
  @Test
  public void testLongStacksEventLoopWithBlockingNested() {
    try {
      System.setProperty("vertx.longStacks", "true");
      LongAdder foundAsync = new LongAdder();

      Context context = vertx.getOrCreateContext();
      context.runOnContext(event -> {

        context.executeBlocking(future -> {

          context.executeBlocking(future2 -> {
            future2.fail(new IllegalStateException("This is a sample exception"));
          }, result -> {
            Throwable throwable = result.cause();
            throwable.printStackTrace();
            for (StackTraceElement e : throwable.getStackTrace()) {
              if (e.getClassName().equals("Async")) {
                foundAsync.increment();
              }
            }
            testComplete();
          });

        }, result -> {
        });

      });

      await();
      assertEquals(foundAsync.sum(), 3L);
    } finally {
      System.clearProperty("vertx.longStacks");
    }
  }

  @Test
  public void testLongStacksConsecutive() throws Exception {

    try {
      System.setProperty("vertx.longStacks", "true");
      CountDownLatch latch = new CountDownLatch(2);

      Context context = vertx.getOrCreateContext();
      context.runOnContext(event -> {

        context.executeBlocking(future -> {
          future.fail(new IllegalStateException("This is a sample exception"));
        }, result -> {
          Throwable throwable = result.cause();
          throwable.printStackTrace();
          latch.countDown();
        });

        context.executeBlocking(future -> {
          future.fail(new IllegalStateException("This is a sample exception"));
        }, result -> {
          Throwable throwable = result.cause();
          throwable.printStackTrace();
          latch.countDown();
        });

      });

      latch.await();
    } finally {
      System.clearProperty("vertx.longStacks");
    }


  }

}
